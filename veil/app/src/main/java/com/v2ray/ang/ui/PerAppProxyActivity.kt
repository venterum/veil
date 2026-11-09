package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.PerAppProxyScreen
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.PerAppProxyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-app proxy picker. The screen itself is [PerAppProxyScreen]; this activity
 * owns the app list, MMKV-backed switches and the bulk import/export actions.
 */
class PerAppProxyActivity : BaseComponentActivity() {
    private val viewModel: PerAppProxyViewModel by viewModels()

    private var appsAll by mutableStateOf<List<AppInfo>>(emptyList())
    private var loading by mutableStateOf(true)
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var perAppEnabled by mutableStateOf(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
    )
    private var bypassApps by mutableStateOf(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = viewModel.getAll()
        loadApps()
    }

    @Composable
    override fun ScreenContent() {
        PerAppProxyScreen(
            apps = appsAll,
            loading = loading,
            selected = selected,
            perAppEnabled = perAppEnabled,
            bypassApps = bypassApps,
            onBackClick = { finish() },
            onToggleApp = { packageName ->
                viewModel.toggle(packageName)
                selected = viewModel.getAll()
            },
            onSetPerAppEnabled = { enabled ->
                perAppEnabled = enabled
                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, enabled)
                SettingsChangeManager.makeRestartService()
            },
            onSetBypassApps = { enabled ->
                bypassApps = enabled
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, enabled)
                SettingsChangeManager.makeRestartService()
            },
            onAddAll = { packages ->
                if (packages.isNotEmpty()) {
                    viewModel.addAll(packages)
                    selected = viewModel.getAll()
                }
            },
            onRemoveAll = { packages ->
                if (packages.isNotEmpty()) {
                    viewModel.removeAll(packages)
                    selected = viewModel.getAll()
                }
            },
            onAutoSelect = ::selectProxyAppAuto,
            onImport = ::importProxyApp,
            onExport = ::exportProxyApp,
        )
    }

    private fun loadApps() {
        loading = true
        lifecycleScope.launch {
            try {
                appsAll = withContext(Dispatchers.IO) {
                    AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                }
                selected = viewModel.getAll()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error loading apps", e)
            } finally {
                loading = false
            }
        }
    }

    private fun selectProxyAppAuto() {
        toast(R.string.msg_downloading_content)
        loading = true

        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var content = HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = url,
                        timeout = 5000
                    )
                )
                if (content.isNullOrEmpty()) {
                    val proxyUsername = SettingsManager.getSocksUsername()
                    val proxyPassword = SettingsManager.getSocksPassword()
                    val httpPort = SettingsManager.getHttpPort()
                    content = HttpUtil.getUrlContent(
                        UrlContentRequest(
                            url = url,
                            timeout = 5000,
                            httpPort = httpPort,
                            proxyUsername = proxyUsername,
                            proxyPassword = proxyPassword
                        )
                    ) ?: ""
                }
                withContext(Dispatchers.Main) {
                    applyProxyAppList(content, force = true)
                    toastSuccess(R.string.toast_success)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error downloading proxy app list", e)
            } finally {
                withContext(Dispatchers.Main) {
                    loading = false
                }
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (TextUtils.isEmpty(content)) return
        applyProxyAppList(content, force = false)
        toastSuccess(R.string.toast_success)
    }

    private fun exportProxyApp() {
        var lst = bypassApps.toString()

        viewModel.getAll().forEach { pkg ->
            lst = lst + System.lineSeparator() + pkg
        }
        Utils.setClipboard(applicationContext, lst)
        toastSuccess(R.string.toast_success)
    }

    /**
     * Applies a package list (downloaded or pasted) to the picker. In general
     * mode the listed apps are selected; in bypass mode everything else is.
     */
    private fun applyProxyAppList(content: String, force: Boolean): Boolean {
        return try {
            val proxyApps = if (TextUtils.isEmpty(content)) {
                Utils.readTextFromAssets(v2RayApplication, "proxy_package_name")
            } else {
                content
            }
            if (TextUtils.isEmpty(proxyApps)) return false

            viewModel.clear()
            val packages = appsAll
                .filter { app ->
                    val inList = inProxyApps(proxyApps, app.packageName, force)
                    if (bypassApps) !inList else inList
                }
                .map { it.packageName }
            viewModel.addAll(packages)
            selected = viewModel.getAll()
            allowPerAppProxy()
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error selecting proxy app", e)
            false
        }
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == "com.google.android.webview") return false
            if (packageName.startsWith("com.google")) return true
        }
        return proxyApps.indexOf(packageName) >= 0
    }

    private fun allowPerAppProxy() {
        if (!perAppEnabled) {
            perAppEnabled = true
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
            SettingsChangeManager.makeRestartService()
        }
    }
}
