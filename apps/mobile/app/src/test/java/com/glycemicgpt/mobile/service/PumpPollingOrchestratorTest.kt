package com.glycemicgpt.mobile.service

import com.glycemicgpt.mobile.data.local.dao.RawHistoryLogDao
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import com.glycemicgpt.mobile.data.repository.SyncQueueEnqueuer
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.ControlIqMode
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PumpPollingOrchestratorTest {

    private val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val pumpDriver = mockk<PumpDriver>(relaxed = true) {
        every { observeConnectionState() } returns connectionStateFlow
        coEvery { getIoB() } returns Result.success(
            IoBReading(iob = 2.5f, timestamp = Instant.now()),
        )
        coEvery { getBasalRate() } returns Result.success(
            BasalReading(
                rate = 0.8f,
                isAutomated = true,
                controlIqMode = ControlIqMode.STANDARD,
                timestamp = Instant.now(),
            ),
        )
        coEvery { getBatteryStatus() } returns Result.success(
            BatteryStatus(percentage = 80, isCharging = false, timestamp = Instant.now()),
        )
        coEvery { getReservoirLevel() } returns Result.success(
            ReservoirReading(unitsRemaining = 150f, timestamp = Instant.now()),
        )
        coEvery { getCgmStatus() } returns Result.success(
            CgmReading(glucoseMgDl = 120, trendArrow = CgmTrend.FLAT, timestamp = Instant.now()),
        )
        coEvery { getBolusHistory(any()) } returns Result.success(emptyList())
        coEvery { getHistoryLogs(any()) } returns Result.success(emptyList())
        coEvery { getPumpHardwareInfo() } returns Result.failure(RuntimeException("not connected"))
    }
    private val repository = mockk<PumpDataRepository>(relaxed = true) {
        coEvery { getLatestBolusTimestamp() } returns null
    }
    private val syncEnqueuer = mockk<SyncQueueEnqueuer>(relaxed = true)
    private val rawHistoryLogDao = mockk<RawHistoryLogDao>(relaxed = true)

    private fun createOrchestrator() = PumpPollingOrchestrator(pumpDriver, repository, syncEnqueuer, rawHistoryLogDao)

    @Test
    fun `does not poll when disconnected`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        advanceTimeBy(60_000)

        coVerify(exactly = 0) { pumpDriver.getIoB() }
        orchestrator.stop()
    }

    @Test
    fun `polls immediately when connected`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100) // let coroutines run

        coVerify(atLeast = 1) { pumpDriver.getIoB() }
        coVerify(atLeast = 1) { pumpDriver.getBasalRate() }
        coVerify(atLeast = 1) { pumpDriver.getBatteryStatus() }
        coVerify(atLeast = 1) { pumpDriver.getReservoirLevel() }
        coVerify(atLeast = 1) { pumpDriver.getBolusHistory(any()) }
        orchestrator.stop()
    }

    @Test
    fun `saves readings to repository on poll`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100)

        coVerify(atLeast = 1) { repository.saveIoB(any()) }
        coVerify(atLeast = 1) { repository.saveBasal(any()) }
        coVerify(atLeast = 1) { repository.saveBattery(any()) }
        coVerify(atLeast = 1) { repository.saveReservoir(any()) }
        coVerify(atLeast = 1) { repository.saveCgm(any()) }
        orchestrator.stop()
    }

    @Test
    fun `polls IoB again after fast interval`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100) // initial poll
        coVerify(exactly = 1) { pumpDriver.getIoB() }

        advanceTimeBy(PumpPollingOrchestrator.INTERVAL_FAST_MS)
        coVerify(exactly = 2) { pumpDriver.getIoB() }
        orchestrator.stop()
    }

    @Test
    fun `stops polling on disconnect`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100)
        coVerify(exactly = 1) { pumpDriver.getIoB() }

        connectionStateFlow.value = ConnectionState.DISCONNECTED
        advanceTimeBy(PumpPollingOrchestrator.INTERVAL_FAST_MS + 100)
        // Should not have polled again after disconnect
        coVerify(exactly = 1) { pumpDriver.getIoB() }
        orchestrator.stop()
    }

    @Test
    fun `resumes polling on reconnect`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100)
        coVerify(exactly = 1) { pumpDriver.getIoB() }

        connectionStateFlow.value = ConnectionState.DISCONNECTED
        advanceTimeBy(1000)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100)
        coVerify(exactly = 2) { pumpDriver.getIoB() }
        orchestrator.stop()
    }

    @Test
    fun `low battery multiplies poll interval`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.phoneBatteryLow = true
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100)
        coVerify(exactly = 1) { pumpDriver.getIoB() }

        // Normal interval passes but should NOT trigger another poll
        advanceTimeBy(PumpPollingOrchestrator.INTERVAL_FAST_MS)
        coVerify(exactly = 1) { pumpDriver.getIoB() }

        // Full low-battery interval passes
        advanceTimeBy(
            PumpPollingOrchestrator.INTERVAL_FAST_MS *
                (PumpPollingOrchestrator.LOW_BATTERY_MULTIPLIER - 1),
        )
        coVerify(exactly = 2) { pumpDriver.getIoB() }
        orchestrator.stop()
    }

    @Test
    fun `polls CGM in fast loop when connected`() = runTest {
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100) // initial poll
        coVerify(exactly = 1) { pumpDriver.getCgmStatus() }

        advanceTimeBy(PumpPollingOrchestrator.INTERVAL_FAST_MS)
        coVerify(exactly = 2) { pumpDriver.getCgmStatus() }
        orchestrator.stop()
    }

    @Test
    fun `phoneBatteryLow defaults to false`() {
        val orchestrator = createOrchestrator()
        assertFalse(orchestrator.phoneBatteryLow)
    }

    @Test
    fun `continues polling when individual read fails`() = runTest {
        coEvery { pumpDriver.getIoB() } returns Result.failure(RuntimeException("timeout"))
        val orchestrator = createOrchestrator()
        orchestrator.start(this)

        connectionStateFlow.value = ConnectionState.CONNECTED
        advanceTimeBy(100)

        // Other reads should still succeed
        coVerify(atLeast = 1) { repository.saveBasal(any()) }
        coVerify(atLeast = 1) { repository.saveBattery(any()) }

        // Should continue polling despite IoB failure
        advanceTimeBy(PumpPollingOrchestrator.INTERVAL_FAST_MS)
        coVerify(exactly = 2) { pumpDriver.getIoB() }
        orchestrator.stop()
    }
}
