package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.view.View
import android.graphics.Color
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.finishWithMaterialTransition
import com.v2ray.ang.extension.launchWithMaterialTransition
import com.v2ray.ang.extension.performLightHapticFeedback
import com.v2ray.ang.extension.performMediumHapticFeedback
import com.v2ray.ang.extension.startActivityForResultWithMaterialTransition
import com.v2ray.ang.extension.startActivityWithMaterialTransition
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.service.CoreTunToggleService
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.ConnectionInfoSheet
import com.v2ray.ang.ui.compose.ConnectionInfoSheetState
import com.v2ray.ang.ui.compose.ExpressiveToolbarActions
import com.v2ray.ang.ui.compose.ExpressiveBottomBar
import com.v2ray.ang.ui.compose.ExpressiveBottomBarState
import com.v2ray.ang.ui.compose.VeilSearchBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.v2ray.ang.util.LogUtil
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private var connectedAt: Long = 0L
    private var uptimeJob: Job? = null
    private var searchButtonEnabled by mutableStateOf(true)
    private var searchVisible by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var bottomBarState by mutableStateOf(ExpressiveBottomBarState())
    private var connectionInfoState by mutableStateOf(ConnectionInfoSheetState())

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
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, getString(R.string.title_server))

        // setup Compose expressive toolbar buttons
        binding.composeToolbarActions.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    ExpressiveToolbarActions(
                        onFilterClick = { toggleSearch() },
                        onImportMenuAction = { actionId -> handleImportMenuAction(actionId) },
                        onOverflowAction = { actionId -> handleOverflowAction(actionId) },
                        showSearchButton = searchButtonEnabled,
                    )
                }
            }
        }

        // setup viewpager and tablayout
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true

        // setup navigation drawer
        setupNavigationDrawer()

        // setup search
        binding.composeSearchBar.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    VeilSearchBar(
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                            mainViewModel.filterConfig(query)
                        },
                        visible = searchVisible,
                        onDismiss = {
                            searchVisible = false
                            searchQuery = ""
                            mainViewModel.filterConfig("")
                        },
                    )
                }
            }
        }

        bindBottomBar()

        setupGroupTab()
        setupViewModel()
        SubscriptionUpdater.sync()
        UpdateCheckerManager.checkForUpdateAutomatically()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    private fun setupNavigationDrawer() {
        val drawerArrow = DrawerArrowDrawable(this)
        binding.toolbar.navigationIcon = drawerArrow
        binding.toolbar.setNavigationOnClickListener {
            if (binding.drawerLayout.isDrawerVisible(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
        setupDrawerMenu()

        val contentView = binding.drawerLayout.getChildAt(0)
        binding.drawerLayout.setScrimColor(Color.TRANSPARENT)
        binding.drawerLayout.setDrawerElevation(0f)

        applyAppFont()

        val drawerBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val direction = if (drawerView.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
                contentView.translationX = drawerView.width * slideOffset * direction
                contentView.alpha = 1f - 0.5f * slideOffset
            }

            override fun onDrawerOpened(drawerView: View) {
                drawerBackCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerBackCallback.isEnabled = false
                contentView.translationX = 0f
                contentView.alpha = 1f
            }
        })
    }

    private fun setupDrawerMenu() {
        val entries = listOf(
            DrawerEntry.Header(R.string.title_drawer_section_main),
            DrawerEntry.Item(R.id.sub_setting, R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
            DrawerEntry.Item(R.id.per_app_proxy_settings, R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
            DrawerEntry.Item(R.id.routing_setting, R.drawable.ic_routing_24dp, R.string.routing_settings_title),
            DrawerEntry.Item(R.id.mode_selector, R.drawable.ic_tun_off_24dp, R.string.title_mode),
            DrawerEntry.Item(R.id.settings, R.drawable.ic_settings_24dp, R.string.title_settings),
            DrawerEntry.Header(R.string.title_drawer_section_more),
            DrawerEntry.Item(R.id.logcat, R.drawable.ic_logcat_24dp, R.string.title_logcat),
            DrawerEntry.Item(R.id.backup_restore, R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
            DrawerEntry.Item(R.id.about, R.drawable.ic_about_24dp, R.string.title_about),
            DrawerEntry.Item(R.id.kill_app, R.drawable.ic_kill_app_24dp, R.string.title_kill_app),
        )
        binding.navRecycler.layoutManager = LinearLayoutManager(this)
        binding.navRecycler.adapter = DrawerAdapter(entries) { itemId, view ->
            view.performLightHapticFeedback()
            handleDrawerNavigation(itemId)
        }
    }

    private fun bindBottomBar() {
        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    ExpressiveBottomBar(
                        state = bottomBarState,
                        onStartStop = ::handleFabAction,
                        onModeSelector = ::showModeSelector,
                        onTunToggle = ::handleTunToggle,
                        onTest = ::handleLayoutTestClick,
                        onConnectionInfo = ::showConnectionInfoSheet,
                    )
                    ConnectionInfoSheet(
                        state = connectionInfoState,
                        onDismiss = { connectionInfoState = connectionInfoState.copy(visible = false) },
                        onCopyIp = ::copyConnectionInfoIp,
                        onPing = ::pingFromConnectionInfo,
                        onShare = ::shareFromConnectionInfo,
                    )
                }
            }
        }
        binding.bottomControlContainer.removeAllViews()
        binding.bottomControlContainer.addView(composeView)
        setSelectedServerName()
    }

    /**
     * Public hook for the server-list fragments to refresh the pill's server name
     * immediately after the user picks a different server.
     */
    fun onSelectedServerChanged() {
        setSelectedServerName()
    }


    /**
     * Applies the app font to the navigation header title and the toolbar title.
     * When the "use Google Sans" preference is on (default), the bundled
     * Google Sans Flex typeface is used. Otherwise the device default
     * typeface is applied.
     */
    private fun applyAppFont() {
        val useGoogleSans = MmkvManager.decodeSettingsBool(AppConfig.PREF_GOOGLE_SANS, true)
        val typeface = if (useGoogleSans) {
            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.google_sans_flex)
        } else {
            resolveSystemTypeface()
        }
        findViewById<android.widget.TextView>(R.id.tv_app_name)?.typeface = typeface
        findToolbarTitleView(binding.toolbar)?.typeface = typeface
    }

    /**
     * Locates the internal title [android.widget.TextView] that a [androidx.appcompat.widget.Toolbar]
     * lazily creates when a title is set. The view has no public id, so it is resolved by
     * matching its text against the toolbar's current title.
     */
    private fun findToolbarTitleView(toolbar: android.view.ViewGroup): android.widget.TextView? {
        val title = (toolbar as? androidx.appcompat.widget.Toolbar)?.title?.toString() ?: return null
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is android.widget.TextView && child.text?.toString() == title) {
                return child
            }
        }
        return null
    }

    /**
     * Resolves the device's default typeface from the [android.R.style.Theme_DeviceDefault]
     * theme so that OEM/user-selected system fonts (e.g. Samsung One UI, MIUI) are honored.
     * Falls back to [android.graphics.Typeface.DEFAULT] (Roboto on stock Android).
     *
     * Note: proprietary system-UI fonts such as Google Sans are not exposed to third-party
     * apps, so on stock devices this resolves to Roboto.
     */
    private fun resolveSystemTypeface(): android.graphics.Typeface {
        val themedContext = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
        val typedArray = themedContext.obtainStyledAttributes(intArrayOf(android.R.attr.fontFamily))
        val fontFamily = try {
            typedArray.getString(0)
        } finally {
            typedArray.recycle()
        }
        return if (!fontFamily.isNullOrEmpty()) {
            android.graphics.Typeface.create(fontFamily, android.graphics.Typeface.NORMAL)
        } else {
            android.graphics.Typeface.DEFAULT
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { content ->
            if (!content.isNullOrEmpty()) {
                setTestStateText(content)
            }
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.netSpeed.observe(this) { (up, down) ->
            setSpeedText(up.toSpeedString(), down.toSpeedString())
            if (connectionInfoState.visible) {
                connectionInfoState = connectionInfoState.copy(
                    downloadSpeed = down.toSpeedString(),
                    uploadSpeed = up.toSpeedString(),
                )
            }
        }
        mainViewModel.connectionPing.observe(this) { ping ->
            if (!ping.isNullOrEmpty()) {
                setTestStateText(ping)
            }
        }
        mainViewModel.connectionIp.observe(this) { ip ->
            setConnectionIpText(ip)
        }
        mainViewModel.snackbarMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                showOrUpdateSnackbar(message)
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setTestStateText(content: String?) {
        bottomBarState = bottomBarState.copy(status = content.orEmpty())
    }

    private fun setSpeedText(up: String, down: String) {
        bottomBarState = bottomBarState.copy(uploadSpeed = up, downloadSpeed = down)
    }

    /**
     * Shows the remarks of the currently selected server in whichever main UI mode is
     * bound (bottom bar pill or panel), so the active server is visible without opening
     * the list. Hidden when nothing is selected; no-op for a mode that isn't bound.
     */
    fun setSelectedServerName() {
        val guid = MmkvManager.getSelectServer()
        val remarks = if (guid.isNullOrEmpty()) null else MmkvManager.decodeServerConfig(guid)?.remarks
        bottomBarState = bottomBarState.copy(serverName = remarks)
    }

    private fun setConnectionIpText(ip: String?) {
        val compressedIp = com.v2ray.ang.util.IPv6Util.compressIPv6(ip, maxDisplayLength = 26)
        bottomBarState = bottomBarState.copy(connectionIp = compressedIp)
    }

    private fun showOrUpdateSnackbar(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)
        binding.viewPager.offscreenPageLimit = groups.size.coerceAtLeast(1)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }

        binding.tabGroup.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.view?.performLightHapticFeedback()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }.takeIf { it >= 0 } ?: (groups.size - 1)
        binding.viewPager.setCurrentItem(targetIndex, false)

        binding.tabGroup.isVisible = groups.size > 1
        refreshGroupTabTitles(true)
    }

    fun refreshGroupTabTitles(refreshAll: Boolean = false) {
        val groupsToRefresh = if (refreshAll || mainViewModel.subscriptionId.isEmpty()) {
            groupPagerAdapter.groups
        } else {
            groupPagerAdapter.groups.filter { it.id == mainViewModel.subscriptionId }
        }

        groupsToRefresh.forEach { group ->
            if (group.id.isEmpty()) {
                return@forEach
            }
            val tabIndex = groupPagerAdapter.groups.indexOfFirst { it.id == group.id }
            if (tabIndex >= 0) {
                val count = MmkvManager.decodeServerList(group.id).size
                binding.tabGroup.getTabAt(tabIndex)?.text = "${group.remarks} ($count)"
            }
        }
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
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

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunning.value == true) {
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        } else {
            // service not running: keep existing no-op (could show a message if desired)
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

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun setTestState(content: String?) {
        setTestStateText(content)
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
                bottomBarState = bottomBarState.copy(uptime = uptimeText)
                delay(1000)
            }
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        bottomBarState = bottomBarState.copy(
            isLoading = isLoading,
            isRunning = isRunning,
            showSpeed = isRunning && !isLoading &&
                MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_TOOLBAR_ENABLED) == true,
            connectionIp = if (isRunning && !isLoading) bottomBarState.connectionIp else null,
        )

        if (isRunning) {
            setTestState(getString(R.string.connection_connected))
            setTunButtonVisible(SettingsManager.isProxyTunMode())
            updateTunToggleState()
            startUptimeTimer(true)
        } else {
            setTestState(getString(R.string.connection_not_connected))
            setTunButtonVisible(false)
            startUptimeTimer(false)
            if (SettingsManager.isTunEnabled()) {
                stopTunService()
            }
        }
    }

    private fun showConnectionInfoSheet() {
        val speed = mainViewModel.netSpeed.value
        connectionInfoState = ConnectionInfoSheetState(
            visible = true,
            downloadSpeed = speed?.second?.toSpeedString() ?: "0 B/s",
            uploadSpeed = speed?.first?.toSpeedString() ?: "0 B/s",
        )

        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                SpeedtestManager.getRemoteIPDetail()
            }
            if (!connectionInfoState.visible) return@launch
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
            connectionInfoState = connectionInfoState.copy(ip = ip, location = location, isp = isp)
        }
    }

    private fun copyConnectionInfoIp() {
        val ip = connectionInfoState.ip
        if (!ip.isNullOrBlank() && ip != "-") {
            Utils.setClipboard(this, ip)
            toast(R.string.connection_info_ip_copied)
        }
    }

    private fun pingFromConnectionInfo() {
        setTestState(getString(R.string.connection_test_testing))
        mainViewModel.testCurrentServerRealPing()
        connectionInfoState = connectionInfoState.copy(visible = false)
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

    override fun onResume() {
        super.onResume()
        setSelectedServerName()
        updateTunToggleState()
        val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SERVER_SEARCH_BUTTON_ENABLED, true)
        if (enabled != searchButtonEnabled) {
            searchButtonEnabled = enabled
            if (!enabled && searchVisible) {
                toggleSearch()
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.search_view -> {
            toggleSearch()
            true
        }

        R.id.import_qrcode,
        R.id.import_clipboard,
        R.id.import_local,
        R.id.import_manually_policy_group,
        R.id.import_manually_proxy_chain,
        R.id.import_manually_vmess,
        R.id.import_manually_vless,
        R.id.import_manually_ss,
        R.id.import_manually_socks,
        R.id.import_manually_http,
        R.id.import_manually_trojan,
        R.id.import_manually_wireguard,
        R.id.import_manually_hysteria2,
        R.id.import_manually_olcrtc -> handleImportMenuAction(item.itemId)

        R.id.export_all -> {
            exportAll()
            true
        }

        R.id.real_ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            delAllConfig()
            true
        }

        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }

        R.id.del_invalid_config -> {
            delInvalidConfig()
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

        R.id.locate_selected_config -> {
            locateSelectedServer()
            true
        }

        else -> super.onOptionsItemSelected(item)
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
            R.id.import_manually_hysteria2 -> importManually(EConfigType.HYSTERIA2.value)
            R.id.import_manually_olcrtc -> importManually(EConfigType.OLCRTC.value)
            else -> return false
        }
        return true
    }

    private fun handleOverflowAction(actionId: Int): Boolean {
        return when (actionId) {
            R.id.search_view -> {
                toggleSearch()
                true
            }
            R.id.service_restart -> {
                restartV2Ray()
                true
            }
            R.id.del_all_config -> {
                delAllConfig()
                true
            }
            R.id.del_duplicate_config -> {
                delDuplicateConfig()
                true
            }
            R.id.del_invalid_config -> {
                delInvalidConfig()
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
            R.id.locate_selected_config -> {
                locateSelectedServer()
                true
            }
            R.id.sub_update -> {
                importConfigViaSub()
                true
            }
            else -> false
        }
    }

    private fun toggleSearch() {
        if (searchVisible) {
            searchVisible = false
            searchQuery = ""
            mainViewModel.filterConfig("")
        } else {
            searchVisible = true
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
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            if (clipboard.isBlank()) {
                toastError(R.string.toast_failure)
                return true
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val preview = AngConfigManager.previewImport(clipboard)
                withContext(Dispatchers.Main) {
                    showImportClipboardDialog(clipboard, preview)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun showImportClipboardDialog(clipboard: String, preview: AngConfigManager.ImportPreview) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_clipboard_title)
            .setMessage(buildImportClipboardMessage(preview))
            .setPositiveButton(R.string.action_import) { _, _ ->
                importBatchConfig(clipboard)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }

                        countSub > 0 -> setupGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
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
    fun importConfigViaSub(): Boolean {
        showLoading()

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
                    refreshGroupTabTitles()
                }
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        MaterialAlertDialogBuilder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delDuplicateConfig() {
        MaterialAlertDialogBuilder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delInvalidConfig() {
        MaterialAlertDialogBuilder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
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

    /**
     * Locates and scrolls to the currently selected server.
     * If the selected server is in a different group, automatically switches to that group first.
     */
    private fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
        if (targetGroupIndex < 0) {
            toast(R.string.toast_server_not_found_in_group)
            return
        }

        // Switch to target group if needed, then scroll to the server
        if (binding.viewPager.currentItem != targetGroupIndex) {
            binding.viewPager.setCurrentItem(targetGroupIndex, true)
            binding.viewPager.postDelayed({ scrollToSelectedServer(targetGroupIndex) }, 1000)
        } else {
            scrollToSelectedServer(targetGroupIndex)
        }
    }

    /**
     * Scrolls to the selected server in the specified fragment.
     * @param groupIndex The index of the group/fragment to scroll in
     */
    private fun scrollToSelectedServer(groupIndex: Int) {
        val itemId = groupPagerAdapter.getItemId(groupIndex)
        val tag = "f$itemId"
        val fragment = supportFragmentManager.findFragmentByTag(tag)

        if (fragment?.isAdded == true && fragment.view != null) {
            when (fragment) {
                is AllServerFragment -> fragment.scrollToSelectedServer()
                is GroupServerFragment -> fragment.scrollToSelectedServer()
                else -> toast(R.string.toast_fragment_not_available)
            }
        } else {
            toast(R.string.toast_fragment_not_available)
        }
    }

    private fun handleDrawerNavigation(itemId: Int) {
        if (itemId == R.id.mode_selector) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            showModeSelector()
            return
        }
        val intent = when (itemId) {
            R.id.sub_setting -> Intent(this, SubSettingActivity::class.java)
            R.id.per_app_proxy_settings -> Intent(this, PerAppProxyActivity::class.java)
            R.id.routing_setting -> Intent(this, RoutingSettingActivity::class.java)
            R.id.settings -> Intent(this, SettingsActivity::class.java)
            R.id.logcat -> Intent(this, LogcatActivity::class.java)
            R.id.backup_restore -> Intent(this, BackupActivity::class.java)
            R.id.about -> Intent(this, AboutActivity::class.java)
            R.id.kill_app -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                CoreServiceManager.stopVService(this)
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                return
            }
            else -> null
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)

        intent?.let {
            if (itemId == R.id.sub_setting || itemId == R.id.settings || itemId == R.id.backup_restore) {
                requestActivityLauncher.launchWithMaterialTransition(this, it)
            } else {
                startActivityWithMaterialTransition(it)
            }
        }
    }

    private fun showModeSelector() {
        val entries = resources.getStringArray(R.array.mode_entries)
        val values = resources.getStringArray(R.array.mode_value)
        val current = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE, AppConfig.VPN)
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_mode)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                val newValue = values[which]
                MmkvManager.encodeSettings(AppConfig.PREF_MODE, newValue)
                SettingsChangeManager.makeRestartService()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        bottomBarState = bottomBarState.copy(tunEnabled = tunOn)
    }

    private fun setTunButtonVisible(visible: Boolean) {
        bottomBarState = bottomBarState.copy(showTun = visible)
    }

    override fun onDestroy() {
        tabMediator?.detach()
        uptimeJob?.cancel()
        super.onDestroy()
    }
}
