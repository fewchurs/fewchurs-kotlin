package com.fewchurs.sdk.state

import com.fewchurs.sdk.data.FewchursRepository
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.Sort
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public sealed interface BoardUiState {
    public data object Loading : BoardUiState

    public data class Content(
        val features: List<Feature>,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false,
        /** Set when the last "load more" failed; call [BoardState.loadMore] to retry. */
        val loadMoreError: FewchursException? = null,
    ) : BoardUiState

    public data class Error(val error: FewchursException) : BoardUiState
}

/**
 * Headless, paginated feature list for one [sort]. Observe [state]; call [loadMore] near the end
 * of the list. Votes are optimistic and shared with every other state holder.
 *
 * Create with [com.fewchurs.sdk.Fewchurs.boardState].
 */
public class BoardState internal constructor(
    private val repository: FewchursRepository,
    public val sort: Sort,
    private val scope: CoroutineScope,
    private val pageSize: Int,
    private val filter: (Feature) -> Boolean,
    private val ownsScope: Boolean,
) {
    private data class Internal(
        val ids: List<String> = emptyList(),
        val cursor: String? = null,
        val endReached: Boolean = false,
        val initialLoading: Boolean = true,
        val initialError: FewchursException? = null,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreError: FewchursException? = null,
    )

    private val internal = MutableStateFlow(Internal())
    private var loadJob: Job? = null

    private val _messages = MutableSharedFlow<FewchursException>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One-off errors worth a snackbar (failed vote, failed refresh while content is shown). */
    public val messages: SharedFlow<FewchursException> = _messages.asSharedFlow()

    public val state: StateFlow<BoardUiState> = combine(internal, repository.features) { s, cache ->
        val features = s.ids.mapNotNull { cache[it] }.filter(filter)
        when {
            s.initialLoading && s.ids.isEmpty() -> BoardUiState.Loading
            s.initialError != null && s.ids.isEmpty() -> BoardUiState.Error(s.initialError)
            else -> BoardUiState.Content(
                features = features,
                canLoadMore = !s.endReached,
                isLoadingMore = s.isLoadingMore,
                isRefreshing = s.isRefreshing,
                loadMoreError = s.loadMoreError,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, BoardUiState.Loading)

    init {
        scope.launch {
            repository.submitted.collect { feature ->
                if (sort == Sort.New && filter(feature)) {
                    internal.update { it.copy(ids = listOf(feature.id) + (it.ids - feature.id)) }
                }
            }
        }
        scope.launch { repository.voteFailures.collect { _messages.emit(it) } }
        loadFirstPage(isRefresh = false)
    }

    /** Reloads the first page. Keeps showing current items while refreshing. */
    public fun refresh() {
        loadFirstPage(isRefresh = internal.value.ids.isNotEmpty())
    }

    /** Retries after [BoardUiState.Error]. */
    public fun retry(): Unit = refresh()

    public fun loadMore() {
        val s = internal.value
        if (s.endReached || s.initialLoading || s.isLoadingMore || loadJob?.isActive == true) return
        internal.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        loadJob = scope.launch {
            try {
                val (ids, cursor) = fetchVisible(s.cursor)
                internal.update {
                    it.copy(ids = (it.ids + ids).distinct(), cursor = cursor, endReached = cursor == null, isLoadingMore = false)
                }
            } catch (e: CancellationException) {
                internal.update { it.copy(isLoadingMore = false) }
                throw e
            } catch (e: FewchursException) {
                internal.update { it.copy(isLoadingMore = false, loadMoreError = e) }
            }
        }
    }

    public fun toggleVote(featureId: String): Unit = repository.toggleVote(featureId)

    /** Stops background work. Only needed for state holders created without a scope. */
    public fun close() {
        if (ownsScope) scope.cancel()
    }

    private fun loadFirstPage(isRefresh: Boolean) {
        loadJob?.cancel()
        internal.update {
            if (isRefresh) it.copy(isRefreshing = true, isLoadingMore = false)
            else it.copy(initialLoading = true, initialError = null, isLoadingMore = false)
        }
        loadJob = scope.launch {
            try {
                val (ids, cursor) = fetchVisible(null)
                internal.value = Internal(ids = ids, cursor = cursor, endReached = cursor == null, initialLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FewchursException) {
                val hadContent = internal.value.ids.isNotEmpty()
                internal.update { it.copy(initialLoading = false, isRefreshing = false, initialError = if (hadContent) null else e) }
                if (hadContent) _messages.emit(e)
            }
        }
    }

    // Keeps paging while a page has nothing visible (e.g. all declined), so the list never stalls.
    private suspend fun fetchVisible(startCursor: String?): Pair<List<String>, String?> {
        var cursor = startCursor
        val ids = mutableListOf<String>()
        var visible = 0
        var rounds = 0
        do {
            val page = repository.listFeatures(sort, pageSize, cursor)
            ids += page.items.map { it.id }
            visible += page.items.count(filter)
            cursor = page.nextCursor
            rounds++
        } while (visible == 0 && cursor != null && rounds < MAX_EMPTY_ROUNDS)
        return ids to cursor
    }

    private companion object {
        const val MAX_EMPTY_ROUNDS = 5
    }
}
