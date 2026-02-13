package com.glycemicgpt.wear.data

import android.content.ComponentName
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.glycemicgpt.wear.complications.BgComplicationDataSource
import com.glycemicgpt.wear.complications.IoBComplicationDataSource
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import timber.log.Timber

class GlycemicDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var iobUpdated = false
        var cgmUpdated = false

        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            val path = event.dataItem.uri.path ?: return@forEach
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                WearDataContract.IOB_PATH -> {
                    WatchDataRepository.updateIoB(
                        iob = dataMap.getFloat(WearDataContract.KEY_IOB_VALUE),
                        timestampMs = dataMap.getLong(WearDataContract.KEY_IOB_TIMESTAMP),
                    )
                    iobUpdated = true
                    Timber.d("Received IoB from phone: %.2f", dataMap.getFloat(WearDataContract.KEY_IOB_VALUE))
                }

                WearDataContract.CGM_PATH -> {
                    WatchDataRepository.updateCgm(
                        mgDl = dataMap.getInt(WearDataContract.KEY_CGM_MG_DL),
                        trend = dataMap.getString(WearDataContract.KEY_CGM_TREND, "UNKNOWN"),
                        timestampMs = dataMap.getLong(WearDataContract.KEY_CGM_TIMESTAMP),
                        low = dataMap.getInt(WearDataContract.KEY_GLUCOSE_LOW, 70),
                        high = dataMap.getInt(WearDataContract.KEY_GLUCOSE_HIGH, 180),
                        urgentLow = dataMap.getInt(WearDataContract.KEY_GLUCOSE_URGENT_LOW, 55),
                        urgentHigh = dataMap.getInt(WearDataContract.KEY_GLUCOSE_URGENT_HIGH, 250),
                    )
                    cgmUpdated = true
                    Timber.d("Received CGM from phone: %d mg/dL", dataMap.getInt(WearDataContract.KEY_CGM_MG_DL))
                }

                WearDataContract.ALERT_PATH -> {
                    val alertType = dataMap.getString(WearDataContract.KEY_ALERT_TYPE, "none")
                    WatchDataRepository.updateAlert(
                        type = alertType,
                        bgValue = dataMap.getInt(WearDataContract.KEY_ALERT_BG_VALUE, 0),
                        timestampMs = dataMap.getLong(WearDataContract.KEY_ALERT_TIMESTAMP),
                        message = dataMap.getString(WearDataContract.KEY_ALERT_MESSAGE, ""),
                    )
                    if (alertType != "none") {
                        vibrateForAlert(alertType)
                    }
                    Timber.d("Received alert from phone: %s", alertType)
                }
            }
        }

        if (iobUpdated) {
            requestComplicationUpdate(IoBComplicationDataSource::class.java)
        }
        if (cgmUpdated) {
            requestComplicationUpdate(BgComplicationDataSource::class.java)
        }
    }

    private fun vibrateForAlert(alertType: String) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val effect = if (alertType.startsWith("urgent")) {
                // Urgent: strong pulsing pattern
                VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
            } else {
                // Warning: single short pulse
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Timber.w(e, "Failed to vibrate for alert")
        }
    }

    private fun requestComplicationUpdate(dataSourceClass: Class<*>) {
        try {
            val requester = ComplicationDataSourceUpdateRequester.create(
                context = applicationContext,
                complicationDataSourceComponent = ComponentName(applicationContext, dataSourceClass),
            )
            requester.requestUpdateAll()
        } catch (e: Exception) {
            Timber.w(e, "Failed to request complication update for %s", dataSourceClass.simpleName)
        }
    }
}
