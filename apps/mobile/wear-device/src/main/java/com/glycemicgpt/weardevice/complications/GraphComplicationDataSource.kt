package com.glycemicgpt.weardevice.complications

import android.app.PendingIntent
import android.content.Intent
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
import com.glycemicgpt.weardevice.presentation.GraphDetailActivity

class GraphComplicationDataSource : SuspendingComplicationDataSourceService() {

    private val tapPendingIntent by lazy {
        val intent = Intent(this, GraphDetailActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        PendingIntent.getActivity(
            this, REQUEST_CODE_GRAPH, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val IMG_WIDTH = 400
        private const val IMG_HEIGHT = 100
        private const val MIN_READINGS = 3
        private const val REQUEST_CODE_GRAPH = 2
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        if (type != ComplicationType.SMALL_IMAGE) return NoDataComplicationData()
        val bitmap = renderPreviewGraph()
        val icon = Icon.createWithBitmap(bitmap)
        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder("Glucose trend graph preview").build(),
        ).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (request.complicationType != ComplicationType.SMALL_IMAGE) {
            return NoDataComplicationData()
        }

        val config = WatchDataRepository.watchFaceConfig.value
        if (!config.showGraph) return NoDataComplicationData()

        val history = WatchDataRepository.cgmHistory.value
        if (history.size < MIN_READINGS) return NoDataComplicationData()

        val rangeMs = config.graphRangeHours * 3_600_000L
        val cutoff = System.currentTimeMillis() - rangeMs
        val readings = history
            .filter { it.timestampMs >= cutoff && it.mgDl in 20..500 }
            .sortedBy { it.timestampMs }
        if (readings.size < MIN_READINGS) return NoDataComplicationData()

        val boluses = WatchDataRepository.bolusHistory.value
            .filter { it.timestampMs >= cutoff }
            .sortedBy { it.timestampMs }

        val low = readings.last().low
        val high = readings.last().high

        // Use compact config for complication sparkline (no range labels to save space)
        val graphConfig = WatchGraphRenderer.GraphConfig(
            width = IMG_WIDTH,
            height = IMG_HEIGHT,
            showBolusMarkers = boluses.isNotEmpty(),
            showRangeLabels = false,
            bolusLabelSize = 7f,
        )
        val categoryLabels = WatchDataRepository.categoryLabels.value
        val graphData = WatchGraphRenderer.GraphData(readings, boluses, low, high, categoryLabels)
        val bitmap = WatchGraphRenderer.render(graphConfig, graphData)

        val icon = Icon.createWithBitmap(bitmap)
        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder(
                "Glucose trend: ${readings.size} readings over ${config.graphRangeHours}h"
            ).build(),
        ).setTapAction(tapPendingIntent).build()
    }

    private fun renderPreviewGraph(): android.graphics.Bitmap {
        val now = System.currentTimeMillis()
        val fakeReadings = listOf(120, 135, 150, 145, 160, 155, 140, 130, 125, 110, 105, 115)
            .mapIndexed { i, bg ->
                WatchDataRepository.CgmState(
                    mgDl = bg, trend = "FLAT",
                    timestampMs = now - (12 - i) * 300_000L,
                    low = 70, high = 180, urgentLow = 55, urgentHigh = 250,
                )
            }
        val config = WatchGraphRenderer.GraphConfig(
            width = IMG_WIDTH,
            height = IMG_HEIGHT,
            showBolusMarkers = false,
            showRangeLabels = false,
        )
        return WatchGraphRenderer.render(config, WatchGraphRenderer.GraphData(fakeReadings, emptyList(), 70, 180))
    }
}
