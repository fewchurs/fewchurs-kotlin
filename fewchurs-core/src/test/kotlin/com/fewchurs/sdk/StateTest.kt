package com.fewchurs.sdk

import app.cash.turbine.test
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.model.Sort
import com.fewchurs.sdk.state.BoardState
import com.fewchurs.sdk.state.BoardUiState
import com.fewchurs.sdk.state.CommentsUiState
import com.fewchurs.sdk.state.FeatureDetailState
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StateTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    @Test fun `board loads, pages and stops at the end`() = runTest {
        server.enqueue(featurePage(feature("a"), feature("b"), next = "b"))
        server.enqueue(featurePage(feature("c")))
        val repo = server.repository(backgroundScope)
        val board = BoardState(repo, Sort.Top, backgroundScope, 2, { true }, ownsScope = false)

        board.state.test {
            assertEquals(BoardUiState.Loading, awaitItem())
            val first = awaitItem() as BoardUiState.Content
            assertEquals(listOf("a", "b"), first.features.map { it.id })
            assertTrue(first.canLoadMore)

            board.loadMore()
            var s = awaitItem() as BoardUiState.Content
            while (s.isLoadingMore) s = awaitItem() as BoardUiState.Content
            assertEquals(listOf("a", "b", "c"), s.features.map { it.id })
            assertFalse(s.canLoadMore)
        }
        server.takeRequest()
        assertEquals("/api/v1/features?sort=top&limit=2&cursor=b", server.takeRequest().path)
    }

    @Test fun `initial error then retry`() = runTest {
        server.enqueue(error("invalid_key", 401))
        server.enqueue(featurePage())
        val board = BoardState(server.repository(backgroundScope), Sort.New, backgroundScope, 20, { true }, false)
        eventually { board.state.value is BoardUiState.Error }
        board.retry()
        eventually { board.state.value is BoardUiState.Content }
        assertEquals(BoardUiState.Content(emptyList(), canLoadMore = false), board.state.value)
    }

    @Test fun `hidden declined page keeps loading until something is visible`() = runTest {
        server.enqueue(featurePage(feature("d1", status = "declined"), next = "d1"))
        server.enqueue(featurePage(feature("ok")))
        val board = BoardState(
            server.repository(backgroundScope), Sort.Top, backgroundScope, 1,
            { f: Feature -> f.status != FeatureStatus.DECLINED }, false,
        )
        eventually { board.state.value is BoardUiState.Content }
        assertEquals(listOf("ok"), (board.state.value as BoardUiState.Content).features.map { it.id })
    }

    @Test fun `votes and submissions show up on the board`() = runTest {
        server.enqueue(featurePage(feature("a", votes = 1)))
        server.enqueue(json(feature("fresh"), 201))
        server.enqueue(json("""{"upvoteCount":2,"hasVoted":true}"""))
        val repo = server.repository(backgroundScope)
        val board = BoardState(repo, Sort.New, backgroundScope, 20, { true }, false)
        eventually { board.state.value is BoardUiState.Content }

        repo.submitFeature("Fresh idea", "", null, false)
        board.toggleVote("a")
        eventually { repo.features.value["a"]?.upvoteCount == 2 && server.requestCount == 3 }
        eventually { (board.state.value as BoardUiState.Content).features.size == 2 }

        val content = board.state.value as BoardUiState.Content
        assertEquals(listOf("fresh", "a"), content.features.map { it.id })
        assertEquals(2, content.features[1].upvoteCount)
    }

    @Test fun `comments skip empty pages caused by deleted comments`() = runTest {
        server.enqueue(featurePage(feature("f")))
        server.enqueue(commentPage(next = "c2")) // page of only deleted comments
        server.enqueue(commentPage(comment("c3"), comment("c4", device = DEVICE)))
        val repo = server.repository(backgroundScope)
        repo.listFeatures(Sort.Top, 20, null)
        val detail = FeatureDetailState(repo, "f", backgroundScope, 2, false)
        eventually { detail.comments.value is CommentsUiState.Content }

        val comments = detail.comments.value as CommentsUiState.Content
        assertEquals(listOf("c3", "c4"), comments.comments.map { it.id })
        assertFalse(comments.canLoadMore)
        assertTrue(comments.comments[1].isMine)
        assertEquals("f", detail.feature.value?.id)
    }
}
