package com.glycemicgpt.weardevice.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.glycemicgpt.weardevice.data.WatchDataRepository
import com.glycemicgpt.weardevice.presentation.AlertsActivity

class AlertsComplicationDataSource : SuspendingComplicationDataSourceService() {

    private val tapPendingIntent by lazy {
        val intent = Intent(this, AlertsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        PendingIntent.getActivity(
            this, REQUEST_CODE_ALERTS, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ICON_SIZE = 48
        private const val REQUEST_CODE_ALERTS = 3

        // Bell icon colors
        private const val COLOR_DIM_WHITE = 0x99FFFFFF.toInt()
        private const val COLOR_ALERT_RED = 0xFFEF4444.toInt()
        private const val COLOR_ALERT_YELLOW = 0xFFFBBF24.toInt()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        if (type != ComplicationType.SMALL_IMAGE) return NoDataComplicationData()
        return buildComplicationData(bellColor = COLOR_DIM_WHITE, description = "Alerts preview")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (request.complicationType != ComplicationType.SMALL_IMAGE) {
            return NoDataComplicationData()
        }

        val alert = WatchDataRepository.alert.value
        val bellColor = when {
            alert == null -> COLOR_DIM_WHITE
            alert.type.startsWith("urgent") -> COLOR_ALERT_RED
            else -> COLOR_ALERT_YELLOW
        }
        val description = if (alert != null) "Active alert: ${alert.type}" else "No active alerts"
        return buildComplicationData(bellColor, description)
    }

    private fun buildComplicationData(bellColor: Int, description: String): ComplicationData {
        val bitmap = renderBellIcon(bellColor)
        val icon = Icon.createWithBitmap(bitmap)
        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
            contentDescription = PlainComplicationText.Builder(description).build(),
        ).setTapAction(tapPendingIntent).build()
    }

    private fun renderBellIcon(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val cx = ICON_SIZE / 2f
        val cy = ICON_SIZE / 2f
        val scale = ICON_SIZE / 24f

        // Bell body (scaled from 24dp viewport)
        val path = Path().apply {
            // Bell dome
            moveTo(cx - 6f * scale, cy + 3f * scale)
            lineTo(cx - 6f * scale, cy - 2f * scale)
            cubicTo(
                cx - 6f * scale, cy - 5.07f * scale,
                cx - 4.37f * scale, cy - 7.64f * scale,
                cx - 1.5f * scale, cy - 8.32f * scale,
            )
            lineTo(cx - 1.5f * scale, cy - 9f * scale)
            cubicTo(
                cx - 1.5f * scale, cy - 9.83f * scale,
                cx - 0.83f * scale, cy - 10.5f * scale,
                cx, cy - 10.5f * scale,
            )
            cubicTo(
                cx + 0.83f * scale, cy - 10.5f * scale,
                cx + 1.5f * scale, cy - 9.83f * scale,
                cx + 1.5f * scale, cy - 9f * scale,
            )
            lineTo(cx + 1.5f * scale, cy - 8.32f * scale)
            cubicTo(
                cx + 4.37f * scale, cy - 7.64f * scale,
                cx + 6f * scale, cy - 5.07f * scale,
                cx + 6f * scale, cy - 2f * scale,
            )
            lineTo(cx + 6f * scale, cy + 3f * scale)
            lineTo(cx + 8f * scale, cy + 5f * scale)
            lineTo(cx + 8f * scale, cy + 6f * scale)
            lineTo(cx - 8f * scale, cy + 6f * scale)
            lineTo(cx - 8f * scale, cy + 5f * scale)
            close()
        }
        canvas.drawPath(path, paint)

        // Bell clapper (bottom circle)
        canvas.drawCircle(cx, cy + 8.5f * scale, 2f * scale, paint)

        return bitmap
    }
}
