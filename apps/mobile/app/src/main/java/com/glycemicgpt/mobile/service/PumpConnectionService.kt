package com.glycemicgpt.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.glycemicgpt.mobile.R
import com.glycemicgpt.mobile.ble.connection.BleConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that maintains persistent BLE connection to the pump
 * and orchestrates periodic data polling.
 *
 * Polling pauses when BLE connection is lost and resumes on reconnect.
 * Reduces poll frequency when phone battery drops below 15%.
 */
@AndroidEntryPoint
class PumpConnectionService : Service() {

    companion object {
        const val CHANNEL_ID = "pump_connection"
        const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_THRESHOLD = 15

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PumpConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PumpConnectionService::class.java))
        }
    }

    @Inject
    lateinit var pollingOrchestrator: PumpPollingOrchestrator

    @Inject
    lateinit var backendSyncManager: BackendSyncManager

    @Inject
    lateinit var connectionManager: BleConnectionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                val wasLow = pollingOrchestrator.phoneBatteryLow
                pollingOrchestrator.phoneBatteryLow = pct < LOW_BATTERY_THRESHOLD
                if (wasLow != pollingOrchestrator.phoneBatteryLow) {
                    Timber.d(
                        "Phone battery %d%% - polling %s",
                        pct,
                        if (pollingOrchestrator.phoneBatteryLow) "reduced" else "normal",
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("PumpConnectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        pollingOrchestrator.backendSyncManager = backendSyncManager
        pollingOrchestrator.start(serviceScope)
        backendSyncManager.start(serviceScope)
        connectionManager.autoReconnectIfPaired()
        if (!batteryReceiverRegistered) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
        }

        Timber.d("PumpConnectionService started in foreground with polling")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingOrchestrator.stop()
        backendSyncManager.stop()
        if (batteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was not registered
            }
            batteryReceiverRegistered = false
        }
        serviceScope.cancel()
        Timber.d("PumpConnectionService destroyed")
        super.onDestroy()
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
