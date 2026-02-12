package com.glycemicgpt.mobile

import com.glycemicgpt.mobile.ble.connection.BleConnectionManager
import com.glycemicgpt.mobile.ble.connection.TandemBleDriver
import com.glycemicgpt.mobile.domain.model.ConnectionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TandemBleDriverTest {

    private val connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val connectionManager = mockk<BleConnectionManager>(relaxed = true) {
        every { connectionState } returns connectionStateFlow
    }
    private val driver = TandemBleDriver(connectionManager)

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
    fun `getIoB returns not implemented until story 16_3`() = runTest {
        val result = driver.getIoB()
        assertTrue(result.isFailure)
    }

    @Test
    fun `observeConnectionState returns manager state`() = runTest {
        connectionStateFlow.value = ConnectionState.CONNECTED
        val state = driver.observeConnectionState().first()
        assertEquals(ConnectionState.CONNECTED, state)
    }
}
