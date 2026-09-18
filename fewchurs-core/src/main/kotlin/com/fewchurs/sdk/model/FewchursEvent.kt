package com.fewchurs.sdk.model

import com.fewchurs.sdk.error.FewchursException

/** Things that happened inside the SDK, for your own analytics. The SDK sends no analytics itself. */
public sealed interface FewchursEvent {
    public data object BoardOpened : FewchursEvent
    public data class FeatureSubmitted(val featureId: String) : FewchursEvent
    public data class Voted(val featureId: String, val voted: Boolean) : FewchursEvent
    public data class Commented(val featureId: String, val commentId: String) : FewchursEvent
    public data class Followed(val featureId: String, val following: Boolean) : FewchursEvent
    public data class Error(val error: FewchursException) : FewchursEvent
}
