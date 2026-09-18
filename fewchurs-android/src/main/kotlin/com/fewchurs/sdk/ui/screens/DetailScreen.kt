package com.fewchurs.sdk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.model.Comment
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FeatureValidation
import com.fewchurs.sdk.state.CommentsUiState
import com.fewchurs.sdk.ui.R
import com.fewchurs.sdk.ui.components.FewchursTextField
import com.fewchurs.sdk.ui.components.FeatureMeta
import com.fewchurs.sdk.ui.components.MessageState
import com.fewchurs.sdk.ui.components.VoteButton
import com.fewchurs.sdk.ui.components.VoteButtonState
import com.fewchurs.sdk.ui.config.BoardOptions
import com.fewchurs.sdk.ui.config.UiConfig
import com.fewchurs.sdk.ui.internal.localized
import com.fewchurs.sdk.ui.internal.relativeTime
import com.fewchurs.sdk.ui.internal.text
import com.fewchurs.sdk.ui.theme.FewchursTheme

@Composable
internal fun DetailScreen(
    viewModel: DetailViewModel,
    options: BoardOptions,
    hideVoteCounts: Boolean,
    voteButton: (@Composable (VoteButtonState) -> Unit)?,
    windowInsets: WindowInsets,
    onBack: () -> Unit,
) {
    val feature by viewModel.detail.feature.collectAsStateWithLifecycle()
    val comments by viewModel.detail.comments.collectAsStateWithLifecycle()
    val followPending by viewModel.followPending.collectAsStateWithLifecycle()
    val commentPending by viewModel.commentPending.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var askEmail by rememberSaveable { mutableStateOf(false) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var authorName by rememberSaveable { mutableStateOf(viewModel.savedAuthorName) }

    val errors = rememberErrorTexts()
    val followedText = stringResource(R.string.fewchurs_followed_message)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.Error -> snackbar.showSnackbar(errors.of(event.error))
                DetailEvent.AskEmail -> askEmail = true
                DetailEvent.Followed -> snackbar.showSnackbar(followedText)
                DetailEvent.CommentSent -> commentText = ""
            }
        }
    }

    Scaffold(
        contentWindowInsets = windowInsets,
        containerColor = FewchursTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fewchurs_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FewchursTheme.colors.background),
            )
        },
        bottomBar = {
            if (options.allowComments && feature != null) {
                Composer(
                    text = commentText,
                    onTextChange = { commentText = it.take(FeatureValidation.COMMENT_MAX) },
                    name = authorName,
                    onNameChange = { authorName = it.take(FeatureValidation.AUTHOR_NAME_MAX) },
                    pending = commentPending,
                    onSend = { viewModel.sendComment(commentText, authorName) },
                )
            }
        },
    ) { padding ->
        val f = feature
        when {
            f == null && missing -> MessageState(
                title = stringResource(R.string.fewchurs_error_not_found),
                message = "",
                actionLabel = stringResource(R.string.fewchurs_back),
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            f == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> DetailContent(
                feature = f,
                comments = comments,
                hideVoteCounts = hideVoteCounts,
                voteButton = voteButton,
                followPending = followPending,
                onVote = viewModel.detail::toggleVote,
                onFollow = viewModel::toggleFollow,
                onLoadMore = viewModel.detail::loadMoreComments,
                onRetryComments = viewModel.detail::refreshComments,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (askEmail) {
        EmailDialog(
            initial = Fewchurs.config?.currentUserEmail().orEmpty(),
            onDismiss = { askEmail = false },
            onConfirm = { email, remember ->
                askEmail = false
                viewModel.follow(email, remember)
            },
        )
    }
}

@Composable
private fun DetailContent(
    feature: Feature,
    comments: CommentsUiState,
    hideVoteCounts: Boolean,
    voteButton: (@Composable (VoteButtonState) -> Unit)?,
    followPending: Boolean,
    onVote: () -> Unit,
    onFollow: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FewchursTheme.spacing
    val colors = FewchursTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(feature.title, style = FewchursTheme.typography.title.copy(fontSize = FewchursTheme.typography.title.fontSize * 1.3f))
                FeatureMeta(feature, showComments = false)
                if (feature.description.isNotBlank()) {
                    Text(feature.description, style = FewchursTheme.typography.body, color = colors.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val state = VoteButtonState(feature.upvoteCount, feature.hasVoted, hideVoteCounts, onVote)
                    if (voteButton != null) voteButton(state) else VoteButton(state)
                    FollowButton(feature.isFollowing, followPending, onFollow)
                    if (feature.followerCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.fewchurs_followers_count, feature.followerCount, feature.followerCount),
                            style = FewchursTheme.typography.label,
                            color = colors.muted,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.fewchurs_comments_title),
                    style = FewchursTheme.typography.title,
                )
            }
        }
        when (comments) {
            CommentsUiState.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            is CommentsUiState.Error -> item(key = "error") {
                MessageState(
                    title = stringResource(R.string.fewchurs_comments_error),
                    message = comments.error.localized(),
                    actionLabel = stringResource(R.string.fewchurs_retry),
                    onAction = onRetryComments,
                )
            }
            is CommentsUiState.Content -> {
                if (comments.comments.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(R.string.fewchurs_no_comments), style = FewchursTheme.typography.body, color = colors.muted)
                    }
                }
                items(comments.comments, key = { it.id }) { CommentRow(it) }
                if (comments.canLoadMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (comments.isLoadingMore) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = onLoadMore) { Text(stringResource(R.string.fewchurs_load_more_comments)) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun FollowButton(following: Boolean, pending: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (pending) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(if (following) Icons.Filled.Check else Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(stringResource(if (following) R.string.fewchurs_following else R.string.fewchurs_follow))
    }
    FilledTonalButton(onClick = onClick, enabled = !pending, shape = FewchursTheme.shapes.button) { content() }
}

@Composable
private fun CommentRow(comment: Comment) {
    val colors = FewchursTheme.colors
    Surface(
        shape = FewchursTheme.shapes.card,
        color = if (comment.isDeveloper) colors.primary.copy(alpha = 0.08f) else colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(FewchursTheme.spacing.cardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(comment.authorName, style = FewchursTheme.typography.label.copy(fontWeight = FontWeight.SemiBold))
                if (comment.isDeveloper) Badge(stringResource(R.string.fewchurs_developer))
                if (comment.isMine) Badge(stringResource(R.string.fewchurs_mine))
                Spacer(Modifier.weight(1f))
                Text(relativeTime(comment.createdAtMillis), style = FewchursTheme.typography.label, color = colors.muted)
            }
            Text(comment.text, style = FewchursTheme.typography.body)
        }
    }
}

@Composable
private fun Badge(label: String) {
    Text(
        label,
        style = FewchursTheme.typography.label,
        color = FewchursTheme.colors.onPrimary,
        modifier = Modifier
            .background(FewchursTheme.colors.primary, FewchursTheme.shapes.chip)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    pending: Boolean,
    onSend: () -> Unit,
) {
    val colors = FewchursTheme.colors
    val canSend = FeatureValidation.isValidComment(text) && !pending
    Surface(color = colors.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(horizontal = FewchursTheme.spacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (text.isNotEmpty()) {
                FewchursTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = { Text(stringResource(R.string.fewchurs_name_hint)) },
                    singleLine = true,
                    shape = FewchursTheme.shapes.button,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FewchursTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.fewchurs_comment_hint)) },
                    maxLines = 4,
                    shape = FewchursTheme.shapes.button,
                    supportingText = if (text.length > FeatureValidation.COMMENT_MAX - 100) {
                        { Text("${text.length}/${FeatureValidation.COMMENT_MAX}") }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSend, enabled = canSend) {
                    if (pending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.fewchurs_send),
                            tint = if (canSend) colors.primary else colors.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var email by rememberSaveable { mutableStateOf(initial) }
    var remember by rememberSaveable { mutableStateOf(false) }
    val valid = FeatureValidation.isValidEmail(email)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fewchurs_follow_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text(UiConfig.strings.followDialogMessage, R.string.fewchurs_follow_dialog_message))
                FewchursTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text(stringResource(R.string.fewchurs_email_label)) },
                    singleLine = true,
                    isError = email.isNotEmpty() && !valid,
                    supportingText = if (email.isNotEmpty() && !valid) {
                        { Text(stringResource(R.string.fewchurs_email_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    shape = FewchursTheme.shapes.button,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it })
                    Text(stringResource(R.string.fewchurs_remember_email), style = FewchursTheme.typography.body)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(email, remember) }, enabled = valid) {
                Text(stringResource(R.string.fewchurs_follow))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fewchurs_cancel)) } },
    )
}
