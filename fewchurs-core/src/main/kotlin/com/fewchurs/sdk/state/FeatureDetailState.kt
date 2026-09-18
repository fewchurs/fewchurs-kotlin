package com.fewchurs.sdk.state

import com.fewchurs.sdk.data.FewchursRepository
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.Comment
import com.fewchurs.sdk.model.Feature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public sealed interface CommentsUiState {
    public data object Loading : CommentsUiState

    public data class Content(
        val comments: List<Comment>,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
        val loadMoreError: FewchursException? = null,
    ) : CommentsUiState

    public data class Error(val error: FewchursException) : CommentsUiState
}

/**
 * Headless state for one feature: the feature itself (live from the shared cache), its
 * comments, voting, following and commenting.
 *
 * Create with [com.fewchurs.sdk.Fewchurs.featureDetailState].
 */
public class FeatureDetailState internal constructor(
    private val repository: FewchursRepository,
    public val featureId: String,
    private val scope: CoroutineScope,
    private val pageSize: Int,
    private val ownsScope: Boolean,
) {
    /** Null until loaded, or after the feature was deleted on the server. */
    public val feature: StateFlow<Feature?> = repository.features
        .map { it[featureId] }
        .stateIn(scope, SharingStarted.Eagerly, repository.features.value[featureId])

    private val _comments = MutableStateFlow<CommentsUiState>(CommentsUiState.Loading)
    public val comments: StateFlow<CommentsUiState> = _comments.asStateFlow()

    private val _messages = MutableSharedFlow<FewchursException>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    public val messages: SharedFlow<FewchursException> = _messages.asSharedFlow()

    private var cursor: String? = null
    private var commentsJob: Job? = null

    init {
        scope.launch { repository.voteFailures.collect { _messages.emit(it) } }
        if (feature.value == null) {
            scope.launch {
                try {
                    repository.getFeature(featureId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FewchursException) {
                    _messages.emit(e)
                }
            }
        }
        refreshComments()
    }

    public fun toggleVote(): Unit = repository.toggleVote(featureId)

    /** Follows with [email]; the user gets an email when the status changes. Throws on failure. */
    public suspend fun follow(email: String): Unit = repository.follow(featureId, email)

    public suspend fun unfollow(): Unit = repository.unfollow(featureId)

    /** Posts a comment and appends it to [comments]. Throws on failure. */
    public suspend fun addComment(text: String, authorName: String? = null): Comment {
        val comment = repository.addComment(featureId, text, authorName)
        _comments.update { current ->
            when (current) {
                is CommentsUiState.Content -> current.copy(comments = (current.comments + comment).distinctBy { it.id })
                else -> CommentsUiState.Content(listOf(comment), canLoadMore = false)
            }
        }
        return comment
    }

    public fun refreshComments() {
        commentsJob?.cancel()
        val previous = _comments.value
        if (previous !is CommentsUiState.Content) _comments.value = CommentsUiState.Loading
        commentsJob = scope.launch {
            try {
                val (items, next) = fetchComments(null)
                cursor = next
                _comments.value = CommentsUiState.Content(items, canLoadMore = next != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FewchursException) {
                if (previous is CommentsUiState.Content) _messages.emit(e) else _comments.value = CommentsUiState.Error(e)
            }
        }
    }

    public fun loadMoreComments() {
        val current = _comments.value as? CommentsUiState.Content ?: return
        if (!current.canLoadMore || current.isLoadingMore || commentsJob?.isActive == true) return
        _comments.value = current.copy(isLoadingMore = true, loadMoreError = null)
        commentsJob = scope.launch {
            try {
                val (items, next) = fetchComments(cursor)
                cursor = next
                _comments.update { s ->
                    val c = s as? CommentsUiState.Content ?: return@update s
                    c.copy(comments = (c.comments + items).distinctBy { it.id }, canLoadMore = next != null, isLoadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FewchursException) {
                _comments.update { s -> (s as? CommentsUiState.Content)?.copy(isLoadingMore = false, loadMoreError = e) ?: s }
            }
        }
    }

    public fun close() {
        if (ownsScope) scope.cancel()
    }

    // Deleted comments are filtered after paging on the server, so a page can come back short or
    // empty while more exist. Keep going until something shows up or the pages run out.
    private suspend fun fetchComments(startCursor: String?): Pair<List<Comment>, String?> {
        var next = startCursor
        val items = mutableListOf<Comment>()
        var rounds = 0
        do {
            val page = repository.listComments(featureId, pageSize, next)
            items += page.items
            next = page.nextCursor
            rounds++
        } while (items.isEmpty() && next != null && rounds < MAX_EMPTY_ROUNDS)
        return items to next
    }

    private companion object {
        const val MAX_EMPTY_ROUNDS = 10
    }
}
