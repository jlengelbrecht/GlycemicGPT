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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glycemicgpt.mobile.R
import com.glycemicgpt.mobile.ble.connection.BleConnectionManager
import com.glycemicgpt.mobile.domain.model.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that maintains persistent BLE connection to the pump
 * and orchestrates periodic data polling.
 *
 * Holds a PARTIAL_WAKE_LOCK while the pump is connected to prevent the CPU
 * from sleeping and missing the 15-second keep-alive polls (the pump drops
 * idle BLE connections after ~30 seconds).
 *
 * The wake lock uses a 20-minute safety timeout as a leak guard, and a
 * renewal loop re-acquires it every 15 minutes while connected.
 *
 * Polling pauses when BLE connection is lost and resumes on reconnect.
 * Reduces poll frequency when phone battery drops below 15%.
 * Releases wake lock when phone battery drops below 5% (critical).
 */
@AndroidEntryPoint
class PumpConnectionService : Service() {

    companion object {
        const val CHANNEL_ID = "pump_connection"
        const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_THRESHOLD = 15
        private const val CRITICAL_BATTERY_THRESHOLD = 5
        private const val WAKE_LOCK_TAG = "GlycemicGPT:PumpBleConnection"
        private const val WAKE_LOCK_TIMEOUT_MS = 20L * 60 * 1000 // 20-minute safety timeout
        private const val WAKE_LOCK_RENEW_MS = 15L * 60 * 1000 // renew every 15 minutes

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
    private val wakeLockSync = Any()
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionWatcherJob: Job? = null
    private var wakeLockRenewalJob: Job? = null
    private var started = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                val wasLow = pollingOrchestrator.phoneBatteryLow
                pollingOrchestrator.phoneBatteryLow = pct < LOW_BATTERY_THRESHOLD

                // Critical battery: release wake lock to preserve phone battery
                if (pct < CRITICAL_BATTERY_THRESHOLD) {
                    synchronized(wakeLockSync) {
                        if (wakeLock?.isHeld == true) {
                            releaseWakeLockLocked()
                            Timber.w(
                                "Phone battery critical (%d%%), wake lock released to preserve battery",
                                pct,
                            )
                        }
                    }
                } else {
                    synchronized(wakeLockSync) {
                        if (wakeLock?.isHeld != true &&
                            connectionManager.connectionState.value == ConnectionState.CONNECTED
                        ) {
                            // Re-acquire if battery recovered and pump is still connected
                            acquireWakeLockLocked()
                            Timber.d("Phone battery recovered (%d%%), wake lock re-acquired", pct)
                        }
                    }
                }

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

        // Guard: only start orchestrators and watchers once per service lifecycle.
        // onStartCommand may be called multiple times (re-delivery, duplicate starts).
        if (!started) {
            started = true
            pollingOrchestrator.backendSyncManager = backendSyncManager
            pollingOrchestrator.start(serviceScope)
            backendSyncManager.start(serviceScope)
            connectionManager.autoReconnectIfPaired()

            // Watch connection state to acquire/release wake lock
            connectionWatcherJob = serviceScope.launch {
                connectionManager.connectionState.collect { state ->
                    synchronized(wakeLockSync) {
                        if (state == ConnectionState.CONNECTED) {
                            acquireWakeLockLocked()
                            startWakeLockRenewalLocked()
                        } else {
                            stopWakeLockRenewalLocked()
                            releaseWakeLockLocked()
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            batteryReceiverRegistered = true
        }

        Timber.d("PumpConnectionService started in foreground with polling")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingOrchestrator.stop()
        backendSyncManager.stop()
        connectionWatcherJob?.cancel()
        connectionWatcherJob = null
        synchronized(wakeLockSync) {
            stopWakeLockRenewalLocked()
            releaseWakeLockLocked()
        }
        if (batteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was not registered
            }
            batteryReceiverRegistered = false
        }
        started = false
        serviceScope.cancel()
        Timber.d("PumpConnectionService destroyed")
        super.onDestroy()
    }

    /** Must be called under [wakeLockSync]. */
    private fun acquireWakeLockLocked() {
        // Release old instance first to avoid orphaned WakeLock objects
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        Timber.d("Wake lock acquired for BLE connection")
    }

    /** Must be called under [wakeLockSync]. */
    private fun releaseWakeLockLocked() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("Wake lock released")
            }
        }
        wakeLock = null
    }

    /**
     * Starts a coroutine that periodically re-acquires the wake lock before the
     * safety timeout expires. This ensures continuous CPU wakefulness during
     * overnight BLE monitoring. Must be called under [wakeLockSync].
     */
    private fun startWakeLockRenewalLocked() {
        if (wakeLockRenewalJob?.isActive == true) return
        wakeLockRenewalJob = serviceScope.launch {
            while (true) {
                delay(WAKE_LOCK_RENEW_MS)
                synchronized(wakeLockSync) {
                    if (connectionManager.connectionState.value == ConnectionState.CONNECTED) {
                        acquireWakeLockLocked()
                        Timber.d("Wake lock renewed")
                    }
                }
            }
        }
    }

    /** Must be called under [wakeLockSync]. */
    private fun stopWakeLockRenewalLocked() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
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
