package com.glycemicgpt.mobile.presentation.settings

import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.local.PumpCredentialStore
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.HealthResponse
import com.glycemicgpt.mobile.data.remote.dto.LoginRequest
import com.glycemicgpt.mobile.data.remote.dto.LoginResponse
import com.glycemicgpt.mobile.data.remote.dto.UserDto
import com.glycemicgpt.mobile.data.update.AppUpdateChecker
import com.glycemicgpt.mobile.data.update.DownloadResult
import com.glycemicgpt.mobile.data.update.UpdateCheckResult
import com.glycemicgpt.mobile.data.update.UpdateInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val authTokenStore = mockk<AuthTokenStore>(relaxed = true) {
        every { getBaseUrl() } returns "https://test.example.com"
        every { isLoggedIn() } returns false
        every { getUserEmail() } returns null
    }
    private val pumpCredentialStore = mockk<PumpCredentialStore>(relaxed = true) {
        every { isPaired() } returns false
        every { getPairedAddress() } returns null
    }
    private val appSettingsStore = mockk<AppSettingsStore>(relaxed = true) {
        every { backendSyncEnabled } returns true
        every { dataRetentionDays } returns 7
    }
    private val api = mockk<GlycemicGptApi>()
    private val appUpdateChecker = mockk<AppUpdateChecker>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SettingsViewModel(authTokenStore, pumpCredentialStore, appSettingsStore, api, appUpdateChecker)

    @Test
    fun `loadState initializes from stores`() {
        val vm = createViewModel()
        val state = vm.uiState.value

        assertEquals("https://test.example.com", state.baseUrl)
        assertFalse(state.isLoggedIn)
        assertFalse(state.isPumpPaired)
        assertTrue(state.backendSyncEnabled)
        assertEquals(7, state.dataRetentionDays)
    }

    @Test
    fun `loadState restores user email when logged in`() {
        every { authTokenStore.isLoggedIn() } returns true
        every { authTokenStore.getUserEmail() } returns "saved@test.com"
        val vm = createViewModel()

        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals("saved@test.com", vm.uiState.value.userEmail)
    }

    @Test
    fun `saveBaseUrl validates HTTPS in release builds`() {
        // In test (non-debug), HTTP should fail validation
        // Note: BuildConfig.DEBUG is true in test, so HTTP is allowed
        val vm = createViewModel()
        vm.saveBaseUrl("https://valid.example.com")
        assertEquals("https://valid.example.com", vm.uiState.value.baseUrl)
        verify { authTokenStore.saveBaseUrl("https://valid.example.com") }
    }

    @Test
    fun `saveBaseUrl trims input`() {
        val vm = createViewModel()
        vm.saveBaseUrl("  https://example.com  ")
        verify { authTokenStore.saveBaseUrl("https://example.com") }
    }

    @Test
    fun `saveBaseUrl does not persist empty string`() {
        val vm = createViewModel()
        vm.saveBaseUrl("")
        verify(exactly = 0) { authTokenStore.saveBaseUrl("") }
    }

    @Test
    fun `saveBaseUrl rejects malformed URL`() {
        val vm = createViewModel()
        vm.saveBaseUrl("not-a-url")
        assertEquals("Invalid URL. HTTPS required.", vm.uiState.value.connectionTestResult)
        verify(exactly = 0) { authTokenStore.saveBaseUrl("not-a-url") }
    }

    @Test
    fun `testConnection shows success on 200`() = runTest {
        coEvery { api.healthCheck() } returns Response.success(HealthResponse(status = "ok"))
        val vm = createViewModel()

        vm.testConnection()

        assertEquals("Connected successfully", vm.uiState.value.connectionTestResult)
        assertFalse(vm.uiState.value.isTestingConnection)
    }

    @Test
    fun `testConnection shows error on HTTP error`() = runTest {
        coEvery { api.healthCheck() } returns Response.error(
            500,
            "error".toResponseBody(),
        )
        val vm = createViewModel()

        vm.testConnection()

        assertTrue(vm.uiState.value.connectionTestResult!!.contains("500"))
    }

    @Test
    fun `testConnection shows error on network failure`() = runTest {
        coEvery { api.healthCheck() } throws java.io.IOException("No route to host")
        val vm = createViewModel()

        vm.testConnection()

        assertTrue(vm.uiState.value.connectionTestResult!!.contains("No route to host"))
    }

    @Test
    fun `testConnection requires URL`() {
        every { authTokenStore.getBaseUrl() } returns ""
        val vm = createViewModel()

        vm.testConnection()

        assertEquals("Enter a server URL first", vm.uiState.value.connectionTestResult)
    }

    @Test
    fun `login succeeds and saves credentials`() = runTest {
        coEvery { api.login(any()) } returns Response.success(
            LoginResponse(
                accessToken = "jwt123",
                tokenType = "bearer",
                expiresIn = 3600,
                user = UserDto(id = "1", email = "user@test.com", role = "user"),
            ),
        )
        val vm = createViewModel()

        vm.login("user@test.com", "password123")

        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals("user@test.com", vm.uiState.value.userEmail)
        assertFalse(vm.uiState.value.isLoggingIn)
        assertNull(vm.uiState.value.loginError)
        verify { authTokenStore.saveCredentials(any(), "jwt123", any(), "user@test.com") }
    }

    @Test
    fun `login handles null response body`() = runTest {
        coEvery { api.login(any()) } returns Response.success(null)
        val vm = createViewModel()

        vm.login("user@test.com", "password123")

        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals("Login failed: empty response from server", vm.uiState.value.loginError)
    }

    @Test
    fun `login shows error on 401`() = runTest {
        coEvery { api.login(any()) } returns Response.error(
            401,
            "unauthorized".toResponseBody(),
        )
        val vm = createViewModel()

        vm.login("user@test.com", "wrongpassword")

        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals("Invalid email or password", vm.uiState.value.loginError)
    }

    @Test
    fun `login requires URL`() {
        every { authTokenStore.getBaseUrl() } returns ""
        val vm = createViewModel()

        vm.login("user@test.com", "password")

        assertEquals("Configure server URL first", vm.uiState.value.loginError)
    }

    @Test
    fun `login requires email and password`() {
        val vm = createViewModel()

        vm.login("", "password")
        assertEquals("Email and password are required", vm.uiState.value.loginError)

        vm.login("user@test.com", "")
        assertEquals("Email and password are required", vm.uiState.value.loginError)
    }

    @Test
    fun `logout clears token`() {
        every { authTokenStore.isLoggedIn() } returns true
        val vm = createViewModel()

        vm.logout()

        assertFalse(vm.uiState.value.isLoggedIn)
        assertNull(vm.uiState.value.userEmail)
        verify { authTokenStore.clearToken() }
    }

    @Test
    fun `unpair clears pump credentials`() {
        every { pumpCredentialStore.isPaired() } returns true
        every { pumpCredentialStore.getPairedAddress() } returns "AA:BB:CC:DD:EE:FF"
        val vm = createViewModel()
        assertTrue(vm.uiState.value.isPumpPaired)

        vm.unpair()

        assertFalse(vm.uiState.value.isPumpPaired)
        assertNull(vm.uiState.value.pairedPumpAddress)
        verify { pumpCredentialStore.clearPairing() }
    }

    @Test
    fun `setBackendSyncEnabled persists to store`() {
        val vm = createViewModel()

        vm.setBackendSyncEnabled(false)

        assertFalse(vm.uiState.value.backendSyncEnabled)
        verify { appSettingsStore.backendSyncEnabled = false }
    }

    @Test
    fun `setDataRetentionDays clamps to range`() {
        val vm = createViewModel()

        vm.setDataRetentionDays(0) // below min
        assertEquals(1, vm.uiState.value.dataRetentionDays)

        vm.setDataRetentionDays(50) // above max
        assertEquals(30, vm.uiState.value.dataRetentionDays)

        vm.setDataRetentionDays(14) // valid
        assertEquals(14, vm.uiState.value.dataRetentionDays)
    }

    @Test
    fun `show and dismiss logout confirm`() {
        val vm = createViewModel()

        vm.showLogoutConfirm()
        assertTrue(vm.uiState.value.showLogoutConfirm)

        vm.dismissLogoutConfirm()
        assertFalse(vm.uiState.value.showLogoutConfirm)
    }

    @Test
    fun `show and dismiss unpair confirm`() {
        val vm = createViewModel()

        vm.showUnpairConfirm()
        assertTrue(vm.uiState.value.showUnpairConfirm)

        vm.dismissUnpairConfirm()
        assertFalse(vm.uiState.value.showUnpairConfirm)
    }

    @Test
    fun `checkForUpdate shows available when newer version exists`() = runTest {
        coEvery { appUpdateChecker.check(any()) } returns UpdateCheckResult.UpdateAvailable(
            UpdateInfo(
                latestVersion = "1.0.0",
                latestVersionCode = 1_000_000,
                downloadUrl = "https://github.com/releases/download/v1.0.0/GlycemicGPT-1.0.0-release.apk",
                releaseNotes = "Bug fixes",
                apkSizeBytes = 5_000_000,
            ),
        )
        val vm = createViewModel()

        vm.checkForUpdate()

        val state = vm.uiState.value.updateState
        assertTrue(state is UpdateUiState.Available)
        assertEquals("1.0.0", (state as UpdateUiState.Available).version)
    }

    @Test
    fun `checkForUpdate shows up to date`() = runTest {
        coEvery { appUpdateChecker.check(any()) } returns UpdateCheckResult.UpToDate
        val vm = createViewModel()

        vm.checkForUpdate()

        assertTrue(vm.uiState.value.updateState is UpdateUiState.UpToDate)
    }

    @Test
    fun `checkForUpdate shows error on failure`() = runTest {
        coEvery { appUpdateChecker.check(any()) } returns UpdateCheckResult.Error("Network error")
        val vm = createViewModel()

        vm.checkForUpdate()

        val state = vm.uiState.value.updateState
        assertTrue(state is UpdateUiState.Error)
        assertEquals("Network error", (state as UpdateUiState.Error).message)
    }

    @Test
    fun `downloadAndInstallUpdate transitions to ready on success`() = runTest {
        val apkFile = File("/tmp/test.apk")
        coEvery { appUpdateChecker.downloadApk(any(), any(), any()) } returns DownloadResult.Success(apkFile)
        val vm = createViewModel()

        vm.downloadAndInstallUpdate("https://example.com/test.apk", 5_000_000)

        val state = vm.uiState.value.updateState
        assertTrue(state is UpdateUiState.ReadyToInstall)
        assertEquals(apkFile, (state as UpdateUiState.ReadyToInstall).apkFile)
    }

    @Test
    fun `downloadAndInstallUpdate shows error on failure`() = runTest {
        coEvery { appUpdateChecker.downloadApk(any(), any(), any()) } returns DownloadResult.Error("Download failed")
        val vm = createViewModel()

        vm.downloadAndInstallUpdate("https://example.com/test.apk", 5_000_000)

        val state = vm.uiState.value.updateState
        assertTrue(state is UpdateUiState.Error)
        assertEquals("Download failed", (state as UpdateUiState.Error).message)
    }

    @Test
    fun `dismissUpdateState resets to idle`() {
        val vm = createViewModel()

        vm.dismissUpdateState()

        assertTrue(vm.uiState.value.updateState is UpdateUiState.Idle)
    }
}
