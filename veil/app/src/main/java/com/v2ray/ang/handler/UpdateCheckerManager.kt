package com.v2ray.ang.handler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UpdateCheckerManager {

    private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * Checks for updates silently in the background and notifies the user via a
     * notification when a newer version is available. Rate-limited to once per
     * [AUTO_CHECK_INTERVAL_MS] and deduplicated per version so the user is not
     * re-notified for an update they have already seen.
     */
    fun checkForUpdateAutomatically(context: Context = AngApplication.application) {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_CHECK_UPDATE, true)) {
            return
        }
        val now = System.currentTimeMillis()
        val lastCheck = MmkvManager.decodeSettingsLong(AppConfig.PREF_LAST_UPDATE_CHECK, 0L)
        if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
            return
        }
        MmkvManager.encodeSettings(AppConfig.PREF_LAST_UPDATE_CHECK, now)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = checkForUpdate()
                if (!result.hasUpdate) return@launch
                val latestVersion = result.latestVersion ?: return@launch
                val lastNotified = MmkvManager.decodeSettingsString(AppConfig.PREF_LAST_UPDATE_NOTIFIED_VERSION)
                if (latestVersion == lastNotified) return@launch
                MmkvManager.encodeSettings(AppConfig.PREF_LAST_UPDATE_NOTIFIED_VERSION, latestVersion)
                showUpdateNotification(context, result)
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Automatic update check failed: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(context: Context, result: CheckUpdateResult) {
        val contentIntent = result.downloadUrl?.let { url ->
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        NotificationHelper.notify(
            NotificationChannelType.UPDATE_AVAILABLE,
            context,
            context.getString(R.string.update_new_version_found, result.latestVersion ?: ""),
            context.getString(R.string.update_now),
            contentIntent
        )
    }

    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            AppConfig.APP_API_URL.concatUrl("latest")
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                ?.firstOrNull()
                ?: throw IllegalStateException("No pre-release found")
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        val latestVersion = latestRelease.tagName.removePrefix("v")
        LogUtil.i(
            AppConfig.TAG,
            "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})"
        )

        return@withContext if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = downloadUrl,
                isPreRelease = latestRelease.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val v1 = version1.split(".")
        val v2 = version2.split(".")

        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i].leadingNumber() else 0
            val num2 = if (i < v2.size) v2[i].leadingNumber() else 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    /** The leading digits of a version component ("0-beta" -> 0), or 0 when there are none. */
    private fun String.leadingNumber(): Int =
        takeWhile { it.isDigit() }.toIntOrNull() ?: 0

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = assetsByAbi.firstOrNull { it.name.contains(fDroid) }
            ?: assetsByAbi.firstOrNull()

        return asset?.browserDownloadUrl
            ?: throw IllegalStateException("No compatible APK found")
    }
}
