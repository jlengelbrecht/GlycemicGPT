package com.glycemicgpt.wear.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.glycemicgpt.wear.data.WearDataContract
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

private const val MAX_MESSAGES = 50

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        setContent {
            MaterialTheme {
                ChatScreen(
                    activity = this,
                    tts = tts,
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun ChatScreen(
    activity: ComponentActivity,
    tts: TextToSpeech?,
) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var isWaiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    var timeoutJob by remember { mutableStateOf<Job?>(null) }

    val messageClient = remember { Wearable.getMessageClient(activity) }
    val capabilityClient = remember { Wearable.getCapabilityClient(activity) }

    // Register message listener for responses
    DisposableEffect(Unit) {
        val listener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
            when (event.path) {
                WearDataContract.CHAT_RESPONSE_PATH -> {
                    timeoutJob?.cancel()
                    val parsed = ChatMessageParser.parse(event.data)
                    if (parsed != null) {
                        messages.add(ChatMessage(text = parsed.response, isUser = false))
                        while (messages.size > MAX_MESSAGES) {
                            messages.removeAt(0)
                        }
                        tts?.speak(parsed.response, TextToSpeech.QUEUE_FLUSH, null, "chat_response")
                    } else {
                        error = "Failed to parse response"
                    }
                    isWaiting = false
                }
                WearDataContract.CHAT_ERROR_PATH -> {
                    timeoutJob?.cancel()
                    error = String(event.data, Charsets.UTF_8)
                    isWaiting = false
                }
            }
        }
        messageClient.addListener(listener)
        onDispose {
            messageClient.removeListener(listener)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                messages.add(ChatMessage(text = spokenText, isUser = true))
                // Cap message list to prevent unbounded memory growth
                while (messages.size > MAX_MESSAGES) {
                    messages.removeAt(0)
                }
                error = null
                isWaiting = true

                scope.launch {
                    sendChatMessage(
                        capabilityClient = capabilityClient,
                        messageClient = messageClient,
                        text = spokenText,
                        onError = { msg ->
                            error = msg
                            isWaiting = false
                        },
                    )

                    // Timeout after 30 seconds
                    timeoutJob = launch {
                        delay(30_000)
                        if (isWaiting) {
                            error = "Response timed out"
                            isWaiting = false
                        }
                    }
                }

                // Scroll to bottom
                scope.launch {
                    delay(100)
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Message list
            ScalingLazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "Tap mic to ask AI",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                items(messages) { message ->
                    ChatBubble(message)
                }

                if (isWaiting) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                indicatorColor = Color.Gray,
                            )
                        }
                    }
                }
            }

            // Error display
            error?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Mic button
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask GlycemicGPT...")
                    }
                    speechLauncher.launch(intent)
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF3B82F6),
                ),
                enabled = !isWaiting,
            ) {
                Text(
                    text = "MIC",
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (message.isUser) Color(0xFF3B82F6) else Color(0xFF374151),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 12.sp,
            )
        }
    }
}

private suspend fun sendChatMessage(
    capabilityClient: CapabilityClient,
    messageClient: MessageClient,
    text: String,
    onError: (String) -> Unit,
) {
    try {
        val capInfo = withContext(Dispatchers.IO) {
            capabilityClient.getCapability(
                WearDataContract.CHAT_RELAY_CAPABILITY,
                CapabilityClient.FILTER_REACHABLE,
            ).await()
        }

        val phoneNodeId = capInfo.nodes.firstOrNull()?.id
        if (phoneNodeId == null) {
            onError("Phone not connected")
            return
        }

        withContext(Dispatchers.IO) {
            messageClient.sendMessage(
                phoneNodeId,
                WearDataContract.CHAT_REQUEST_PATH,
                text.toByteArray(Charsets.UTF_8),
            ).await()
        }

        Timber.d("Sent chat request to phone: %s", text.take(50))
    } catch (e: Exception) {
        Timber.w(e, "Failed to send chat message to phone")
        onError("Failed to reach phone")
    }
}
