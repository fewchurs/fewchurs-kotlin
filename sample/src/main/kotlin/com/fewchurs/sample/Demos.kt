package com.fewchurs.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fewchurs.sdk.Fewchurs
import com.fewchurs.sdk.error.FewchursException
import com.fewchurs.sdk.model.FeatureStatus
import com.fewchurs.sdk.model.Sort
import com.fewchurs.sdk.state.BoardUiState
import com.fewchurs.sdk.ui.FewchursBoard
import com.fewchurs.sdk.ui.theme.FewchursColors
import com.fewchurs.sdk.ui.theme.FewchursShapes
import com.fewchurs.sdk.ui.theme.FewchursSpacing
import com.fewchurs.sdk.ui.theme.FewchursTheme
import com.fewchurs.sdk.ui.theme.FewchursTypography
import kotlinx.coroutines.launch

/** Level 2 + 4: scoped theme tokens and slots. */
@Composable
internal fun ThemedBoard(onClose: () -> Unit) {
    val orange = Color(0xFFED5F18)
    val ink = Color(0xFF1C1917)
    FewchursTheme(
        colors = FewchursColors(
            primary = orange,
            onPrimary = Color.White,
            background = Color(0xFFFFFBF5),
            surface = Color.White,
            onSurface = ink,
            muted = Color(0xFF78716C),
            border = Color(0xFFF1E7DA),
            error = Color(0xFFDC2626),
            statusColors = mapOf(
                FeatureStatus.OPEN to Color(0xFF78716C),
                FeatureStatus.PLANNED to Color(0xFF7C3AED),
                FeatureStatus.IN_PROGRESS to orange,
                FeatureStatus.DONE to Color(0xFF16A34A),
                FeatureStatus.DECLINED to Color(0xFFDC2626),
            ),
        ),
        shapes = FewchursShapes(
            card = RoundedCornerShape(20.dp),
            button = RoundedCornerShape(14.dp),
            chip = RoundedCornerShape(6.dp),
            sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ),
        typography = FewchursTypography(
            title = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
            body = MaterialTheme.typography.bodyMedium,
            label = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        ),
        spacing = FewchursSpacing.Compact,
    ) {
        FewchursBoard(
            onClose = onClose,
            header = {
                Column(
                    Modifier.fillMaxWidth().background(orange.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Text("Shape our roadmap 🧡", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ink)
                    Text("Vote for what you want next. We read everything.", color = Color(0xFF78716C))
                }
            },
            voteButton = { state ->
                Row(
                    Modifier
                        .background(if (state.voted) orange else orange.copy(alpha = 0.1f), RoundedCornerShape(50))
                        .clickable(onClick = state.onToggle)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.voted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Vote",
                        tint = if (state.voted) Color.White else orange,
                    )
                    if (!state.hideCount) {
                        Text(" ${state.count}", color = if (state.voted) Color.White else orange, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )
    }
}

/** Level 5: no Fewchurs UI at all, only the state holder. */
@Composable
internal fun HeadlessBoard(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val board = remember { Fewchurs.boardState(sort = Sort.Top, scope = scope) }
    val state by board.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My own UI") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = board::refresh) { Text("Refresh") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                BoardUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is BoardUiState.Error -> Text("Error: ${s.error.userMessage}", Modifier.align(Alignment.Center))
                is BoardUiState.Content -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(s.features, key = { it.id }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { board.toggleVote(f.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (f.hasVoted) "▲ ${f.upvoteCount}" else "△ ${f.upvoteCount}",
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(64.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = MaterialTheme.typography.bodyLarge)
                                Text(f.status.name.lowercase(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider()
                    }
                    if (s.canLoadMore) {
                        item {
                            TextButton(onClick = board::loadMore, modifier = Modifier.fillMaxWidth()) {
                                Text(if (s.isLoadingMore) "Loading…" else "Load more")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Submit from your own UI" (plan §2.5). */
@Composable
internal fun SubmitFromOwnUi(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your own form") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !pending,
                onClick = {
                    pending = true
                    scope.launch {
                        result = try {
                            val feature = Fewchurs.submitFeature(title = title, description = description)
                            "Submitted ${feature.id} (${feature.status})"
                        } catch (e: FewchursException) {
                            "Failed: ${e.userMessage}"
                        }
                        pending = false
                    }
                },
            ) { Text(if (pending) "Sending…" else "Submit") }
            result?.let { Text(it) }
        }
    }
}

@Composable
internal fun EventLog(onBack: () -> Unit) {
    val events by SampleApp.eventLog.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fewchurs.events") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (events.isEmpty()) item { Text("Nothing yet. Open a board, vote, comment…") }
            items(events) { Text(it.toString(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}
