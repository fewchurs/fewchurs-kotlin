package com.fewchurs.sdk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fewchurs.sdk.ui.theme.FewchursTheme

@Composable
internal fun MessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = FewchursTheme.typography.title, textAlign = TextAlign.Center)
        Text(message, style = FewchursTheme.typography.body, color = FewchursTheme.colors.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, shape = FewchursTheme.shapes.button, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}
