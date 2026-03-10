package com.glycemicgpt.weardevice.push

import com.glycemicgpt.weardevice.data.WearDataContract
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Receives watch face APK bytes from the phone app via ChannelClient
 * and installs the watch face using the Watch Face Push API.
 *
 * Flow:
 * 1. Phone opens ChannelClient with path /glycemicgpt/watchface/push
 * 2. Phone streams WFF APK bytes through the channel
 * 3. This service receives channel open, reads bytes to a temp file
 * 4. Calls WatchFacePushManager to install and activate the face
 * 5. Sends status back to phone via MessageClient
 */
class WatchFaceReceiveService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != WearDataContract.WATCHFACE_PUSH_CHANNEL) return

        Timber.d("Watch face push channel opened from phone")
        scope.launch {
            receiveAndInstallWatchFace(channel)
        }
    }

    private suspend fun receiveAndInstallWatchFace(channel: ChannelClient.Channel) {
        val tempFile = File(cacheDir, "watchface-push.apk")
        try {
            // Read APK bytes from channel into temp file
            val channelClient = Wearable.getChannelClient(this)
            val inputStream = channelClient.getInputStream(channel).await()
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output, bufferSize = 8192)
            }
            inputStream.close()

            val fileSize = tempFile.length()
            if (fileSize == 0L) {
                Timber.w("Received empty watch face APK, skipping install")
                sendStatus("error", error = "Empty APK received")
                return
            }
            Timber.d("Received watch face APK: %d bytes", fileSize)

            // Install via Watch Face Push API
            val installer = WatchFaceInstaller(this)
            val result = installer.installOrUpdate(tempFile)

            when (result) {
                is WatchFaceInstaller.Result.Installed -> {
                    Timber.d("Watch face installed: slot=%s", result.slotId)
                    sendStatus("installed", slotId = result.slotId)
                }
                is WatchFaceInstaller.Result.Updated -> {
                    Timber.d("Watch face updated: slot=%s", result.slotId)
                    sendStatus("updated", slotId = result.slotId)
                }
                is WatchFaceInstaller.Result.Error -> {
                    Timber.w("Watch face install failed: %s", result.message)
                    sendStatus("error", error = result.message)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to receive/install watch face")
            sendStatus("error", error = e.message ?: "Unknown error")
        } finally {
            tempFile.delete()
            try {
                Wearable.getChannelClient(this).close(channel).await()
            } catch (e: Exception) {
                Timber.w(e, "Failed to close channel")
            }
        }
    }

    private suspend fun sendStatus(status: String, slotId: String? = null, error: String? = null) {
        try {
            val messageClient = Wearable.getMessageClient(this)
            val nodeClient = Wearable.getNodeClient(this)
            val nodes = nodeClient.connectedNodes.await()
            val payload = buildString {
                append(status)
                slotId?.let { append("|slot=$it") }
                error?.let { append("|error=$it") }
            }.toByteArray()

            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    WearDataContract.WATCHFACE_PUSH_STATUS_PATH,
                    payload,
                ).await()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to send push status to phone")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
