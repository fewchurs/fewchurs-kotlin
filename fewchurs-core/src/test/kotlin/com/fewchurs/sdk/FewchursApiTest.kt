package com.fewchurs.sdk

import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.internal.network.CreateFeatureBody
import com.fewchurs.sdk.internal.network.IsoDate
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FewchursApiTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    @Test fun `sends auth, device and user agent headers`() = runTest {
        server.enqueue(featurePage(feature("a")))
        server.api().listFeatures("top", 20, null)
        val request = server.takeRequest()
        assertEquals("Bearer fr_test_key", request.getHeader("Authorization"))
        assertEquals(DEVICE, request.getHeader("X-Device-Id"))
        assertEquals("Fewchurs-Android/test", request.getHeader("User-Agent"))
        assertEquals("en-US", request.getHeader("Accept-Language"))
        assertEquals("/api/v1/features?sort=top&limit=20", request.path)
    }

    @Test fun `passes cursor and clamps limit`() = runTest {
        server.enqueue(featurePage())
        server.api().listFeatures("new", 500, "abc")
        assertEquals("/api/v1/features?sort=new&limit=100&cursor=abc", server.takeRequest().path)
    }

    @Test fun `parses features and ignores unknown fields`() = runTest {
        server.enqueue(featurePage(feature("a", votes = 3, voted = true).replace("\"title\"", "\"brandNew\":1,\"title\""), next = "a"))
        val page = server.api().listFeatures("top", 20, null)
        assertEquals("a", page.nextCursor)
        assertEquals(3, page.features.single().upvoteCount)
        assertTrue(page.features.single().hasVoted)
    }

    @Test fun `posts JSON body when creating a feature`() = runTest {
        server.enqueue(json(feature("new"), 201))
        server.api().createFeature(CreateFeatureBody("Dark mode", "Please", "a@b.com", true))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/features", request.path)
        assertEquals("""{"title":"Dark mode","description":"Please","email":"a@b.com","isSubscriber":true}""", request.body.readUtf8())
    }

    @Test fun `vote uses POST and unvote uses DELETE`() = runTest {
        server.enqueue(json("""{"upvoteCount":5,"hasVoted":true}"""))
        server.enqueue(json("""{"upvoteCount":4,"hasVoted":false}"""))
        assertEquals(5, server.api().setVote("f1", true).upvoteCount)
        assertEquals(4, server.api().setVote("f1", false).upvoteCount)
        assertEquals("POST /api/v1/features/f1/vote", server.takeRequest().requestLine.substringBeforeLast(" "))
        assertEquals("DELETE /api/v1/features/f1/vote", server.takeRequest().requestLine.substringBeforeLast(" "))
    }

    @Test fun `maps error envelope codes`() = runTest {
        val cases = listOf(
            error("invalid_key", 401) to FewchursException.InvalidApiKey::class,
            error("validation_failed", 400, "Title too short") to FewchursException.Validation::class,
            error("rate_limited", 429) to FewchursException.RateLimited::class,
            error("limit_reached", 402) to FewchursException.LimitReached::class,
            error("not_found", 404) to FewchursException.NotFound::class,
            error("missing_device_id", 400) to FewchursException.Internal::class,
            json("<html>oops</html>", 404) to FewchursException.NotFound::class,
        )
        for ((response, expected) in cases) {
            server.enqueue(response)
            try {
                server.api().createFeature(CreateFeatureBody("abc", ""))
                fail("expected $expected")
            } catch (e: FewchursException) {
                assertEquals(expected, e::class)
            }
        }
    }

    @Test fun `validation error keeps server message`() = runTest {
        server.enqueue(error("validation_failed", 400, "Title too short"))
        try {
            server.api().createFeature(CreateFeatureBody("abc", ""))
            fail()
        } catch (e: FewchursException.Validation) {
            assertEquals("Title too short", e.userMessage)
        }
    }

    @Test fun `retries GET on 5xx then succeeds`() = runTest {
        server.enqueue(error("internal", 500))
        server.enqueue(error("internal", 500))
        server.enqueue(featurePage(feature("a")))
        assertEquals(1, server.api().listFeatures("top", 20, null).features.size)
        assertEquals(3, server.requestCount)
    }

    @Test fun `gives up after two retries`() = runTest {
        repeat(3) { server.enqueue(error("internal", 500)) }
        try {
            server.api().listFeatures("top", 20, null)
            fail()
        } catch (e: FewchursException.Server) {
            assertEquals(500, e.statusCode)
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun `never retries content-creating POSTs`() = runTest {
        server.enqueue(error("internal", 500))
        try {
            server.api().createFeature(CreateFeatureBody("abc", ""))
            fail()
        } catch (_: FewchursException.Server) {
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun `connection failure maps to Network`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        try {
            server.api().config()
            fail()
        } catch (_: FewchursException.Network) {
        }
    }

    @Test fun `bad JSON maps to Unknown`() = runTest {
        server.enqueue(json("not json"))
        try {
            server.api().config()
            fail()
        } catch (_: FewchursException.Unknown) {
        }
    }

    @Test fun `config falls back to defaults for missing fields`() = runTest {
        server.enqueue(json("""{"showBranding":false}"""))
        val config = server.api().config()
        assertEquals(false, config.showBranding)
        assertEquals(false, config.hideVoteCounts)
        assertNull(config.appName)
    }

    @Test fun `parses ISO timestamps`() {
        assertEquals(1789726530123L, IsoDate.parse("2026-09-18T10:15:30.123Z"))
        assertEquals(1789726530000L, IsoDate.parse("2026-09-18T10:15:30Z"))
        assertEquals(1789726530000L, IsoDate.parse("2026-09-18T12:15:30+02:00"))
        assertEquals(0L, IsoDate.parse("yesterday"))
        assertEquals(0L, IsoDate.parse(null))
    }

    @Test fun `default base URL is the production API`() {
        assertEquals("https://www.fewchurs.shop/api/v1", FewchursConfig.DEFAULT_BASE_URL)
    }
}
