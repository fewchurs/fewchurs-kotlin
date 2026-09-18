package com.fewchurs.sdk

import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.model.FewchursEvent
import com.fewchurs.sdk.model.Sort
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    @Test fun `maps models and derives isMine from device id`() = runTest {
        server.enqueue(featurePage(feature("mine", author = DEVICE), feature("theirs", status = "shipped_to_mars")))
        val repo = server.repository(backgroundScope)
        val page = repo.listFeatures(Sort.Top, 20, null)
        assertTrue(page.items[0].isMine)
        assertFalse(page.items[1].isMine)
        assertEquals(FeatureStatus.UNKNOWN, page.items[1].status)
        assertEquals(1789726530123L, page.items[0].createdAtMillis)
    }

    @Test fun `optimistic vote updates instantly then reconciles with server count`() = runTest {
        server.enqueue(featurePage(feature("f", votes = 4)))
        server.enqueue(json("""{"upvoteCount":10,"hasVoted":true}"""))
        val events = mutableListOf<FewchursEvent>()
        val repo = server.repository(backgroundScope, events)
        repo.listFeatures(Sort.Top, 20, null)

        repo.toggleVote("f")
        assertEquals(5, repo.features.value["f"]!!.upvoteCount)
        assertTrue(repo.features.value["f"]!!.hasVoted)

        eventually { repo.features.value["f"]!!.upvoteCount == 10 }
        server.takeRequest()
        assertEquals("POST", server.takeRequest().method)
        assertTrue(FewchursEvent.Voted("f", true) in events)
    }

    @Test fun `rapid taps collapse to final state`() = runTest {
        server.enqueue(featurePage(feature("f", votes = 4)))
        val repo = server.repository(backgroundScope)
        repo.listFeatures(Sort.Top, 20, null)

        repo.toggleVote("f")
        repo.toggleVote("f")
        advanceUntilIdle()

        assertEquals(1, server.requestCount) // back to the original state: no vote request at all
        assertFalse(repo.features.value["f"]!!.hasVoted)
        assertEquals(4, repo.features.value["f"]!!.upvoteCount)
    }

    @Test fun `failed vote rolls back and reports`() = runTest {
        server.enqueue(featurePage(feature("f", votes = 4)))
        server.enqueue(error("rate_limited", 429)) // not retried: 4xx
        val repo = server.repository(backgroundScope)
        repo.listFeatures(Sort.Top, 20, null)
        val failures = mutableListOf<FewchursException>()
        backgroundScope.launchCollect(repo.voteFailures, failures)

        repo.toggleVote("f")
        eventually { failures.isNotEmpty() }

        assertFalse(repo.features.value["f"]!!.hasVoted)
        assertEquals(4, repo.features.value["f"]!!.upvoteCount)
        assertTrue(failures.single() is FewchursException.RateLimited)
    }

    @Test fun `not found removes feature from cache`() = runTest {
        server.enqueue(featurePage(feature("gone")))
        server.enqueue(error("not_found", 404))
        val repo = server.repository(backgroundScope)
        repo.listFeatures(Sort.Top, 20, null)
        try {
            repo.unfollow("gone")
            fail()
        } catch (_: FewchursException.NotFound) {
        }
        assertFalse("gone" in repo.features.value)
    }

    @Test fun `client-side validation mirrors server rules`() = runTest {
        val repo = server.repository(backgroundScope)
        for ((title, email) in listOf("ab" to null, "x".repeat(101) to null, "Valid" to "not-an-email")) {
            try {
                repo.submitFeature(title, "", email, false)
                fail("$title / $email should be rejected")
            } catch (_: FewchursException.Validation) {
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `submit marks feature as mine and following when email given`() = runTest {
        server.enqueue(json(feature("new"), 201))
        val events = mutableListOf<FewchursEvent>()
        val repo = server.repository(backgroundScope, events)
        val f = repo.submitFeature("  Dark mode  ", "", "a@b.com", false)
        assertTrue(f.isMine && f.isFollowing)
        assertTrue(FewchursEvent.FeatureSubmitted("new") in events)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"title\":\"Dark mode\""))
    }

    @Test fun `comment updates count and is mine`() = runTest {
        server.enqueue(featurePage(feature("f")))
        server.enqueue(json(comment("c1", device = DEVICE), 201))
        val repo = server.repository(backgroundScope)
        repo.listFeatures(Sort.Top, 20, null)
        val c = repo.addComment("f", "Nice", "  ")
        assertTrue(c.isMine)
        assertEquals(1, repo.features.value["f"]!!.commentCount)
        server.takeRequest()
        assertEquals("""{"featureId":"f","text":"Nice"}""", server.takeRequest().body.readUtf8())
    }

    @Test fun `settings fall back to defaults when offline and are cached when loaded`() = runTest {
        server.enqueue(error("invalid_key", 401))
        val repo = server.repository(backgroundScope)
        assertTrue(repo.loadSettings().showBranding)

        server.enqueue(json("""{"showBranding":false,"hideVoteCounts":true}"""))
        assertTrue(repo.loadSettings().hideVoteCounts)
        repo.loadSettings()
        assertEquals(2, server.requestCount)
    }
}
