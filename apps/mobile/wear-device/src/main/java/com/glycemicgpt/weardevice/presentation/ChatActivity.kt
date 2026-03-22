package com.glycemicgpt.weardevice.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.glycemicgpt.weardevice.data.WatchDataRepository
import com.glycemicgpt.weardevice.data.WatchDataRepository.ChatState
import com.glycemicgpt.weardevice.messaging.WearMessageSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchDataRepository.clearChat()

        val prefillQuery = intent?.getStringExtra(EXTRA_QUERY)?.take(MAX_QUERY_LENGTH)

        setContent {
            WearChatScreen(prefillQuery = prefillQuery)
        }
    }

    companion object {
        const val EXTRA_QUERY = "chat_query"
        const val MAX_QUERY_LENGTH = 500
    }
}

@Composable
private fun WearChatScreen(prefillQuery: String?) {
    val chatState by WatchDataRepository.chatState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasSent by remember { mutableStateOf(false) }

    // Voice/keyboard input launcher using system speech recognizer
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                hasSent = true
                sendQuery(scope, context, spokenText)
            }
        }
    }

    // Timeout: if loading for more than 30s, show error
    LaunchedEffect(chatState) {
        if (chatState is ChatState.Loading) {
            delay(CHAT_TIMEOUT_MS)
            // Only timeout if still loading (response may have arrived)
            if (WatchDataRepository.chatState.value is ChatState.Loading) {
                WatchDataRepository.setChatError("Request timed out. Check phone connection.")
            }
        }
    }

    // Clear chat state when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            val current = WatchDataRepository.chatState.value
            if (current is ChatState.Loading) {
                WatchDataRepository.clearChat()
            }
        }
    }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (val state = chatState) {
                    is ChatState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Asking AI...",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                        )
                    }

                    is ChatState.Error -> {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.title3,
                            color = Color(0xFFEF4444),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                        )
                    }

                    is ChatState.Success -> {
                        Text(
                            text = stripMarkdown(state.response),
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.disclaimer.ifBlank { DEFAULT_AI_DISCLAIMER },
                            style = MaterialTheme.typography.caption3,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Extra padding so disclaimer doesn't clip at bottom of round screen
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    is ChatState.Idle -> {
                        if (!hasSent && prefillQuery != null) {
                            Text(
                                text = prefillQuery,
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    hasSent = true
                                    sendQuery(scope, context, prefillQuery)
                                },
                                modifier = Modifier.fillMaxWidth(0.8f),
                            ) {
                                Text("Send")
                            }
                        } else {
                            Text(
                                text = "Ask AI",
                                style = MaterialTheme.typography.title3,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Voice input button (launches system speech recognizer)
                            Button(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask a question...")
                                    }
                                    try {
                                        voiceLauncher.launch(intent)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        timber.log.Timber.w("No speech recognizer available")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.9f),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFF3B82F6),
                                ),
                            ) {
                                Text("Ask a question...", textAlign = TextAlign.Center)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Quick questions",
                                style = MaterialTheme.typography.caption3,
                                color = Color(0xFF9CA3AF),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            for (query in QUICK_QUERIES) {
                                Button(
                                    onClick = {
                                        hasSent = true
                                        sendQuery(scope, context, query)
                                    },
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                ) {
                                    Text(query, textAlign = TextAlign.Center)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sendQuery(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    query: String,
) {
    val sanitizedQuery = query.trim().take(ChatActivity.MAX_QUERY_LENGTH)
    if (sanitizedQuery.isBlank()) {
        WatchDataRepository.setChatError("Please enter a question")
        return
    }

    WatchDataRepository.setChatLoading()
    scope.launch {
        try {
            val sent = WearMessageSender.sendChatRequest(context, sanitizedQuery)
            if (!sent) {
                WatchDataRepository.setChatError("Phone not connected")
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to send chat request")
            WatchDataRepository.setChatError("Failed to send request")
        }
    }
}

private val QUICK_QUERIES = listOf(
    "How am I doing?",
    "Breakfast advice",
    "Why is my BG high?",
)

private const val CHAT_TIMEOUT_MS = 30_000L
private const val DEFAULT_AI_DISCLAIMER = "Not medical advice. Consult your doctor."

/** Pre-compiled regexes for markdown stripping (avoid recompilation per call). */
private object MarkdownPatterns {
    val FENCED_CODE = Regex("""```[\s\S]*?```""")
    val HEADERS = Regex("""^#{1,6}\s+""", RegexOption.MULTILINE)
    val BOLD_ITALIC_STAR = Regex("""\*{3}(.+?)\*{3}""")
    val BOLD_ITALIC_UNDER = Regex("""_{3}(.+?)_{3}""")
    val BOLD_STAR = Regex("""\*{2}(.+?)\*{2}""")
    val BOLD_UNDER = Regex("""_{2}(.+?)_{2}""")
    val ITALIC_STAR = Regex("""\*(.+?)\*""")
    val ITALIC_UNDER = Regex("""(^|\s)_(.+?)_(?=\s|$)""")
    val HORIZONTAL_RULE = Regex("""^[-*_]{3,}\s*$""", RegexOption.MULTILINE)
    val TABLE_SEPARATOR = Regex("""^\|[-:| ]+\|\s*$""", RegexOption.MULTILINE)
    val TABLE_ROW = Regex("""^\|(.+)\|\s*$""", RegexOption.MULTILINE)
    val INLINE_CODE = Regex("""`([^`]+)`""")
    val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
    val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    val BULLET = Regex("""^[-*+]\s+""", RegexOption.MULTILINE)
    val BLANK_LINES = Regex("""\n{3,}""")
}

/** Strips markdown syntax to produce clean readable plaintext for the watch screen. */
private fun stripMarkdown(md: String): String {
    return md
        .replace(MarkdownPatterns.FENCED_CODE, "")
        .replace(MarkdownPatterns.HEADERS, "")
        .replace(MarkdownPatterns.BOLD_ITALIC_STAR, "$1")
        .replace(MarkdownPatterns.BOLD_ITALIC_UNDER, "$1")
        .replace(MarkdownPatterns.BOLD_STAR, "$1")
        .replace(MarkdownPatterns.BOLD_UNDER, "$1")
        .replace(MarkdownPatterns.ITALIC_STAR, "$1")
        .replace(MarkdownPatterns.ITALIC_UNDER, "$1$2")
        .replace(MarkdownPatterns.HORIZONTAL_RULE, "")
        .replace(MarkdownPatterns.TABLE_SEPARATOR, "")
        .replace(MarkdownPatterns.TABLE_ROW) { match ->
            match.groupValues[1].split("|").joinToString("  ") { it.trim() }
        }
        .replace(MarkdownPatterns.INLINE_CODE, "$1")
        .replace(MarkdownPatterns.IMAGE, "")
        .replace(MarkdownPatterns.LINK, "$1")
        .replace(MarkdownPatterns.BULLET, "\u2022 ")
        .replace(MarkdownPatterns.BLANK_LINES, "\n\n")
        .trim()
}
