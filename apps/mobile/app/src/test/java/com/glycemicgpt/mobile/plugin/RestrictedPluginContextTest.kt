package com.glycemicgpt.mobile.plugin

import android.content.Context
import android.content.Intent
import com.glycemicgpt.mobile.domain.plugin.PLUGIN_API_VERSION
import com.glycemicgpt.mobile.domain.plugin.PluginSettingsStore
import com.glycemicgpt.mobile.domain.pump.DebugLogger
import com.glycemicgpt.mobile.domain.pump.SafetyLimits
import com.glycemicgpt.mobile.domain.plugin.events.PluginEventBus
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedPluginContextTest {

    private val baseContext: Context = mockk(relaxed = true)
    private val settingsStore: PluginSettingsStore = mockk(relaxed = true)
    private val debugLogger: DebugLogger = mockk(relaxed = true)
    private val eventBus: PluginEventBus = mockk(relaxed = true)
    private val safetyLimits = MutableStateFlow(SafetyLimits())

    private fun createContext() = RestrictedPluginContext.create(
        baseContext = baseContext,
        pluginId = "test.runtime.plugin",
        settingsStore = settingsStore,
        debugLogger = debugLogger,
        eventBus = eventBus,
        safetyLimits = safetyLimits,
    )

    @Test
    fun `creates PluginContext with correct pluginId`() {
        val ctx = createContext()
        assertEquals("test.runtime.plugin", ctx.pluginId)
    }

    @Test
    fun `creates PluginContext with correct apiVersion`() {
        val ctx = createContext()
        assertEquals(PLUGIN_API_VERSION, ctx.apiVersion)
    }

    @Test
    fun `passes through settingsStore`() {
        val ctx = createContext()
        assertEquals(settingsStore, ctx.settingsStore)
    }

    @Test
    fun `passes through debugLogger`() {
        val ctx = createContext()
        assertEquals(debugLogger, ctx.debugLogger)
    }

    @Test
    fun `passes through eventBus`() {
        val ctx = createContext()
        assertEquals(eventBus, ctx.eventBus)
    }

    @Test
    fun `passes through safetyLimits`() {
        val ctx = createContext()
        assertEquals(safetyLimits, ctx.safetyLimits)
    }

    @Test
    fun `androidContext is RestrictedContext`() {
        val ctx = createContext()
        assertTrue(ctx.androidContext is RestrictedContext)
    }

    @Test
    fun `RestrictedContext blocks startActivity`() {
        val restricted = RestrictedContext(baseContext)
        val thrown = try {
            restricted.startActivity(Intent())
            null
        } catch (e: SecurityException) {
            e
        }
        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("startActivity"))
    }

    @Test
    fun `RestrictedContext blocks startService`() {
        val restricted = RestrictedContext(baseContext)
        val thrown = try {
            restricted.startService(Intent())
            null
        } catch (e: SecurityException) {
            e
        }
        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("startService"))
    }

    @Test
    fun `RestrictedContext blocks getSystemService`() {
        val restricted = RestrictedContext(baseContext)
        val thrown = try {
            restricted.getSystemService(Context.ALARM_SERVICE)
            null
        } catch (e: SecurityException) {
            e
        }
        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("getSystemService"))
    }

    @Test
    fun `RestrictedContext blocks getContentResolver`() {
        val restricted = RestrictedContext(baseContext)
        val thrown = try {
            restricted.contentResolver
            null
        } catch (e: SecurityException) {
            e
        }
        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("getContentResolver"))
    }

    @Test
    fun `RestrictedContext blocks sendBroadcast`() {
        val restricted = RestrictedContext(baseContext)
        val thrown = try {
            restricted.sendBroadcast(Intent())
            null
        } catch (e: SecurityException) {
            e
        }
        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("sendBroadcast"))
    }

    @Test
    fun `DeniedCredentialProvider blocks getPairedAddress`() {
        val ctx = createContext()
        val thrown = try {
            ctx.credentialProvider.getPairedAddress()
            null
        } catch (e: UnsupportedOperationException) {
            e
        }
        assertNotNull(thrown)
        assertTrue(thrown!!.message!!.contains("credentials"))
    }

    @Test
    fun `DeniedCredentialProvider blocks savePairing`() {
        val ctx = createContext()
        val thrown = try {
            ctx.credentialProvider.savePairing("AA:BB:CC:DD:EE:FF", "1234")
            null
        } catch (e: UnsupportedOperationException) {
            e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `DeniedCredentialProvider blocks isPaired`() {
        val ctx = createContext()
        val thrown = try {
            ctx.credentialProvider.isPaired()
            null
        } catch (e: UnsupportedOperationException) {
            e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `DeniedCredentialProvider blocks clearPairing`() {
        val ctx = createContext()
        val thrown = try {
            ctx.credentialProvider.clearPairing()
            null
        } catch (e: UnsupportedOperationException) {
            e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `DeniedCredentialProvider blocks JPAKE credentials`() {
        val ctx = createContext()
        val thrown = try {
            ctx.credentialProvider.saveJpakeCredentials("secret", "nonce")
            null
        } catch (e: UnsupportedOperationException) {
            e
        }
        assertNotNull(thrown)
    }
}
