package com.glycemicgpt.mobile.service

import com.glycemicgpt.mobile.data.local.dao.RawHistoryLogDao
import com.glycemicgpt.mobile.data.local.entity.RawHistoryLogEntity
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import com.glycemicgpt.mobile.data.repository.SyncQueueEnqueuer
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.pump.PumpDriver
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
 * - IoB + basal rate: every 30 seconds (AC1)
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

    companion object {
        const val INTERVAL_FAST_MS = 30_000L       // IoB + basal
        const val INTERVAL_MEDIUM_MS = 300_000L     // bolus history (5 min)
        const val INTERVAL_SLOW_MS = 900_000L       // battery + reservoir (15 min)

        // When phone battery is low, slow everything down by this factor
        const val LOW_BATTERY_MULTIPLIER = 3
    }

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
     * Fast loop: IoB + basal rate every 30s.
     * Runs an initial poll immediately, then repeats at interval.
     */
    private suspend fun pollFastLoop() {
        while (true) {
            pollIoB()
            pollBasal()
            delay(effectiveInterval(INTERVAL_FAST_MS))
        }
    }

    /** Medium loop: bolus history every 5 min. */
    private suspend fun pollMediumLoop() {
        while (true) {
            pollBolusHistory()
            delay(effectiveInterval(INTERVAL_MEDIUM_MS))
        }
    }

    /** Slow loop: battery + reservoir + raw history logs every 15 min. */
    private suspend fun pollSlowLoop() {
        while (true) {
            pollBattery()
            pollReservoir()
            pollHistoryLogs()
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
