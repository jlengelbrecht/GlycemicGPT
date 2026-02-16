package com.glycemicgpt.mobile.service

import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.local.dao.RawHistoryLogDao
import com.glycemicgpt.mobile.data.local.dao.SyncDao
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncQueuePruningTest {

    private val syncDao = mockk<SyncDao>(relaxed = true)
    private val rawHistoryLogDao = mockk<RawHistoryLogDao>(relaxed = true)
    private val api = mockk<GlycemicGptApi>()
    private val authTokenStore = mockk<AuthTokenStore> {
        every { isLoggedIn() } returns true
    }
    private val appSettingsStore = mockk<AppSettingsStore> {
        every { backendSyncEnabled } returns true
    }
    private val moshi = Moshi.Builder().build()

    private val manager = BackendSyncManager(
        syncDao, rawHistoryLogDao, api, authTokenStore, appSettingsStore, moshi,
    )

    @Test
    fun `pruneQueueIfNeeded does nothing when under limit`() = runTest {
        coEvery { syncDao.countAll() } returns 100
        manager.pruneQueueIfNeeded()
        coVerify(exactly = 0) { syncDao.pruneOldest(any()) }
    }

    @Test
    fun `pruneQueueIfNeeded prunes excess when over limit`() = runTest {
        coEvery { syncDao.countAll() } returns 5500
        manager.pruneQueueIfNeeded()
        coVerify { syncDao.pruneOldest(500) }
    }

    @Test
    fun `pruneQueueIfNeeded does nothing at exact limit`() = runTest {
        coEvery { syncDao.countAll() } returns 5000
        manager.pruneQueueIfNeeded()
        coVerify(exactly = 0) { syncDao.pruneOldest(any()) }
    }
}
