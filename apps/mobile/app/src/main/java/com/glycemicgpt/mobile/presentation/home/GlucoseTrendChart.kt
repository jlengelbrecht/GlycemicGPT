package com.glycemicgpt.mobile.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.presentation.theme.GlucoseColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ChartPeriod(val label: String, val hours: Long) {
    THREE_HOURS("3H", 3),
    SIX_HOURS("6H", 6),
    TWELVE_HOURS("12H", 12),
    TWENTY_FOUR_HOURS("24H", 24),
}

@Composable
fun GlucoseTrendChart(
    readings: List<CgmReading>,
    iobReadings: List<IoBReading>,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Period selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ChartPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = period == selectedPeriod,
                        onClick = { onPeriodSelected(period) },
                        label = {
                            Text(
                                text = period.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("period_chip_${period.label}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (readings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("chart_empty_state"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No glucose readings yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val textMeasurer = rememberTextMeasurer()
                val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                val iobColor = MaterialTheme.colorScheme.primary
                val nowMs = remember(readings, selectedPeriod) {
                    System.currentTimeMillis()
                }
                val iobSuffix = if (iobReadings.isNotEmpty()) ", with insulin on board overlay" else ""
                val a11yLabel = "Glucose trend chart showing ${readings.size} readings " +
                    "over the last ${selectedPeriod.label}" + iobSuffix

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("glucose_chart")
                        .semantics { contentDescription = a11yLabel },
                ) {
                    drawGlucoseChart(
                        readings = readings,
                        iobReadings = iobReadings,
                        period = selectedPeriod,
                        nowMs = nowMs,
                        textMeasurer = textMeasurer,
                        axisLabelColor = axisLabelColor,
                        gridColor = gridColor,
                        iobColor = iobColor,
                    )
                }

                // IoB legend
                if (iobReadings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "IoB",
                            style = MaterialTheme.typography.labelSmall,
                            color = iobColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGlucoseChart(
    readings: List<CgmReading>,
    iobReadings: List<IoBReading>,
    period: ChartPeriod,
    nowMs: Long,
    textMeasurer: TextMeasurer,
    axisLabelColor: Color,
    gridColor: Color,
    iobColor: Color,
) {
    val leftPadding = 36.dp.toPx()
    val bottomPadding = 24.dp.toPx()
    val topPadding = 8.dp.toPx()
    val rightPadding = if (iobReadings.isNotEmpty()) 32.dp.toPx() else 8.dp.toPx()

    val chartWidth = size.width - leftPadding - rightPadding
    val chartHeight = size.height - topPadding - bottomPadding

    // Y-axis range: 40 to 300 mg/dL
    val yMin = 40f
    val yMax = 300f
    val yRange = yMax - yMin

    // X-axis range
    val periodMs = period.hours * 3600_000L
    val xMin = nowMs - periodMs
    val xMax = nowMs

    // Target range band (70-180 mg/dL)
    val bandTopY = topPadding + chartHeight * (1f - (GlucoseThresholds.HIGH - yMin) / yRange)
    val bandBottomY = topPadding + chartHeight * (1f - (GlucoseThresholds.LOW - yMin) / yRange)
    drawRect(
        color = GlucoseColors.InRange.copy(alpha = 0.08f),
        topLeft = Offset(leftPadding, bandTopY),
        size = Size(chartWidth, bandBottomY - bandTopY),
    )

    // Grid lines
    val gridValues = listOf(70f, 180f, 250f)
    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
    for (value in gridValues) {
        val y = topPadding + chartHeight * (1f - (value - yMin) / yRange)
        drawLine(
            color = gridColor,
            start = Offset(leftPadding, y),
            end = Offset(leftPadding + chartWidth, y),
            pathEffect = dashedEffect,
            strokeWidth = 1f,
        )
    }

    // Y-axis labels (left: glucose)
    val yLabelValues = listOf(70, 180, 250)
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisLabelColor)
    for (value in yLabelValues) {
        val y = topPadding + chartHeight * (1f - (value - yMin) / yRange)
        val textResult = textMeasurer.measure("$value", labelStyle)
        drawText(
            textResult,
            topLeft = Offset(
                leftPadding - textResult.size.width - 3.dp.toPx(),
                y - textResult.size.height / 2f,
            ),
        )
    }

    // X-axis time labels (shorter format to avoid overlap)
    val timeFormatter = DateTimeFormatter.ofPattern(
        if (period.hours <= 6) "h:mm" else "ha",
    ).withZone(ZoneId.systemDefault())

    val tickCount = when (period) {
        ChartPeriod.THREE_HOURS -> 3
        ChartPeriod.SIX_HOURS -> 3
        ChartPeriod.TWELVE_HOURS -> 4
        ChartPeriod.TWENTY_FOUR_HOURS -> 4
    }

    for (i in 0..tickCount) {
        val tickMs = xMin + (xMax - xMin) * i.toLong() / tickCount.toLong()
        val x = leftPadding + chartWidth * i.toFloat() / tickCount.toFloat()
        val label = timeFormatter.format(Instant.ofEpochMilli(tickMs))
        val textResult = textMeasurer.measure(label, labelStyle)
        drawText(
            textResult,
            topLeft = Offset(
                x - textResult.size.width / 2f,
                size.height - bottomPadding + 4.dp.toPx(),
            ),
        )
    }

    // IoB overlay (filled area + line on secondary axis)
    if (iobReadings.size >= 2) {
        val maxIob = iobReadings.maxOf { it.iob }.coerceAtLeast(1f)
        val iobAreaPath = Path()
        val iobLinePath = Path()
        var started = false
        var lastInRangeX = 0f

        for (reading in iobReadings) {
            val ts = reading.timestamp.toEpochMilli()
            if (ts < xMin || ts > xMax) continue

            val x = leftPadding + chartWidth * (ts - xMin).toFloat() / (xMax - xMin).toFloat()
            // IoB uses full chart height, scaled to maxIob
            val y = topPadding + chartHeight * (1f - reading.iob / maxIob)
            lastInRangeX = x

            if (!started) {
                iobAreaPath.moveTo(x, topPadding + chartHeight) // bottom
                iobAreaPath.lineTo(x, y)
                iobLinePath.moveTo(x, y)
                started = true
            } else {
                iobAreaPath.lineTo(x, y)
                iobLinePath.lineTo(x, y)
            }
        }

        if (started) {
            // Close the area path back to bottom
            iobAreaPath.lineTo(lastInRangeX, topPadding + chartHeight)
            iobAreaPath.close()

            drawPath(iobAreaPath, iobColor.copy(alpha = 0.06f), style = Fill)
            drawPath(iobLinePath, iobColor.copy(alpha = 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))

            // Right Y-axis labels for IoB
            val iobLabelStyle = TextStyle(fontSize = 9.sp, color = iobColor.copy(alpha = 0.6f))
            val topLabel = textMeasurer.measure("%.1fu".format(maxIob), iobLabelStyle)
            drawText(topLabel, topLeft = Offset(leftPadding + chartWidth + 3.dp.toPx(), topPadding))
            val bottomLabel = textMeasurer.measure("0u", iobLabelStyle)
            drawText(
                bottomLabel,
                topLeft = Offset(
                    leftPadding + chartWidth + 3.dp.toPx(),
                    topPadding + chartHeight - bottomLabel.size.height,
                ),
            )
        }
    }

    // Glucose data points (drawn last so they're on top)
    val dotRadius = 3.dp.toPx()
    for (reading in readings) {
        val ts = reading.timestamp.toEpochMilli()
        if (ts < xMin || ts > xMax) continue

        val x = leftPadding + chartWidth * (ts - xMin).toFloat() / (xMax - xMin).toFloat()
        val clampedValue = reading.glucoseMgDl.coerceIn(yMin.toInt(), yMax.toInt())
        val y = topPadding + chartHeight * (1f - (clampedValue - yMin) / yRange)
        val color = glucoseColor(reading.glucoseMgDl)

        drawCircle(
            color = color,
            radius = dotRadius,
            center = Offset(x, y),
        )
    }
}
