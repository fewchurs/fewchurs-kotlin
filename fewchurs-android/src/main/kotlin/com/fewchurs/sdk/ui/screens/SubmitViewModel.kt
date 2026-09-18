package com.fewchurs.sdk.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.Feature
import com.fewchurs.sdk.model.FeatureValidation
import com.fewchurs.sdk.ui.config.EmailField
import com.fewchurs.sdk.ui.internal.UiPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Form state lives in SavedStateHandle so a draft survives rotation and process death. */
internal class SubmitViewModel(
    private val handle: SavedStateHandle,
    private val emailField: EmailField,
    prefs: UiPrefs,
) : ViewModel() {
    val title: StateFlow<String> = handle.getStateFlow(TITLE, "")
    val description: StateFlow<String> = handle.getStateFlow(DESCRIPTION, "")
    val email: StateFlow<String> = handle.getStateFlow(
        EMAIL,
        if (emailField == EmailField.Hidden) "" else Fewchurs.config?.currentUserEmail() ?: prefs.rememberedEmail.orEmpty(),
    )

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _submitted = MutableStateFlow<Feature?>(null)
    val submitted: StateFlow<Feature?> = _submitted.asStateFlow()

    private val _errors = Channel<FewchursException>(Channel.BUFFERED)
    val errors: Flow<FewchursException> = _errors.receiveAsFlow()

    fun setTitle(value: String) {
        handle[TITLE] = value.take(FeatureValidation.TITLE_MAX)
    }

    fun setDescription(value: String) {
        handle[DESCRIPTION] = value.take(FeatureValidation.DESCRIPTION_MAX)
    }

    fun setEmail(value: String) {
        handle[EMAIL] = value.trim()
    }

    fun isEmailValid(value: String): Boolean = when (emailField) {
        EmailField.Hidden -> true
        EmailField.Optional -> value.isBlank() || FeatureValidation.isValidEmail(value)
        EmailField.Required -> FeatureValidation.isValidEmail(value)
    }

    fun canSubmit(title: String, description: String, email: String): Boolean =
        !_pending.value && FeatureValidation.isValidTitle(title) &&
            FeatureValidation.isValidDescription(description) && isEmailValid(email)

    fun submit() {
        val t = title.value
        val d = description.value
        val e = email.value.takeIf { emailField != EmailField.Hidden && it.isNotBlank() }
        if (!canSubmit(t, d, email.value)) return
        _pending.value = true
        viewModelScope.launch {
            try {
                _submitted.value = Fewchurs.submitFeature(title = t, description = d, email = e)
                handle[TITLE] = ""
                handle[DESCRIPTION] = ""
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: FewchursException) {
                _errors.send(ex)
            } finally {
                _pending.value = false
            }
        }
    }

    private companion object {
        const val TITLE = "fewchurs_title"
        const val DESCRIPTION = "fewchurs_description"
        const val EMAIL = "fewchurs_email"
    }
}
