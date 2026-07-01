package com.v2ray.ang.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.databinding.MainUiBigButtonBinding
import com.v2ray.ang.databinding.MainUiClassicBinding
import com.v2ray.ang.databinding.MainUiExpressiveBinding
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
import com.v2ray.ang.handler.SettingsManager.MainUiMode
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.service.CoreTunToggleService
import com.v2ray.ang.util.LogUtil
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

    private var expressiveBinding: MainUiExpressiveBinding? = null
    private var bigButtonBinding: MainUiBigButtonBinding? = null
    private var classicBinding: MainUiClassicBinding? = null

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private var connectedAt: Long = 0L
    private var uptimeJob: Job? = null
    private var currentUiMode: MainUiMode = MainUiMode.EXPRESSIVE
    private var toolbarAtTop = false

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

        // setup viewpager and tablayout
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true

        // setup navigation drawer
        setupNavigationDrawer()

        applyMainUiMode()

        setupGroupTab()
        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        setupDrawerMenu()

        applyAppFont()

        val drawerBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerBackCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerBackCallback.isEnabled = false
            }
        })
    }

    private fun setupDrawerMenu() {
        val entries = listOf(
            DrawerEntry.Header(R.string.title_drawer_section_main),
            DrawerEntry.Item(R.id.sub_setting, R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
            DrawerEntry.Item(R.id.per_app_proxy_settings, R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
            DrawerEntry.Item(R.id.routing_setting, R.drawable.ic_routing_24dp, R.string.routing_settings_title),
            DrawerEntry.Item(R.id.user_asset_setting, R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
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

    /**
     * Inflates the UI controls that depend on the selected main screen layout mode.
     * The mode is read from MMKV and can be changed from Settings -> UI.
     */
    private fun applyMainUiMode() {
        val newMode = SettingsManager.getMainUiMode()
        val newToolbarAtTop = SettingsManager.isToolbarAtTop()
        if (newMode == currentUiMode && toolbarAtTop == newToolbarAtTop &&
            (expressiveBinding != null || bigButtonBinding != null || classicBinding != null)
        ) {
            return
        }
        currentUiMode = newMode
        toolbarAtTop = newToolbarAtTop

        // Clean up previous bindings if the mode is being reapplied
        expressiveBinding = null
        bigButtonBinding = null
        classicBinding = null
        binding.topControlContainer.removeAllViews()
        binding.bottomControlContainer.removeAllViews()

        when (newMode) {
            MainUiMode.EXPRESSIVE -> bindExpressiveUi()
            MainUiMode.CLASSIC -> bindClassicUi()
            MainUiMode.BIG_BUTTON -> bindBigButtonUi()
        }

        // Restore the current running state so the new controls reflect reality.
        applyRunningState(false, mainViewModel.isRunning.value == true)
    }

    private fun bindExpressiveUi() {
        val container = if (toolbarAtTop) binding.topControlContainer else binding.bottomControlContainer
        expressiveBinding = MainUiExpressiveBinding.inflate(layoutInflater, container, true)

        expressiveBinding?.apply {
            val startTranslation = if (toolbarAtTop) -64f else 64f
            bottomBar.alpha = 0f
            bottomBar.translationY = startTranslation
            bottomBar.scaleX = 0.96f
            bottomBar.scaleY = 0.96f
            springAnimate(bottomBar, SpringAnimation.ALPHA, 1f)
            springAnimate(bottomBar, SpringAnimation.TRANSLATION_Y, 0f)
            springAnimate(bottomBar, SpringAnimation.SCALE_X, 1f)
            springAnimate(bottomBar, SpringAnimation.SCALE_Y, 1f)

            // Adjust padding for top position
            if (toolbarAtTop) {
                val px12 = (12 * resources.displayMetrics.density).toInt()
                bottomBar.setPadding(bottomBar.paddingLeft, px12, bottomBar.paddingRight, 0)
            }

            applyPressFeedback(btnFab, 0.92f)
            applyPressFeedback(btnTunToggle, 0.88f)
            applyPressFeedback(layoutTest, 0.98f)

            btnFab.setOnClickListener {
                it.performMediumHapticFeedback()
                handleFabAction()
            }
            btnTunToggle.setOnClickListener {
                it.performMediumHapticFeedback()
                handleTunToggle()
            }
            layoutTest.setOnClickListener {
                it.performLightHapticFeedback()
                handleLayoutTestClick()
            }
            layoutTest.setOnLongClickListener {
                it.performMediumHapticFeedback()
                showConnectionInfoSheet()
                true
            }
        }
    }

    private fun bindBigButtonUi() {
        bigButtonBinding = MainUiBigButtonBinding.inflate(layoutInflater, binding.topControlContainer, true)
        bigButtonBinding?.apply {
            bigButtonContainer.alpha = 0f
            bigButtonContainer.scaleX = 0.96f
            bigButtonContainer.scaleY = 0.96f
            bigButtonContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            applyPressFeedback(btnBigConnect, 0.92f)
            applyPressFeedback(layoutBigTun, 0.97f)

            btnBigConnect.setOnClickListener {
                it.performMediumHapticFeedback()
                handleFabAction()
            }
            layoutBigTun.setOnClickListener {
                it.performMediumHapticFeedback()
                handleTunToggle()
            }
        }
    }

    private fun bindClassicUi() {
        classicBinding = MainUiClassicBinding.inflate(layoutInflater, binding.bottomControlContainer, true)
        classicBinding?.apply {
            bottomBar.alpha = 0f
            bottomBar.translationY = 80f
            btnFab.alpha = 0f
            btnFab.scaleX = 0.5f
            btnFab.scaleY = 0.5f

            bottomBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            btnFab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setStartDelay(100)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()

            applyPressFeedback(btnFab, 0.9f)
            applyPressFeedback(btnClassicServers, 0.85f)
            applyPressFeedback(btnClassicLogs, 0.85f)
            applyPressFeedback(btnClassicRouting, 0.85f)
            applyPressFeedback(btnClassicSettings, 0.85f)

            btnFab.setOnClickListener {
                it.performMediumHapticFeedback()
                handleFabAction()
            }
            btnClassicServers.setOnClickListener {
                it.performLightHapticFeedback()
                handleDrawerNavigation(R.id.sub_setting)
            }
            btnClassicLogs.setOnClickListener {
                it.performLightHapticFeedback()
                startActivityWithMaterialTransition(Intent(this@MainActivity, LogcatActivity::class.java))
            }
            btnClassicRouting.setOnClickListener {
                it.performLightHapticFeedback()
                startActivityWithMaterialTransition(Intent(this@MainActivity, RoutingSettingActivity::class.java))
            }
            btnClassicSettings.setOnClickListener {
                it.performLightHapticFeedback()
                requestActivityLauncher.launchWithMaterialTransition(this@MainActivity, Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    private fun applyPressFeedback(view: View, scale: Float) {
        val card = view as? com.google.android.material.card.MaterialCardView
        val baseElevation = card?.elevation ?: view.translationZ
        val pressElevation = baseElevation * 0.35f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springAnimate(v, SpringAnimation.SCALE_X, scale, SpringForce.STIFFNESS_HIGH)
                    springAnimate(v, SpringAnimation.SCALE_Y, scale, SpringForce.STIFFNESS_HIGH)
                    springAnimate(v, SpringAnimation.TRANSLATION_Z, pressElevation, SpringForce.STIFFNESS_HIGH)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    springAnimate(v, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                    springAnimate(v, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                    springAnimate(v, SpringAnimation.TRANSLATION_Z, baseElevation, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                }
            }
            false
        }
    }

    /**
     * Applies the app font to the navigation header title.
     * When the "use Google Sans" preference is on (default), the bundled
     * Google Sans Flex typeface is used. Otherwise the device default
     * typeface is applied.
     */
    private fun applyAppFont() {
        val useGoogleSans = MmkvManager.decodeSettingsBool(AppConfig.PREF_GOOGLE_SANS, true)
        val headerTitle = findViewById<android.widget.TextView>(R.id.tv_app_name) ?: return
        headerTitle.typeface = if (useGoogleSans) {
            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.google_sans_flex)
        } else {
            resolveSystemTypeface()
        }
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
        }
        mainViewModel.connectionPing.observe(this) { ping ->
            if (!ping.isNullOrEmpty()) {
                setTestStateText(ping)
            }
        }
        mainViewModel.connectionIp.observe(this) { ip ->
            setConnectionIpText(ip)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setTestStateText(content: String?) {
        expressiveBinding?.tvTestState?.text = content
        bigButtonBinding?.tvBigStatus?.text = content
    }

    private fun setSpeedText(up: String, down: String) {
        expressiveBinding?.tvSpeedUp?.text = "↑ $up"
        expressiveBinding?.tvSpeedDown?.text = "↓ $down"
    }

    private fun setConnectionIpText(ip: String?) {
        val compressedIp = com.v2ray.ang.util.IPv6Util.compressIPv6(ip, maxDisplayLength = 26)
        expressiveBinding?.tvConnectionIp?.text = compressedIp
        expressiveBinding?.tvConnectionIp?.isVisible = !compressedIp.isNullOrEmpty()
        bigButtonBinding?.tvBigIp?.text = getString(R.string.connection_ip_format, compressedIp.orEmpty())
        bigButtonBinding?.tvBigIp?.isVisible = !compressedIp.isNullOrEmpty()
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)

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
            return
        }
        CoreServiceManager.startVService(this)
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
                expressiveBinding?.tvUptime?.text = uptimeText
                delay(1000)
            }
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        applyRunningStateToExpressive(isLoading, isRunning)
        applyRunningStateToBigButton(isLoading, isRunning)
        applyRunningStateToClassic(isLoading, isRunning)

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

    private fun applyRunningStateToExpressive(isLoading: Boolean, isRunning: Boolean) {
        val eb = expressiveBinding ?: return
        if (isLoading) {
            eb.layoutSpeed.isVisible = false
            eb.tvConnectionIp.isVisible = false
            eb.ivFabIcon.setImageDrawable(null)
            eb.loadingIndicator.show()
            eb.btnTunToggle.isVisible = false
            return
        }

        eb.loadingIndicator.hide()
        if (isRunning) {
            eb.ivFabIcon.setImageResource(R.drawable.ic_stop_outline_24dp)
            animateCardColor(
                eb.btnFab,
                MaterialColors.getColor(eb.btnFab, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                MaterialColors.getColor(eb.btnFab, android.R.attr.colorPrimary)
            )
            eb.ivFabIcon.setColorFilter(
                MaterialColors.getColor(eb.ivFabIcon, com.google.android.material.R.attr.colorOnPrimary),
                PorterDuff.Mode.SRC_IN
            )
            eb.btnFab.contentDescription = getString(R.string.action_stop_service)
            eb.layoutTest.isFocusable = true
            eb.layoutSpeed.isVisible = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_TOOLBAR_ENABLED) == true
        } else {
            eb.ivFabIcon.setImageResource(R.drawable.ic_play_outline_24dp)
            animateCardColor(
                eb.btnFab,
                MaterialColors.getColor(eb.btnFab, android.R.attr.colorPrimary),
                MaterialColors.getColor(eb.btnFab, com.google.android.material.R.attr.colorSurfaceContainerHighest)
            )
            eb.ivFabIcon.setColorFilter(
                MaterialColors.getColor(eb.ivFabIcon, com.google.android.material.R.attr.colorOnSurfaceVariant),
                PorterDuff.Mode.SRC_IN
            )
            eb.btnFab.contentDescription = getString(R.string.tasker_start_service)
            eb.layoutTest.isFocusable = false
            eb.layoutSpeed.isVisible = false
            eb.tvConnectionIp.isVisible = false
        }
    }

    private fun showConnectionInfoSheet() {
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_connection_info, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetView)

        val tvIp = sheetView.findViewById<TextView>(R.id.tv_sheet_ip)
        val tvLocation = sheetView.findViewById<TextView>(R.id.tv_sheet_location)
        val tvIsp = sheetView.findViewById<TextView>(R.id.tv_sheet_isp)
        val tvDown = sheetView.findViewById<TextView>(R.id.tv_sheet_down)
        val tvUp = sheetView.findViewById<TextView>(R.id.tv_sheet_up)

        val speed = mainViewModel.netSpeed.value
        tvDown.text = speed?.let { "↓ ${it.second.toSpeedString()}" } ?: "↓ 0 B/s"
        tvUp.text = speed?.let { "↑ ${it.first.toSpeedString()}" } ?: "↑ 0 B/s"

        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                SpeedtestManager.getRemoteIPDetail()
            }
            detail?.let { info ->
                val ip = listOf(info.ip, info.clientIp, info.ip_addr, info.query)
                    .firstOrNull { !it.isNullOrBlank() } ?: "-"
                val location = listOfNotNull(info.city, info.region, info.country_name)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() } ?: "-"
                val isp = listOf(info.isp, info.organization, info.asn)
                    .firstOrNull { !it.isNullOrBlank() } ?: "-"
                tvIp.text = ip
                tvLocation.text = location
                tvIsp.text = isp
            } ?: run {
                tvIp.text = "-"
                tvLocation.text = "-"
                tvIsp.text = "-"
            }
        }

        dialog.show()
    }

    private fun applyRunningStateToBigButton(isLoading: Boolean, isRunning: Boolean) {
        val bb = bigButtonBinding ?: return
        bb.loadingIndicator.isVisible = isLoading
        if (isLoading) {
            bb.ivBigIcon.setImageDrawable(null)
            animateCardColor(
                bb.btnBigConnect,
                MaterialColors.getColor(bb.btnBigConnect, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                MaterialColors.getColor(bb.btnBigConnect, com.google.android.material.R.attr.colorPrimaryContainer)
            )
            bb.layoutBigTun.isVisible = false
            bb.tvBigIp.isVisible = false
            return
        }

        if (isRunning) {
            bb.ivBigIcon.setImageResource(R.drawable.ic_stop_outline_24dp)
            animateCardColor(
                bb.btnBigConnect,
                MaterialColors.getColor(bb.btnBigConnect, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                MaterialColors.getColor(bb.btnBigConnect, com.google.android.material.R.attr.colorPrimaryContainer)
            )
            animateIconColor(
                bb.ivBigIcon,
                MaterialColors.getColor(bb.ivBigIcon, com.google.android.material.R.attr.colorOnSurfaceVariant),
                MaterialColors.getColor(bb.ivBigIcon, com.google.android.material.R.attr.colorOnPrimaryContainer)
            )
            bb.btnBigConnect.contentDescription = getString(R.string.action_stop_service)
            bb.layoutBigTun.isVisible = SettingsManager.isProxyTunMode()
        } else {
            bb.ivBigIcon.setImageResource(R.drawable.ic_play_outline_24dp)
            animateCardColor(
                bb.btnBigConnect,
                MaterialColors.getColor(bb.btnBigConnect, com.google.android.material.R.attr.colorPrimaryContainer),
                MaterialColors.getColor(bb.btnBigConnect, com.google.android.material.R.attr.colorSurfaceContainerHighest)
            )
            animateIconColor(
                bb.ivBigIcon,
                MaterialColors.getColor(bb.ivBigIcon, com.google.android.material.R.attr.colorOnPrimaryContainer),
                MaterialColors.getColor(bb.ivBigIcon, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            bb.btnBigConnect.contentDescription = getString(R.string.tasker_start_service)
            bb.layoutBigTun.isVisible = false
            bb.tvBigIp.isVisible = false
        }
    }

    private fun applyRunningStateToClassic(isLoading: Boolean, isRunning: Boolean) {
        val cb = classicBinding ?: return
        if (isLoading) {
            cb.ivFabIcon.setImageDrawable(null)
            animateCardColor(
                cb.btnFab,
                MaterialColors.getColor(cb.btnFab, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                MaterialColors.getColor(cb.btnFab, com.google.android.material.R.attr.colorPrimaryContainer)
            )
            return
        }

        if (isRunning) {
            cb.ivFabIcon.setImageResource(R.drawable.ic_stop_outline_24dp)
            animateIconColor(
                cb.ivFabIcon,
                MaterialColors.getColor(cb.ivFabIcon, com.google.android.material.R.attr.colorOnSurfaceVariant),
                MaterialColors.getColor(cb.ivFabIcon, com.google.android.material.R.attr.colorOnPrimaryContainer)
            )
            animateCardColor(
                cb.btnFab,
                MaterialColors.getColor(cb.btnFab, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                MaterialColors.getColor(cb.btnFab, com.google.android.material.R.attr.colorPrimaryContainer)
            )
            cb.btnFab.contentDescription = getString(R.string.action_stop_service)
        } else {
            cb.ivFabIcon.setImageResource(R.drawable.ic_play_outline_24dp)
            animateIconColor(
                cb.ivFabIcon,
                MaterialColors.getColor(cb.ivFabIcon, com.google.android.material.R.attr.colorOnPrimaryContainer),
                MaterialColors.getColor(cb.ivFabIcon, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            animateCardColor(
                cb.btnFab,
                MaterialColors.getColor(cb.btnFab, com.google.android.material.R.attr.colorPrimaryContainer),
                MaterialColors.getColor(cb.btnFab, com.google.android.material.R.attr.colorSurfaceContainerHighest)
            )
            cb.btnFab.contentDescription = getString(R.string.tasker_start_service)
        }
    }

    override fun onResume() {
        super.onResume()
        applyMainUiMode()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    mainViewModel.filterConfig(newText.orEmpty())
                    return false
                }
            })

            searchView.setOnCloseListener {
                mainViewModel.filterConfig("")
                false
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }

        R.id.import_manually_proxy_chain -> {
            importManually(EConfigType.PROXYCHAIN.value)
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }

        R.id.import_manually_olcrtc -> {
            importManually(EConfigType.OLCRTC.value)
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

    private fun importManually(createConfigType: Int) {
        val intent = if (createConfigType == EConfigType.POLICYGROUP.value) {
            Intent()
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerGroupActivity::class.java)
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            Intent()
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerProxyChainActivity::class.java)
        } else if (createConfigType == EConfigType.OLCRTC.value) {
            Intent()
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, OlcrtcActivity::class.java)
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
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
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
        val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment

        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.scrollToSelectedServer()
        } else {
            toast(R.string.toast_fragment_not_available)
        }
    }

    private fun handleDrawerNavigation(itemId: Int) {
        val intent = when (itemId) {
            R.id.sub_setting -> Intent(this, SubSettingActivity::class.java)
            R.id.per_app_proxy_settings -> Intent(this, PerAppProxyActivity::class.java)
            R.id.routing_setting -> Intent(this, RoutingSettingActivity::class.java)
            R.id.user_asset_setting -> Intent(this, UserAssetActivity::class.java)
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
        updateExpressiveTunState(tunOn)
        updateBigButtonTunState(tunOn)
    }

    private fun updateExpressiveTunState(tunOn: Boolean) {
        val eb = expressiveBinding ?: return
        if (tunOn) {
            eb.ivTunIcon.setImageResource(R.drawable.ic_tun_on_24dp)
            eb.btnTunToggle.setCardBackgroundColor(
                MaterialColors.getColor(eb.btnTunToggle, com.google.android.material.R.attr.colorPrimaryContainer)
            )
            eb.ivTunIcon.setColorFilter(
                MaterialColors.getColor(eb.ivTunIcon, com.google.android.material.R.attr.colorOnPrimaryContainer),
                PorterDuff.Mode.SRC_IN
            )
            eb.btnTunToggle.contentDescription = getString(R.string.title_tun_enabled)
        } else {
            eb.ivTunIcon.setImageResource(R.drawable.ic_tun_off_24dp)
            eb.btnTunToggle.setCardBackgroundColor(
                MaterialColors.getColor(eb.btnTunToggle, com.google.android.material.R.attr.colorSurfaceContainerHighest)
            )
            eb.ivTunIcon.setColorFilter(
                MaterialColors.getColor(eb.ivTunIcon, com.google.android.material.R.attr.colorOnSurfaceVariant),
                PorterDuff.Mode.SRC_IN
            )
            eb.btnTunToggle.contentDescription = getString(R.string.title_tun_disabled)
        }
    }

    private fun updateBigButtonTunState(tunOn: Boolean) {
        val bb = bigButtonBinding ?: return
        bb.switchBigTun.isChecked = tunOn
        bb.tvBigTunStatus.text = getString(if (tunOn) R.string.title_tun_enabled else R.string.title_tun_disabled)
        bb.tvBigTunStatus.setTextColor(
            MaterialColors.getColor(
                bb.tvBigTunStatus,
                if (tunOn) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorOnSurfaceVariant
            )
        )
    }

    private fun setTunButtonVisible(visible: Boolean) {
        expressiveBinding?.let { setExpressiveTunButtonVisible(it.btnTunToggle, visible) }
        bigButtonBinding?.let { bb ->
            if (visible) {
                bb.layoutBigTun.isVisible = true
                bb.layoutBigTun.alpha = 0f
                bb.layoutBigTun.animate().alpha(1f).setDuration(250).start()
            } else {
                bb.layoutBigTun.animate().alpha(0f).setDuration(200)
                    .withEndAction { bb.layoutBigTun.isVisible = false }
                    .start()
            }
        }
    }

    private fun setExpressiveTunButtonVisible(button: com.google.android.material.card.MaterialCardView, visible: Boolean) {
        if (visible && !button.isVisible) {
            button.scaleX = 0f
            button.scaleY = 0f
            button.alpha = 0f
            button.isVisible = true
            springAnimate(button, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
            springAnimate(button, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
            springAnimate(button, SpringAnimation.ALPHA, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
        } else if (!visible && button.isVisible) {
            SpringAnimation(button, SpringAnimation.SCALE_X, 0f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                start()
            }
            SpringAnimation(button, SpringAnimation.SCALE_Y, 0f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                start()
            }
            SpringAnimation(button, SpringAnimation.ALPHA, 0f).apply {
                spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                addEndListener { _, _, _, _ -> button.isVisible = false }
                start()
            }
        }
    }

    private fun springAnimate(
        view: View,
        property: DynamicAnimation.ViewProperty,
        toValue: Float,
        stiffness: Float = SpringForce.STIFFNESS_MEDIUM,
        dampingRatio: Float = SpringForce.DAMPING_RATIO_NO_BOUNCY
    ) {
        SpringAnimation(view, property, toValue).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = dampingRatio
            start()
        }
    }

    private fun animateCardColor(
        card: com.google.android.material.card.MaterialCardView,
        fromColor: Int,
        toColor: Int
    ) {
        val prop = object : FloatPropertyCompat<com.google.android.material.card.MaterialCardView>("cardColor") {
            override fun setValue(obj: com.google.android.material.card.MaterialCardView, value: Float) {
                obj.setCardBackgroundColor(android.animation.ArgbEvaluator().evaluate(value, fromColor, toColor) as Int)
            }
            override fun getValue(obj: com.google.android.material.card.MaterialCardView): Float = 0f
        }
        SpringAnimation(card, prop, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            start()
        }
    }

    private fun animateIconColor(imageView: ImageView, fromColor: Int, toColor: Int) {
        val prop = object : FloatPropertyCompat<ImageView>("iconColor") {
            override fun setValue(obj: ImageView, value: Float) {
                obj.setColorFilter(android.animation.ArgbEvaluator().evaluate(value, fromColor, toColor) as Int, PorterDuff.Mode.SRC_IN)
            }
            override fun getValue(obj: ImageView): Float = 0f
        }
        SpringAnimation(imageView, prop, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            start()
        }
    }

    override fun onDestroy() {
        tabMediator?.detach()
        uptimeJob?.cancel()
        super.onDestroy()
    }
}