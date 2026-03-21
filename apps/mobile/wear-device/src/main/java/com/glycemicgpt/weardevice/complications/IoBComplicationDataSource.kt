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
import com.glycemicgpt.weardevice.presentation.ChatActivity

class IoBComplicationDataSource : SuspendingComplicationDataSourceService() {

    private val tapPendingIntent by lazy {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_SOURCE, SOURCE_IOB)
        }
        PendingIntent.getActivity(
            this, REQUEST_CODE_IOB, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_SOURCE = "complication_source"
        const val SOURCE_IOB = "iob"
        private const val REQUEST_CODE_IOB = 1
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val text = PlainComplicationText.Builder("2.45").build()
        val title = PlainComplicationText.Builder("IoB").build()
        val description = PlainComplicationText.Builder("Insulin on Board: 2.45 units").build()

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text = text, contentDescription = description)
                    .setTitle(title)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("IoB: 2.45 units").build(),
                    contentDescription = description,
                )
                    .setTitle(title)
                    .build()
            else -> NoDataComplicationData()
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (!WatchDataRepository.watchFaceConfig.value.showIoB) {
            return NoDataComplicationData()
        }
        val iobState = WatchDataRepository.iob.value
        val iobText = iobState?.let { "%.2f".format(it.iob) } ?: "--"
        val descriptionText = iobState?.let { "Insulin on Board: $iobText units" } ?: "No data"

        val text = PlainComplicationText.Builder(iobText).build()
        val title = PlainComplicationText.Builder("IoB").build()
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
                    text = PlainComplicationText.Builder("IoB: $iobText units").build(),
                    contentDescription = description,
                )
                    .setTitle(title)
                    .setTapAction(tap)
                    .build()
            else -> NoDataComplicationData()
        }
    }
}
