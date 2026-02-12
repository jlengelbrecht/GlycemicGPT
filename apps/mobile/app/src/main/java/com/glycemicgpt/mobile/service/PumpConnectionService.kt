package com.glycemicgpt.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.glycemicgpt.mobile.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Foreground service that maintains persistent BLE connection to the pump.
 *
 * This service keeps the BLE connection alive even when the app is in the
 * background, ensuring continuous data collection from the pump.
 *
 * Pump data polling and backend sync will be implemented in Stories 16.4 and 16.5.
 */
@AndroidEntryPoint
class PumpConnectionService : Service() {

    companion object {
        const val CHANNEL_ID = "pump_connection"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("PumpConnectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        Timber.d("PumpConnectionService started in foreground")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("PumpConnectionService destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pump Connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Maintains connection to insulin pump"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.pump_service_notification_title))
            .setContentText(getString(R.string.pump_service_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
