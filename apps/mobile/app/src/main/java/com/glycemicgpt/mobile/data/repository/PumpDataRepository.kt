package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.local.dao.PumpDao
import com.glycemicgpt.mobile.data.local.entity.BasalReadingEntity
import com.glycemicgpt.mobile.data.local.entity.BatteryReadingEntity
import com.glycemicgpt.mobile.data.local.entity.BolusEventEntity
import com.glycemicgpt.mobile.data.local.entity.CgmReadingEntity
import com.glycemicgpt.mobile.data.local.entity.IoBReadingEntity
import com.glycemicgpt.mobile.data.local.entity.ReservoirReadingEntity
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.ControlIqMode
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that bridges domain models and Room persistence.
 *
 * All write methods accept domain models and convert to entities internally.
 * All read methods return domain models or Flows of domain models.
 */
@Singleton
class PumpDataRepository @Inject constructor(
    private val pumpDao: PumpDao,
) {

    companion object {
        /** Default data retention: 7 days. */
        val DEFAULT_RETENTION_MS: Long = TimeUnit.DAYS.toMillis(7)
    }

    // -- IoB ------------------------------------------------------------------

    suspend fun saveIoB(reading: IoBReading) {
        pumpDao.insertIoB(
            IoBReadingEntity(
                iob = reading.iob,
                timestampMs = reading.timestamp.toEpochMilli(),
            ),
        )
    }

    fun observeLatestIoB(): Flow<IoBReading?> =
        pumpDao.observeLatestIoB().map { it?.toDomain() }

    fun observeIoBHistory(since: Instant): Flow<List<IoBReading>> =
        pumpDao.observeIoBHistory(since.toEpochMilli()).map { entities ->
            entities.map { it.toDomain() }
        }

    // -- Basal ----------------------------------------------------------------

    suspend fun saveBasal(reading: BasalReading) {
        pumpDao.insertBasal(
            BasalReadingEntity(
                rate = reading.rate,
                isAutomated = reading.isAutomated,
                controlIqMode = reading.controlIqMode.name,
                timestampMs = reading.timestamp.toEpochMilli(),
            ),
        )
    }

    fun observeLatestBasal(): Flow<BasalReading?> =
        pumpDao.observeLatestBasal().map { it?.toDomain() }

    // -- Bolus ----------------------------------------------------------------

    suspend fun saveBoluses(events: List<BolusEvent>) {
        if (events.isEmpty()) return
        pumpDao.insertBoluses(
            events.map {
                BolusEventEntity(
                    units = it.units,
                    isAutomated = it.isAutomated,
                    isCorrection = it.isCorrection,
                    timestampMs = it.timestamp.toEpochMilli(),
                )
            },
        )
    }

    suspend fun getLatestBolusTimestamp(): Instant? {
        val ms = pumpDao.getLatestBolusTimestamp() ?: return null
        return Instant.ofEpochMilli(ms)
    }

    // -- Battery --------------------------------------------------------------

    suspend fun saveBattery(status: BatteryStatus) {
        pumpDao.insertBattery(
            BatteryReadingEntity(
                percentage = status.percentage,
                isCharging = status.isCharging,
                timestampMs = status.timestamp.toEpochMilli(),
            ),
        )
    }

    fun observeLatestBattery(): Flow<BatteryStatus?> =
        pumpDao.observeLatestBattery().map { it?.toDomain() }

    // -- Reservoir ------------------------------------------------------------

    suspend fun saveReservoir(reading: ReservoirReading) {
        pumpDao.insertReservoir(
            ReservoirReadingEntity(
                unitsRemaining = reading.unitsRemaining,
                timestampMs = reading.timestamp.toEpochMilli(),
            ),
        )
    }

    fun observeLatestReservoir(): Flow<ReservoirReading?> =
        pumpDao.observeLatestReservoir().map { it?.toDomain() }

    // -- CGM ------------------------------------------------------------------

    suspend fun saveCgm(reading: CgmReading) {
        pumpDao.insertCgm(
            CgmReadingEntity(
                glucoseMgDl = reading.glucoseMgDl,
                trendArrow = reading.trendArrow.name,
                timestampMs = reading.timestamp.toEpochMilli(),
            ),
        )
    }

    fun observeLatestCgm(): Flow<CgmReading?> =
        pumpDao.observeLatestCgm().map { it?.toDomain() }

    fun observeCgmHistory(since: Instant): Flow<List<CgmReading>> =
        pumpDao.observeCgmHistory(since.toEpochMilli()).map { entities ->
            entities.map { it.toDomain() }
        }

    // -- Cleanup --------------------------------------------------------------

    /**
     * Delete all readings older than [retentionMs] from now.
     * Returns total number of rows deleted.
     */
    suspend fun deleteOldReadings(retentionMs: Long = DEFAULT_RETENTION_MS): Int {
        val cutoff = System.currentTimeMillis() - retentionMs
        val total = pumpDao.deleteAllBefore(cutoff)
        if (total > 0) {
            Timber.d("Data retention cleanup: deleted %d old readings", total)
        }
        return total
    }
}

// -- Entity -> Domain mappers ------------------------------------------------

private fun IoBReadingEntity.toDomain() = IoBReading(
    iob = iob,
    timestamp = Instant.ofEpochMilli(timestampMs),
)

private fun BasalReadingEntity.toDomain() = BasalReading(
    rate = rate,
    isAutomated = isAutomated,
    controlIqMode = try {
        ControlIqMode.valueOf(controlIqMode)
    } catch (_: IllegalArgumentException) {
        ControlIqMode.STANDARD
    },
    timestamp = Instant.ofEpochMilli(timestampMs),
)

private fun BatteryReadingEntity.toDomain() = BatteryStatus(
    percentage = percentage,
    isCharging = isCharging,
    timestamp = Instant.ofEpochMilli(timestampMs),
)

private fun ReservoirReadingEntity.toDomain() = ReservoirReading(
    unitsRemaining = unitsRemaining,
    timestamp = Instant.ofEpochMilli(timestampMs),
)

private fun CgmReadingEntity.toDomain() = CgmReading(
    glucoseMgDl = glucoseMgDl,
    trendArrow = try {
        CgmTrend.valueOf(trendArrow)
    } catch (_: IllegalArgumentException) {
        CgmTrend.UNKNOWN
    },
    timestamp = Instant.ofEpochMilli(timestampMs),
)
