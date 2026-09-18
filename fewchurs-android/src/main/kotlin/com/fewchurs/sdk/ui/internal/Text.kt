package com.fewchurs.sdk.ui.internal

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.ui.R

/** A config override if set, otherwise the (host-overridable) string resource. */
@Composable
internal fun text(override: String?, res: Int): String = override ?: stringResource(res)

@Composable
internal fun FewchursException.localized(): String = when (this) {
    is FewchursException.Network -> stringResource(R.string.fewchurs_error_offline)
    is FewchursException.RateLimited -> stringResource(R.string.fewchurs_error_rate_limited)
    is FewchursException.LimitReached -> stringResource(R.string.fewchurs_error_limit_reached)
    is FewchursException.NotFound -> stringResource(R.string.fewchurs_error_not_found)
    is FewchursException.Validation -> userMessage
    is FewchursException.NotConfigured -> stringResource(R.string.fewchurs_not_configured_title)
    else -> stringResource(R.string.fewchurs_error_generic)
}

@Composable
internal fun FeatureStatus.label(): String = stringResource(
    when (this) {
        FeatureStatus.OPEN -> R.string.fewchurs_status_open
        FeatureStatus.PLANNED -> R.string.fewchurs_status_planned
        FeatureStatus.IN_PROGRESS -> R.string.fewchurs_status_in_progress
        FeatureStatus.DONE -> R.string.fewchurs_status_done
        FeatureStatus.DECLINED -> R.string.fewchurs_status_declined
        FeatureStatus.UNKNOWN -> R.string.fewchurs_status_unknown
    },
)

internal fun relativeTime(millis: Long): String {
    if (millis <= 0) return ""
    val now = System.currentTimeMillis()
    // Device and server clocks drift; never show a just-posted comment as "in 1 min.".
    return DateUtils.getRelativeTimeSpanString(
        minOf(millis, now), now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
