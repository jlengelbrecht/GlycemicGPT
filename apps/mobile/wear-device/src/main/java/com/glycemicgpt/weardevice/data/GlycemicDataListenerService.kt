package com.glycemicgpt.weardevice.data

import android.content.ComponentName
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.glycemicgpt.weardevice.complications.AlertsComplicationDataSource
import com.glycemicgpt.weardevice.complications.BgComplicationDataSource
import com.glycemicgpt.weardevice.complications.GraphComplicationDataSource
import com.glycemicgpt.weardevice.complications.IoBComplicationDataSource
import com.glycemicgpt.weardevice.util.GlucoseDisplayUtils
import com.glycemicgpt.weardevice.util.GlucoseDisplayUtils.sanitizeThresholds
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import timber.log.Timber

class GlycemicDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var iobUpdated = false
        var cgmUpdated = false
        var configUpdated = false
        var alertUpdated = false
        var bolusUpdated = false

        // Two-pass processing: config events first so that data/alert events
        // see the latest showAlert / showIoB state within the same batch.
        val changedEvents = dataEvents.filter { it.type == DataEvent.TYPE_CHANGED }

        // Pass 1 -- config + category labels (processed first so data events see latest state)
        changedEvents.forEach { event ->
            val path = event.dataItem.uri.path ?: return@forEach
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                WearDataContract.CONFIG_PATH -> {
                    WatchDataRepository.updateWatchFaceConfig(
                        showIoB = dataMap.getBoolean(WearDataContract.KEY_CONFIG_SHOW_IOB, true),
                        showGraph = dataMap.getBoolean(WearDataContract.KEY_CONFIG_SHOW_GRAPH, true),
                        showAlert = dataMap.getBoolean(WearDataContract.KEY_CONFIG_SHOW_ALERT, true),
                        showSeconds = dataMap.getBoolean(WearDataContract.KEY_CONFIG_SHOW_SECONDS, false),
                        graphRangeHours = dataMap.getInt(WearDataContract.KEY_CONFIG_GRAPH_RANGE_HOURS, 3),
                        theme = dataMap.getString(WearDataContract.KEY_CONFIG_THEME, "dark"),
                    )
                    configUpdated = true
                    Timber.d("Received watch face config from phone")
                }

                WearDataContract.CATEGORY_LABELS_PATH -> {
                    val json = dataMap.getString(WearDataContract.KEY_CATEGORY_LABELS_JSON, "")
                    if (json.isNotEmpty()) {
                        try {
                            val obj = org.json.JSONObject(json)
                            val labels = buildMap {
                                for (key in obj.keys()) {
                                    put(key, obj.getString(key))
                                }
                            }
                            WatchDataRepository.updateCategoryLabels(labels)
                            Timber.d("Received category labels from phone: %d labels", labels.size)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse category labels JSON")
                        }
                    }
                }
            }
        }

        // Pass 2 -- data and alert events
        changedEvents.forEach { event ->
            val path = event.dataItem.uri.path ?: return@forEach
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                WearDataContract.IOB_PATH -> {
                    WatchDataRepository.updateIoB(
                        iob = dataMap.getFloat(WearDataContract.KEY_IOB_VALUE),
                        timestampMs = dataMap.getLong(WearDataContract.KEY_IOB_TIMESTAMP),
                    )
                    iobUpdated = true
                    Timber.d("Received IoB update from phone")
                }

                WearDataContract.CGM_PATH -> {
                    val mgDl = dataMap.getInt(WearDataContract.KEY_CGM_MG_DL)
                    if (!GlucoseDisplayUtils.isValidGlucose(mgDl)) {
                        Timber.w("Rejected invalid CGM value from phone")
                        return@forEach
                    }
                    val thresholds = sanitizeThresholds(
                        rawLow = dataMap.getInt(WearDataContract.KEY_GLUCOSE_LOW, 70),
                        rawHigh = dataMap.getInt(WearDataContract.KEY_GLUCOSE_HIGH, 180),
                        rawUrgentLow = dataMap.getInt(WearDataContract.KEY_GLUCOSE_URGENT_LOW, 55),
                        rawUrgentHigh = dataMap.getInt(WearDataContract.KEY_GLUCOSE_URGENT_HIGH, 250),
                    )
                    WatchDataRepository.updateCgm(
                        mgDl = mgDl,
                        trend = dataMap.getString(WearDataContract.KEY_CGM_TREND, "UNKNOWN"),
                        timestampMs = dataMap.getLong(WearDataContract.KEY_CGM_TIMESTAMP),
                        low = thresholds.low,
                        high = thresholds.high,
                        urgentLow = thresholds.urgentLow,
                        urgentHigh = thresholds.urgentHigh,
                    )
                    cgmUpdated = true
                    Timber.d("Received CGM update from phone")
                }

                WearDataContract.ALERT_PATH -> {
                    val alertType = dataMap.getString(WearDataContract.KEY_ALERT_TYPE, "none")
                    val alertsEnabled = WatchDataRepository.watchFaceConfig.value.showAlert
                    val rawBg = dataMap.getInt(WearDataContract.KEY_ALERT_BG_VALUE, 0)
                    val bgValue = if (GlucoseDisplayUtils.isValidGlucose(rawBg)) rawBg else 0
                    WatchDataRepository.updateAlert(
                        type = alertType,
                        bgValue = bgValue,
                        timestampMs = dataMap.getLong(WearDataContract.KEY_ALERT_TIMESTAMP),
                        message = dataMap.getString(WearDataContract.KEY_ALERT_MESSAGE, ""),
                    )
                    alertUpdated = true
                    if (alertType != "none" && alertsEnabled) {
                        vibrateForAlert(alertType)
                    }
                    Timber.d("Received alert from phone: %s (vibrate=%b)", alertType, alertsEnabled)
                }

                WearDataContract.BOLUS_HISTORY_PATH -> {
                    val units = dataMap.getFloatArray(WearDataContract.KEY_BOLUS_UNITS)
                    val corrUnits = dataMap.getFloatArray(WearDataContract.KEY_BOLUS_CORRECTION_UNITS)
                    val mealUnits = dataMap.getFloatArray(WearDataContract.KEY_BOLUS_MEAL_UNITS)
                    val automated = dataMap.getLongArray(WearDataContract.KEY_BOLUS_IS_AUTOMATED)
                    val correction = dataMap.getLongArray(WearDataContract.KEY_BOLUS_IS_CORRECTION)
                    val timestamps = dataMap.getLongArray(WearDataContract.KEY_BOLUS_TIMESTAMPS)
                    val categories = dataMap.getStringArray(WearDataContract.KEY_BOLUS_CATEGORIES)

                    if (units != null && timestamps != null && units.size == timestamps.size) {
                        val expectedSize = units.size
                        if (corrUnits != null && corrUnits.size != expectedSize ||
                            mealUnits != null && mealUnits.size != expectedSize
                        ) {
                            Timber.w("Bolus sub-arrays have mismatched lengths: units=%d corr=%d meal=%d",
                                expectedSize, corrUnits?.size ?: 0, mealUnits?.size ?: 0)
                        }
                        val records = units.indices.mapNotNull { i ->
                            val u = units[i]
                            // Validate: finite, non-negative, within safety max (25U max bolus)
                            if (!u.isFinite() || u < 0f || u > 25f) {
                                Timber.w("Rejected invalid bolus units: %f", u)
                                return@mapNotNull null
                            }
                            val rawCorr = corrUnits?.getOrNull(i) ?: 0f
                            val rawMeal = mealUnits?.getOrNull(i) ?: 0f
                            if (rawCorr !in 0f..25f || rawMeal !in 0f..25f) {
                                Timber.w("Clamping out-of-range bolus sub-units: corr=%f meal=%f", rawCorr, rawMeal)
                            }
                            WatchDataRepository.BolusHistoryRecord(
                                units = u,
                                correctionUnits = rawCorr.coerceIn(0f, 25f),
                                mealUnits = rawMeal.coerceIn(0f, 25f),
                                isAutomated = (automated?.getOrNull(i) ?: 0L) != 0L,
                                isCorrection = (correction?.getOrNull(i) ?: 0L) != 0L,
                                timestampMs = timestamps[i],
                                category = categories?.getOrNull(i) ?: "",
                            )
                        }
                        WatchDataRepository.updateBolusHistory(records)
                        bolusUpdated = true
                        Timber.d("Received bolus history from phone: %d records", records.size)
                    } else {
                        Timber.w("Received malformed bolus history from phone")
                    }
                }
            }
        }

        if (configUpdated) {
            // Config change may affect which complications are visible
            requestComplicationUpdate(IoBComplicationDataSource::class.java)
        }
        if (iobUpdated) {
            requestComplicationUpdate(IoBComplicationDataSource::class.java)
        }
        if (cgmUpdated) {
            requestComplicationUpdate(BgComplicationDataSource::class.java)
            requestComplicationUpdate(GraphComplicationDataSource::class.java)
        }
        if (alertUpdated) {
            requestComplicationUpdate(AlertsComplicationDataSource::class.java)
        }
        if (bolusUpdated) {
            requestComplicationUpdate(GraphComplicationDataSource::class.java)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearDataContract.CHAT_RESPONSE_PATH -> {
                val responseData = messageEvent.data
                if (responseData == null) {
                    WatchDataRepository.setChatError("Empty response")
                    Timber.w("Received chat response with null data")
                    return
                }
                try {
                    val json = JSONObject(String(responseData, Charsets.UTF_8))
                    val response = json.optString("response", "")
                    val disclaimer = json.optString("disclaimer", "")
                    if (response.isBlank()) {
                        WatchDataRepository.setChatError("Empty response from AI")
                        return
                    }
                    WatchDataRepository.setChatResponse(response, disclaimer)
                    Timber.d("Received chat response from phone (%d chars)", response.length)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse chat response")
                    WatchDataRepository.setChatError("Failed to parse response")
                }
            }

            WearDataContract.CHAT_ERROR_PATH -> {
                val data = messageEvent.data
                if (data == null) {
                    WatchDataRepository.setChatError("Unknown error")
                    Timber.w("Received chat error with null data")
                    return
                }
                val rawError = String(data, Charsets.UTF_8)
                // Cap error message length to avoid displaying stack traces or internal details
                val errorMsg = if (rawError.length > MAX_ERROR_LENGTH) {
                    rawError.take(MAX_ERROR_LENGTH) + "..."
                } else {
                    rawError
                }
                WatchDataRepository.setChatError(errorMsg)
                Timber.d("Received chat error from phone (%d chars)", rawError.length)
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun vibrateForAlert(alertType: String) {
        try {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = manager.defaultVibrator

            val effect = if (alertType.startsWith("urgent")) {
                VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
            } else {
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Timber.w(e, "Failed to vibrate for alert")
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 200
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
