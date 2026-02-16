package com.glycemicgpt.mobile.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.data.repository.ChatRepository
import com.glycemicgpt.mobile.data.repository.NoProviderException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import javax.inject.Inject

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val disclaimer: String? = null,
)

sealed class ChatPageState {
    data object Loading : ChatPageState()
    data object NoProvider : ChatPageState()
    data object Ready : ChatPageState()
    data object Offline : ChatPageState()
}

data class AiChatUiState(
    val pageState: ChatPageState = ChatPageState.Loading,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    companion object {
        const val MAX_MESSAGE_LENGTH = 2000
        private const val MAX_MESSAGES = 100
    }

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        checkProvider()
    }

    fun checkProvider() {
        viewModelScope.launch {
            _uiState.update { it.copy(pageState = ChatPageState.Loading) }
            chatRepository.checkProviderConfigured()
                .onSuccess {
                    _uiState.update { it.copy(pageState = ChatPageState.Ready) }
                }
                .onFailure { e ->
                    val state = if (e is NoProviderException) {
                        ChatPageState.NoProvider
                    } else {
                        ChatPageState.Offline
                    }
                    _uiState.update { it.copy(pageState = state) }
                }
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return
        if (text.length > MAX_MESSAGE_LENGTH) {
            _uiState.update { it.copy(error = "Message is too long (max $MAX_MESSAGE_LENGTH characters)") }
            return
        }

        val userMessage = ChatMessage(role = MessageRole.USER, content = text)
        _uiState.update {
            it.copy(
                messages = (it.messages + userMessage).takeLast(MAX_MESSAGES),
                inputText = "",
                isSending = true,
                error = null,
            )
        }

        viewModelScope.launch {
            chatRepository.sendMessage(text)
                .onSuccess { response ->
                    val assistantMessage = ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = response.response,
                        disclaimer = response.disclaimer,
                    )
                    _uiState.update {
                        it.copy(
                            messages = (it.messages + assistantMessage).takeLast(MAX_MESSAGES),
                            isSending = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            error = userFriendlyError(e),
                        )
                    }
                }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearChat() {
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onSuggestionClicked(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    private fun userFriendlyError(e: Throwable): String {
        return when {
            e is SocketTimeoutException -> "AI response took too long. Please try again"
            e is IOException -> "Check your internet connection and try again"
            e.message?.contains("401") == true -> "Session expired. Please sign in again"
            e.message?.contains("5") == true &&
                e.message?.contains("HTTP") == true -> "Server error. Please try again later"
            else -> e.message ?: "Failed to get response"
        }
    }
}
