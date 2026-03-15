package com.glycemicgpt.weardevice.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

        // Append to history buffer, dedup by timestamp, keep most recent MAX_CGM_HISTORY
        val current = _cgmHistory.value.toMutableList()
        if (current.none { it.timestampMs == timestampMs }) {
            current.add(state)
            if (current.size > MAX_CGM_HISTORY) {
                _cgmHistory.value = current.drop(current.size - MAX_CGM_HISTORY)
            } else {
                _cgmHistory.value = current
            }
        }
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
