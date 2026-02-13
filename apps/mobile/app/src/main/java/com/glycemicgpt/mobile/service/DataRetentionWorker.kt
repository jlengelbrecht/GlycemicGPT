package com.glycemicgpt.mobile.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs daily to clean up old pump readings.
 *
 * Deletes all readings older than the configured retention period.
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PumpDataRepository,
    private val appSettingsStore: AppSettingsStore,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "data_retention_cleanup"
    }

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = appSettingsStore.dataRetentionDays
            val retentionMs = TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val deleted = repository.deleteOldReadings(retentionMs)
            Timber.d("DataRetentionWorker: cleaned up %d old readings (retention=%d days)", deleted, retentionDays)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "DataRetentionWorker failed")
            Result.retry()
        }
    }
}
