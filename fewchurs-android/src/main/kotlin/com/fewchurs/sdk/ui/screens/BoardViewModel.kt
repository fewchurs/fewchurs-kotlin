package com.fewchurs.sdk.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.model.BoardSettings
import com.fewchurs.sdk.model.Sort
import com.fewchurs.sdk.state.BoardState
import com.fewchurs.sdk.ui.config.BoardOptions
import com.fewchurs.sdk.ui.config.BoardTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class BoardViewModel(private val handle: SavedStateHandle, private val options: BoardOptions) : ViewModel() {
    private val repository = Fewchurs.repository

    // Roadmap needs a server-side status filter; hidden until the API supports it.
    val tabs: List<BoardTab> = options.tabs.filter { it != BoardTab.Roadmap }.distinct().ifEmpty { listOf(BoardTab.Top, BoardTab.New) }

    val selectedTab: StateFlow<BoardTab> =
        handle.getStateFlow(TAB_KEY, options.defaultTab.takeIf { it in tabs } ?: tabs.first())

    val settings: StateFlow<BoardSettings> = repository.settings
        .map { it ?: BoardSettings() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BoardSettings())

    private val boards = HashMap<BoardTab, BoardState>()

    private val scrollToTop = Channel<BoardTab>(Channel.CONFLATED)
    val scrollToTopRequests: Flow<BoardTab> = scrollToTop.receiveAsFlow()

    init {
        viewModelScope.launch { repository.loadSettings() }
    }

    fun board(tab: BoardTab): BoardState = boards.getOrPut(tab) {
        Fewchurs.boardState(
            sort = if (tab == BoardTab.New) Sort.New else Sort.Top,
            scope = viewModelScope,
            pageSize = options.pageSize,
            hideDeclined = !options.showDeclined,
        )
    }

    fun select(tab: BoardTab) {
        handle[TAB_KEY] = tab
    }

    /** After a submission: jump to "New", where it now sits on top. */
    fun onSubmitted() {
        if (BoardTab.New in tabs) {
            select(BoardTab.New)
            scrollToTop.trySend(BoardTab.New)
        } else {
            board(selectedTab.value).refresh()
        }
    }

    private companion object {
        const val TAB_KEY = "fewchurs_tab"
    }
}
