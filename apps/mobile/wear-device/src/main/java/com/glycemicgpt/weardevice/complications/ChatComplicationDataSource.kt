package com.glycemicgpt.weardevice.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import com.glycemicgpt.weardevice.presentation.ChatActivity

class ChatComplicationDataSource : SuspendingComplicationDataSourceService() {

    private val tapPendingIntent by lazy {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        PendingIntent.getActivity(
            this, REQUEST_CODE_CHAT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ICON_SIZE = 48
        private const val REQUEST_CODE_CHAT = 4
        private const val COLOR_DIM_WHITE = 0x99FFFFFF.toInt()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        if (type != ComplicationType.SMALL_IMAGE) return NoDataComplicationData()
        return buildComplicationData()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (request.complicationType != ComplicationType.SMALL_IMAGE) {
            return NoDataComplicationData()
        }
        return buildComplicationData()
    }

    private fun buildComplicationData(): ComplicationData {
        val bitmap = renderChatIcon()
        val icon = Icon.createWithBitmap(bitmap)
        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
            contentDescription = PlainComplicationText.Builder("AI Chat").build(),
        ).setTapAction(tapPendingIntent).build()
    }

    private fun renderChatIcon(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = COLOR_DIM_WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val scale = ICON_SIZE / 24f
        val cx = ICON_SIZE / 2f

        // Chat bubble body (rounded rectangle)
        val bubbleRect = RectF(
            2f * scale, 2f * scale,
            22f * scale, 16f * scale,
        )
        canvas.drawRoundRect(bubbleRect, 3f * scale, 3f * scale, paint)

        // Chat bubble tail (triangle pointing down-left)
        val tail = Path().apply {
            moveTo(4f * scale, 16f * scale)
            lineTo(2f * scale, 20f * scale)
            lineTo(8f * scale, 16f * scale)
            close()
        }
        canvas.drawPath(tail, paint)

        // Three dots inside bubble (typing indicator)
        val dotPaint = Paint().apply {
            color = 0xFF000000.toInt()
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val dotY = 9f * scale
        val dotR = 1.2f * scale
        canvas.drawCircle(cx - 4f * scale, dotY, dotR, dotPaint)
        canvas.drawCircle(cx, dotY, dotR, dotPaint)
        canvas.drawCircle(cx + 4f * scale, dotY, dotR, dotPaint)

        return bitmap
    }
}
