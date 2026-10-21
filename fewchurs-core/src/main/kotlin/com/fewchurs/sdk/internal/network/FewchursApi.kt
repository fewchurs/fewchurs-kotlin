package com.fewchurs.sdk.internal.network

import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.internal.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thin HTTP layer over the `/api/v1` endpoints. Stateless apart from the OkHttp client. */
internal class FewchursApi(
    baseUrl: String,
    private val apiKey: String,
    private val deviceId: () -> String,
    private val userAgent: String,
    private val acceptLanguage: () -> String,
    private val logger: Logger,
    httpClient: OkHttpClient? = null,
    private val retryBaseDelayMs: Long = 400,
) {
    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    private val client: OkHttpClient = (httpClient?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun config(): ConfigDto = execute(get("config"), ConfigDto.serializer(), retry = true)

    suspend fun listFeatures(sort: String, limit: Int, cursor: String?): FeatureListDto =
        execute(
            get("features") {
                addQueryParameter("sort", sort)
                addQueryParameter("limit", limit.coerceIn(1, 100).toString())
                if (cursor != null) addQueryParameter("cursor", cursor)
            },
            FeatureListDto.serializer(),
            retry = true,
        )

    suspend fun getFeature(id: String): FeatureDto = execute(get("features", id), FeatureDto.serializer(), retry = true)

    // Creates content, so never retried automatically (would risk duplicates).
    suspend fun createFeature(body: CreateFeatureBody): FeatureDto =
        execute(send("POST", body, CreateFeatureBody.serializer(), "features"), FeatureDto.serializer(), retry = false)

    // Votes are idempotent on the server, so retrying is safe.
    suspend fun setVote(featureId: String, voted: Boolean): VoteDto =
        execute(send(if (voted) "POST" else "DELETE", null, null, "features", featureId, "vote"), VoteDto.serializer(), retry = true)

    suspend fun follow(featureId: String, email: String): FollowDto =
        execute(send("POST", FollowBody(email), FollowBody.serializer(), "features", featureId, "follow"), FollowDto.serializer(), retry = false)

    suspend fun unfollow(featureId: String): FollowDto =
        execute(send("DELETE", null, null, "features", featureId, "follow"), FollowDto.serializer(), retry = false)

    suspend fun listComments(featureId: String, limit: Int, cursor: String?): CommentListDto =
        execute(
            get("features", featureId, "comments") {
                addQueryParameter("limit", limit.coerceIn(1, 100).toString())
                if (cursor != null) addQueryParameter("cursor", cursor)
            },
            CommentListDto.serializer(),
            retry = true,
        )

    suspend fun createComment(body: CreateCommentBody): CommentDto =
        execute(send("POST", body, CreateCommentBody.serializer(), "comments"), CommentDto.serializer(), retry = false)

    // --- plumbing ---

    private fun url(vararg segments: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            query()
        }.build()

    private fun get(vararg segments: String, query: HttpUrl.Builder.() -> Unit = {}): () -> Request =
        { baseRequest(url(*segments, query = query)).get().build() }

    private fun <B> send(method: String, body: B?, serializer: KSerializer<B>?, vararg segments: String): () -> Request = {
        val requestBody = if (body != null && serializer != null) {
            json.encodeToString(serializer, body).toRequestBody(JSON)
        } else if (method == "DELETE") {
            null
        } else {
            "".toRequestBody(JSON)
        }
        baseRequest(url(*segments)).method(method, requestBody).build()
    }

    private fun baseRequest(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .header("X-Device-Id", deviceId())
        .header("User-Agent", userAgent)
        .header("Accept-Language", acceptLanguage())
        .header("Accept", "application/json")

    private suspend fun <T> execute(buildRequest: () -> Request, serializer: KSerializer<T>, retry: Boolean): T {
        var attempt = 0
        while (true) {
            try {
                return executeOnce(buildRequest, serializer)
            } catch (e: FewchursException) {
                val retryable = e is FewchursException.Network || e is FewchursException.Server
                if (!retry || !retryable || attempt >= MAX_RETRIES) throw e
                delay(retryBaseDelayMs * (1L shl attempt)) // 400ms, 800ms
                attempt++
            }
        }
    }

    private suspend fun <T> executeOnce(buildRequest: () -> Request, serializer: KSerializer<T>): T {
        val request = withContext(Dispatchers.IO) { buildRequest() } // device ID provider may touch disk
        logger.debug("→ ${request.method} ${request.url}")
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            logger.debug("✕ ${request.method} ${request.url}: ${e.message}")
            throw FewchursException.Network(e)
        }
        return withContext(Dispatchers.IO) {
            response.use { res ->
                val body = try {
                    res.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw FewchursException.Network(e)
                }
                logger.debug("← ${res.code} ${request.method} ${request.url}")
                if (!res.isSuccessful) throw mapError(res.code, body)
                try {
                    json.decodeFromString(serializer, body)
                } catch (e: SerializationException) {
                    throw FewchursException.Unknown("Unexpected response from ${request.url.encodedPath}", e)
                } catch (e: IllegalArgumentException) {
                    throw FewchursException.Unknown("Unexpected response from ${request.url.encodedPath}", e)
                }
            }
        }
    }

    private fun mapError(status: Int, body: String): FewchursException {
        val error = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), body).error }.getOrNull()
        val message = error?.message ?: "HTTP $status"
        val mapped = when (error?.code) {
            "invalid_key", "unauthorized" -> FewchursException.InvalidApiKey()
            "missing_device_id" -> FewchursException.Internal("Server did not receive a device ID: $message")
            "validation_failed" -> FewchursException.Validation(message)
            "rate_limited" -> FewchursException.RateLimited()
            "limit_reached" -> FewchursException.LimitReached(message)
            "not_found" -> FewchursException.NotFound()
            "internal" -> FewchursException.Server(status)
            else -> when {
                status == 401 -> FewchursException.InvalidApiKey()
                status == 404 -> FewchursException.NotFound()
                status == 429 -> FewchursException.RateLimited()
                status == 402 -> FewchursException.LimitReached(message)
                status >= 500 -> FewchursException.Server(status)
                status == 400 && error != null -> FewchursException.Validation(message)
                else -> FewchursException.Unknown("Unexpected HTTP $status: $message")
            }
        }
        if (mapped is FewchursException.InvalidApiKey) {
            logger.error("Fewchurs rejected the API key. Check the key in your dashboard (Apps → API keys).")
        }
        return mapped
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        })
    }

    private companion object {
        const val MAX_RETRIES = 2
        val JSON = "application/json; charset=utf-8".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
