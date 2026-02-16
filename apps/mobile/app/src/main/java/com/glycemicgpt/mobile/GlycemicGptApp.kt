package com.glycemicgpt.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.glycemicgpt.mobile.data.local.PumpCredentialStore
import com.glycemicgpt.mobile.logging.ReleaseTree
import com.glycemicgpt.mobile.service.DataRetentionWorker
import com.glycemicgpt.mobile.service.PumpConnectionService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GlycemicGptApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var pumpCredentialStore: PumpCredentialStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        scheduleDataRetention()

        // Start pump connection service on cold start if already paired.
        // This ensures polling and auto-reconnect resume after app restart.
        if (pumpCredentialStore.isPaired()) {
            PumpConnectionService.start(this)
        }
    }

    private fun scheduleDataRetention() {
        val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(
            1, TimeUnit.DAYS,
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
