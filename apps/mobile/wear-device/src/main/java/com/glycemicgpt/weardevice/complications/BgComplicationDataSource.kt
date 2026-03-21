package com.glycemicgpt.weardevice.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.glycemicgpt.weardevice.data.WatchDataRepository
import com.glycemicgpt.weardevice.presentation.AlertsActivity
import com.glycemicgpt.weardevice.util.GlucoseDisplayUtils

class BgComplicationDataSource : SuspendingComplicationDataSourceService() {

    private val tapPendingIntent by lazy {
        val intent = Intent(this, AlertsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_SOURCE, SOURCE_BG)
        }
        PendingIntent.getActivity(
            this, REQUEST_CODE_BG, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_SOURCE = "complication_source"
        const val SOURCE_BG = "bg"
        private const val REQUEST_CODE_BG = 0
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val text = PlainComplicationText.Builder("120 \u2192").build()
        val title = PlainComplicationText.Builder("BG").build()
        val description = PlainComplicationText.Builder("Blood Glucose: 120 mg/dL").build()

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text = text, contentDescription = description)
                    .setTitle(title)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("BG: 120 mg/dL \u2192").build(),
                    contentDescription = description,
                )
                    .setTitle(title)
                    .build()
            else -> NoDataComplicationData()
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val now = System.currentTimeMillis()
        val staleThresholdMs = 15 * 60_000L // 15 minutes
        val cgmState = WatchDataRepository.cgm.value?.takeIf {
            val ageMs = now - it.timestampMs
            GlucoseDisplayUtils.isValidGlucose(it.mgDl) &&
                ageMs in 0 until staleThresholdMs
        }
        val bgText = cgmState?.let {
            "${it.mgDl} ${GlucoseDisplayUtils.trendSymbol(it.trend)}"
        } ?: "--"
        val descriptionText = cgmState?.let { "Blood Glucose: ${it.mgDl} mg/dL" } ?: "No data"

        val text = PlainComplicationText.Builder(bgText).build()
        val title = PlainComplicationText.Builder("BG").build()
        val description = PlainComplicationText.Builder(descriptionText).build()

        val tap = tapPendingIntent
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text = text, contentDescription = description)
                    .setTitle(title)
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(
                        "BG: ${cgmState?.mgDl ?: "--"} mg/dL ${cgmState?.let { GlucoseDisplayUtils.trendSymbol(it.trend) } ?: ""}",
                    ).build(),
                    contentDescription = description,
                )
                    .setTitle(title)
                    .setTapAction(tap)
                    .build()
            else -> NoDataComplicationData()
        }
    }
}
