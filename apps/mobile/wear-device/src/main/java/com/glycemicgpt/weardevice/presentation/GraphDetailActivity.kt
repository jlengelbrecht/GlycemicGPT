package com.glycemicgpt.weardevice.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.glycemicgpt.weardevice.complications.WatchGraphRenderer
import com.glycemicgpt.weardevice.data.WatchDataRepository
import com.glycemicgpt.weardevice.util.GlucoseDisplayUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GraphDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GraphDetailScreen()
        }
    }
}

@Composable
private fun GraphDetailScreen() {
    val cgmHistory by WatchDataRepository.cgmHistory.collectAsState()
    val bolusHistory by WatchDataRepository.bolusHistory.collectAsState()
    val config by WatchDataRepository.watchFaceConfig.collectAsState()
    val categoryLabels by WatchDataRepository.categoryLabels.collectAsState()

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            if (cgmHistory.size < 3) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Not enough data",
                        style = MaterialTheme.typography.body2,
                        color = Color(0xFF9CA3AF),
                    )
                }
            } else {
                val rangeMs = config.graphRangeHours.toLong() * 3_600_000L
                // Recompute cutoff when data or config changes
                val cutoff = remember(cgmHistory, bolusHistory, config.graphRangeHours) {
                    System.currentTimeMillis() - rangeMs
                }
                val readings = remember(cgmHistory, cutoff) {
                    cgmHistory.filter { it.timestampMs >= cutoff }.sortedBy { it.timestampMs }
                }
                val boluses = remember(bolusHistory, cutoff) {
                    bolusHistory.filter { it.timestampMs >= cutoff }.sortedBy { it.timestampMs }
                }

                if (readings.size < 3) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Not enough data",
                            style = MaterialTheme.typography.body2,
                            color = Color(0xFF9CA3AF),
                        )
                    }
                } else {
                    InteractiveGraph(
                        readings = readings,
                        boluses = boluses,
                        low = readings.last().low,
                        high = readings.last().high,
                        categoryLabels = categoryLabels,
                    )
                }
            }
        }
    }
}

private data class TooltipData(
    val mgDl: Int,
    val timestampMs: Long,
    val x: Float,
    val y: Float,
)

@Composable
private fun InteractiveGraph(
    readings: List<WatchDataRepository.CgmState>,
    boluses: List<WatchDataRepository.BolusHistoryRecord>,
    low: Int,
    high: Int,
    categoryLabels: Map<String, String> = emptyMap(),
) {
    var tooltip by remember { mutableStateOf<TooltipData?>(null) }

    // Dot positions populated during draw, read during tap. Plain mutable ref avoids
    // triggering recomposition from inside the Canvas draw lambda.
    val dotPositions = remember { mutableListOf<Triple<Float, Float, WatchDataRepository.CgmState>>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(readings) {
                    detectTapGestures { offset ->
                        val hitRadius = 20.dp.toPx()
                        val nearest = dotPositions.minByOrNull { (dx, dy, _) ->
                            val distX = offset.x - dx
                            val distY = offset.y - dy
                            distX * distX + distY * distY
                        }
                        if (nearest != null) {
                            val (dx, dy, reading) = nearest
                            val distX = offset.x - dx
                            val distY = offset.y - dy
                            val dist = kotlin.math.sqrt(distX * distX + distY * distY)
                            tooltip = if (dist <= hitRadius) {
                                TooltipData(reading.mgDl, reading.timestampMs, dx, dy)
                            } else {
                                null
                            }
                        } else {
                            tooltip = null
                        }
                    }
                },
        ) {
            dotPositions.clear()
            drawDetailGraph(readings, boluses, low, high, categoryLabels, dotPositions)
        }

        // Tooltip overlay
        val tip = tooltip
        val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
        if (tip != null) {
            val timeStr = timeFormatter.format(Date(tip.timestampMs))
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTooltip(tip.x, tip.y, "${tip.mgDl} mg/dL", timeStr)
            }
        }
    }
}

