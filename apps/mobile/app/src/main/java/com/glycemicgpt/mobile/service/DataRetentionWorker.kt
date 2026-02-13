package com.glycemicgpt.mobile.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * WorkManager worker that runs daily to clean up old pump readings.
 *
 * Deletes all readings older than the configured retention period (default 7 days).
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PumpDataRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "data_retention_cleanup"
    }

    override suspend fun doWork(): Result {
        return try {
            val deleted = repository.deleteOldReadings()
            Timber.d("DataRetentionWorker: cleaned up %d old readings", deleted)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "DataRetentionWorker failed")
            Result.retry()
        }
    }
}
