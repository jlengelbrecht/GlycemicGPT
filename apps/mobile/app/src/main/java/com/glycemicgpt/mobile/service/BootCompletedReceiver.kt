package com.glycemicgpt.mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glycemicgpt.mobile.data.local.PumpCredentialStore
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Starts PumpConnectionService after device reboot if a pump is paired.
 *
 * Complements the auto-start in GlycemicGptApp.onCreate() which handles
 * cold app starts but not device reboots.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var pumpCredentialStore: PumpCredentialStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        if (pumpCredentialStore.isPaired()) {
            Timber.d("Boot completed, starting PumpConnectionService (pump is paired)")
            PumpConnectionService.start(context)
        } else {
            Timber.d("Boot completed, pump not paired -- skipping service start")
        }
    }
}
