package com.fewchurs.sdk.data

import com.fewchurs.sdk.FewchursInternalApi
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.internal.Logger
import com.fewchurs.sdk.internal.network.CommentDto
import com.fewchurs.sdk.internal.network.CreateCommentBody
import com.fewchurs.sdk.internal.network.CreateFeatureBody
import com.fewchurs.sdk.internal.network.FeatureDto
import com.fewchurs.sdk.internal.network.FewchursApi
import com.fewchurs.sdk.internal.network.IsoDate
import com.fewchurs.sdk.model.BoardSettings
import com.fewchurs.sdk.model.Comment
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.model.FeatureValidation
import com.fewchurs.sdk.model.FewchursEvent
import com.fewchurs.sdk.model.Page
import com.fewchurs.sdk.model.Sort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for everything the SDK has loaded. Every screen and state holder reads
 * features from [features], so a vote on the detail screen shows up on the board instantly.
 */
@FewchursInternalApi
public class FewchursRepository internal constructor(
    private val api: FewchursApi,
    private val deviceId: () -> String,
    private val emit: (FewchursEvent) -> Unit,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val voteDebounceMs: Long = 350,
) {
    private val _features = MutableStateFlow<Map<String, Feature>>(emptyMap())
    public val features: StateFlow<Map<String, Feature>> = _features.asStateFlow()

    private val _settings = MutableStateFlow<BoardSettings?>(null)
    public val settings: StateFlow<BoardSettings?> = _settings.asStateFlow()
    private val settingsMutex = Mutex()

    private val _submitted = MutableSharedFlow<Feature>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Features created on this device, so boards can put them on top of "New". */
    public val submitted: SharedFlow<Feature> = _submitted.asSharedFlow()

    private val _voteFailures = MutableSharedFlow<FewchursException>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    public val voteFailures: SharedFlow<FewchursException> = _voteFailures.asSharedFlow()

    // Optimistic voting bookkeeping, guarded by [voteLock].
    private val voteLock = Any()
    private val serverVotes = HashMap<String, Pair<Boolean, Int>>() // last state confirmed by the server
    private val desiredVotes = HashMap<String, Boolean>()
    private val voteJobs = HashMap<String, Job>()

    // --- settings ---

    /** Dashboard settings, fetched once per session. Falls back to defaults if offline. */
    public suspend fun loadSettings(): BoardSettings {
        _settings.value?.let { return it }
        return settingsMutex.withLock {
            _settings.value ?: try {
                val dto = call { api.config() }
                BoardSettings(dto.showBranding, dto.hideVoteCounts, dto.appName).also { _settings.value = it }
            } catch (e: FewchursException) {
                logger.debug("Using default board settings: ${e.message}")
                BoardSettings()
            }
        }
    }

    // --- features ---

    public suspend fun listFeatures(sort: Sort, limit: Int, cursor: String?): Page<Feature> {
        val dto = call { api.listFeatures(sort.apiValue, limit, cursor) }
        val items = dto.features.map { it.toModel() }
        upsert(items)
        return Page(items, dto.nextCursor)
    }

    public suspend fun getFeature(id: String): Feature {
        val feature = call(id) { api.getFeature(id) }.toModel()
        upsert(listOf(feature))
        return feature
    }

    public suspend fun submitFeature(title: String, description: String, email: String?, isSubscriber: Boolean): Feature {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }
        when {
            !FeatureValidation.isValidTitle(cleanTitle) -> throw FewchursException.Validation(
                "Title must be ${FeatureValidation.TITLE_MIN}–${FeatureValidation.TITLE_MAX} characters.",
            )
            !FeatureValidation.isValidDescription(cleanDescription) -> throw FewchursException.Validation(
                "Description must be at most ${FeatureValidation.DESCRIPTION_MAX} characters.",
            )
            cleanEmail != null && !FeatureValidation.isValidEmail(cleanEmail) ->
                throw FewchursException.Validation("Enter a valid email address.")
        }
        val dto = call { api.createFeature(CreateFeatureBody(cleanTitle, cleanDescription, cleanEmail, isSubscriber)) }
        // The server follows the feature for the email, but its response predates that.
        val feature = dto.toModel().copy(
            isMine = true,
            isFollowing = cleanEmail != null,
            followerCount = if (cleanEmail != null) maxOf(1, dto.followerCount) else dto.followerCount,
        )
        upsert(listOf(feature))
        _submitted.tryEmit(feature)
        emit(FewchursEvent.FeatureSubmitted(feature.id))
        return feature
    }

    // --- votes ---

    /** Flips this device's vote optimistically; see [setVote]. */
    public fun toggleVote(featureId: String) {
        val current = _features.value[featureId] ?: return
        setVote(featureId, !current.hasVoted)
    }

    /**
     * Updates the vote instantly, then syncs it in the background. Rapid taps are collapsed into
     * the final state; on failure the vote rolls back and [voteFailures] emits.
     */
    public fun setVote(featureId: String, voted: Boolean) {
        _features.update { map ->
            val f = map[featureId] ?: return@update map
            if (f.hasVoted == voted) return@update map
            map + (featureId to f.copy(hasVoted = voted, upvoteCount = (f.upvoteCount + if (voted) 1 else -1).coerceAtLeast(0)))
        }
        synchronized(voteLock) {
            desiredVotes[featureId] = voted
            if (voteJobs[featureId] == null) {
                voteJobs[featureId] = scope.launch { syncVote(featureId) }
            }
        }
    }

    /** Non-optimistic vote for headless callers; returns the new upvote count. */
    public suspend fun vote(featureId: String, voted: Boolean): Int {
        val result = call(featureId) { api.setVote(featureId, voted) }
        applyServerVote(featureId, result.hasVoted, result.upvoteCount)
        emit(FewchursEvent.Voted(featureId, result.hasVoted))
        return result.upvoteCount
    }

    private suspend fun syncVote(featureId: String) {
        delay(voteDebounceMs)
        while (true) {
            val target = synchronized(voteLock) { desiredVotes[featureId] } ?: return finishVoteJob(featureId)
            val known = synchronized(voteLock) { serverVotes[featureId] }
            val confirmed: Pair<Boolean, Int> = if (known?.first == target) {
                known
            } else {
                try {
                    val r = call(featureId) { api.setVote(featureId, target) }
                    emit(FewchursEvent.Voted(featureId, r.hasVoted))
                    (r.hasVoted to r.upvoteCount).also { synchronized(voteLock) { serverVotes[featureId] = it } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FewchursException) {
                    rollbackVote(featureId)
                    _voteFailures.tryEmit(e)
                    return
                }
            }
            val done = synchronized(voteLock) {
                if (desiredVotes[featureId] == target) {
                    desiredVotes.remove(featureId)
                    voteJobs.remove(featureId)
                    true
                } else {
                    false
                }
            }
            if (done) {
                applyServerVote(featureId, confirmed.first, confirmed.second)
                return
            }
        }
    }

    private fun finishVoteJob(featureId: String) {
        synchronized(voteLock) { voteJobs.remove(featureId) }
    }

    private fun rollbackVote(featureId: String) {
        val known = synchronized(voteLock) {
            desiredVotes.remove(featureId)
            voteJobs.remove(featureId)
            serverVotes[featureId]
        }
        if (known != null) {
            _features.update { map ->
                val f = map[featureId] ?: return@update map
                map + (featureId to f.copy(hasVoted = known.first, upvoteCount = known.second))
            }
        }
    }

    private fun applyServerVote(featureId: String, hasVoted: Boolean, count: Int) {
        synchronized(voteLock) { serverVotes[featureId] = hasVoted to count }
        _features.update { map ->
            val f = map[featureId] ?: return@update map
            map + (featureId to f.copy(hasVoted = hasVoted, upvoteCount = count))
        }
    }

    // --- follow ---

    public suspend fun follow(featureId: String, email: String) {
        val clean = email.trim()
        if (!FeatureValidation.isValidEmail(clean)) throw FewchursException.Validation("Enter a valid email address.")
        call(featureId) { api.follow(featureId, clean) }
        updateFeature(featureId) { if (it.isFollowing) it else it.copy(isFollowing = true, followerCount = it.followerCount + 1) }
        emit(FewchursEvent.Followed(featureId, true))
    }

    public suspend fun unfollow(featureId: String) {
        call(featureId) { api.unfollow(featureId) }
        updateFeature(featureId) {
            if (!it.isFollowing) it else it.copy(isFollowing = false, followerCount = (it.followerCount - 1).coerceAtLeast(0))
        }
        emit(FewchursEvent.Followed(featureId, false))
    }

    // --- comments ---

    public suspend fun listComments(featureId: String, limit: Int, cursor: String?): Page<Comment> {
        val dto = call(featureId) { api.listComments(featureId, limit, cursor) }
        return Page(dto.comments.map { it.toModel() }, dto.nextCursor)
    }

    public suspend fun addComment(featureId: String, text: String, authorName: String?): Comment {
        val cleanText = text.trim()
        val cleanName = authorName?.trim()?.takeIf { it.isNotEmpty() }
        if (!FeatureValidation.isValidComment(cleanText)) {
            throw FewchursException.Validation("Comment must be 1–${FeatureValidation.COMMENT_MAX} characters.")
        }
        if (cleanName != null && !FeatureValidation.isValidAuthorName(cleanName)) {
            throw FewchursException.Validation("Name must be at most ${FeatureValidation.AUTHOR_NAME_MAX} characters.")
        }
        val comment = call(featureId) { api.createComment(CreateCommentBody(featureId, cleanText, cleanName)) }
            .toModel().copy(isMine = true)
        updateFeature(featureId) { it.copy(commentCount = it.commentCount + 1) }
        emit(FewchursEvent.Commented(featureId, comment.id))
        return comment
    }

    // --- helpers ---

    private fun upsert(items: List<Feature>) {
        if (items.isEmpty()) return
        val pending = synchronized(voteLock) {
            items.forEach { if (it.id !in desiredVotes) serverVotes[it.id] = it.hasVoted to it.upvoteCount }
            desiredVotes.keys.toSet()
        }
        _features.update { map ->
            val next = map.toMutableMap()
            items.forEach { fresh ->
                val existing = map[fresh.id]
                // Keep the optimistic vote while it is still syncing.
                next[fresh.id] = if (existing != null && fresh.id in pending) {
                    fresh.copy(hasVoted = existing.hasVoted, upvoteCount = existing.upvoteCount)
                } else {
                    fresh
                }
            }
            next
        }
    }

    private fun updateFeature(id: String, transform: (Feature) -> Feature) {
        _features.update { map -> map[id]?.let { map + (id to transform(it)) } ?: map }
    }

    /** Runs an API call, reports failures as events and drops features the server no longer has. */
    private suspend fun <T> call(featureId: String? = null, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FewchursException) {
            if (e is FewchursException.NotFound && featureId != null) {
                _features.update { it - featureId }
            }
            emit(FewchursEvent.Error(e))
            throw e
        } catch (e: Exception) {
            val wrapped = FewchursException.Unknown(e.message ?: e.javaClass.simpleName, e)
            emit(FewchursEvent.Error(wrapped))
            throw wrapped
        }
    }

    private fun FeatureDto.toModel(): Feature = Feature(
        id = id,
        title = title,
        description = description,
        status = FeatureStatus.fromApi(status),
        upvoteCount = upvoteCount,
        commentCount = commentCount,
        followerCount = followerCount,
        hasVoted = hasVoted,
        isFollowing = isFollowing,
        isMine = isMine ?: (authorDeviceId != null && authorDeviceId == deviceId()),
        createdAtMillis = IsoDate.parse(createdAt),
        updatedAtMillis = IsoDate.parse(updatedAt ?: createdAt),
    )

    private fun CommentDto.toModel(): Comment = Comment(
        id = id,
        text = text,
        authorName = authorName,
        isDeveloper = isDeveloper,
        isMine = isMine ?: (deviceId != null && deviceId == this@FewchursRepository.deviceId()),
        createdAtMillis = IsoDate.parse(createdAt),
    )
}
