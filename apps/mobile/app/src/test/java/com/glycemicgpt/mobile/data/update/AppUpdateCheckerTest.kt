package com.glycemicgpt.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // isAllowedDownloadHost tests

    @Test
    fun `isAllowedDownloadHost allows github dot com`() {
        assertTrue(AppUpdateChecker.isAllowedDownloadHost("https://github.com/releases/download/v1.0/app.apk"))
    }

    @Test
    fun `isAllowedDownloadHost allows objects dot githubusercontent`() {
        assertTrue(AppUpdateChecker.isAllowedDownloadHost("https://objects.githubusercontent.com/some-path/app.apk"))
    }

    @Test
    fun `isAllowedDownloadHost blocks arbitrary host`() {
        assertFalse(AppUpdateChecker.isAllowedDownloadHost("https://evil.com/malware.apk"))
    }

    @Test
    fun `isAllowedDownloadHost blocks subdomain spoofing`() {
        assertFalse(AppUpdateChecker.isAllowedDownloadHost("https://evil-github.com/app.apk"))
    }

    @Test
    fun `isAllowedDownloadHost rejects malformed URL`() {
        assertFalse(AppUpdateChecker.isAllowedDownloadHost("not-a-url"))
    }

    // sanitizeFileName tests

    @Test
    fun `sanitizeFileName strips query string`() {
        assertEquals("app.apk", AppUpdateChecker.sanitizeFileName("app.apk?token=abc"))
    }

    @Test
    fun `sanitizeFileName strips fragment`() {
        assertEquals("app.apk", AppUpdateChecker.sanitizeFileName("app.apk#section"))
    }

    @Test
    fun `sanitizeFileName replaces special characters`() {
        assertEquals("app__release.apk", AppUpdateChecker.sanitizeFileName("app/ release.apk"))
    }

    @Test
    fun `sanitizeFileName returns fallback for empty input`() {
        assertEquals("update.apk", AppUpdateChecker.sanitizeFileName(""))
    }

    @Test
    fun `sanitizeFileName preserves valid name`() {
        assertEquals("GlycemicGPT-0.1.81-release.apk", AppUpdateChecker.sanitizeFileName("GlycemicGPT-0.1.81-release.apk"))
    }
}
