package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.local.dao.SyncDao
import com.glycemicgpt.mobile.data.local.entity.SyncQueueEntity
import com.glycemicgpt.mobile.data.remote.PumpEventMapper
import com.glycemicgpt.mobile.data.remote.dto.PumpEventDto
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts domain models to PumpEventDto JSON and inserts them
 * into the sync_queue table for later upload.
 */
@Singleton
class SyncQueueEnqueuer @Inject constructor(
    private val syncDao: SyncDao,
    private val moshi: Moshi,
) {

    private val adapter = moshi.adapter(PumpEventDto::class.java)

    suspend fun enqueueIoB(reading: IoBReading) {
        enqueue(PumpEventMapper.fromIoB(reading))
    }

    suspend fun enqueueBasal(reading: BasalReading) {
        enqueue(PumpEventMapper.fromBasal(reading))
    }

    suspend fun enqueueBoluses(events: List<BolusEvent>) {
        events.forEach { enqueue(PumpEventMapper.fromBolus(it)) }
    }

    private suspend fun enqueue(dto: PumpEventDto) {
        syncDao.enqueue(
            SyncQueueEntity(
                eventType = dto.eventType,
                eventTimestampMs = dto.eventTimestamp.toEpochMilli(),
                payload = adapter.toJson(dto),
            ),
        )
    }
}
