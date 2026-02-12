package com.glycemicgpt.mobile

import com.glycemicgpt.mobile.ble.connection.TandemBleDriver
import com.glycemicgpt.mobile.domain.model.ConnectionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TandemBleDriverTest {

    private val driver = TandemBleDriver()

    @Test
    fun `initial connection state is disconnected`() = runTest {
        val state = driver.observeConnectionState().first()
        assertEquals(ConnectionState.DISCONNECTED, state)
    }

    @Test
    fun `connect returns not implemented until story 16_2`() = runTest {
        val result = driver.connect("AA:BB:CC:DD:EE:FF")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getIoB returns not implemented until story 16_3`() = runTest {
        val result = driver.getIoB()
        assertTrue(result.isFailure)
    }

    @Test
    fun `disconnect sets state to disconnected`() = runTest {
        driver.disconnect()
        val state = driver.observeConnectionState().first()
        assertEquals(ConnectionState.DISCONNECTED, state)
    }
}
