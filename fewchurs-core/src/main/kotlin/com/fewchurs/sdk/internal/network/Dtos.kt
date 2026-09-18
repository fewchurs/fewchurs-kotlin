package com.fewchurs.sdk.internal.network

import kotlinx.serialization.Serializable

@Serializable
internal data class FeatureDto(
    val id: String,
    val title: String = "",
    val description: String = "",
    val status: String? = null,
    val upvoteCount: Int = 0,
    val commentCount: Int = 0,
    val followerCount: Int = 0,
    val hasVoted: Boolean = false,
    val isFollowing: Boolean = false,
    // Newer servers send isMine; older ones send the raw author device ID.
    val isMine: Boolean? = null,
    val authorDeviceId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class FeatureListDto(val features: List<FeatureDto> = emptyList(), val nextCursor: String? = null)

@Serializable
internal data class CommentDto(
    val id: String,
    val text: String = "",
    val authorName: String = "",
    val isDeveloper: Boolean = false,
    val isMine: Boolean? = null,
    val deviceId: String? = null,
    val createdAt: String? = null,
)

@Serializable
internal data class CommentListDto(val comments: List<CommentDto> = emptyList(), val nextCursor: String? = null)

@Serializable
internal data class VoteDto(val upvoteCount: Int = 0, val hasVoted: Boolean = false)

@Serializable
internal data class FollowDto(val following: Boolean = false)

@Serializable
internal data class ConfigDto(
    val showBranding: Boolean = true,
    val hideVoteCounts: Boolean = false,
    val appName: String? = null,
)

@Serializable
internal data class CreateFeatureBody(
    val title: String,
    val description: String,
    val email: String? = null,
    val isSubscriber: Boolean = false,
)

@Serializable
internal data class FollowBody(val email: String)

@Serializable
internal data class CreateCommentBody(val featureId: String, val text: String, val authorName: String? = null)

@Serializable
internal data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
internal data class ErrorBody(val code: String? = null, val message: String? = null)
