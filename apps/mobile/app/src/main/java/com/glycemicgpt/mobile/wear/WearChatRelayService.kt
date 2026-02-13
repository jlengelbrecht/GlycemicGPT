package com.glycemicgpt.mobile.wear

import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.repository.ChatRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WearChatRelayService : WearableListenerService() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var authTokenStore: AuthTokenStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDataContract.CHAT_REQUEST_PATH) return

        val requestText = String(messageEvent.data, Charsets.UTF_8)
        val sourceNodeId = messageEvent.sourceNodeId

        // Enforce max message length to prevent abuse
        if (requestText.length > MAX_MESSAGE_LENGTH) {
            Timber.w("Chat request too long (%d chars), rejecting", requestText.length)
            serviceScope.launch {
                sendError(sourceNodeId, "Message too long (max $MAX_MESSAGE_LENGTH chars)")
            }
            return
        }

        // Check auth state before making API call
        if (!authTokenStore.isLoggedIn()) {
            Timber.w("Chat request received but user is not logged in")
            serviceScope.launch {
                sendError(sourceNodeId, "Not signed in. Open the phone app to sign in.")
            }
            return
        }

        Timber.d("Received chat request from watch: %s", requestText.take(50))

        serviceScope.launch {
            chatRepository.sendMessage(requestText)
                .onSuccess { chatResponse ->
                    val responseJson = JSONObject().apply {
                        put("response", chatResponse.response)
                        put("disclaimer", chatResponse.disclaimer)
                    }.toString()

                    try {
                        Wearable.getMessageClient(this@WearChatRelayService)
                            .sendMessage(
                                sourceNodeId,
                                WearDataContract.CHAT_RESPONSE_PATH,
                                responseJson.toByteArray(Charsets.UTF_8),
                            )
                            .await()
                        Timber.d("Sent chat response to watch")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to send chat response to watch")
                    }
                }
                .onFailure { error ->
                    val errorMsg = error.message ?: "Unknown error"
                    Timber.w(error, "Chat request failed: %s", errorMsg)
                    sendError(sourceNodeId, errorMsg)
                }
        }
    }

    private suspend fun sendError(nodeId: String, message: String) {
        try {
            Wearable.getMessageClient(this@WearChatRelayService)
                .sendMessage(
                    nodeId,
                    WearDataContract.CHAT_ERROR_PATH,
                    message.toByteArray(Charsets.UTF_8),
                )
                .await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to send error to watch")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 500
    }
}
