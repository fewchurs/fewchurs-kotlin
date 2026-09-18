package com.fewchurs.sdk.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.ui.internal.UiPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal sealed interface DetailEvent {
    data class Error(val error: FewchursException) : DetailEvent
    data object AskEmail : DetailEvent
    data object Followed : DetailEvent
    data object CommentSent : DetailEvent
}

internal class DetailViewModel(featureId: String, private val prefs: UiPrefs, pageSize: Int) : ViewModel() {
    val detail = Fewchurs.featureDetailState(featureId, viewModelScope, pageSize)

    private val _followPending = MutableStateFlow(false)
    val followPending: StateFlow<Boolean> = _followPending.asStateFlow()

    private val _commentPending = MutableStateFlow(false)
    val commentPending: StateFlow<Boolean> = _commentPending.asStateFlow()

    /** The feature isn't cached and couldn't be fetched (deleted, or deep link on an old server). */
    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    val savedAuthorName: String get() = prefs.authorName.orEmpty()

    init {
        viewModelScope.launch {
            detail.messages.collect { error ->
                if (detail.feature.value == null) _missing.value = true else _events.send(DetailEvent.Error(error))
            }
        }
    }

    fun toggleFollow() {
        val feature = detail.feature.value ?: return
        if (_followPending.value) return
        if (feature.isFollowing) {
            run { detail.unfollow() }
            return
        }
        val email = Fewchurs.config?.currentUserEmail() ?: prefs.rememberedEmail
        if (email == null) {
            _events.trySend(DetailEvent.AskEmail)
        } else {
            follow(email, remember = false)
        }
    }

    fun follow(email: String, remember: Boolean) {
        run {
            detail.follow(email)
            if (remember) prefs.rememberedEmail = email
            _events.send(DetailEvent.Followed)
        }
    }

    fun sendComment(text: String, authorName: String) {
        if (_commentPending.value) return
        _commentPending.value = true
        viewModelScope.launch {
            try {
                prefs.authorName = authorName
                detail.addComment(text, authorName)
                _events.send(DetailEvent.CommentSent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FewchursException) {
                _events.send(DetailEvent.Error(e))
            } finally {
                _commentPending.value = false
            }
        }
    }

    private fun run(block: suspend () -> Unit) {
        _followPending.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FewchursException) {
                _events.send(DetailEvent.Error(e))
            } finally {
                _followPending.value = false
            }
        }
    }
}
