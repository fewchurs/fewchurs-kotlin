package com.fewchurs.sdk

import com.fewchurs.sdk.data.FewchursRepository
import com.fewchurs.sdk.model.BoardSettings
import com.fewchurs.sdk.model.Comment
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.Page
import com.fewchurs.sdk.model.Sort

/**
 * Raw, non-optimistic access to the Fewchurs API for building a fully custom UI.
 * Every call throws [com.fewchurs.sdk.error.FewchursException] on failure.
 *
 * Results still update the shared cache, so any visible board stays in sync.
 */
public class FewchursClient internal constructor(private val repository: () -> FewchursRepository) {

    public suspend fun settings(): BoardSettings = repository().loadSettings()

    public suspend fun listFeatures(sort: Sort = Sort.Top, limit: Int = 20, cursor: String? = null): Page<Feature> =
        repository().listFeatures(sort, limit, cursor)

    /** Requires a server with `GET /v1/features/{id}`. */
    public suspend fun getFeature(featureId: String): Feature = repository().getFeature(featureId)

    public suspend fun submitFeature(
        title: String,
        description: String = "",
        email: String? = null,
        isSubscriber: Boolean? = null,
    ): Feature = Fewchurs.submitFeature(title, description, email, isSubscriber)

    /** Adds this device's vote. Idempotent. Returns the new upvote count. */
    public suspend fun vote(featureId: String): Int = repository().vote(featureId, true)

    /** Removes this device's vote. Idempotent. Returns the new upvote count. */
    public suspend fun unvote(featureId: String): Int = repository().vote(featureId, false)

    public suspend fun follow(featureId: String, email: String): Unit = repository().follow(featureId, email)

    public suspend fun unfollow(featureId: String): Unit = repository().unfollow(featureId)

    public suspend fun listComments(featureId: String, limit: Int = 20, cursor: String? = null): Page<Comment> =
        repository().listComments(featureId, limit, cursor)

    public suspend fun addComment(featureId: String, text: String, authorName: String? = null): Comment =
        repository().addComment(featureId, text, authorName)
}
