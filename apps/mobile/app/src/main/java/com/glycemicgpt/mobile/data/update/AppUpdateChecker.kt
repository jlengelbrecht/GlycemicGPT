package com.glycemicgpt.mobile.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    val assets: List<GitHubAsset>,
    val body: String?,
)

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    val name: String,
    @Json(name = "browser_download_url") val browserDownloadUrl: String,
    val size: Long,
)

data class UpdateInfo(
    val latestVersion: String,
    val latestVersionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String?,
    val apkSizeBytes: Long,
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class DownloadResult {
    data class Success(val apkFile: File) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    // Standalone client without auth/baseUrl interceptors that would break GitHub API calls
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersionCode: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        "GitHub API returned ${response.code}",
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext UpdateCheckResult.Error("Empty response")

                val adapter = moshi.adapter(GitHubRelease::class.java)
                val release = adapter.fromJson(body)
                    ?: return@withContext UpdateCheckResult.Error("Failed to parse release")

                val apkAsset = release.assets.firstOrNull {
                    it.name.startsWith(APK_PREFIX) && it.name.endsWith(APK_SUFFIX)
                } ?: return@withContext UpdateCheckResult.Error("No APK found in release")

                val version = release.tagName.removePrefix("v")
                val remoteVersionCode = parseVersionCode(version)

                if (remoteVersionCode > currentVersionCode) {
                    UpdateCheckResult.UpdateAvailable(
                        UpdateInfo(
                            latestVersion = version,
                            latestVersionCode = remoteVersionCode,
                            downloadUrl = apkAsset.browserDownloadUrl,
                            releaseNotes = release.body,
                            apkSizeBytes = apkAsset.size,
                        ),
                    )
                } else {
                    UpdateCheckResult.UpToDate
                }
            } catch (e: Exception) {
                Timber.w(e, "Update check failed")
                UpdateCheckResult.Error(e.message ?: "Unknown error")
            }
        }

    suspend fun downloadApk(url: String, fileName: String): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Error(
                        "Download failed: HTTP ${response.code}",
                    )
                }

                val apkFile = File(context.cacheDir, fileName)
                response.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext DownloadResult.Error("Empty download body")

                DownloadResult.Success(apkFile)
            } catch (e: Exception) {
                Timber.w(e, "APK download failed")
                DownloadResult.Error(e.message ?: "Download failed")
            }
        }

    fun createInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/jlengelbrecht/GlycemicGPT/releases/latest"
        private const val APK_PREFIX = "GlycemicGPT-"
        private const val APK_SUFFIX = "-release.apk"

        fun parseVersionCode(version: String): Int {
            val parts = version.split(".")
            val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
            return major * 1_000_000 + minor * 10_000 + patch
        }
    }
}
