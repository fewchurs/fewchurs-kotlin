package com.fewchurs.sdk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.state.BoardUiState
import com.fewchurs.sdk.ui.R
import com.fewchurs.sdk.ui.components.FeatureCard
import com.fewchurs.sdk.ui.components.FeatureCardActions
import com.fewchurs.sdk.ui.components.MessageState
import com.fewchurs.sdk.ui.components.PoweredByBadge
import com.fewchurs.sdk.ui.components.SkeletonCard
import com.fewchurs.sdk.ui.components.VoteButtonState
import com.fewchurs.sdk.ui.config.BoardOptions
import com.fewchurs.sdk.ui.config.BoardTab
import com.fewchurs.sdk.ui.config.SubmitButton
import com.fewchurs.sdk.ui.config.UiConfig
import com.fewchurs.sdk.ui.internal.localized
import com.fewchurs.sdk.ui.internal.text
import com.fewchurs.sdk.ui.theme.FewchursTheme
import kotlinx.coroutines.flow.collectLatest

/** Slot content from [com.fewchurs.sdk.ui.FewchursBoard]. */
internal class BoardSlots(
    val header: (@Composable () -> Unit)?,
    val emptyState: (@Composable () -> Unit)?,
    val featureCard: (@Composable (Feature, FeatureCardActions) -> Unit)?,
    val voteButton: (@Composable (VoteButtonState) -> Unit)?,
)

@Composable
internal fun BoardScreen(
    viewModel: BoardViewModel,
    options: BoardOptions,
    slots: BoardSlots,
    windowInsets: WindowInsets,
    onClose: (() -> Unit)?,
    onOpen: (Feature) -> Unit,
    onSubmit: () -> Unit,
) {
    val strings = UiConfig.strings
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val board = viewModel.board(tab)
    val state by board.state.collectAsStateWithLifecycle()
    // One list state per tab so switching tabs keeps each scroll position (and survives rotation).
    val listStates = viewModel.tabs.associateWith { t ->
        key(t) { rememberSaveable(saver = LazyListState.Saver) { LazyListState() } }
    }
    val listState = listStates.getValue(tab)
    val submitLabel = text(strings.submitButton, R.string.fewchurs_submit_button)

    val messageTexts = rememberErrorTexts()
    LaunchedEffect(board) {
        board.messages.collectLatest { snackbar.showSnackbar(messageTexts.of(it)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.scrollToTopRequests.collect { t -> listStates[t]?.scrollToItem(0) }
    }

    Scaffold(
        contentWindowInsets = windowInsets,
        containerColor = FewchursTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    title = { Text(text(strings.boardTitle, R.string.fewchurs_board_title)) },
                    navigationIcon = {
                        if (onClose != null) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fewchurs_close))
                            }
                        }
                    },
                    actions = {
                        if (options.allowSubmissions && options.submitButtonStyle == SubmitButton.TopBarAction) {
                            IconButton(onClick = onSubmit) { Icon(Icons.Filled.Add, contentDescription = submitLabel) }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = FewchursTheme.colors.background),
                )
                if (viewModel.tabs.size > 1) {
                    PrimaryTabRow(
                        selectedTabIndex = viewModel.tabs.indexOf(tab),
                        containerColor = FewchursTheme.colors.background,
                    ) {
                        viewModel.tabs.forEach { t ->
                            Tab(
                                selected = t == tab,
                                onClick = { viewModel.select(t) },
                                text = { Text(stringResource(t.labelRes())) },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (options.allowSubmissions && options.submitButtonStyle == SubmitButton.ExtendedFab) {
                ExtendedFloatingActionButton(
                    onClick = onSubmit,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(submitLabel) },
                    containerColor = FewchursTheme.colors.primary,
                    contentColor = FewchursTheme.colors.onPrimary,
                )
            }
        },
        bottomBar = {
            if (options.allowSubmissions && options.submitButtonStyle == SubmitButton.BottomBar) {
                Button(
                    onClick = onSubmit,
                    shape = FewchursTheme.shapes.button,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(FewchursTheme.spacing.screen),
                ) { Text(submitLabel) }
            }
        },
    ) { padding ->
        val content = state as? BoardUiState.Content
        PullToRefreshBox(
            isRefreshing = content?.isRefreshing == true,
            onRefresh = board::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                BoardUiState.Loading -> SkeletonList()
                is BoardUiState.Error -> ErrorContent(s.error, onRetry = board::retry)
                is BoardUiState.Content -> FeatureList(
                    content = s,
                    listState = listState,
                    hideVoteCounts = settings.hideVoteCounts,
                    showBranding = settings.showBranding,
                    slots = slots,
                    onOpen = onOpen,
                    onVote = board::toggleVote,
                    onLoadMore = board::loadMore,
                    bottomSpace = if (options.submitButtonStyle == SubmitButton.ExtendedFab && options.allowSubmissions) 88.dp else 16.dp,
                )
            }
        }
    }
}

