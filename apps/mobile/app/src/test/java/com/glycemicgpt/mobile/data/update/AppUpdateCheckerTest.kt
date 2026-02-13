package com.glycemicgpt.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun `parseVersionCode for simple version`() {
        assertEquals(1_000_000, AppUpdateChecker.parseVersionCode("1.0.0"))
    }

    @Test
    fun `parseVersionCode for patch version`() {
        assertEquals(1_000_003, AppUpdateChecker.parseVersionCode("1.0.3"))
    }

    @Test
    fun `parseVersionCode for minor version`() {
        assertEquals(1_020_000, AppUpdateChecker.parseVersionCode("1.2.0"))
    }

    @Test
    fun `parseVersionCode for complex version`() {
        assertEquals(2_050_079, AppUpdateChecker.parseVersionCode("2.5.79"))
    }

    @Test
    fun `parseVersionCode newer is greater`() {
        val old = AppUpdateChecker.parseVersionCode("0.1.79")
        val newer = AppUpdateChecker.parseVersionCode("0.1.80")
        assertTrue(newer > old)
    }

    @Test
    fun `parseVersionCode major bump is greater than minor`() {
        val minorBump = AppUpdateChecker.parseVersionCode("0.99.99")
        val majorBump = AppUpdateChecker.parseVersionCode("1.0.0")
        assertTrue(majorBump > minorBump)
    }

    @Test
    fun `parseVersionCode handles two-part version`() {
        assertEquals(1_020_000, AppUpdateChecker.parseVersionCode("1.2"))
    }

    @Test
    fun `parseVersionCode handles single-part version`() {
        assertEquals(3_000_000, AppUpdateChecker.parseVersionCode("3"))
    }
}
