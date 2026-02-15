package com.glycemicgpt.mobile

import com.glycemicgpt.mobile.ble.connection.BleConnectionManager
import com.glycemicgpt.mobile.ble.connection.TandemBleDriver
import com.glycemicgpt.mobile.ble.protocol.TandemProtocol
import com.glycemicgpt.mobile.data.local.BleDebugStore
import com.glycemicgpt.mobile.domain.model.ConnectionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.concurrent.TimeoutException

class TandemBleDriverTest {

    private val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val bleDebugStore = BleDebugStore()
    private val connectionManager = mockk<BleConnectionManager>(relaxed = true) {
        every { connectionState } returns connectionStateFlow
    }
    private val driver = TandemBleDriver(connectionManager, bleDebugStore)

    @Test
    fun `initial connection state is disconnected`() = runTest {
        val state = driver.observeConnectionState().first()
        assertEquals(ConnectionState.DISCONNECTED, state)
    }

    @Test
    fun `connect delegates to connection manager`() = runTest {
        val result = driver.connect("AA:BB:CC:DD:EE:FF")
        assertTrue(result.isSuccess)
        verify { connectionManager.connect("AA:BB:CC:DD:EE:FF", null) }
    }

    @Test
    fun `disconnect delegates to connection manager`() = runTest {
        val result = driver.disconnect()
        assertTrue(result.isSuccess)
        verify { connectionManager.disconnect() }
    }

    @Test
    fun `observeConnectionState returns manager state`() = runTest {
        connectionStateFlow.value = ConnectionState.CONNECTED
        val state = driver.observeConnectionState().first()
        assertEquals(ConnectionState.CONNECTED, state)
    }

    // -- IoB read tests ------------------------------------------------------

    @Test
    fun `getIoB returns parsed reading on success`() = runTest {
        val mockCargo = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN).putInt(2500).array()
        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_CONTROL_IQ_IOB_REQ, any(), any())
        } returns mockCargo

        val result = driver.getIoB()
        assertTrue(result.isSuccess)
        assertEquals(2.5f, result.getOrThrow().iob, 0.001f)
    }

    @Test
    fun `getIoB returns failure on timeout`() = runTest {
        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_CONTROL_IQ_IOB_REQ, any(), any())
        } throws TimeoutException("timed out")

        val result = driver.getIoB()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TimeoutException)
    }

    @Test
    fun `getIoB returns failure when not connected`() = runTest {
        coEvery {
            connectionManager.sendStatusRequest(any(), any(), any())
        } throws IllegalStateException("Not connected to pump")

        val result = driver.getIoB()
        assertTrue(result.isFailure)
    }

    // -- Basal rate tests ----------------------------------------------------

    @Test
    fun `getBasalRate returns parsed reading on success`() = runTest {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(750) // 0.75 u/hr
        buf.put(0) // Standard mode
        buf.put(1) // Automated
        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_CURRENT_BASAL_STATUS_REQ, any(), any())
        } returns buf.array()

        val result = driver.getBasalRate()
        assertTrue(result.isSuccess)
        assertEquals(0.75f, result.getOrThrow().rate, 0.001f)
        assertTrue(result.getOrThrow().isAutomated)
    }

    // -- Battery tests -------------------------------------------------------

    @Test
    fun `getBatteryStatus returns parsed reading on success`() = runTest {
        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_CURRENT_BATTERY_REQ, any(), any())
        } returns byteArrayOf(72, 0) // 72%, not charging

        val result = driver.getBatteryStatus()
        assertTrue(result.isSuccess)
        assertEquals(72, result.getOrThrow().percentage)
    }

    // -- Reservoir tests -----------------------------------------------------

    @Test
    fun `getReservoirLevel returns parsed reading on success`() = runTest {
        val cargo = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN).putInt(150_000).array() // 150 units
        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_INSULIN_STATUS_REQ, any(), any())
        } returns cargo

        val result = driver.getReservoirLevel()
        assertTrue(result.isSuccess)
        assertEquals(150f, result.getOrThrow().unitsRemaining, 0.001f)
    }

    // -- Pump settings tests -------------------------------------------------

    @Test
    fun `getPumpSettings returns parsed settings on success`() = runTest {
        val fw = "7.8.1".toByteArray()
        val serial = "SN123".toByteArray()
        val model = "X2".toByteArray()
        val buf = ByteBuffer.allocate(4 + fw.size + 4 + serial.size + 4 + model.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(fw.size).put(fw)
        buf.putInt(serial.size).put(serial)
        buf.putInt(model.size).put(model)

        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_PUMP_SETTINGS_REQ, any(), any())
        } returns buf.array()

        val result = driver.getPumpSettings()
        assertTrue(result.isSuccess)
        assertEquals("7.8.1", result.getOrThrow().firmwareVersion)
        assertEquals("SN123", result.getOrThrow().serialNumber)
    }

    // -- Bolus history tests -------------------------------------------------

    @Test
    fun `getBolusHistory returns parsed events on success`() = runTest {
        val now = Instant.now()
        val buf = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(3500) // 3.5 units
        buf.put(0x01) // automated
        buf.putLong(now.minusSeconds(600).toEpochMilli())

        coEvery {
            connectionManager.sendStatusRequest(TandemProtocol.OPCODE_BOLUS_CALC_DATA_REQ, any(), any())
        } returns buf.array()

        val since = now.minusSeconds(3600)
        val result = driver.getBolusHistory(since)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(3.5f, result.getOrThrow()[0].units, 0.001f)
    }

    // -- Opcode verification -------------------------------------------------

    @Test
    fun `each read method uses correct opcode`() = runTest {
        val emptyCargo = ByteArray(0)
        coEvery { connectionManager.sendStatusRequest(any(), any(), any()) } throws
            IllegalStateException("not connected")

        driver.getIoB()
        driver.getBasalRate()
        driver.getBatteryStatus()
        driver.getReservoirLevel()
        driver.getPumpSettings()
        driver.getBolusHistory(Instant.now())

        coVerify { connectionManager.sendStatusRequest(108, any(), any()) }
        coVerify { connectionManager.sendStatusRequest(114, any(), any()) }
        coVerify { connectionManager.sendStatusRequest(57, any(), any()) }
        coVerify { connectionManager.sendStatusRequest(41, any(), any()) }
        coVerify { connectionManager.sendStatusRequest(90, any(), any()) }
        coVerify { connectionManager.sendStatusRequest(75, any(), any()) }
    }
}