@Composable
private fun FeatureList(
    content: BoardUiState.Content,
    listState: LazyListState,
    hideVoteCounts: Boolean,
    showBranding: Boolean,
    slots: BoardSlots,
    onOpen: (Feature) -> Unit,
    onVote: (String) -> Unit,
    onLoadMore: () -> Unit,
    bottomSpace: androidx.compose.ui.unit.Dp,
) {
    val spacing = FewchursTheme.spacing
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, content.canLoadMore, content.features.size) {
        if (nearEnd && content.canLoadMore && content.loadMoreError == null) onLoadMore()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.screen, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        slots.header?.let { header -> item(key = "header") { header() } }
        if (content.features.isEmpty()) {
            item(key = "empty") {
                slots.emptyState?.invoke() ?: MessageState(
                    title = text(UiConfig.strings.emptyTitle, R.string.fewchurs_empty_title),
                    message = text(UiConfig.strings.emptyMessage, R.string.fewchurs_empty_message),
                )
            }
        }
        items(content.features, key = { it.id }) { feature ->
            val actions = FeatureCardActions(toggleVote = { onVote(feature.id) }, open = { onOpen(feature) })
            if (slots.featureCard != null) {
                slots.featureCard.invoke(feature, actions)
            } else {
                FeatureCard(feature, actions, hideVoteCounts, voteButton = slots.voteButton)
            }
        }
        if (content.isLoadingMore) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (content.loadMoreError != null) {
            item(key = "loadMoreError") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.fewchurs_load_more_failed) + " " + stringResource(R.string.fewchurs_retry))
                    }
                }
            }
        }
        if (showBranding && !content.canLoadMore) {
            item(key = "branding") { PoweredByBadge() }
        }
    }
}

@Composable
private fun SkeletonList() {
    val spacing = FewchursTheme.spacing
    Column(
        Modifier.fillMaxSize().padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap),
    ) { repeat(5) { SkeletonCard() } }
}

@Composable
private fun ErrorContent(error: FewchursException, onRetry: () -> Unit) {
    // Wrapped in a list so pull-to-refresh also works from the error state.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val offline = error is FewchursException.Network
            MessageState(
                title = stringResource(if (offline) R.string.fewchurs_offline_title else R.string.fewchurs_error_title),
                message = if (offline) stringResource(R.string.fewchurs_offline_message) else error.localized(),
                actionLabel = stringResource(R.string.fewchurs_retry),
                onAction = onRetry,
            )
        }
    }
}

private fun BoardTab.labelRes(): Int = when (this) {
    BoardTab.Top -> R.string.fewchurs_tab_top
    BoardTab.New -> R.string.fewchurs_tab_new
    BoardTab.Roadmap -> R.string.fewchurs_tab_roadmap
}

/** Resolves error strings up front so they can be used from coroutines. */
internal class ErrorTexts(private val map: Map<Class<*>, String>, private val generic: String) {
    fun of(e: FewchursException): String = if (e is FewchursException.Validation) e.userMessage else map[e.javaClass] ?: generic
}

@Composable
internal fun rememberErrorTexts(): ErrorTexts = ErrorTexts(
    mapOf(
        FewchursException.Network::class.java to stringResource(R.string.fewchurs_error_offline),
        FewchursException.RateLimited::class.java to stringResource(R.string.fewchurs_error_rate_limited),
        FewchursException.LimitReached::class.java to stringResource(R.string.fewchurs_error_limit_reached),
        FewchursException.NotFound::class.java to stringResource(R.string.fewchurs_error_not_found),
    ),
    stringResource(R.string.fewchurs_error_generic),
)
