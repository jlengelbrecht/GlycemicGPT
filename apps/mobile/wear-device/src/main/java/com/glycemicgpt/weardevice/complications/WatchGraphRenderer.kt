package com.glycemicgpt.weardevice.complications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.glycemicgpt.weardevice.data.WatchDataRepository
import com.glycemicgpt.weardevice.util.GlucoseDisplayUtils

/**
 * Renders glucose sparkline graphs with optional bolus markers and range boundary labels.
 * Used by both the complication (small) and GraphDetailActivity (full-screen).
 */
object WatchGraphRenderer {

    data class GraphConfig(
        val width: Int,
        val height: Int,
        val padding: Float = 4f,
        val lineWidth: Float = 3f,
        val dotRadius: Float = 2.5f,
        val rangeLineWidth: Float = 1f,
        val showBolusMarkers: Boolean = true,
        val showRangeLabels: Boolean = true,
        val bolusLabelSize: Float = 8f,
        val rangeLabelSize: Float = 9f,
    )

    data class GraphData(
        val readings: List<WatchDataRepository.CgmState>,
        val boluses: List<WatchDataRepository.BolusHistoryRecord> = emptyList(),
        val low: Int,
        val high: Int,
        /** User-customized category labels from backend. Keys are BolusCategory names. */
        val categoryLabels: Map<String, String> = emptyMap(),
    )

    // Bolus type colors matching phone-side BolusTypeColors in Theme.kt
    object BolusColors {
        const val AUTO_CORRECTION = 0xFFE91E63.toInt()    // Pink
        const val MANUAL_CORRECTION = 0xFFFF5722.toInt()   // Deep orange
        const val MEAL = 0xFF7C4DFF.toInt()                // Deep purple
        const val MEAL_WITH_CORRECTION = 0xFFAB47BC.toInt() // Medium purple
        const val OTHER = 0xFF78909C.toInt()               // Blue-grey
    }

