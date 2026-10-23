package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.launchWithMaterialTransition
import com.v2ray.ang.extension.startActivityWithMaterialTransition
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.service.CoreTunToggleService
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.ConnectionInfoSheetState
import com.v2ray.ang.ui.compose.main.ClipboardImportPreview
import com.v2ray.ang.ui.compose.main.ConnectionUiState
import com.v2ray.ang.ui.compose.main.MainDisplayFlags
import com.v2ray.ang.ui.compose.main.MainDrawerDestination
import com.v2ray.ang.ui.compose.main.MainScreen
import com.v2ray.ang.ui.compose.main.MainScreenActions
import com.v2ray.ang.util.IPv6Util
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    private var groups by mutableStateOf<List<GroupMapItem>>(emptyList())
    private var selectedGuid by mutableStateOf("")
    private var currentMode by mutableStateOf(AppConfig.VPN)
    private var searchButtonEnabled by mutableStateOf(true)
    private var importing by mutableStateOf(false)
    private var display by mutableStateOf(MainDisplayFlags())
    private var connectionState by mutableStateOf(ConnectionUiState())
    private var infoState by mutableStateOf(ConnectionInfoSheetState())
    private var clipboardImport by mutableStateOf<ClipboardImportPreview?>(null)

    private var connectedAt: Long = 0L
    private var uptimeJob: Job? = null

    private val mainScreenActions = MainScreenActions(
        onStartStop = ::handleStartStop,
        onTunToggle = ::handleTunToggle,
        onTest = ::handleTestClick,
        onConnectionInfo = ::showConnectionInfoSheet,
        onModeSelected = ::selectMode,
        onFilter = { query -> mainViewModel.filterConfig(query) },
        onGroupSelected = ::selectGroup,
        onSelectServer = ::selectServer,
        onSwapServers = { from, to -> mainViewModel.swapServer(from, to) },
        onEditServer = ::editServer,
        onRemoveServer = ::removeServer,
        onDrawerNavigate = ::handleDrawerNavigation,
        onImportAction = { actionId -> handleImportMenuAction(actionId) },
        onOverflowAction = { actionId -> handleOverflowAction(actionId) },
        onDestructiveAction = ::handleDestructiveAction,
        onConnectionInfoDismiss = { infoState = infoState.copy(visible = false) },
        onCopyIp = ::copyConnectionInfoIp,
        onPingFromInfo = ::pingFromConnectionInfo,
        onShareFromInfo = ::shareFromConnectionInfo,
        onClipboardImportConfirm = ::confirmClipboardImport,
        onClipboardImportDismiss = { clipboardImport = null },
    )

    /**
     * MainActivity is the root of the app's task. The system already moves the
     * task to the back instead of finishing it when the user presses back, so
     * the base back dispatcher callback is not needed here. This lets the
     * default predictive back animation run without interference.
     */
    override fun shouldRegisterBackDispatcherCallback(): Boolean = false

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        } else {
            applyRunningState(isLoading = false, isRunning = false)
        }
    }
    private val requestTunVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startTunService()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        SettingsChangeManager.consumeSetupGroupTab()
        refreshGroups()
        refreshDisplayFlags()
        refreshPreferences()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshPreferences()
        refreshDisplayFlags()
        refreshGroups()
        selectedGuid = MmkvManager.getSelectServer().orEmpty()
        connectionState = ConnectionUiState(
            serverName = selectedServerRemarks(),
            tunEnabled = SettingsManager.isTunEnabled(),
        )

        setContent {
            AppTheme {
                MainScreen(
                    groups = groups,
                    serversFlow = mainViewModel.serversCacheFlow,
                    subscriptionIdFlow = mainViewModel.subscriptionIdFlow,
                    listRevisionFlow = mainViewModel.updateListActionFlow,
                    connection = connectionState,
                    selectedGuid = selectedGuid,
                    display = display,
                    currentMode = currentMode,
                    searchButtonEnabled = searchButtonEnabled,
                    importing = importing,
                    infoState = infoState,
                    clipboardImport = clipboardImport,
                    actions = mainScreenActions,
                )
            }
        }

        setupViewModel()
        SubscriptionUpdater.sync()
        UpdateCheckerManager.checkForUpdateAutomatically()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    override fun onResume() {
        super.onResume()
        refreshPreferences()
        refreshDisplayFlags()
        refreshGroups()
        selectedGuid = MmkvManager.getSelectServer().orEmpty()
        mainViewModel.reloadServerList()
        setSelectedServerName()
        updateTunToggleState()
        if (connectionState.isRunning) {
            connectionState = connectionState.copy(
                showSpeed = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_TOOLBAR_ENABLED) == true,
            )
        }
    }

    private fun refreshPreferences() {
        currentMode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN) ?: AppConfig.VPN
        searchButtonEnabled = MmkvManager.decodeSettingsBool(
            AppConfig.PREF_SERVER_SEARCH_BUTTON_ENABLED,
            true,
        )
    }

    private fun refreshGroups() {
        groups = mainViewModel.getSubscriptions(this)
    }

    private fun refreshDisplayFlags() {
        display = MainDisplayFlags(
            grouped = mainViewModel.isAllGroupedMode(),
            doubleColumn = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false),
            cardStyleNew = SettingsManager.getServerCardStyle() == "new",
            showEmojiAvatar = SettingsManager.getServerEmojiAvatar(),
            showIcons = mainViewModel.shouldShowAllTabIcons(),
            showQuickTestAll = MmkvManager.decodeSettingsBool(AppConfig.PREF_QUICK_ACTION_TEST_ALL, true),
            showQuickSort = MmkvManager.decodeSettingsBool(AppConfig.PREF_QUICK_ACTION_SORT, true),
            showQuickUpdate = MmkvManager.decodeSettingsBool(AppConfig.PREF_QUICK_ACTION_UPDATE, true),
            showUnsupportedPingPill = MmkvManager.decodeSettingsBool(AppConfig.PREF_UNSUPPORTED_PING_PILL, true),
            bolderFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_MAIN_BOLDER_FONT, true),
        )
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { content ->
            if (!content.isNullOrEmpty()) {
                connectionState = connectionState.copy(statusDetail = content)
            }
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.netSpeed.observe(this) { (up, down) ->
            setSpeedText(up, down)
            if (infoState.visible) {
                infoState = infoState.copy(
                    downloadSpeed = down.toSpeedString(),
                    uploadSpeed = up.toSpeedString(),
                )
            }
        }
        mainViewModel.connectionPing.observe(this) { ping ->
            if (!ping.isNullOrEmpty()) {
                connectionState = connectionState.copy(statusDetail = ping)
            }
        }
        mainViewModel.connectionIp.observe(this) { ip ->
            setConnectionIpText(ip)
        }
        mainViewModel.snackbarMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                toast(message)
            }
            // A start failure (or a cancelled connection attempt) settles the loading state,
            // otherwise the connect button would stay stuck in its loading spinner.
            applyRunningState(isLoading = false, isRunning = mainViewModel.isRunning.value == true)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setSpeedText(up: Long, down: Long) {
        connectionState = connectionState.copy(
            uploadSpeed = up.toSpeedString(),
            downloadSpeed = down.toSpeedString(),
            uploadRate = up,
            downloadRate = down,
        )
    }

    private fun setSelectedServerName() {
        connectionState = connectionState.copy(serverName = selectedServerRemarks())
    }

    private fun selectedServerRemarks(): String? {
        val guid = MmkvManager.getSelectServer()
        return if (guid.isNullOrEmpty()) null else MmkvManager.decodeServerConfig(guid)?.remarks
    }

    private fun setConnectionIpText(ip: String?) {
        val compressedIp = IPv6Util.compressIPv6(ip, maxDisplayLength = 26)
        connectionState = connectionState.copy(connectionIp = compressedIp)
    }

    private fun startUptimeTimer(running: Boolean) {
        if (!running) {
            uptimeJob?.cancel()
            return
        }
        if (uptimeJob?.isActive == true) return
        connectedAt = System.currentTimeMillis()
        uptimeJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - connectedAt
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / 60000) % 60
                val hours = elapsed / 3600000
                val uptimeText = if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%02d:%02d", minutes, seconds)
                }
                connectionState = connectionState.copy(uptime = uptimeText)
                delay(1000)
            }
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        connectionState = connectionState.copy(
            isLoading = isLoading,
            isRunning = isRunning,
            statusDetail = "",
            showSpeed = isRunning && !isLoading &&
                MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_TOOLBAR_ENABLED) == true,
            connectionIp = if (isRunning && !isLoading) connectionState.connectionIp else null,
            uptime = if (isRunning) connectionState.uptime else "00:00",
            uploadRate = if (isRunning) connectionState.uploadRate else 0L,
            downloadRate = if (isRunning) connectionState.downloadRate else 0L,
            uploadSpeed = if (isRunning) connectionState.uploadSpeed else "0 B/s",
            downloadSpeed = if (isRunning) connectionState.downloadSpeed else "0 B/s",
        )

        if (isRunning) {
            setTunButtonVisible(SettingsManager.isProxyTunMode())
            updateTunToggleState()
            startUptimeTimer(true)
        } else {
            setTunButtonVisible(false)
            startUptimeTimer(false)
            if (SettingsManager.isTunEnabled()) {
                stopTunService()
            }
        }
    }

    private fun handleStartStop() {
        if (connectionState.isLoading) {
            // The user tapped the button again while connecting — cancel the attempt.
            CoreServiceManager.cancelVService(this)
            applyRunningState(isLoading = false, isRunning = false)
            return
        }

        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else {
            startWithPermission()
        }
    }

    private fun startWithPermission() {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun handleTestClick() {
        if (mainViewModel.isRunning.value == true) {
            connectionState = connectionState.copy(statusDetail = getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            applyRunningState(isLoading = false, isRunning = false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
            && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) == true
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {
                CoreServiceManager.startVService(this)
            }
        } else {
            CoreServiceManager.startVService(this)
        }
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startWithPermission()
        }
    }

    private fun selectGroup(id: String) {
        if (mainViewModel.subscriptionId != id) {
            mainViewModel.subscriptionIdChanged(id)
        }
    }

    private fun selectServer(guid: String) {
        if (guid != MmkvManager.getSelectServer()) {
            MmkvManager.setSelectServer(guid)
            selectedGuid = guid
            setSelectedServerName()

            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            }
        }
    }

    private fun removeServer(guid: String) {
        mainViewModel.removeServer(guid)
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            else -> ServerActivity::class.java
        }

        val intent = Intent(this, activityClass)
            .putExtra("guid", guid)
            .putExtra("isRunning", mainViewModel.isRunning.value)
            .putExtra("createConfigType", profile.configType.value)
            .putExtra("subscriptionId", mainViewModel.subscriptionId)

        requestActivityLauncher.launchWithMaterialTransition(this, intent)
    }

    private fun selectMode(value: String) {
        if (currentMode == value) return
        MmkvManager.encodeSettings(AppConfig.PREF_MODE, value)
        currentMode = value
        if (mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
    }

    private fun showConnectionInfoSheet() {
        val speed = mainViewModel.netSpeed.value
        infoState = ConnectionInfoSheetState(
            visible = true,
            downloadSpeed = speed?.second?.toSpeedString() ?: "0 B/s",
            uploadSpeed = speed?.first?.toSpeedString() ?: "0 B/s",
        )

        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                SpeedtestManager.getRemoteIPDetail()
            }
            if (!infoState.visible) return@launch
            val ip = detail?.let { info ->
                listOf(info.ip, info.clientIp, info.ip_addr, info.query)
                    .firstOrNull { !it.isNullOrBlank() }
            } ?: "-"
            val location = detail?.let { info ->
                listOfNotNull(info.city, info.region, info.country_name)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }
            } ?: "-"
            val isp = detail?.let { info ->
                listOf(info.isp, info.organization, info.asn)
                    .firstOrNull { !it.isNullOrBlank() }
            } ?: "-"
            infoState = infoState.copy(ip = ip, location = location, isp = isp)
        }
    }

    private fun copyConnectionInfoIp() {
        val ip = infoState.ip
        if (!ip.isNullOrBlank() && ip != "-") {
            Utils.setClipboard(this, ip)
            toast(R.string.connection_info_ip_copied)
        }
    }

    private fun pingFromConnectionInfo() {
        connectionState = connectionState.copy(statusDetail = getString(R.string.connection_test_testing))
        mainViewModel.testCurrentServerRealPing()
        infoState = infoState.copy(visible = false)
    }

    private fun shareFromConnectionInfo() {
        val guid = MmkvManager.getSelectServer()
        if (guid.isNullOrEmpty()) {
            toastError(R.string.title_file_chooser)
        } else if (AngConfigManager.share2Clipboard(this, guid) == 0) {
            toast(R.string.toast_success)
        } else {
            toastError(R.string.toast_failure)
        }
    }

    private fun handleImportMenuAction(actionId: Int): Boolean {
        when (actionId) {
            R.id.import_qrcode -> importQRcode()
            R.id.import_clipboard -> importClipboard()
            R.id.import_local -> importConfigLocal()
            R.id.import_manually_policy_group -> importManually(EConfigType.POLICYGROUP.value)
            R.id.import_manually_proxy_chain -> importManually(EConfigType.PROXYCHAIN.value)
            R.id.import_manually_vmess -> importManually(EConfigType.VMESS.value)
            R.id.import_manually_vless -> importManually(EConfigType.VLESS.value)
            R.id.import_manually_ss -> importManually(EConfigType.SHADOWSOCKS.value)
            R.id.import_manually_socks -> importManually(EConfigType.SOCKS.value)
            R.id.import_manually_http -> importManually(EConfigType.HTTP.value)
            R.id.import_manually_trojan -> importManually(EConfigType.TROJAN.value)
            R.id.import_manually_wireguard -> importManually(EConfigType.WIREGUARD.value)
            R.id.import_manually_amneziawg -> importManually(EConfigType.AMNEZIAWG.value)
            R.id.import_manually_hysteria2 -> importManually(EConfigType.HYSTERIA2.value)
            R.id.import_manually_olcrtc -> importManually(EConfigType.OLCRTC.value)
            R.id.import_manually_openflux -> importManually(EConfigType.OPENFLUX.value)
            else -> return false
        }
        return true
    }

    private fun handleOverflowAction(actionId: Int): Boolean {
        return when (actionId) {
            R.id.service_restart -> {
                restartV2Ray()
                true
            }

            R.id.export_all -> {
                exportAll()
                true
            }

            R.id.real_ping_all -> {
                toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
                mainViewModel.testAllRealPing()
                true
            }

            R.id.sort_by_test_results -> {
                sortByTestResults()
                true
            }

            R.id.sub_update -> {
                importConfigViaSub()
                true
            }

            else -> false
        }
    }

    private fun handleDestructiveAction(actionId: Int) {
        when (actionId) {
            R.id.del_all_config -> delAllConfig()
            R.id.del_duplicate_config -> delDuplicateConfig()
            R.id.del_invalid_config -> delInvalidConfig()
        }
    }

    private fun importManually(createConfigType: Int) {
        val intent = if (createConfigType == EConfigType.POLICYGROUP.value) {
            Intent()
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerGroupActivity::class.java)
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            Intent()
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerProxyChainActivity::class.java)
        } else {
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        }
        startActivityWithMaterialTransition(intent)
    }

    /**
     * import config from qrcode
     */
    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard(): Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            if (clipboard.isBlank()) {
                toastError(R.string.toast_failure)
                return true
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val preview = AngConfigManager.previewImport(clipboard)
                withContext(Dispatchers.Main) {
                    clipboardImport = ClipboardImportPreview(
                        clipboard,
                        buildImportClipboardMessage(preview),
                    )
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun confirmClipboardImport() {
        val content = clipboardImport?.content ?: return
        clipboardImport = null
        importBatchConfig(content)
    }

    private fun buildImportClipboardMessage(preview: AngConfigManager.ImportPreview): String {
        val sb = StringBuilder()
        if (preview.configCount > 0) {
            sb.append(getString(R.string.import_clipboard_configs, preview.configCount))
            preview.configLabels.take(8).forEach { label ->
                sb.append('\n').append("• ").append(label)
            }
            if (preview.configLabels.size > 8) {
                sb.append('\n').append('…')
            }
        }
        if (preview.subscriptionCount > 0) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(
                getString(
                    R.string.import_clipboard_subscription,
                    preview.subscriptionUrls.firstOrNull().orEmpty()
                )
            )
        }
        if (sb.isEmpty()) {
            sb.append(getString(R.string.import_clipboard_unrecognized))
        }
        return sb.toString()
    }

    private fun importBatchConfig(server: String?) {
        updateImportingState(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            setSelectedServerName()
                        }

                        countSub > 0 -> refreshGroups()
                        else -> toastError(R.string.toast_failure)
                    }
                    updateImportingState(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    updateImportingState(false)
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    private fun importConfigViaSub(): Boolean {
        updateImportingState(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                if (result.hwidRejectedCount > 0) {
                    toast(getString(R.string.title_hwid_rejected, result.hwidRejectedCount))
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                    setSelectedServerName()
                }
                updateImportingState(false)
            }
        }
        return true
    }

    private fun exportAll() {
        updateImportingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                updateImportingState(false)
            }
        }
    }

    private fun delAllConfig() {
        updateImportingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.removeAllServer()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                setSelectedServerName()
                toast(getString(R.string.title_del_config_count, ret))
                updateImportingState(false)
            }
        }
    }

    private fun delDuplicateConfig() {
        updateImportingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.removeDuplicateServer()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                setSelectedServerName()
                toast(getString(R.string.title_del_duplicate_config_count, ret))
                updateImportingState(false)
            }
        }
    }

    private fun delInvalidConfig() {
        updateImportingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.removeInvalidServer()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                setSelectedServerName()
                toast(getString(R.string.title_del_config_count, ret))
                updateImportingState(false)
            }
        }
    }

    private fun sortByTestResults() {
        updateImportingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                updateImportingState(false)
            }
        }
    }

    private fun updateImportingState(value: Boolean) {
        runOnUiThread { importing = value }
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    private fun handleDrawerNavigation(destination: MainDrawerDestination) {
        when (destination) {
            MainDrawerDestination.SUBSCRIPTIONS -> requestActivityLauncher.launchWithMaterialTransition(
                this,
                Intent(this, SubSettingActivity::class.java),
            )

            MainDrawerDestination.PER_APP_PROXY -> startActivityWithMaterialTransition(
                Intent(this, PerAppProxyActivity::class.java),
            )

            MainDrawerDestination.ROUTING -> startActivityWithMaterialTransition(
                Intent(this, RoutingSettingActivity::class.java),
            )

            MainDrawerDestination.SETTINGS -> requestActivityLauncher.launchWithMaterialTransition(
                this,
                Intent(this, SettingsActivity::class.java),
            )

            MainDrawerDestination.LOGCAT -> startActivityWithMaterialTransition(
                Intent(this, LogcatActivity::class.java),
            )

            MainDrawerDestination.BACKUP -> requestActivityLauncher.launchWithMaterialTransition(
                this,
                Intent(this, BackupActivity::class.java),
            )

            MainDrawerDestination.ABOUT -> startActivityWithMaterialTransition(
                Intent(this, AboutActivity::class.java),
            )

            MainDrawerDestination.KILL_APP -> {
                // The VPN/TUN services live in the daemon process, so kill only after it has
                // received the stop and torn the interface down.
                if (SettingsManager.isTunEnabled()) {
                    stopTunService()
                }
                MessageUtil.sendMsg2ServiceForResult(this, AppConfig.MSG_STATE_STOP, "") {
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }

    private fun handleTunToggle() {
        if (SettingsManager.isTunEnabled()) {
            stopTunService()
        } else {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startTunService()
            } else {
                requestTunVpnPermission.launch(intent)
            }
        }
    }

    private fun startTunService() {
        SettingsManager.setTunEnabled(true)
        val intent = Intent(this, CoreTunToggleService::class.java)
        ContextCompat.startForegroundService(this, intent)
        updateTunToggleState()
    }

    private fun stopTunService() {
        SettingsManager.setTunEnabled(false)
        val intent = Intent(this, CoreTunToggleService::class.java)
        intent.action = AppConfig.ACTION_STOP_TUN
        startService(intent)
        sendBroadcast(Intent(AppConfig.ACTION_STOP_TUN))
        updateTunToggleState()
    }

    private fun updateTunToggleState() {
        val tunOn = SettingsManager.isTunEnabled()
        connectionState = connectionState.copy(tunEnabled = tunOn)
    }

    private fun setTunButtonVisible(visible: Boolean) {
        connectionState = connectionState.copy(showTun = visible)
    }

    override fun onDestroy() {
        uptimeJob?.cancel()
        super.onDestroy()
    }
}
