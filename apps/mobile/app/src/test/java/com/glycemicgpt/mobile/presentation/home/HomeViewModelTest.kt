package com.glycemicgpt.mobile.presentation.home

import com.glycemicgpt.mobile.data.local.GlucoseRangeStore
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.GlucoseRangeResponse
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.ControlIqMode
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import com.glycemicgpt.mobile.service.BackendSyncManager
import com.glycemicgpt.mobile.service.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val iobFlow = MutableStateFlow<IoBReading?>(null)
    private val basalFlow = MutableStateFlow<BasalReading?>(null)
    private val batteryFlow = MutableStateFlow<BatteryStatus?>(null)
    private val reservoirFlow = MutableStateFlow<ReservoirReading?>(null)
    private val cgmFlow = MutableStateFlow<CgmReading?>(null)
    private val syncStatusFlow = MutableStateFlow(SyncStatus())

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
    }

    private val repository = mockk<PumpDataRepository>(relaxed = true) {
        every { observeLatestIoB() } returns iobFlow
        every { observeLatestBasal() } returns basalFlow
        every { observeLatestBattery() } returns batteryFlow
        every { observeLatestReservoir() } returns reservoirFlow
        every { observeLatestCgm() } returns cgmFlow
    }

    private val backendSyncManager = mockk<BackendSyncManager>(relaxed = true) {
        every { syncStatus } returns syncStatusFlow
    }

    private val glucoseRangeStore = mockk<GlucoseRangeStore>(relaxed = true) {
        every { urgentLow } returns GlucoseRangeStore.DEFAULT_URGENT_LOW
        every { low } returns GlucoseRangeStore.DEFAULT_LOW
        every { high } returns GlucoseRangeStore.DEFAULT_HIGH
        every { urgentHigh } returns GlucoseRangeStore.DEFAULT_URGENT_HIGH
        every { isStale(any()) } returns false
    }

    private val api = mockk<GlycemicGptApi>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(pumpDriver, repository, backendSyncManager, glucoseRangeStore, api)

    @Test
    fun `initial state has null readings and not refreshing`() = runTest {
        val vm = createViewModel()

        assertNull(vm.iob.value)
        assertNull(vm.basalRate.value)
        assertNull(vm.battery.value)
        assertNull(vm.reservoir.value)
        assertNull(vm.cgm.value)
        assertFalse(vm.isRefreshing.value)
        assertEquals(ConnectionState.DISCONNECTED, vm.connectionState.value)
    }

    @Test
    fun `cgm flow emits when repository updates`() = runTest {
        val vm = createViewModel()

        // Subscribe to activate WhileSubscribed stateIn
        val collected = mutableListOf<CgmReading?>()
        val job = backgroundScope.launch(testDispatcher) {
            vm.cgm.collect { collected.add(it) }
        }

        assertNull(vm.cgm.value)

        val reading = CgmReading(glucoseMgDl = 180, trendArrow = CgmTrend.SINGLE_UP, timestamp = Instant.now())
        cgmFlow.value = reading

        assertNotNull(vm.cgm.value)
        assertEquals(180, vm.cgm.value!!.glucoseMgDl)
        assertEquals(CgmTrend.SINGLE_UP, vm.cgm.value!!.trendArrow)

        job.cancel()
    }

    @Test
    fun `refreshData calls all pump driver methods including CGM`() = runTest {
        val vm = createViewModel()

        vm.refreshData()
        advanceUntilIdle()

        coVerify(atLeast = 1) { pumpDriver.getIoB() }
        coVerify(atLeast = 1) { pumpDriver.getBasalRate() }
        coVerify(atLeast = 1) { pumpDriver.getBatteryStatus() }
        coVerify(atLeast = 1) { pumpDriver.getReservoirLevel() }
        coVerify(atLeast = 1) { pumpDriver.getCgmStatus() }
    }

    @Test
    fun `refreshData saves CGM result to repository`() = runTest {
        val vm = createViewModel()

        vm.refreshData()
        advanceUntilIdle()

        coVerify(atLeast = 1) { repository.saveCgm(any()) }
    }

    @Test
    fun `refreshData sets isRefreshing to false when done`() = runTest {
        val vm = createViewModel()

        vm.refreshData()
        advanceUntilIdle()

        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun `refreshData resets isRefreshing on failure`() = runTest {
        coEvery { pumpDriver.getIoB() } throws RuntimeException("BLE error")
        val vm = createViewModel()

        vm.refreshData()
        advanceUntilIdle()

        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun `glucoseThresholds returns values from store`() {
        every { glucoseRangeStore.urgentLow } returns 50
        every { glucoseRangeStore.low } returns 65
        every { glucoseRangeStore.high } returns 200
        every { glucoseRangeStore.urgentHigh } returns 280
        val vm = createViewModel()
        val t = vm.glucoseThresholds.value
        assertEquals(50, t.urgentLow)
        assertEquals(65, t.low)
        assertEquals(200, t.high)
        assertEquals(280, t.urgentHigh)
    }

    @Test
    fun `connection state flows from pump driver`() = runTest {
        val vm = createViewModel()

        // Subscribe to activate WhileSubscribed stateIn
        val job = backgroundScope.launch(testDispatcher) {
            vm.connectionState.collect {}
        }

        assertEquals(ConnectionState.DISCONNECTED, vm.connectionState.value)

        connectionStateFlow.value = ConnectionState.CONNECTED
        assertEquals(ConnectionState.CONNECTED, vm.connectionState.value)

        job.cancel()
    }

    @Test
    fun `refreshData updates glucose thresholds from API`() = runTest {
        val rangeResponse = GlucoseRangeResponse(
            urgentLow = 55f,
            lowTarget = 80f,
            highTarget = 200f,
            urgentHigh = 300f,
        )
        coEvery { api.getGlucoseRange() } returns Response.success(rangeResponse)
        every { glucoseRangeStore.urgentLow } returns 55
        every { glucoseRangeStore.low } returns 80
        every { glucoseRangeStore.high } returns 200
        every { glucoseRangeStore.urgentHigh } returns 300

        val vm = createViewModel()
        vm.refreshData()
        advanceUntilIdle()

        verify { glucoseRangeStore.updateAll(urgentLow = 55, low = 80, high = 200, urgentHigh = 300) }
        val t = vm.glucoseThresholds.value
        assertEquals(55, t.urgentLow)
        assertEquals(80, t.low)
        assertEquals(200, t.high)
        assertEquals(300, t.urgentHigh)
    }

    @Test
    fun `refreshGlucoseRange failure preserves existing thresholds`() = runTest {
        coEvery { api.getGlucoseRange() } throws RuntimeException("Network error")
        val vm = createViewModel()

        vm.refreshData()
        advanceUntilIdle()

        // Thresholds should remain at defaults from store
        val t = vm.glucoseThresholds.value
        assertEquals(GlucoseRangeStore.DEFAULT_URGENT_LOW, t.urgentLow)
        assertEquals(GlucoseRangeStore.DEFAULT_LOW, t.low)
        assertEquals(GlucoseRangeStore.DEFAULT_HIGH, t.high)
        assertEquals(GlucoseRangeStore.DEFAULT_URGENT_HIGH, t.urgentHigh)
        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun `init refreshes glucose range when store is stale`() = runTest {
        every { glucoseRangeStore.isStale(any()) } returns true
        val rangeResponse = GlucoseRangeResponse(
            urgentLow = 60f,
            lowTarget = 85f,
            highTarget = 190f,
            urgentHigh = 290f,
        )
        coEvery { api.getGlucoseRange() } returns Response.success(rangeResponse)

        createViewModel()
        advanceUntilIdle()

        coVerify(atLeast = 1) { api.getGlucoseRange() }
        verify { glucoseRangeStore.updateAll(urgentLow = 60, low = 85, high = 190, urgentHigh = 290) }
    }
}
