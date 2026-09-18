package com.fewchurs.sdk

import com.fewchurs.sdk.data.FewchursRepository
import com.fewchurs.sdk.internal.Logger
import com.fewchurs.sdk.internal.network.FewchursApi
import com.fewchurs.sdk.model.FewchursEvent
import kotlinx.coroutines.CoroutineScope
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

internal const val DEVICE = "device-123"

internal fun MockWebServer.api(): FewchursApi = FewchursApi(
    baseUrl = url("/api/v1").toString(),
    apiKey = "fr_test_key",
    deviceId = { DEVICE },
    userAgent = "Fewchurs-Android/test",
    acceptLanguage = { "en-US" },
    logger = Logger(LogLevel.None),
    retryBaseDelayMs = 1,
)

internal fun MockWebServer.repository(scope: CoroutineScope, events: MutableList<FewchursEvent> = mutableListOf()) =
    FewchursRepository(api(), { DEVICE }, { events += it }, Logger(LogLevel.None), scope, voteDebounceMs = 100)

internal fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
    .setHeader("Content-Type", "application/json").setBody(body)

internal fun error(code: String, status: Int, message: String = "msg") =
    json("""{"error":{"code":"$code","message":"$message"}}""", status)

internal fun feature(
    id: String,
    votes: Int = 0,
    voted: Boolean = false,
    status: String = "open",
    author: String = "someone-else",
) = """{"id":"$id","title":"Title $id","description":"Desc","status":"$status","upvoteCount":$votes,
    "commentCount":0,"followerCount":0,"authorDeviceId":"$author","authorIsSubscriber":false,"translation":null,
    "createdAt":"2026-09-18T10:15:30.123Z","updatedAt":"2026-09-18T10:15:30.123Z","hasVoted":$voted,"isFollowing":false}"""

internal fun featurePage(vararg features: String, next: String? = null) =
    json("""{"features":[${features.joinToString(",")}],"nextCursor":${next?.let { "\"$it\"" } ?: "null"}}""")

internal fun comment(id: String, device: String = "other") =
    """{"id":"$id","text":"Hi $id","authorName":"Sam","deviceId":"$device","isDeveloper":false,"createdAt":"2026-09-18T10:15:30.000Z"}"""

internal fun commentPage(vararg comments: String, next: String? = null) =
    json("""{"comments":[${comments.joinToString(",")}],"nextCursor":${next?.let { "\"$it\"" } ?: "null"}}""")
