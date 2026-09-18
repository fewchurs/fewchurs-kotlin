package com.fewchurs.sdk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FewchursEvent
import com.fewchurs.sdk.ui.components.FeatureCardActions
import com.fewchurs.sdk.ui.components.MessageState
import com.fewchurs.sdk.ui.components.VoteButtonState
import com.fewchurs.sdk.ui.config.UiConfig
import com.fewchurs.sdk.ui.internal.UiPrefs
import com.fewchurs.sdk.ui.screens.BoardScreen
import com.fewchurs.sdk.ui.screens.BoardSlots
import com.fewchurs.sdk.ui.screens.BoardViewModel
import com.fewchurs.sdk.ui.screens.DetailScreen
import com.fewchurs.sdk.ui.screens.DetailViewModel
import com.fewchurs.sdk.ui.screens.SubmitScreen
import com.fewchurs.sdk.ui.screens.SubmitViewModel
import com.fewchurs.sdk.ui.theme.FewchursTheme
import com.fewchurs.sdk.ui.theme.ResolvedFewchursTheme

/**
 * The full feature-request board: list, detail, comments, follow and submit.
 * Place it anywhere — a screen, a tab, a navigation destination.
 *
 * @param onClose shows a close button when set.
 * @param windowInsets insets the board pads for. Pass `WindowInsets(0)` when the host already
 *   handles system bars.
 * @param header content above the list.
 * @param emptyState replaces the "no requests yet" message.
 * @param featureCard replaces each row. Voting, paging and errors keep working.
 * @param voteButton replaces only the vote control (list and detail).
 */
@Composable
public fun FewchursBoard(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    header: (@Composable () -> Unit)? = null,
    emptyState: (@Composable () -> Unit)? = null,
    featureCard: (@Composable (Feature, FeatureCardActions) -> Unit)? = null,
    voteButton: (@Composable (VoteButtonState) -> Unit)? = null,
) {
    ResolvedFewchursTheme {
        Surface(modifier.fillMaxSize(), color = FewchursTheme.colors.background, contentColor = FewchursTheme.colors.onSurface) {
            if (!Fewchurs.isConfigured) {
                MessageState(
                    title = stringResource(R.string.fewchurs_not_configured_title),
                    message = stringResource(R.string.fewchurs_not_configured_message),
                )
                return@Surface
            }
            BoardHost(onClose, windowInsets, BoardSlots(header, emptyState, featureCard, voteButton))
        }
    }
}

/** The board in a modal bottom sheet. */
@Composable
public fun FewchursBoardSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    featureCard: (@Composable (Feature, FeatureCardActions) -> Unit)? = null,
    voteButton: (@Composable (VoteButtonState) -> Unit)? = null,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        FewchursBoard(
            modifier = Modifier.fillMaxHeight(),
            onClose = onDismiss,
            windowInsets = WindowInsets(0),
            header = header,
            featureCard = featureCard,
            voteButton = voteButton,
        )
    }
}

private const val BOARD = "board"
private const val DETAIL = "detail:"
private const val SUBMIT = "submit:"

@Composable
private fun BoardHost(onClose: (() -> Unit)?, windowInsets: WindowInsets, slots: BoardSlots) {
    val context = LocalContext.current
    val prefs = remember(context) { UiPrefs(context) }
    val options = UiConfig.board
    val boardVm: BoardViewModel = viewModel(key = "fewchurs_board") { BoardViewModel(createSavedStateHandle(), options) }
    val settings by boardVm.settings.collectAsStateWithLifecycle()

    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            opened = true
            Fewchurs.emit(FewchursEvent.BoardOpened)
        }
    }

    // A tiny back stack of route strings; saveable, so it survives rotation and process death.
    var stack by rememberSaveable { mutableStateOf(listOf(BOARD)) }
    var submitSession by rememberSaveable { mutableIntStateOf(0) }
    val pop = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { pop() }

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            val forward = targetState != BOARD
            (slideInHorizontally(tween(250)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(tween(250)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
        },
        label = "fewchursNav",
    ) { route ->
        when {
            route.startsWith(DETAIL) -> {
                val id = route.removePrefix(DETAIL)
                val vm: DetailViewModel = viewModel(key = "fewchurs_detail_$id") { DetailViewModel(id, prefs, options.pageSize) }
                DetailScreen(vm, options, settings.hideVoteCounts, slots.voteButton, windowInsets, onBack = pop)
            }
            route.startsWith(SUBMIT) -> {
                val vm: SubmitViewModel = viewModel(key = "fewchurs_$route") {
                    SubmitViewModel(createSavedStateHandle(), options.emailField, prefs)
                }
                SubmitScreen(
                    viewModel = vm,
                    emailField = options.emailField,
                    windowInsets = windowInsets,
                    onBack = pop,
                    onDone = {
                        boardVm.onSubmitted()
                        stack = listOf(BOARD)
                    },
                )
            }
            else -> BoardScreen(
                viewModel = boardVm,
                options = options,
                slots = slots,
                windowInsets = windowInsets,
                onClose = onClose,
                onOpen = { stack = stack + (DETAIL + it.id) },
                onSubmit = { stack = stack + (SUBMIT + ++submitSession) },
            )
        }
    }
}
