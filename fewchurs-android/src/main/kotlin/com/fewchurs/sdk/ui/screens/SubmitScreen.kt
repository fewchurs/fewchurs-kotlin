package com.fewchurs.sdk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fewchurs.sdk.model.FeatureValidation
import com.fewchurs.sdk.ui.R
import com.fewchurs.sdk.ui.components.FewchursTextField
import com.fewchurs.sdk.ui.config.EmailField
import com.fewchurs.sdk.ui.config.UiConfig
import com.fewchurs.sdk.ui.internal.text
import com.fewchurs.sdk.ui.theme.FewchursTheme

@Composable
internal fun SubmitScreen(
    viewModel: SubmitViewModel,
    emailField: EmailField,
    windowInsets: WindowInsets,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val submitted by viewModel.submitted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val errors = rememberErrorTexts()
    LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(errors.of(it)) } }

    val strings = UiConfig.strings
    Scaffold(
        contentWindowInsets = windowInsets,
        containerColor = FewchursTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                title = { Text(text(strings.submitTitle, R.string.fewchurs_submit_title)) },
                navigationIcon = {
                    if (submitted == null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fewchurs_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FewchursTheme.colors.background),
            )
        },
    ) { padding ->
        if (submitted != null) {
            Confirmation(onDone, Modifier.padding(padding))
            return@Scaffold
        }
        val spacing = FewchursTheme.spacing
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val titleTooShort = title.isNotEmpty() && title.trim().length < FeatureValidation.TITLE_MIN
            FewchursTextField(
                value = title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.fewchurs_title_label)) },
                placeholder = { Text(stringResource(R.string.fewchurs_title_hint)) },
                singleLine = true,
                isError = titleTooShort,
                supportingText = {
                    Row2(
                        start = if (titleTooShort) stringResource(R.string.fewchurs_title_too_short, FeatureValidation.TITLE_MIN) else "",
                        end = "${title.length}/${FeatureValidation.TITLE_MAX}",
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = FewchursTheme.shapes.button,
                modifier = Modifier.fillMaxWidth(),
            )
            FewchursTextField(
                value = description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.fewchurs_description_label)) },
                placeholder = { Text(stringResource(R.string.fewchurs_description_hint)) },
                minLines = 4,
                supportingText = { Row2(start = "", end = "${description.length}/${FeatureValidation.DESCRIPTION_MAX}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = FewchursTheme.shapes.button,
                modifier = Modifier.fillMaxWidth(),
            )
            if (emailField != EmailField.Hidden) {
                val invalid = email.isNotEmpty() && !viewModel.isEmailValid(email)
                FewchursTextField(
                    value = email,
                    onValueChange = viewModel::setEmail,
                    label = {
                        Text(
                            stringResource(
                                if (emailField == EmailField.Required) R.string.fewchurs_email_required_label
                                else R.string.fewchurs_email_optional_label,
                            ),
                        )
                    },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.fewchurs_email_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    shape = FewchursTheme.shapes.button,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit(title, description, email) && !pending,
                shape = FewchursTheme.shapes.button,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (pending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FewchursTheme.colors.onPrimary)
                } else {
                    Text(stringResource(R.string.fewchurs_submit_action))
                }
            }
        }
    }
}

@Composable
private fun Row2(start: String, end: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(start, modifier = Modifier.weight(1f))
        Text(end)
    }
}

@Composable
private fun Confirmation(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val strings = UiConfig.strings
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FewchursTheme.colors.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text(strings.submittedTitle, R.string.fewchurs_submitted_title), style = FewchursTheme.typography.title, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text(strings.submittedMessage, R.string.fewchurs_submitted_message),
            style = FewchursTheme.typography.body,
            color = FewchursTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, shape = FewchursTheme.shapes.button) { Text(stringResource(R.string.fewchurs_done)) }
    }
}
