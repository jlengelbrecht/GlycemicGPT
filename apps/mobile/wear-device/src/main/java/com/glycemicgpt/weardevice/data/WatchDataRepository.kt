package com.glycemicgpt.weardevice.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WatchDataRepository {

    data class IoBState(
        val iob: Float,
        val timestampMs: Long,
    )

    data class CgmState(
        val mgDl: Int,
        val trend: String,
        val timestampMs: Long,
        val low: Int,
        val high: Int,
        val urgentLow: Int,
        val urgentHigh: Int,
    )

    data class AlertState(
        val type: String,
        val bgValue: Int,
        val timestampMs: Long,
        val message: String,
    )

    sealed class ChatState {
        data object Idle : ChatState()
        data object Loading : ChatState()
        data class Success(val response: String, val disclaimer: String) : ChatState()
        data class Error(val message: String) : ChatState()
    }

    data class BolusHistoryRecord(
        val units: Float,
        val correctionUnits: Float,
        val mealUnits: Float,
        val isAutomated: Boolean,
        val isCorrection: Boolean,
        val timestampMs: Long,
        /** Resolved platform category name (e.g. "AUTO_CORRECTION", "FOOD", "CORRECTION").
         *  When non-empty, takes priority over raw flag-based derivation. This category
         *  is resolved on the phone side via BolusCategoryMapper using the plugin's
         *  BolusCategoryProvider + backend custom labels. */
        val category: String = "",
    )

    data class WatchFaceConfigState(
        val showIoB: Boolean = true,
        val showGraph: Boolean = true,
        val showAlert: Boolean = true,
        val showSeconds: Boolean = false,
        val graphRangeHours: Int = 3,
        val theme: String = "dark",
    )

    private val _iob = MutableStateFlow<IoBState?>(null)
    val iob: StateFlow<IoBState?> = _iob.asStateFlow()

    private val _cgm = MutableStateFlow<CgmState?>(null)
    val cgm: StateFlow<CgmState?> = _cgm.asStateFlow()

    /** Circular buffer of recent CGM readings for sparkline graph (up to 6 hours at 5-min intervals). */
    private val _cgmHistory = MutableStateFlow<List<CgmState>>(emptyList())
    val cgmHistory: StateFlow<List<CgmState>> = _cgmHistory.asStateFlow()

    private const val MAX_CGM_HISTORY = 72 // 6 hours at 5-min intervals
    private const val DEDUP_WINDOW_MS = 30_000L // 30-second proximity window for timestamp dedup
    private const val MAX_BOLUS_HISTORY = 48 // 6 hours of bolus events (generous cap)

    /** Bolus history for graph overlay markers. */
    private val _bolusHistory = MutableStateFlow<List<BolusHistoryRecord>>(emptyList())
    val bolusHistory: StateFlow<List<BolusHistoryRecord>> = _bolusHistory.asStateFlow()

    /** User-customized bolus category display labels synced from phone/backend.
     *  Keys are BolusCategory names (e.g. "AUTO_CORRECTION"), values are display strings. */
    private val _categoryLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val categoryLabels: StateFlow<Map<String, String>> = _categoryLabels.asStateFlow()

    private val _alert = MutableStateFlow<AlertState?>(null)
    val alert: StateFlow<AlertState?> = _alert.asStateFlow()

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _watchFaceConfig = MutableStateFlow(WatchFaceConfigState())
    val watchFaceConfig: StateFlow<WatchFaceConfigState> = _watchFaceConfig.asStateFlow()

    fun updateIoB(iob: Float, timestampMs: Long) {
        _iob.value = IoBState(iob, timestampMs)
    }

    fun updateCgm(
        mgDl: Int,
        trend: String,
        timestampMs: Long,
        low: Int,
        high: Int,
        urgentLow: Int,
        urgentHigh: Int,
    ) {
        val state = CgmState(mgDl, trend, timestampMs, low, high, urgentLow, urgentHigh)
        _cgm.value = state

        // Atomically append to history buffer, dedup by timestamp proximity, keep most recent
        _cgmHistory.update { current ->
            val isDuplicate = current.any {
                kotlin.math.abs(it.timestampMs - timestampMs) < DEDUP_WINDOW_MS
            }
            if (isDuplicate) {
                current
            } else {
                val updated = current + state
                if (updated.size > MAX_CGM_HISTORY) {
                    updated.drop(updated.size - MAX_CGM_HISTORY)
                } else {
                    updated
                }
            }
        }
    }

    fun updateBolusHistory(records: List<BolusHistoryRecord>) {
        _bolusHistory.value = records
            .sortedByDescending { it.timestampMs }
            .take(MAX_BOLUS_HISTORY)
    }

    fun addBolusRecord(record: BolusHistoryRecord) {
        _bolusHistory.update { current ->
            val isDuplicate = current.any {
                kotlin.math.abs(it.timestampMs - record.timestampMs) < DEDUP_WINDOW_MS &&
                    kotlin.math.abs(it.units - record.units) < 0.001f
            }
            if (isDuplicate) {
                current
            } else {
                (current + record)
                    .sortedByDescending { it.timestampMs }
                    .take(MAX_BOLUS_HISTORY)
            }
        }
    }

    fun updateCategoryLabels(labels: Map<String, String>) {
        _categoryLabels.value = labels
    }

    fun updateAlert(type: String, bgValue: Int, timestampMs: Long, message: String) {
        _alert.value = if (type == "none") null else AlertState(type, bgValue, timestampMs, message)
    }

    fun clearAlert() {
        _alert.value = null
    }

    fun setChatResponse(response: String, disclaimer: String) {
        _chatState.value = ChatState.Success(response, disclaimer)
    }

    fun setChatLoading() {
        _chatState.value = ChatState.Loading
    }

    fun setChatError(error: String) {
        _chatState.value = ChatState.Error(error)
    }

    fun clearChat() {
        _chatState.value = ChatState.Idle
    }

    private val VALID_GRAPH_RANGES = setOf(1, 3, 6)
    private val VALID_THEMES = setOf("dark", "clinical_blue", "high_contrast")

    fun updateWatchFaceConfig(
        showIoB: Boolean,
        showGraph: Boolean,
        showAlert: Boolean,
        showSeconds: Boolean,
        graphRangeHours: Int,
        theme: String,
    ) {
        _watchFaceConfig.value = WatchFaceConfigState(
            showIoB = showIoB,
            showGraph = showGraph,
            showAlert = showAlert,
            showSeconds = showSeconds,
            graphRangeHours = if (graphRangeHours in VALID_GRAPH_RANGES) graphRangeHours else 3,
            theme = if (theme in VALID_THEMES) theme else "dark",
        )
    }
}
