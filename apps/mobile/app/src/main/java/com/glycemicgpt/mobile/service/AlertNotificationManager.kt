package com.glycemicgpt.mobile.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import timber.log.Timber
import com.glycemicgpt.mobile.data.local.entity.AlertEntity
import com.glycemicgpt.mobile.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_URGENT = "urgent_alerts"
        const val CHANNEL_STANDARD = "standard_alerts"
        private const val GROUP_KEY = "com.glycemicgpt.ALERTS"
        private val notificationIdCounter = AtomicInteger(1000)
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            "Urgent Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Emergency and urgent glucose alerts"
            setBypassDnd(true)
            enableVibration(true)
        }

        val standardChannel = NotificationChannel(
            CHANNEL_STANDARD,
            "Standard Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Warning and informational glucose alerts"
        }

        manager.createNotificationChannel(urgentChannel)
        manager.createNotificationChannel(standardChannel)
    }

    fun showAlertNotification(alert: AlertEntity) {
        // On Android 13+ (API 33), POST_NOTIFICATIONS must be granted at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.w("POST_NOTIFICATIONS permission not granted, skipping alert notification")
                return
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java)

        val isUrgent = alert.severity in listOf("urgent", "emergency")
        val channelId = if (isUrgent) CHANNEL_URGENT else CHANNEL_STANDARD

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "alerts")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.serverId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = buildTitle(alert)
        val text = alert.message

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                if (isUrgent) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .build()

        manager.notify(notificationIdCounter.incrementAndGet(), notification)
    }

    private fun buildTitle(alert: AlertEntity): String {
        val prefix = when (alert.severity) {
            "emergency" -> "EMERGENCY"
            "urgent" -> "URGENT"
            "warning" -> "Warning"
            else -> "Info"
        }
        val patientSuffix = alert.patientName?.let { " - $it" } ?: ""
        return "$prefix: ${alert.currentValue.toInt()} mg/dL$patientSuffix"
    }
}