    fun render(config: GraphConfig, data: GraphData): Bitmap {
        if (config.width <= 0 || config.height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val readings = data.readings
        if (readings.size < 2) return bitmap

        // Graph area with left padding for range labels
        val labelLeftPadding = if (config.showRangeLabels) config.rangeLabelSize * 3.5f else 0f
        val graphLeft = config.padding + labelLeftPadding
        val graphRight = config.width - config.padding
        val graphTop = config.padding + (if (config.showBolusMarkers && data.boluses.isNotEmpty()) config.bolusLabelSize * 2.5f else 0f)
        val graphBottom = config.height - config.padding
        val graphWidth = graphRight - graphLeft
        val graphHeight = graphBottom - graphTop

        if (graphWidth <= 0f || graphHeight <= 0f) return bitmap

        // Y-axis range
        val values = readings.map { it.mgDl }
        val minY = minOf(values.min(), data.low - 10).coerceAtLeast(20)
        val maxY = maxOf(values.max(), data.high + 10).coerceAtMost(500)
        val yRange = (maxY - minY).toFloat().coerceAtLeast(1f)

        // X-axis range (use min/max for robustness against unsorted input)
        val minX = readings.minOf { it.timestampMs }
        val maxX = readings.maxOf { it.timestampMs }
        val xRange = (maxX - minX).toFloat().coerceAtLeast(1f)

        // Helper lambdas
        fun xPos(timestampMs: Long): Float = graphLeft + ((timestampMs - minX) / xRange) * graphWidth
        fun yPos(mgDl: Int): Float = graphBottom - ((mgDl - minY) / yRange) * graphHeight

        drawRangeBand(canvas, config, graphLeft, graphRight, graphHeight, graphBottom, data.low, data.high, minY, yRange)
        drawRangeLines(canvas, config, graphLeft, graphRight, graphHeight, graphBottom, data.low, data.high, minY, yRange)

        if (config.showRangeLabels) {
            drawRangeLabels(canvas, config, graphLeft, graphHeight, graphBottom, data.low, data.high, minY, yRange)
        }

        drawGlucoseLine(canvas, config, readings, ::xPos, ::yPos)
        drawGlucoseDots(canvas, config, readings, ::xPos, ::yPos)

        if (config.showBolusMarkers && data.boluses.isNotEmpty()) {
            drawBolusMarkers(canvas, config, data.boluses, data.categoryLabels, graphTop, ::xPos)
        }

        return bitmap
    }

    private fun drawRangeBand(
        canvas: Canvas, config: GraphConfig,
        graphLeft: Float, graphRight: Float, graphHeight: Float, graphBottom: Float,
        low: Int, high: Int, minY: Int, yRange: Float,
    ) {
        val paint = Paint().apply {
            color = 0x3322C55E // Semi-transparent green
            style = Paint.Style.FILL
        }
        val lowY = graphBottom - ((low - minY) / yRange) * graphHeight
        val highY = graphBottom - ((high - minY) / yRange) * graphHeight
        canvas.drawRect(graphLeft, highY, graphRight, lowY, paint)
    }

    private fun drawRangeLines(
        canvas: Canvas, config: GraphConfig,
        graphLeft: Float, graphRight: Float, graphHeight: Float, graphBottom: Float,
        low: Int, high: Int, minY: Int, yRange: Float,
    ) {
        val paint = Paint().apply {
            color = 0x66FFFFFF // Semi-transparent white
            strokeWidth = config.rangeLineWidth
            style = Paint.Style.STROKE
        }
        val lowY = graphBottom - ((low - minY) / yRange) * graphHeight
        val highY = graphBottom - ((high - minY) / yRange) * graphHeight
        canvas.drawLine(graphLeft, lowY, graphRight, lowY, paint)
        canvas.drawLine(graphLeft, highY, graphRight, highY, paint)
    }

    private fun drawRangeLabels(
        canvas: Canvas, config: GraphConfig,
        graphLeft: Float, graphHeight: Float, graphBottom: Float,
        low: Int, high: Int, minY: Int, yRange: Float,
    ) {
        val paint = Paint().apply {
            color = 0x99FFFFFF.toInt() // Semi-transparent white
            textSize = config.rangeLabelSize
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val lowY = graphBottom - ((low - minY) / yRange) * graphHeight
        val highY = graphBottom - ((high - minY) / yRange) * graphHeight

        // Draw labels at left edge, vertically centered on the range line
        val labelX = graphLeft - 2f
        canvas.drawText(low.toString(), labelX, lowY + config.rangeLabelSize / 3f, paint)
        canvas.drawText(high.toString(), labelX, highY + config.rangeLabelSize / 3f, paint)
    }

    private fun drawGlucoseLine(
        canvas: Canvas, config: GraphConfig,
        readings: List<WatchDataRepository.CgmState>,
        xPos: (Long) -> Float, yPos: (Int) -> Float,
    ) {
        val paint = Paint().apply {
            strokeWidth = config.lineWidth
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until readings.size - 1) {
            val r1 = readings[i]
            val r2 = readings[i + 1]
            paint.color = GlucoseDisplayUtils.bgColor(r1.mgDl, r1.low, r1.high, r1.urgentLow, r1.urgentHigh)
            canvas.drawLine(xPos(r1.timestampMs), yPos(r1.mgDl), xPos(r2.timestampMs), yPos(r2.mgDl), paint)
        }
    }

    private fun drawGlucoseDots(
        canvas: Canvas, config: GraphConfig,
        readings: List<WatchDataRepository.CgmState>,
        xPos: (Long) -> Float, yPos: (Int) -> Float,
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        for (r in readings) {
            paint.color = GlucoseDisplayUtils.bgColor(r.mgDl, r.low, r.high, r.urgentLow, r.urgentHigh)
            canvas.drawCircle(xPos(r.timestampMs), yPos(r.mgDl), config.dotRadius, paint)
        }
    }

    private fun drawBolusMarkers(
        canvas: Canvas, config: GraphConfig,
        boluses: List<WatchDataRepository.BolusHistoryRecord>,
        categoryLabels: Map<String, String>,
        graphTop: Float,
        xPos: (Long) -> Float,
    ) {
        val markerPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = config.bolusLabelSize
            textAlign = Paint.Align.CENTER
            color = 0xFFFFFFFF.toInt()
        }
        val tagPaint = Paint().apply {
            isAntiAlias = true
            textSize = config.bolusLabelSize * 0.85f
            textAlign = Paint.Align.CENTER
            color = 0xCCFFFFFF.toInt()
        }

        val diamondSize = 4f
        val markerY = graphTop - diamondSize - 1f

        for (bolus in boluses) {
            val bx = xPos(bolus.timestampMs)
            val bolusType = resolveBolusType(bolus)
            markerPaint.color = bolusColor(bolus)

            // Diamond shape
            val diamond = Path().apply {
                moveTo(bx, markerY - diamondSize)
                lineTo(bx + diamondSize, markerY)
                lineTo(bx, markerY + diamondSize)
                lineTo(bx - diamondSize, markerY)
                close()
            }
            canvas.drawPath(diamond, markerPaint)

            // Unit label above diamond
            val unitsLabel = formatBolusUnits(bolus.units)
            canvas.drawText(unitsLabel, bx, markerY - diamondSize - 2f, labelPaint)

            // Category tag above unit label (skip plain MEAL to reduce clutter, matching phone)
            val tag = bolusTypeTag(bolusType, categoryLabels)
            if (tag != null) {
                canvas.drawText(tag, bx, markerY - diamondSize - 2f - config.bolusLabelSize, tagPaint)
            }
        }
    }

    fun bolusColor(bolus: WatchDataRepository.BolusHistoryRecord): Int {
        return when (resolveBolusType(bolus)) {
            BolusType.AUTO_CORRECTION -> BolusColors.AUTO_CORRECTION
            BolusType.MANUAL_CORRECTION -> BolusColors.MANUAL_CORRECTION
            BolusType.MEAL -> BolusColors.MEAL
            BolusType.MEAL_WITH_CORRECTION -> BolusColors.MEAL_WITH_CORRECTION
            BolusType.OTHER -> BolusColors.OTHER
        }
    }

    /**
     * Resolves bolus type using the phone-resolved category when available.
     * Falls back to raw flag-based derivation for pre-migration data or missing category.
     * The category is resolved on the phone side via BolusCategoryMapper using
     * the plugin's BolusCategoryProvider, matching the phone chart's rendering.
     */
    fun resolveBolusType(bolus: WatchDataRepository.BolusHistoryRecord): BolusType {
        // Priority 1: Use phone-resolved category if present
        if (bolus.category.isNotEmpty()) {
            return categoryToBolusType(bolus.category)
        }
        // Fallback: derive from raw flags (matches BolusCategoryMapper.deriveFromFlags)
        return deriveFromFlags(bolus)
    }

    /** Map platform category name (e.g. "AUTO_CORRECTION") to watch BolusType. */
    private fun categoryToBolusType(category: String): BolusType = when (category) {
        "AUTO_CORRECTION" -> BolusType.AUTO_CORRECTION
        "CORRECTION" -> BolusType.MANUAL_CORRECTION
        "FOOD" -> BolusType.MEAL
        "FOOD_AND_CORRECTION" -> BolusType.MEAL_WITH_CORRECTION
        "OVERRIDE" -> BolusType.OTHER
        "AI_SUGGESTED" -> BolusType.OTHER
        "OTHER" -> BolusType.OTHER
        else -> BolusType.OTHER
    }

    /** Flag-based fallback matching BolusCategoryMapper.deriveFromFlags on phone. */
    private fun deriveFromFlags(bolus: WatchDataRepository.BolusHistoryRecord): BolusType = when {
        bolus.isAutomated -> BolusType.AUTO_CORRECTION
        bolus.mealUnits > 0f && bolus.correctionUnits > 0f -> BolusType.MEAL_WITH_CORRECTION
        bolus.isCorrection -> BolusType.MANUAL_CORRECTION
        bolus.mealUnits > 0f -> BolusType.MEAL
        else -> BolusType.OTHER
    }

    /**
     * Returns abbreviated tag text for a bolus type, using custom labels from backend when
     * available. Returns null for plain MEAL to reduce visual clutter (matching phone chart).
     */
    fun bolusTypeTag(type: BolusType, categoryLabels: Map<String, String>): String? {
        // Map BolusType -> platform category name -> custom label
        val categoryName = when (type) {
            BolusType.AUTO_CORRECTION -> "AUTO_CORRECTION"
            BolusType.MANUAL_CORRECTION -> "CORRECTION"
            BolusType.MEAL_WITH_CORRECTION -> "FOOD_AND_CORRECTION"
            BolusType.MEAL -> return null // Skip plain meal (matches phone chart)
            BolusType.OTHER -> "OTHER"
        }
        val label = categoryLabels[categoryName]
        return abbreviateTag(label ?: type.defaultTag)
    }

    /** Truncate long labels for watch display. */
    private fun abbreviateTag(label: String): String {
        return if (label.length > 8) label.take(7) + "." else label
    }

    private fun formatBolusUnits(units: Float): String {
        return if (units == units.toLong().toFloat()) {
            "${units.toLong()}u"
        } else {
            "%.1fu".format(units)
        }
    }

    enum class BolusType(val defaultTag: String) {
        AUTO_CORRECTION("Auto"),
        MANUAL_CORRECTION("Corr"),
        MEAL("Meal"),
        MEAL_WITH_CORRECTION("M+C"),
        OTHER("Other"),
    }
}
