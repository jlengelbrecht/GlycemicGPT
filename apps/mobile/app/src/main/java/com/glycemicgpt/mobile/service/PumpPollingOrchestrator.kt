package com.glycemicgpt.mobile.service

import com.glycemicgpt.mobile.data.local.dao.RawHistoryLogDao
import com.glycemicgpt.mobile.data.local.entity.RawHistoryLogEntity
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import com.glycemicgpt.mobile.data.repository.SyncQueueEnqueuer
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import com.glycemicgpt.mobile.wear.WearDataSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates periodic polling of pump data and persists results to Room.
 *
 * Poll intervals:
 * - IoB + basal rate + CGM: every 15 seconds (keep-alive; pump drops idle connections at ~30s)
 * - Bolus history: every 5 minutes (AC2)
 * - Battery + reservoir: every 15 minutes (AC3)
 *
 * Polling pauses when BLE connection is lost and resumes on reconnect (AC7).
 * Reduces frequency when phone battery is low (AC8).
 */
@Singleton
class PumpPollingOrchestrator @Inject constructor(
    private val pumpDriver: PumpDriver,
    private val repository: PumpDataRepository,
    private val syncEnqueuer: SyncQueueEnqueuer,
    private val rawHistoryLogDao: RawHistoryLogDao,
    private val wearDataSender: WearDataSender,
) {

    /** Set by PumpConnectionService to trigger immediate sync after enqueue. */
    @Volatile
    var backendSyncManager: BackendSyncManager? = null

    /** Track the last known raw event sequence number to fetch incrementally. */
    @Volatile
    private var lastSequenceNumber: Int = 0

    /** Whether hardware info has been cached this session. */
    @Volatile
    private var hardwareInfoCached: Boolean = false

    /** Track the last alert type sent to watch to avoid re-sending the same alert. */
    @Volatile
    private var previousAlertType: String? = null

    private val lock = Any()
    private var fastJob: Job? = null
    private var mediumJob: Job? = null
    private var slowJob: Job? = null
    private var connectionWatcherJob: Job? = null

    @Volatile
    var phoneBatteryLow: Boolean = false

    /**
     * Start watching connection state and polling when connected.
     * Call this from the foreground service's onCreate.
     */
    fun start(scope: CoroutineScope) {
        stop() // cancel any previous jobs
        synchronized(lock) {
            connectionWatcherJob = scope.launch {
                // Restore last known sequence number from Room to avoid re-downloading
                lastSequenceNumber = rawHistoryLogDao.getMaxSequenceNumber() ?: 0

                pumpDriver.observeConnectionState().collectLatest { state ->
                    if (state == ConnectionState.CONNECTED) {
                        Timber.d("Pump connected, starting polling")
                        startPollingLoops(scope)
                    } else {
                        Timber.d("Pump disconnected (state=%s), pausing polling", state)
                        previousAlertType = null
                        cancelPollingLoops()
                    }
                }
            }
        }
    }

    /** Stop all polling. Call from service onDestroy. */
    fun stop() {
        synchronized(lock) {
            cancelPollingLoops()
            connectionWatcherJob?.cancel()
            connectionWatcherJob = null
        }
    }

    private fun startPollingLoops(scope: CoroutineScope) {
        synchronized(lock) {
            cancelPollingLoops()

            fastJob = scope.launch { pollFastLoop() }
            mediumJob = scope.launch { pollMediumLoop() }
            slowJob = scope.launch { pollSlowLoop() }
        }
    }

    private fun cancelPollingLoops() {
        synchronized(lock) {
            fastJob?.cancel()
            mediumJob?.cancel()
            slowJob?.cancel()
            fastJob = null
            mediumJob = null
            slowJob = null
        }
    }

    private fun effectiveInterval(baseMs: Long): Long =
        if (phoneBatteryLow) baseMs * LOW_BATTERY_MULTIPLIER else baseMs

    /**
     * Fast loop: IoB + basal rate + CGM at least every ~15s.
     *
     * Waits [INITIAL_POLL_DELAY_MS] for the connection to stabilize, then
     * staggers requests by [REQUEST_STAGGER_MS] to avoid overwhelming the
     * pump with simultaneous BLE writes. The actual period is approximately
     * INTERVAL_FAST_MS + (FAST_REQUEST_COUNT - 1) * REQUEST_STAGGER_MS
     * plus any BLE response latency.
     */
    private suspend fun pollFastLoop() {
        delay(INITIAL_POLL_DELAY_MS)
        while (true) {
            pollIoB()
            delay(REQUEST_STAGGER_MS)
            pollBasal()
            delay(REQUEST_STAGGER_MS)
            pollCgm()
            delay(effectiveInterval(INTERVAL_FAST_MS))
        }
    }

    /** Medium loop: bolus history at least every ~5 min. */
    private suspend fun pollMediumLoop() {
        // Offset: initial delay + fast loop's 3 requests worth of stagger,
        // so this fires after the first fast loop iteration completes.
        delay(INITIAL_POLL_DELAY_MS + REQUEST_STAGGER_MS * FAST_REQUEST_COUNT)
        while (true) {
            pollBolusHistory()
            delay(effectiveInterval(INTERVAL_MEDIUM_MS))
        }
    }

    /** Slow loop: battery + reservoir + raw history logs at least every ~15 min. */
    private suspend fun pollSlowLoop() {
        // Offset: initial delay + fast loop requests + 1 medium request,
        // so this fires after both fast and medium first iterations.
        delay(INITIAL_POLL_DELAY_MS + REQUEST_STAGGER_MS * (FAST_REQUEST_COUNT + MEDIUM_REQUEST_COUNT))
        while (true) {
            pollBattery()
            delay(REQUEST_STAGGER_MS)
            pollReservoir()
            delay(REQUEST_STAGGER_MS)
            pollHistoryLogs()
            delay(REQUEST_STAGGER_MS)
            cacheHardwareInfoOnce()
            delay(effectiveInterval(INTERVAL_SLOW_MS))
        }
    }

    private suspend fun pollIoB() {
        pumpDriver.getIoB()
            .onSuccess {
                repository.saveIoB(it)
                syncEnqueuer.enqueueIoB(it)
                backendSyncManager?.triggerSync()
                try {
                    wearDataSender.sendIoB(it.iob, it.timestamp.toEpochMilli())
                } catch (e: Exception) {
                    Timber.w(e, "Failed to send IoB to watch")
                }
            }
            .onFailure { Timber.w(it, "Failed to poll IoB") }
    }

    private suspend fun pollBasal() {
        pumpDriver.getBasalRate()
            .onSuccess {
                repository.saveBasal(it)
                syncEnqueuer.enqueueBasal(it)
                backendSyncManager?.triggerSync()
            }
            .onFailure { Timber.w(it, "Failed to poll basal rate") }
    }

    private suspend fun pollBolusHistory() {
        val since = repository.getLatestBolusTimestamp()
            ?: Instant.now().minus(7, ChronoUnit.DAYS)
        pumpDriver.getBolusHistory(since)
            .onSuccess { events ->
                if (events.isNotEmpty()) {
                    repository.saveBoluses(events)
                    syncEnqueuer.enqueueBoluses(events)
                    backendSyncManager?.triggerSync()
                    Timber.d("Saved %d new bolus events", events.size)
                }
            }
            .onFailure { Timber.w(it, "Failed to poll bolus history") }
    }

    private suspend fun pollCgm() {
        try {
            pumpDriver.getCgmStatus()
                .onSuccess {
                    repository.saveCgm(it)
                    try {
                        wearDataSender.sendCgm(
                            mgDl = it.glucoseMgDl,
                            trend = it.trendArrow.name,
                            timestampMs = it.timestamp.toEpochMilli(),
                            low = 70,
                            high = 180,
                            urgentLow = 55,
                            urgentHigh = 250,
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to send CGM to watch")
                    }

                    // Alert threshold detection for watch
                    val alertType = detectAlertType(it.glucoseMgDl)
                    try {
                        if (alertType != null && alertType != previousAlertType) {
                            wearDataSender.sendAlert(
                                type = alertType,
                                bgValue = it.glucoseMgDl,
                                timestampMs = it.timestamp.toEpochMilli(),
                                message = "${alertLabel(alertType)} ${it.glucoseMgDl} mg/dL",
                            )
                            previousAlertType = alertType
                        } else if (alertType == null && previousAlertType != null) {
                            wearDataSender.clearAlert()
                            previousAlertType = null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to send alert to watch")
                    }
                }
                .onFailure { Timber.w(it, "Failed to poll CGM status") }
        } catch (e: Exception) {
            Timber.w(e, "CGM poll exception")
        }
    }

    companion object {
        const val INTERVAL_FAST_MS = 15_000L       // IoB + basal + CGM (keep-alive: pump drops idle connections at ~30s)
        const val INTERVAL_MEDIUM_MS = 300_000L     // bolus history (5 min)
        const val INTERVAL_SLOW_MS = 900_000L       // battery + reservoir (15 min)

        /** Delay before first poll after connection to let the pump settle. */
        const val INITIAL_POLL_DELAY_MS = 2_000L

        /** Stagger between consecutive BLE requests within a loop iteration. */
        const val REQUEST_STAGGER_MS = 200L

        // Request counts per loop -- used to compute stagger offsets.
        // Update these if you add/remove reads from a loop.
        private const val FAST_REQUEST_COUNT = 3   // IoB, basal, CGM
        private const val MEDIUM_REQUEST_COUNT = 1 // bolus history

        // When phone battery is low, slow everything down by this factor
        const val LOW_BATTERY_MULTIPLIER = 3

        // Alert thresholds (match CGM threshold defaults)
        private const val URGENT_LOW = 55
        private const val LOW = 70
        private const val HIGH = 180
        private const val URGENT_HIGH = 250

        fun detectAlertType(mgDl: Int): String? = when {
            mgDl <= URGENT_LOW -> "urgent_low"
            mgDl >= URGENT_HIGH -> "urgent_high"
            mgDl <= LOW -> "low"
            mgDl >= HIGH -> "high"
            else -> null
        }

        fun alertLabel(type: String): String = when (type) {
            "urgent_low" -> "URGENT LOW"
            "urgent_high" -> "URGENT HIGH"
            "low" -> "LOW"
            "high" -> "HIGH"
            else -> ""
        }
    }

    private suspend fun pollBattery() {
        pumpDriver.getBatteryStatus()
            .onSuccess { repository.saveBattery(it) }
            .onFailure { Timber.w(it, "Failed to poll battery") }
    }

    private suspend fun pollReservoir() {
        pumpDriver.getReservoirLevel()
            .onSuccess { repository.saveReservoir(it) }
            .onFailure { Timber.w(it, "Failed to poll reservoir") }
    }

    private suspend fun pollHistoryLogs() {
        pumpDriver.getHistoryLogs(sinceSequence = lastSequenceNumber)
            .onSuccess { records ->
                if (records.isNotEmpty()) {
                    val entities = records.map { record ->
                        RawHistoryLogEntity(
                            sequenceNumber = record.sequenceNumber,
                            rawBytesB64 = record.rawBytesB64,
                            eventTypeId = record.eventTypeId,
                            pumpTimeSeconds = record.pumpTimeSeconds,
                        )
                    }
                    rawHistoryLogDao.insertAll(entities)
                    lastSequenceNumber = records.maxOf { it.sequenceNumber }
                    backendSyncManager?.triggerSync()
                    Timber.d("Saved %d raw history log records", records.size)
                }
            }
            .onFailure { Timber.w(it, "Failed to poll history logs") }
    }

    private suspend fun cacheHardwareInfoOnce() {
        if (hardwareInfoCached) return
        pumpDriver.getPumpHardwareInfo()
            .onSuccess { info ->
                backendSyncManager?.cachedHardwareInfo = info
                hardwareInfoCached = true
                Timber.d("Cached pump hardware info: serial=%d", info.serialNumber)
            }
            .onFailure { Timber.w(it, "Failed to get pump hardware info") }
    }
}
