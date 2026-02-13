package com.glycemicgpt.wear.watchface

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.glycemicgpt.wear.data.WatchDataRepository
import com.glycemicgpt.wear.presentation.ChatActivity
import com.glycemicgpt.wear.presentation.IoBDetailActivity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GlycemicRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
) : Renderer.CanvasRenderer2<GlycemicRenderer.SharedAssets>(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = canvasType,
    interactiveDrawModeUpdateDelayMillis = 1000L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = true,
) {

    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {}
    }

    override suspend fun createSharedAssets(): SharedAssets = SharedAssets()

    private val timePaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val iobPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val iobLabelPaint = Paint().apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val freshnessPaint = Paint().apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val noDataPaint = Paint().apply {
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val alertPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val chatLabelPaint = Paint().apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val timeFormatInteractive = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val timeFormatAmbient = DateTimeFormatter.ofPattern("HH:mm")

    private var iobBounds = Rect()
    private var chatBounds = Rect()

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
        val isAmbient = renderParameters.drawMode == androidx.wear.watchface.DrawMode.AMBIENT
        val centerX = bounds.exactCenterX()
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        canvas.drawColor(Color.BLACK)

        configureForMode(isAmbient)

        // Alert banner (top area, above time)
        val alertState = WatchDataRepository.alert.value
        if (alertState != null) {
            val alertY = height * 0.10f
            alertPaint.textSize = width * 0.06f
            alertPaint.color = if (isAmbient) Color.WHITE else alertColor(alertState.type)
            canvas.drawText(alertState.message, centerX, alertY, alertPaint)
        }

        // Time
        val timeText = if (isAmbient) {
            zonedDateTime.format(timeFormatAmbient)
        } else {
            zonedDateTime.format(timeFormatInteractive)
        }
        timePaint.textSize = width * 0.09f
        canvas.drawText(timeText, centerX, height * 0.22f, timePaint)

        // IoB
        val iobState = WatchDataRepository.iob.value
        val iobText = iobState?.let { "%.2f".format(it.iob) } ?: "--"
        iobPaint.textSize = width * 0.18f
        val iobY = height * 0.45f
        canvas.drawText(iobText, centerX, iobY, iobPaint)

        // Store IoB bounds for tap handling
        val iobTextWidth = iobPaint.measureText(iobText)
        iobBounds.set(
            (centerX - iobTextWidth / 2 - 20).toInt(),
            (iobY - iobPaint.textSize).toInt(),
            (centerX + iobTextWidth / 2 + 20).toInt(),
            (iobY + 10).toInt(),
        )

        // "units" label
        iobLabelPaint.textSize = width * 0.055f
        canvas.drawText("units", centerX, height * 0.52f, iobLabelPaint)

        // BG reading
        val cgmState = WatchDataRepository.cgm.value
        if (cgmState != null) {
            val bgText = "${cgmState.mgDl} mg/dL ${trendSymbol(cgmState.trend)}"
            bgPaint.textSize = width * 0.08f
            bgPaint.color = if (isAmbient) {
                Color.WHITE
            } else {
                bgColor(cgmState.mgDl, cgmState.low, cgmState.high, cgmState.urgentLow, cgmState.urgentHigh)
            }
            canvas.drawText(bgText, centerX, height * 0.68f, bgPaint)

            // Freshness
            val ageMs = System.currentTimeMillis() - cgmState.timestampMs
            val ageText = formatAge(ageMs)
            freshnessPaint.textSize = width * 0.05f
            freshnessPaint.color = if (isAmbient) Color.GRAY else freshnessColor(ageMs)
            canvas.drawText(ageText, centerX, height * 0.76f, freshnessPaint)
        } else {
            noDataPaint.textSize = width * 0.06f
            canvas.drawText("Waiting for data...", centerX, height * 0.68f, noDataPaint)
        }

        // Chat tap zone (bottom area)
        if (!isAmbient) {
            chatLabelPaint.textSize = width * 0.05f
            val chatY = height * 0.90f
            canvas.drawText("AI Chat", centerX, chatY, chatLabelPaint)

            val chatTextWidth = chatLabelPaint.measureText("AI Chat")
            chatBounds.set(
                (centerX - chatTextWidth / 2 - 30).toInt(),
                (chatY - chatLabelPaint.textSize - 10).toInt(),
                (centerX + chatTextWidth / 2 + 30).toInt(),
                (chatY + 10).toInt(),
            )
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
        canvas.drawColor(Color.argb(128, 0, 0, 0))
    }

    fun handleTap(tapType: Int, x: Int, y: Int) {
        if (tapType != TapType.UP) return

        when {
            iobBounds.contains(x, y) -> {
                val intent = Intent(context, IoBDetailActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            chatBounds.contains(x, y) -> {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun configureForMode(isAmbient: Boolean) {
        timePaint.isAntiAlias = !isAmbient
        iobPaint.isAntiAlias = !isAmbient
        iobLabelPaint.isAntiAlias = !isAmbient
        bgPaint.isAntiAlias = !isAmbient
        freshnessPaint.isAntiAlias = !isAmbient
        alertPaint.isAntiAlias = !isAmbient
        chatLabelPaint.isAntiAlias = !isAmbient

        if (isAmbient) {
            timePaint.color = Color.WHITE
            iobPaint.color = Color.WHITE
            iobLabelPaint.color = Color.GRAY
        } else {
            timePaint.color = Color.WHITE
            iobPaint.color = Color.WHITE
            iobLabelPaint.color = Color.GRAY
        }
    }

    companion object {

        fun bgColor(mgDl: Int, low: Int, high: Int, urgentLow: Int, urgentHigh: Int): Int {
            return when {
                mgDl <= urgentLow || mgDl >= urgentHigh -> 0xFFEF4444.toInt() // Red
                mgDl <= low || mgDl >= high -> 0xFFEAB308.toInt()              // Yellow
                else -> 0xFF22C55E.toInt()                                      // Green
            }
        }

        fun alertColor(type: String): Int {
            return when (type) {
                "urgent_low", "urgent_high" -> 0xFFEF4444.toInt() // Red
                "low", "high" -> 0xFFEAB308.toInt()                // Yellow
                else -> Color.WHITE
            }
        }

        fun trendSymbol(trend: String): String {
            return when (trend) {
                "DOUBLE_UP" -> "\u21C8"       // upwards paired arrows
                "SINGLE_UP" -> "\u2191"        // upwards arrow
                "FORTY_FIVE_UP" -> "\u2197"    // north east arrow
                "FLAT" -> "\u2192"             // rightwards arrow
                "FORTY_FIVE_DOWN" -> "\u2198"  // south east arrow
                "SINGLE_DOWN" -> "\u2193"      // downwards arrow
                "DOUBLE_DOWN" -> "\u21CA"      // downwards paired arrows
                else -> "?"
            }
        }

        fun formatAge(ageMs: Long): String {
            val minutes = ageMs / 60_000
            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "${minutes}m ago"
                else -> "${minutes / 60}h ${minutes % 60}m ago"
            }
        }

        fun freshnessColor(ageMs: Long): Int {
            val minutes = ageMs / 60_000
            return when {
                minutes < 2 -> 0xFF22C55E.toInt()   // Green
                minutes < 10 -> 0xFFF97316.toInt()   // Orange
                else -> 0xFFEF4444.toInt()            // Red
            }
        }
    }
}
