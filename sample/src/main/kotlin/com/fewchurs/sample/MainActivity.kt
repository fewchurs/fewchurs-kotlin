package com.fewchurs.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.ui.FewchursBoard
import com.fewchurs.sdk.ui.FewchursBoardSheet

private enum class Demo(val title: String, val subtitle: String) {
    Default("Default board", "FewchursBoard() — inherits this app's MaterialTheme"),
    Sheet("Bottom sheet", "FewchursBoardSheet(visible, onDismiss)"),
    Themed("Heavily themed", "FewchursTheme(...) + header slot + custom vote button"),
    Headless("Custom UI (headless)", "Fewchurs.boardState() + your own composables"),
    Submit("Submit from your own UI", "Fewchurs.submitFeature(title, description)"),
    Views("View-based app", "Fewchurs.showBoard(context) from an XML/View screen"),
    Events("Event log", "Everything Fewchurs.events emitted in this session"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val brand = Color(0xFF4F46E5)
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFA5B4FC)) else lightColorScheme(primary = brand)
            MaterialTheme(colorScheme = scheme) { SampleHome() }
        }
    }
}

@Composable
private fun SampleHome() {
    var demo by rememberSaveable { mutableStateOf<Demo?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    BackHandler(enabled = demo != null) { demo = null }
    val close = { demo = null }

    when (demo) {
        Demo.Default -> FewchursBoard(onClose = close)
        Demo.Themed -> ThemedBoard(onClose = close)
        Demo.Headless -> HeadlessBoard(onBack = close)
        Demo.Submit -> SubmitFromOwnUi(onBack = close)
        Demo.Events -> EventLog(onBack = close)
        else -> DemoList(
            onPick = {
                when (it) {
                    Demo.Sheet -> sheetOpen = true
                    Demo.Views -> context.startActivity(Intent(context, ViewBasedActivity::class.java))
                    else -> demo = it
                }
            },
        )
    }
    FewchursBoardSheet(visible = sheetOpen, onDismiss = { sheetOpen = false })
}

@Composable
private fun DemoList(onPick: (Demo) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Fewchurs Android SDK") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    "Server: ${Fewchurs.config?.baseUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(Demo.entries) { d ->
                Card(onClick = { onPick(d) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(d.title, style = MaterialTheme.typography.titleMedium)
                        Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
