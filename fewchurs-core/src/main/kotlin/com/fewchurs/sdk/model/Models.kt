package com.fewchurs.sdk.model

/** Lifecycle of a feature request. Unknown server values map to [UNKNOWN] instead of failing. */
public enum class FeatureStatus(public val apiValue: String) {
    OPEN("open"),
    PLANNED("planned"),
    IN_PROGRESS("in_progress"),
    DONE("done"),
    DECLINED("declined"),
    UNKNOWN("unknown");

    public companion object {
        public fun fromApi(value: String?): FeatureStatus =
            entries.firstOrNull { it.apiValue == value } ?: UNKNOWN
    }
}

public enum class Sort(public val apiValue: String) { Top("top"), New("new") }

public data class Feature(
    val id: String,
    val title: String,
    val description: String,
    val status: FeatureStatus,
    val upvoteCount: Int,
    val commentCount: Int,
    val followerCount: Int,
    val hasVoted: Boolean,
    val isFollowing: Boolean,
    /** True when this device submitted the request. */
    val isMine: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

public data class Comment(
    val id: String,
    val text: String,
    val authorName: String,
    /** Written by the app's developer from the dashboard. */
    val isDeveloper: Boolean,
    /** Written from this device. */
    val isMine: Boolean,
    val createdAtMillis: Long,
)

public data class Page<T>(val items: List<T>, val nextCursor: String?)

/** Board settings controlled from the Fewchurs dashboard. */
public data class BoardSettings(
    /** "Powered by Fewchurs" badge. False only on the Pro plan. */
    val showBranding: Boolean = true,
    /** The owner hid vote counts; show only the arrow. */
    val hideVoteCounts: Boolean = false,
    val appName: String? = null,
)
