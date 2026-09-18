package com.fewchurs.sdk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.ui.R
import com.fewchurs.sdk.ui.internal.label
import com.fewchurs.sdk.ui.theme.FewchursTheme

/** Everything a custom vote button needs. */
@Immutable
public data class VoteButtonState(
    val count: Int,
    val voted: Boolean,
    /** The app owner hid vote counts: show only the arrow. */
    val hideCount: Boolean,
    val onToggle: () -> Unit,
)

/** Actions available to a custom feature card. */
@Immutable
public data class FeatureCardActions(
    val toggleVote: () -> Unit,
    val open: () -> Unit,
)

@Composable
public fun VoteButton(state: VoteButtonState, modifier: Modifier = Modifier) {
    val colors = FewchursTheme.colors
    val container by animateColorAsState(if (state.voted) colors.primary else colors.primary.copy(alpha = 0.08f), label = "voteContainer")
    val content by animateColorAsState(if (state.voted) colors.onPrimary else colors.onSurface, label = "voteContent")
    val description = stringResource(R.string.fewchurs_vote_description) +
        (if (state.hideCount) "" else ". " + pluralStringResource(R.plurals.fewchurs_votes, state.count, state.count))
    val stateText = stringResource(if (state.voted) R.string.fewchurs_voted else R.string.fewchurs_not_voted)
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
            .clip(FewchursTheme.shapes.button)
            .background(container)
            .clickable(role = Role.Button, onClick = state.onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = stateText
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        if (!state.hideCount) {
            Text(state.count.toString(), style = FewchursTheme.typography.label.copy(fontWeight = FontWeight.SemiBold), color = content)
        }
    }
}

@Composable
public fun StatusChip(status: FeatureStatus, modifier: Modifier = Modifier) {
    val color = FewchursTheme.colors.status(status)
    Text(
        text = status.label(),
        style = FewchursTheme.typography.label,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(FewchursTheme.shapes.chip)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** The default board row. Pass `voteButton` to swap just the vote control. */
@Composable
public fun FeatureCard(
    feature: Feature,
    actions: FeatureCardActions,
    hideVoteCount: Boolean,
    modifier: Modifier = Modifier,
    voteButton: (@Composable (VoteButtonState) -> Unit)? = null,
) {
    val colors = FewchursTheme.colors
    val spacing = FewchursTheme.spacing
    val voteState = VoteButtonState(feature.upvoteCount, feature.hasVoted, hideVoteCount, actions.toggleVote)
    Surface(
        onClick = actions.open,
        shape = FewchursTheme.shapes.card,
        color = colors.surface,
        contentColor = colors.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(spacing.cardPadding), horizontalArrangement = Arrangement.spacedBy(spacing.cardPadding)) {
            if (voteButton != null) voteButton(voteState) else VoteButton(voteState)
            Column(Modifier.weight(1f)) {
                Text(feature.title, style = FewchursTheme.typography.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (feature.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        feature.description,
                        style = FewchursTheme.typography.body,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                FeatureMeta(feature)
            }
        }
    }
}

@Composable
internal fun FeatureMeta(feature: Feature, showComments: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusChip(feature.status)
        if (showComments && feature.commentCount > 0) {
            Text(
                pluralStringResource(R.plurals.fewchurs_comments_count, feature.commentCount, feature.commentCount),
                style = FewchursTheme.typography.label,
                color = FewchursTheme.colors.muted,
            )
        }
        if (feature.isMine) {
            Text(stringResource(R.string.fewchurs_mine), style = FewchursTheme.typography.label, color = FewchursTheme.colors.primary)
        }
    }
}

/** Shown when the server says so (not on Pro). There is deliberately no option to hide it. */
@Composable
public fun PoweredByBadge(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.fewchurs_powered_by),
            style = FewchursTheme.typography.label,
            color = FewchursTheme.colors.muted,
            modifier = Modifier
                .clip(FewchursTheme.shapes.chip)
                .clickable(role = Role.Button) { runCatching { uri.openUri("https://fewchurs.com") } }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** Placeholder card while the first page loads. */
@Composable
internal fun SkeletonCard() {
    val colors = FewchursTheme.colors
    val block = colors.muted.copy(alpha = 0.12f)
    Surface(shape = FewchursTheme.shapes.card, color = colors.surface) {
        Row(Modifier.fillMaxWidth().padding(FewchursTheme.spacing.cardPadding), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp, 56.dp).clip(FewchursTheme.shapes.button).background(block))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(FewchursTheme.shapes.chip).background(block))
                Box(Modifier.fillMaxWidth().height(12.dp).clip(FewchursTheme.shapes.chip).background(block))
                Box(Modifier.width(64.dp).height(18.dp).clip(FewchursTheme.shapes.chip).background(block))
            }
        }
    }
}
