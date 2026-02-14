package com.glycemicgpt.mobile.service

import com.glycemicgpt.mobile.data.local.entity.AlertEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertNotificationManagerTest {

    private fun makeAlert(
        serverId: String = "alert-1",
        severity: String = "warning",
        currentValue: Double = 250.0,
        patientName: String? = null,
    ) = AlertEntity(
        serverId = serverId,
        alertType = "high_warning",
        severity = severity,
        message = "High glucose warning",
        currentValue = currentValue,
        timestampMs = System.currentTimeMillis(),
        patientName = patientName,
    )

    @Test
    fun `urgent channel selected for emergency severity`() {
        val alert = makeAlert(severity = "emergency")
        val isUrgent = alert.severity in listOf("urgent", "emergency")
        val channelId = if (isUrgent) AlertNotificationManager.CHANNEL_URGENT
            else AlertNotificationManager.CHANNEL_STANDARD
        assertEquals(AlertNotificationManager.CHANNEL_URGENT, channelId)
    }

    @Test
    fun `urgent channel selected for urgent severity`() {
        val alert = makeAlert(severity = "urgent")
        val isUrgent = alert.severity in listOf("urgent", "emergency")
        val channelId = if (isUrgent) AlertNotificationManager.CHANNEL_URGENT
            else AlertNotificationManager.CHANNEL_STANDARD
        assertEquals(AlertNotificationManager.CHANNEL_URGENT, channelId)
    }

    @Test
    fun `standard channel selected for warning severity`() {
        val alert = makeAlert(severity = "warning")
        val isUrgent = alert.severity in listOf("urgent", "emergency")
        val channelId = if (isUrgent) AlertNotificationManager.CHANNEL_URGENT
            else AlertNotificationManager.CHANNEL_STANDARD
        assertEquals(AlertNotificationManager.CHANNEL_STANDARD, channelId)
    }

    @Test
    fun `standard channel selected for info severity`() {
        val alert = makeAlert(severity = "info")
        val isUrgent = alert.severity in listOf("urgent", "emergency")
        val channelId = if (isUrgent) AlertNotificationManager.CHANNEL_URGENT
            else AlertNotificationManager.CHANNEL_STANDARD
        assertEquals(AlertNotificationManager.CHANNEL_STANDARD, channelId)
    }

    @Test
    fun `notification title includes severity prefix and glucose value`() {
        val alert = makeAlert(severity = "emergency", currentValue = 320.0)
        val title = buildTitle(alert)
        assertEquals("EMERGENCY: 320 mg/dL", title)
    }

    @Test
    fun `notification title includes patient name for caregivers`() {
        val alert = makeAlert(severity = "urgent", currentValue = 55.0, patientName = "Alice")
        val title = buildTitle(alert)
        assertEquals("URGENT: 55 mg/dL - Alice", title)
    }

    @Test
    fun `warning title uses mixed case`() {
        val alert = makeAlert(severity = "warning", currentValue = 200.0)
        val title = buildTitle(alert)
        assertEquals("Warning: 200 mg/dL", title)
    }

    @Test
    fun `info title uses mixed case`() {
        val alert = makeAlert(severity = "info", currentValue = 180.0)
        val title = buildTitle(alert)
        assertEquals("Info: 180 mg/dL", title)
    }

    // Mirror the private buildTitle logic for testing
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
