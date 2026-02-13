package com.glycemicgpt.mobile.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.repository.ChatRepository
import com.glycemicgpt.mobile.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ListeningState { IDLE, LISTENING }
enum class SpeakingState { IDLE, SPEAKING }

data class AiChatUiState(
    val textInput: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val listeningState: ListeningState = ListeningState.IDLE,
    val speakingState: SpeakingState = SpeakingState.IDLE,
    val ttsEnabled: Boolean = true,
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val appSettingsStore: AppSettingsStore,
    private val authTokenStore: AuthTokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiChatUiState(ttsEnabled = appSettingsStore.ttsEnabled),
    )
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = chatRepository.observeRecentMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Most recent AI response text, for TTS consumption. Cleared after read. */
    private val _latestAiResponse = MutableStateFlow<String?>(null)
    val latestAiResponse: StateFlow<String?> = _latestAiResponse.asStateFlow()

    fun updateTextInput(text: String) {
        _uiState.value = _uiState.value.copy(textInput = text)
    }

    fun sendTextMessage() {
        val text = _uiState.value.textInput.trim()
        if (text.isEmpty() || _uiState.value.isSending) return
        if (text.length > MAX_MESSAGE_LENGTH) {
            _uiState.value = _uiState.value.copy(
                error = "Message too long (max $MAX_MESSAGE_LENGTH characters)",
            )
            return
        }
        doSend(text)
    }

    fun onSpeechResult(text: String) {
        if (text.isBlank() || _uiState.value.isSending) return
        val trimmed = text.trim().take(MAX_MESSAGE_LENGTH)
        _uiState.value = _uiState.value.copy(textInput = trimmed)
        doSend(trimmed)
    }

    fun setListeningState(state: ListeningState) {
        _uiState.value = _uiState.value.copy(listeningState = state)
    }

    fun setSpeakingState(state: SpeakingState) {
        _uiState.value = _uiState.value.copy(speakingState = state)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun consumeLatestAiResponse() {
        _latestAiResponse.value = null
    }

    private fun doSend(text: String) {
        if (!authTokenStore.isLoggedIn()) {
            _uiState.value = _uiState.value.copy(
                error = "Sign in from Settings to use AI Chat",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSending = true,
                error = null,
                textInput = "",
            )
            chatRepository.sendMessageWithContext(text)
                .onSuccess { chatMessage ->
                    _uiState.value = _uiState.value.copy(isSending = false)
                    // Re-read TTS setting in case user toggled it in Settings
                    if (appSettingsStore.ttsEnabled) {
                        _latestAiResponse.value = chatMessage.aiResponse
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send message",
                    )
                }
        }
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 500
    }
}
