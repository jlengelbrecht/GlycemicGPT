package com.glycemicgpt.mobile.wear

import android.content.Context
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes the bundled WFF watch face APK from app assets to the watch
 * via [ChannelClient]. The watch-side [WatchFaceReceiveService] receives
 * the bytes and installs via the Watch Face Push API.
 *
 * Usage:
 * ```
 * val result = watchFacePusher.pushWatchFace()
 * when (result) {
 *     is WatchFacePusher.Result.Success -> // face pushed
 *     is WatchFacePusher.Result.Error -> // show error
 * }
 * ```
 */
@Singleton
class WatchFacePusher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed class Result {
        data class Success(val status: String, val slotId: String?) : Result()
        data class Error(val message: String) : Result()
    }

    companion object {
        private const val WATCHFACE_ASSET = "glycemicgpt-watchface.apk"
        /** Timeout waiting for status message back from watch */
        private const val STATUS_TIMEOUT_MS = 30_000L
        /** Timeout for the channel send operation */
        private const val SEND_TIMEOUT_MS = 120_000L
    }

    /**
     * Push the bundled watch face APK to the first connected watch node.
     * Returns [Result.Success] with the install status from the watch,
     * or [Result.Error] if no watch is connected or the push fails.
     */
    suspend fun pushWatchFace(): Result {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            val watchNode = nodes.firstOrNull()
                ?: return Result.Error("No watch connected")

            Timber.d("Pushing watch face to node: %s (%s)", watchNode.displayName, watchNode.id)

            // Open a channel to the watch on the watchface push path
            val channelClient = Wearable.getChannelClient(context)
            val channel = channelClient.openChannel(
                watchNode.id,
                WearDataContract.WATCHFACE_PUSH_CHANNEL,
            ).await()

            try {
                streamApkToChannel(channelClient, channel)
            } finally {
                try {
                    channelClient.close(channel).await()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to close channel")
                }
            }

            // Wait for status response from watch via MessageClient
            waitForPushStatus()
        } catch (e: Exception) {
            Timber.e(e, "Failed to push watch face")
            Result.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun streamApkToChannel(
        channelClient: ChannelClient,
        channel: ChannelClient.Channel,
    ) = coroutineScope {
        val watchdog = launchSendWatchdog(channelClient, channel)
        try {
            val outputStream = channelClient.getOutputStream(channel).await()
            outputStream.use { output ->
                context.assets.open(WATCHFACE_ASSET).use { input ->
                    copyWithProgress(input, output)
                }
            }
            Timber.d("Watch face APK streamed successfully")
        } finally {
            watchdog.cancel()
        }
    }

    /** Watchdog that force-closes the channel if the send stalls. */
    private fun kotlinx.coroutines.CoroutineScope.launchSendWatchdog(
        channelClient: ChannelClient,
        channel: ChannelClient.Channel,
    ): Job = launch {
        delay(SEND_TIMEOUT_MS)
        Timber.w("Watch face send timed out after %d ms, force-closing channel", SEND_TIMEOUT_MS)
        try {
            channelClient.close(channel).await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to force-close channel on timeout")
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: OutputStream,
    ) {
        val buffer = ByteArray(8192)
        var totalBytes = 0L
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }
        Timber.d("Sent %d bytes to watch", totalBytes)
    }

    /**
     * Wait for the watch to send a status message back via MessageClient.
     * The watch sends a key=value payload to [WearDataContract.WATCHFACE_PUSH_STATUS_PATH].
     *
     * Since MessageClient listeners require registration, we use a simple
     * polling approach with a shared status holder set by [WearChatRelayService]
     * (which already listens for all messages).
     *
     * For the initial implementation, we return a generic success after sending.
     * Story 32.4 (Settings UI) will add proper status listening via a
     * MessageClient.OnMessageReceivedListener callback.
     */
    private suspend fun waitForPushStatus(): Result {
        // TODO(Story 32.4): Register a MessageClient listener for real-time status.
        // For now, assume success if the channel send completed without error.
        // The watch logs the actual install result.
        return Result.Success(status = "sent", slotId = null)
    }
}