private fun DrawScope.drawDetailGraph(
    readings: List<WatchDataRepository.CgmState>,
    boluses: List<WatchDataRepository.BolusHistoryRecord>,
    low: Int, high: Int,
    categoryLabels: Map<String, String>,
    dotPositions: MutableList<Triple<Float, Float, WatchDataRepository.CgmState>>,
) {
    val padding = 4.dp.toPx()
    val rangeLabelSize = 10.dp.toPx()
    val labelLeftPadding = rangeLabelSize * 3f
    val bolusTopSpace = if (boluses.isNotEmpty()) 20.dp.toPx() else 0f

    val graphLeft = padding + labelLeftPadding
    val graphRight = size.width - padding
    val graphTop = padding + bolusTopSpace
    val graphBottom = size.height - padding
    val graphWidth = graphRight - graphLeft
    val graphHeight = graphBottom - graphTop

    if (graphWidth <= 0f || graphHeight <= 0f) return

    val values = readings.map { it.mgDl }
    val minY = minOf(values.min(), low - 10).coerceAtLeast(20)
    val maxY = maxOf(values.max(), high + 10).coerceAtMost(500)
    val yRange = (maxY - minY).toFloat().coerceAtLeast(1f)
    val minX = readings.first().timestampMs
    val maxX = readings.last().timestampMs
    val xRange = (maxX - minX).toFloat().coerceAtLeast(1f)

    fun xPos(ts: Long): Float = graphLeft + ((ts - minX) / xRange) * graphWidth
    fun yPos(mgDl: Int): Float = graphBottom - ((mgDl - minY) / yRange) * graphHeight

    // Range band
    val lowY = yPos(low)
    val highY = yPos(high)
    drawRect(
        color = Color(0x3322C55E),
        topLeft = Offset(graphLeft, highY),
        size = androidx.compose.ui.geometry.Size(graphWidth, lowY - highY),
    )

    // Range lines
    drawLine(Color(0x66FFFFFF), Offset(graphLeft, lowY), Offset(graphRight, lowY), strokeWidth = 1.dp.toPx())
    drawLine(Color(0x66FFFFFF), Offset(graphLeft, highY), Offset(graphRight, highY), strokeWidth = 1.dp.toPx())

    // Range labels
    val labelPaint = android.graphics.Paint().apply {
        color = 0x99FFFFFF.toInt()
        textSize = rangeLabelSize
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    drawContext.canvas.nativeCanvas.drawText(low.toString(), graphLeft - 2.dp.toPx(), lowY + rangeLabelSize / 3f, labelPaint)
    drawContext.canvas.nativeCanvas.drawText(high.toString(), graphLeft - 2.dp.toPx(), highY + rangeLabelSize / 3f, labelPaint)

    // Glucose line segments
    val dotRadius = 4.dp.toPx()
    for (i in 0 until readings.size - 1) {
        val r1 = readings[i]
        val r2 = readings[i + 1]
        val lineColor = Color(GlucoseDisplayUtils.bgColor(r1.mgDl, r1.low, r1.high, r1.urgentLow, r1.urgentHigh))
        drawLine(lineColor, Offset(xPos(r1.timestampMs), yPos(r1.mgDl)), Offset(xPos(r2.timestampMs), yPos(r2.mgDl)), strokeWidth = 3.dp.toPx())
    }

    // Glucose dots (larger for tappability)
    for (r in readings) {
        val cx = xPos(r.timestampMs)
        val cy = yPos(r.mgDl)
        val dotColor = Color(GlucoseDisplayUtils.bgColor(r.mgDl, r.low, r.high, r.urgentLow, r.urgentHigh))
        drawCircle(dotColor, radius = dotRadius, center = Offset(cx, cy))
        dotPositions.add(Triple(cx, cy, r))
    }

    // Bolus markers with category tags
    if (boluses.isNotEmpty()) {
        val markerSize = 5.dp.toPx()
        val bolusLabelPaint = android.graphics.Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 10.dp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val tagPaint = android.graphics.Paint().apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 8.dp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (bolus in boluses) {
            val bx = xPos(bolus.timestampMs)
            val markerY = graphTop - markerSize - 1.dp.toPx()
            val bolusType = WatchGraphRenderer.resolveBolusType(bolus)
            val color = Color(WatchGraphRenderer.bolusColor(bolus))

            val diamond = Path().apply {
                moveTo(bx, markerY - markerSize)
                lineTo(bx + markerSize, markerY)
                lineTo(bx, markerY + markerSize)
                lineTo(bx - markerSize, markerY)
                close()
            }
            drawPath(diamond, color, style = Fill)

            // Units label
            val label = if (bolus.units == bolus.units.toLong().toFloat()) {
                "${bolus.units.toLong()}u"
            } else {
                "%.1fu".format(bolus.units)
            }
            drawContext.canvas.nativeCanvas.drawText(label, bx, markerY - markerSize - 2.dp.toPx(), bolusLabelPaint)

            // Category tag (skip plain MEAL, use custom labels when available)
            val tag = WatchGraphRenderer.bolusTypeTag(bolusType, categoryLabels)
            if (tag != null) {
                drawContext.canvas.nativeCanvas.drawText(tag, bx, markerY - markerSize - 2.dp.toPx() - 10.dp.toPx(), tagPaint)
            }
        }
    }
}

private fun DrawScope.drawTooltip(x: Float, y: Float, valueLine: String, timeLine: String) {
    val bgPaint = android.graphics.Paint().apply {
        color = 0xDD1E293B.toInt()
        isAntiAlias = true
    }
    val textPaint = android.graphics.Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12.dp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val timePaint = android.graphics.Paint().apply {
        color = 0xFF9CA3AF.toInt()
        textSize = 10.dp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val tooltipWidth = 100.dp.toPx()
    val tooltipHeight = 36.dp.toPx()
    val tooltipX = (x - tooltipWidth / 2f).coerceIn(0f, size.width - tooltipWidth)
    val tooltipY = (y - tooltipHeight - 12.dp.toPx()).coerceAtLeast(0f)

    val nativeCanvas = drawContext.canvas.nativeCanvas
    nativeCanvas.drawRoundRect(
        tooltipX, tooltipY,
        tooltipX + tooltipWidth, tooltipY + tooltipHeight,
        8.dp.toPx(), 8.dp.toPx(),
        bgPaint,
    )

    val centerX = tooltipX + tooltipWidth / 2f
    nativeCanvas.drawText(valueLine, centerX, tooltipY + 15.dp.toPx(), textPaint)
    nativeCanvas.drawText(timeLine, centerX, tooltipY + 29.dp.toPx(), timePaint)
}
