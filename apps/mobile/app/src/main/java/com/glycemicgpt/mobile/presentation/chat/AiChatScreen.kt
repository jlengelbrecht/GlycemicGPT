package com.glycemicgpt.mobile.presentation.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.glycemicgpt.mobile.domain.model.ChatMessage
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val latestAiResponse by viewModel.latestAiResponse.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // TTS engine
    var ttsReady by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
        ttsEngine = tts
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                viewModel.setSpeakingState(SpeakingState.SPEAKING)
            }
            override fun onDone(utteranceId: String?) {
                viewModel.setSpeakingState(SpeakingState.IDLE)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                viewModel.setSpeakingState(SpeakingState.IDLE)
            }
        })
        onDispose {
            tts.stop()
            tts.shutdown()
            ttsEngine = null
        }
    }

    // Speak new AI responses via TTS
    LaunchedEffect(latestAiResponse) {
        val text = latestAiResponse ?: return@LaunchedEffect
        if (ttsReady) {
            ttsEngine?.language = Locale.getDefault()
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ai_response")
        }
        viewModel.consumeLatestAiResponse()
    }

    // Speech recognizer
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speechAvailable by remember { mutableStateOf(true) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startListening(speechRecognizer, viewModel)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for voice input")
            }
        }
    }

    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    viewModel.setListeningState(ListeningState.LISTENING)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    viewModel.setListeningState(ListeningState.IDLE)
                }
                override fun onError(error: Int) {
                    viewModel.setListeningState(ListeningState.IDLE)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestMatch = matches?.firstOrNull()
                    if (!bestMatch.isNullOrBlank()) {
                        viewModel.onSpeechResult(bestMatch)
                    }
                    viewModel.setListeningState(ListeningState.IDLE)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer = recognizer
        } else {
            speechAvailable = false
        }
        onDispose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    // Show errors as snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty() && !uiState.isSending) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Ask a question about your diabetes management",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(message = message)
                }
                if (uiState.isSending) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Input bar
            InputBar(
                text = uiState.textInput,
                onTextChange = viewModel::updateTextInput,
                onSend = viewModel::sendTextMessage,
                onMicClick = {
                    if (!speechAvailable) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Voice input not available on this device")
                        }
                        return@InputBar
                    }
                    if (hasAudioPermission) {
                        if (uiState.listeningState == ListeningState.LISTENING) {
                            speechRecognizer?.stopListening()
                            viewModel.setListeningState(ListeningState.IDLE)
                        } else {
                            startListening(speechRecognizer, viewModel)
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isSending = uiState.isSending,
                listeningState = uiState.listeningState,
                micEnabled = speechAvailable,
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // User message -- right aligned
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = message.userMessage,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // AI response -- left aligned
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = message.aiResponse,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.disclaimer.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.disclaimer,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    isSending: Boolean,
    listeningState: ListeningState,
    micEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about your data...") },
            singleLine = false,
            maxLines = 3,
            enabled = !isSending,
        )

        Spacer(modifier = Modifier.width(4.dp))

        AnimatedMicButton(
            listeningState = listeningState,
            onClick = onMicClick,
            enabled = !isSending && micEnabled,
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isSending,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (text.isNotBlank() && !isSending) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

@Composable
private fun AnimatedMicButton(
    listeningState: ListeningState,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val isListening = listeningState == ListeningState.LISTENING

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mic_scale",
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .then(
                if (isListening) {
                    Modifier
                        .scale(scale)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            shape = CircleShape,
                        )
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Start voice input",
            tint = if (isListening) {
                MaterialTheme.colorScheme.error
            } else if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}

private fun startListening(recognizer: SpeechRecognizer?, viewModel: AiChatViewModel) {
    recognizer ?: return
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    viewModel.setListeningState(ListeningState.LISTENING)
    recognizer.startListening(intent)
}
