package com.glycemicgpt.mobile.plugin.adapter

import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.HistoryLogRecord
import com.glycemicgpt.mobile.domain.plugin.DevicePlugin
import com.glycemicgpt.mobile.domain.plugin.capabilities.PumpStatus
import com.glycemicgpt.mobile.domain.pump.SafetyLimits
import com.glycemicgpt.mobile.plugin.PluginRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HistoryLogParserAdapterTest {

    private val registry: PluginRegistry = mockk(relaxed = true)
    private val adapter = HistoryLogParserAdapter(registry)

    @Test
    fun `extractCgmFromHistoryLogs delegates to active plugin`() {
        val records = listOf(
            HistoryLogRecord(
                sequenceNumber = 1,
                rawBytesB64 = "AAAA",
                eventTypeId = 399,
                pumpTimeSeconds = 1000L,
            ),
        )
        val limits = SafetyLimits()
        val expectedReadings = listOf(
            CgmReading(
                glucoseMgDl = 120,
                trendArrow = CgmTrend.FLAT,
                timestamp = Instant.now(),
            ),
        )

        val pumpStatus: PumpStatus = mockk {
            every { extractCgmFromHistoryLogs(records, limits) } returns expectedReadings
        }
        val plugin: DevicePlugin = mockk(relaxed = true) {
            every { getCapability(PumpStatus::class) } returns pumpStatus
        }
        every { registry.activePumpPlugin } returns MutableStateFlow(plugin)

        val result = adapter.extractCgmFromHistoryLogs(records, limits)

        assertEquals(1, result.size)
        assertEquals(120, result[0].glucoseMgDl)
    }

    @Test
    fun `extractCgmFromHistoryLogs returns empty list when no active plugin`() {
        every { registry.activePumpPlugin } returns MutableStateFlow(null)

        val records = listOf(
            HistoryLogRecord(
                sequenceNumber = 1,
                rawBytesB64 = "AAAA",
                eventTypeId = 399,
                pumpTimeSeconds = 1000L,
            ),
        )

        val result = adapter.extractCgmFromHistoryLogs(records, SafetyLimits())

        assertTrue(result.isEmpty())
    }
}
