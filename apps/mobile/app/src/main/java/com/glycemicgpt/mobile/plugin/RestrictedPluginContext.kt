package com.glycemicgpt.mobile.plugin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import com.glycemicgpt.mobile.domain.plugin.PLUGIN_API_VERSION
import com.glycemicgpt.mobile.domain.plugin.PluginContext
import com.glycemicgpt.mobile.domain.pump.DebugLogger
import com.glycemicgpt.mobile.domain.pump.PumpCredentialProvider
import com.glycemicgpt.mobile.domain.pump.SafetyLimits
import com.glycemicgpt.mobile.domain.plugin.events.PluginEventBus
import kotlinx.coroutines.flow.StateFlow

/**
 * Creates a [PluginContext] with restricted internals for runtime-loaded plugins.
 *
 * Compile-time plugins get full access to the application context and credential
 * provider. Runtime plugins get:
 * - [RestrictedContext]: blocks startActivity, startService, startForegroundService,
 *   getSystemService, getContentResolver, sendBroadcast, createPackageContext;
 *   returns self for getApplicationContext(); blocks getBaseContext()
 * - [DeniedCredentialProvider]: throws on all credential operations
 * - Full access to settingsStore, debugLogger, eventBus, safetyLimits
 */
object RestrictedPluginContext {

    fun create(
        baseContext: Context,
        pluginId: String,
        settingsStore: com.glycemicgpt.mobile.domain.plugin.PluginSettingsStore,
        debugLogger: DebugLogger,
        eventBus: PluginEventBus,
        safetyLimits: StateFlow<SafetyLimits>,
    ): PluginContext = PluginContext(
        androidContext = RestrictedContext(baseContext),
        pluginId = pluginId,
        settingsStore = settingsStore,
        credentialProvider = DeniedCredentialProvider,
        debugLogger = debugLogger,
        eventBus = eventBus,
        safetyLimits = safetyLimits,
        apiVersion = PLUGIN_API_VERSION,
    )
}

/**
 * A [ContextWrapper] that blocks dangerous operations for runtime plugins.
 * Throws [SecurityException] on activity, service, broadcast, system service,
 * and content resolver access.
 */
internal class RestrictedContext(base: Context) : ContextWrapper(base) {

    private fun denied(operation: String): Nothing =
        throw SecurityException("Runtime plugins cannot call $operation")

    // Prevent sandbox escape via getApplicationContext() or getBaseContext()
    override fun getApplicationContext(): Context = this
    override fun getBaseContext(): Context = denied("getBaseContext")

    override fun startActivity(intent: Intent?) = denied("startActivity")
    override fun startActivity(intent: Intent?, options: Bundle?) = denied("startActivity")

    override fun startService(service: Intent?): ComponentName = denied("startService")
    override fun startForegroundService(service: Intent?): ComponentName = denied("startForegroundService")
    override fun stopService(name: Intent?): Boolean = denied("stopService")
    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean =
        denied("bindService")

    override fun getSystemService(name: String): Any = denied("getSystemService")

    override fun getContentResolver(): ContentResolver = denied("getContentResolver")

    override fun sendBroadcast(intent: Intent?) = denied("sendBroadcast")
    override fun sendBroadcast(intent: Intent?, receiverPermission: String?) =
        denied("sendBroadcast")

    override fun sendOrderedBroadcast(intent: Intent, receiverPermission: String?) =
        denied("sendOrderedBroadcast")

    override fun sendOrderedBroadcast(intent: Intent, receiverPermission: String?, receiverAppOp: Bundle?) =
        denied("sendOrderedBroadcast")

    override fun sendOrderedBroadcast(
        intent: Intent,
        receiverPermission: String?,
        resultReceiver: BroadcastReceiver?,
        scheduler: Handler?,
        initialCode: Int,
        initialData: String?,
        initialExtras: Bundle?,
    ) = denied("sendOrderedBroadcast")

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
    ): Intent? = denied("registerReceiver")

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        flags: Int,
    ): Intent? = denied("registerReceiver")

    override fun createPackageContext(packageName: String?, flags: Int): Context =
        denied("createPackageContext")

    // Allow safe read-only operations:
    // getFilesDir, getCacheDir, getPackageName, getApplicationInfo, getResources, etc.
    // These pass through to the base context via ContextWrapper defaults.
}

/**
 * A [PumpCredentialProvider] that denies all operations.
 * Runtime plugins must not access pump credentials.
 */
internal object DeniedCredentialProvider : PumpCredentialProvider {

    private fun denied(): Nothing =
        throw UnsupportedOperationException("Runtime plugins cannot access pump credentials")

    override fun getPairedAddress(): String = denied()
    override fun getPairingCode(): String = denied()
    override fun isPaired(): Boolean = denied()
    override fun savePairing(address: String, pairingCode: String) = denied()
    override fun clearPairing() = denied()
    override fun saveJpakeCredentials(derivedSecretHex: String, serverNonceHex: String) = denied()
    override fun getJpakeDerivedSecret(): String = denied()
    override fun getJpakeServerNonce(): String = denied()
    override fun clearJpakeCredentials() = denied()
}
