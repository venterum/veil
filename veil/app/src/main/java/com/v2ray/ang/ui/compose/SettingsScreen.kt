package com.v2ray.ang.ui.compose

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.PROXY_TUN
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.AppIconActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch
import kotlin.random.Random

/** True when the [query] is empty or one of the [texts] contains it (case-insensitive). */
private fun visible(query: String, vararg texts: String): Boolean =
    query.isEmpty() || texts.any { it.contains(query, ignoreCase = true) }

/**
 * Small decorative banner at the top of the settings list. It carries the classic
 * Veil ghost mark and a greeting, and doubles as a "nothing found" surface when a
 * search returns no matches. Purely cosmetic.
 */
@Composable
private fun SearchSummary(query: String) {
    Text(
        text = stringResource(R.string.settings_search_results, query),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    state: SettingsState,
    key: String,
    title: String,
    summary: String? = null,
    default: Boolean = false,
    enabled: Boolean = true,
    icon: Painter? = null,
    drawCard: Boolean = false
) {
    SettingsSwitchItem(
        icon = icon,
        title = title,
        summary = summary,
        checked = state.bool(key, default),
        enabled = enabled,
        drawCard = drawCard,
        onCheckedChange = { state.setBool(key, it) }
    )
}

@Composable
private fun TextRow(
    state: SettingsState,
    key: String,
    title: String,
    default: String = "",
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false,
    emptyDescription: String? = null,
    singleLine: Boolean = true,
    drawCard: Boolean = false
) {
    SettingsEditItem(
        title = title,
        value = state.string(key, default),
        enabled = enabled,
        isPassword = isPassword,
        keyboardNumber = keyboardNumber,
        emptyDescription = emptyDescription,
        singleLine = singleLine,
        drawCard = drawCard,
        onValueChanged = { state.setString(key, it) }
    )
}

@Composable
private fun ListRow(
    state: SettingsState,
    key: String,
    title: String,
    entries: List<String>,
    values: List<String>,
    default: String = "",
    enabled: Boolean = true,
    drawCard: Boolean = false
) {
    SettingsListItem(
        title = title,
        entries = entries,
        values = values,
        selectedValue = state.string(key, default),
        enabled = enabled,
        drawCard = drawCard,
        onSelected = { state.setString(key, it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberSettingsState()
    val listState = rememberLazyListState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val query = searchQuery.trim()

    // Legacy normalization: when HEV TUN is on, local proxy is forced on.
    LaunchedEffect(Unit) {
        if (state.bool(AppConfig.PREF_USE_HEV_TUNNEL, true) &&
            !state.bool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
        ) {
            state.setBoolSilently(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
        }
    }

    val requestRoot: (() -> Unit) -> Unit = { onGranted ->
        if (RootManager.cachedRoot()) {
            onGranted()
        } else {
            scope.launch {
                if (RootManager.refresh()) onGranted()
                else context.toastError(R.string.toast_root_required)
            }
        }
    }

    val generateCredentials = {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        fun random(len: Int) = buildString {
            repeat(len) { append(charset[Random.nextInt(charset.length)]) }
        }
        state.setString(AppConfig.PREF_SOCKS_USERNAME, random(8))
        state.setString(AppConfig.PREF_SOCKS_PASSWORD, random(16))
        context.toast(R.string.toast_socks_credentials_generated)
    }

    val openTelegramProxy = {
        val port = state.string(AppConfig.PREF_SOCKS_PORT, AppConfig.PORT_SOCKS)
        val user = state.string(AppConfig.PREF_SOCKS_USERNAME)
        val pass = state.string(AppConfig.PREF_SOCKS_PASSWORD)
        val sb = StringBuilder("tg://socks?server=${AppConfig.LOOPBACK}&port=$port")
        if (user.isNotEmpty()) sb.append("&user=$user")
        if (pass.isNotEmpty()) sb.append("&pass=$pass")
        Utils.openUri(context, sb.toString())
    }

    val openAppIcon = {
        context.startActivity(Intent(context, AppIconActivity::class.java))
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBackClick,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = {
                    isSearchActive = false
                    searchQuery = ""
                },
                searchPlaceholder = stringResource(R.string.hint_search),
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.hint_search)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(listState)
        ) {
            if (query.length >= 2) {
                item { SearchSummary(query) }
            }
            item { UiSettingsSection(state, query, openAppIcon) }
            item { VpnSettingsSection(state, query) }
            item { CoreSettingsSection(state, query, generateCredentials, openTelegramProxy) }
            item { MuxSettingsSection(state, query) }
            item { FragmentSettingsSection(state, query) }
            item { AdvancedSettingsSection(state, query) }
            item { ModeSettingsSection(state, query, requestRoot) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun UiSettingsSection(
    state: SettingsState,
    query: String,
    onOpenAppIcon: () -> Unit
) {
    val sectionTitle = stringResource(R.string.title_ui_settings)

    val tSpeed = stringResource(R.string.title_pref_speed_enabled)
    val sSpeed = stringResource(R.string.summary_pref_speed_enabled)
    val tSpeedToolbar = stringResource(R.string.title_pref_speed_toolbar_enabled)
    val sSpeedToolbar = stringResource(R.string.summary_pref_speed_toolbar_enabled)
    val tSpeedStatusLive = stringResource(R.string.title_pref_speed_status_bar_live)
    val sSpeedStatusLive = stringResource(R.string.summary_pref_speed_status_bar_live)
    val tSpeedLock = stringResource(R.string.title_pref_speed_lock_screen)
    val sSpeedLock = stringResource(R.string.summary_pref_speed_lock_screen)
    val tConfirmRemove = stringResource(R.string.title_pref_confirm_remove)
    val sConfirmRemove = stringResource(R.string.summary_pref_confirm_remove)
    val tStartScan = stringResource(R.string.title_pref_start_scan_immediate)
    val sStartScan = stringResource(R.string.summary_pref_start_scan_immediate)
    val tCardStyle = stringResource(R.string.title_pref_server_card_style)
    val sCardStyle = stringResource(R.string.summary_pref_server_card_style)
    val tEmojiAvatar = stringResource(R.string.title_pref_server_emoji_avatar)
    val sEmojiAvatar = stringResource(R.string.summary_pref_server_emoji_avatar)
    val tSearchButton = stringResource(R.string.title_pref_server_search_button)
    val sSearchButton = stringResource(R.string.summary_pref_server_search_button)
    val tQuickTest = stringResource(R.string.title_pref_quick_action_test_all)
    val sQuickTest = stringResource(R.string.summary_pref_quick_action_test_all)
    val tQuickSort = stringResource(R.string.title_pref_quick_action_sort)
    val sQuickSort = stringResource(R.string.summary_pref_quick_action_sort)
    val tQuickUpdate = stringResource(R.string.title_pref_quick_action_update)
    val sQuickUpdate = stringResource(R.string.summary_pref_quick_action_update)
    val tUnsupportedPing = stringResource(R.string.title_pref_unsupported_ping_pill)
    val sUnsupportedPing = stringResource(R.string.summary_pref_unsupported_ping_pill)
    val tBolderFont = stringResource(R.string.title_pref_main_bolder_font)
    val sBolderFont = stringResource(R.string.summary_pref_main_bolder_font)
    val tGroupAll = stringResource(R.string.title_pref_group_all_display)
    val sGroupAll = stringResource(R.string.summary_pref_group_all_display)
    val tGroupAllMode = stringResource(R.string.title_pref_group_all_mode)
    val tGroupAllIcons = stringResource(R.string.title_pref_group_all_icons_visible)
    val sGroupAllIcons = stringResource(R.string.summary_pref_group_all_icons_visible)
    val tLanguage = stringResource(R.string.title_language)
    val tNight = stringResource(R.string.title_pref_ui_mode_night)
    val tDynamicColor = stringResource(R.string.title_pref_dynamic_color)
    val sDynamicColor = stringResource(R.string.summary_pref_dynamic_color)
    val tAmoled = stringResource(R.string.title_pref_amoled_theme)
    val sAmoled = stringResource(R.string.summary_pref_amoled_theme)
    val sAmoledLight = stringResource(R.string.summary_pref_amoled_theme_light)
    val isDarkTheme = isSystemInDarkTheme()
    val tGoogleSans = stringResource(R.string.title_pref_google_sans)
    val sGoogleSans = stringResource(R.string.summary_pref_google_sans)
    val tAppIcon = stringResource(R.string.title_app_icon)
    val sAppIcon = stringResource(R.string.summary_app_icon)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    val speedStatusLive = state.bool(AppConfig.PREF_SPEED_STATUS_BAR_LIVE)
    val cardStyle = state.bool(AppConfig.PREF_SERVER_CARD_STYLE, true)
    val groupAll = state.bool(AppConfig.PREF_GROUP_ALL_DISPLAY)
    val groupAllMode = state.string(AppConfig.PREF_GROUP_ALL_MODE, "mode1")
    val isEnglish = com.v2ray.ang.handler.SettingsManager.getLocale().language == "en"

    val sectionVisible = show(tSpeed, sSpeed) || show(tSpeedToolbar, sSpeedToolbar) ||
        show(tSpeedStatusLive, sSpeedStatusLive) || show(tSpeedLock, sSpeedLock) ||
        show(tConfirmRemove, sConfirmRemove) || show(tStartScan, sStartScan) ||
        show(tCardStyle, sCardStyle) || show(tEmojiAvatar, sEmojiAvatar) ||
        show(tSearchButton, sSearchButton) ||
        show(tQuickTest, sQuickTest) || show(tQuickSort, sQuickSort) ||
        show(tQuickUpdate, sQuickUpdate) || show(tUnsupportedPing, sUnsupportedPing) ||
        show(tBolderFont, sBolderFont) || show(tGroupAll, sGroupAll) ||
        show(tGroupAllMode) || (show(tGroupAllIcons, sGroupAllIcons) && groupAllMode == "mode1") ||
        show(tLanguage) || show(tNight) || show(tDynamicColor, sDynamicColor) ||
        show(tAmoled, sAmoled) || show(tGoogleSans, sGoogleSans) || show(tAppIcon, sAppIcon)
    if (!sectionVisible) return

    val groupAllModeEntries = stringArrayResource(R.array.group_all_mode_entries).toList()
    val groupAllModeValues = stringArrayResource(R.array.group_all_mode_values).toList()
    val languageEntries = stringArrayResource(R.array.language_select).toList()
    val languageValues = stringArrayResource(R.array.language_select_value).toList()
    val nightEntries = stringArrayResource(R.array.ui_mode_night).toList()
    val nightValues = stringArrayResource(R.array.ui_mode_night_value).toList()

    PreferenceGroupHeader(title = sectionTitle)
    SettingsSection {
        if (show(tSpeed, sSpeed)) {
            SettingsSwitchRow(state, AppConfig.PREF_SPEED_ENABLED, tSpeed, sSpeed)
        }
        if (show(tSpeedToolbar, sSpeedToolbar)) {
            SettingsSwitchRow(state, AppConfig.PREF_SPEED_TOOLBAR_ENABLED, tSpeedToolbar, sSpeedToolbar)
        }
        if (show(tSpeedStatusLive, sSpeedStatusLive)) {
            SettingsSwitchRow(state, AppConfig.PREF_SPEED_STATUS_BAR_LIVE, tSpeedStatusLive, sSpeedStatusLive)
        }
        if (show(tSpeedLock, sSpeedLock)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_SPEED_LOCK_SCREEN, tSpeedLock, sSpeedLock,
                enabled = speedStatusLive
            )
        }
        if (show(tConfirmRemove, sConfirmRemove)) {
            SettingsSwitchRow(state, AppConfig.PREF_CONFIRM_REMOVE, tConfirmRemove, sConfirmRemove)
        }
        if (show(tStartScan, sStartScan)) {
            SettingsSwitchRow(state, AppConfig.PREF_START_SCAN_IMMEDIATE, tStartScan, sStartScan)
        }
        if (show(tCardStyle, sCardStyle)) {
            SettingsSwitchRow(state, AppConfig.PREF_SERVER_CARD_STYLE, tCardStyle, sCardStyle, default = true)
        }
        if (show(tEmojiAvatar, sEmojiAvatar)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_SERVER_EMOJI_AVATAR, tEmojiAvatar, sEmojiAvatar,
                default = true, enabled = cardStyle
            )
        }
        if (show(tUnsupportedPing, sUnsupportedPing)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_UNSUPPORTED_PING_PILL, tUnsupportedPing, sUnsupportedPing,
                default = true
            )
        }
        if (show(tSearchButton, sSearchButton)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_SERVER_SEARCH_BUTTON_ENABLED, tSearchButton, sSearchButton,
                default = true
            )
        }
        if (show(tQuickTest, sQuickTest)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_QUICK_ACTION_TEST_ALL, tQuickTest, sQuickTest, default = true
            )
        }
        if (show(tQuickSort, sQuickSort)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_QUICK_ACTION_SORT, tQuickSort, sQuickSort, default = true
            )
        }
        if (show(tQuickUpdate, sQuickUpdate)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_QUICK_ACTION_UPDATE, tQuickUpdate, sQuickUpdate, default = true
            )
        }
        if (show(tGroupAll, sGroupAll)) {
            SettingsSwitchRow(state, AppConfig.PREF_GROUP_ALL_DISPLAY, tGroupAll, sGroupAll)
        }
        if (show(tGroupAllMode)) {
            ListRow(
                state, AppConfig.PREF_GROUP_ALL_MODE, tGroupAllMode,
                groupAllModeEntries, groupAllModeValues, default = "mode1", enabled = groupAll
            )
        }
        if (show(tGroupAllIcons, sGroupAllIcons) && groupAllMode == "mode1") {
            SettingsSwitchRow(
                state, AppConfig.PREF_GROUP_ALL_ICONS_VISIBLE, tGroupAllIcons, sGroupAllIcons,
                default = true, enabled = groupAll
            )
        }
        if (show(tLanguage)) {
            ListRow(state, AppConfig.PREF_LANGUAGE, tLanguage, languageEntries, languageValues, "auto")
        }
        if (show(tNight)) {
            ListRow(state, AppConfig.PREF_UI_MODE_NIGHT, tNight, nightEntries, nightValues, "0")
        }
        if (show(tDynamicColor, sDynamicColor)) {
            SettingsSwitchRow(state, AppConfig.PREF_DYNAMIC_COLOR, tDynamicColor, sDynamicColor, default = true)
        }
        if (show(tAmoled, sAmoled)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_AMOLED_THEME, tAmoled,
                if (isDarkTheme) sAmoled else sAmoledLight,
                enabled = isDarkTheme
            )
        }
        if (show(tBolderFont, sBolderFont)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_MAIN_BOLDER_FONT, tBolderFont, sBolderFont,
                default = true
            )
        }
        if (show(tGoogleSans, sGoogleSans)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_GOOGLE_SANS, tGoogleSans, sGoogleSans,
                default = true, enabled = isEnglish
            )
        }
        if (show(tAppIcon, sAppIcon)) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_image_24dp),
                title = tAppIcon,
                subtitle = sAppIcon,
                onClick = onOpenAppIcon
            )
        }
    }
}

@Composable
private fun VpnSettingsSection(state: SettingsState, query: String) {
    val sectionTitle = stringResource(R.string.title_vpn_settings)

    val tIpv6 = stringResource(R.string.title_pref_ipv6_enabled)
    val sIpv6 = stringResource(R.string.summary_pref_ipv6_enabled)
    val tPreferIpv6 = stringResource(R.string.title_pref_prefer_ipv6)
    val sPreferIpv6 = stringResource(R.string.summary_pref_prefer_ipv6)
    val tLocalDns = stringResource(R.string.title_pref_local_dns_enabled)
    val sLocalDns = stringResource(R.string.summary_pref_local_dns_enabled)
    val tFakeDns = stringResource(R.string.title_pref_fake_dns_enabled)
    val sFakeDns = stringResource(R.string.summary_pref_fake_dns_enabled)
    val tAppendProxy = stringResource(R.string.title_pref_append_http_proxy)
    val sAppendProxy = stringResource(R.string.summary_pref_append_http_proxy)
    val tVpnDns = stringResource(R.string.title_pref_vpn_dns)
    val tOlcrtcDns = stringResource(R.string.title_pref_olcrtc_dns)
    val sOlcrtcAuto = stringResource(R.string.summary_pref_olcrtc_dns_auto)
    val tBypassLan = stringResource(R.string.title_pref_vpn_bypass_lan)
    val tIfAddr = stringResource(R.string.title_pref_vpn_interface_address)
    val tMtu = stringResource(R.string.title_pref_vpn_mtu)
    val tHevTun = stringResource(R.string.title_pref_use_hev_tunnel)
    val sHevTun = stringResource(R.string.summary_pref_use_hev_tunnel)
    val tHevLog = stringResource(R.string.title_pref_hev_tunnel_loglevel)
    val tHevTimeout = stringResource(R.string.title_pref_hev_tunnel_rw_timeout)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    val sectionVisible = show(tIpv6, sIpv6) || show(tPreferIpv6, sPreferIpv6) ||
        show(tLocalDns, sLocalDns) || show(tFakeDns, sFakeDns) ||
        show(tAppendProxy, sAppendProxy) || show(tVpnDns) || show(tOlcrtcDns) ||
        show(tBypassLan) || show(tIfAddr) || show(tMtu) || show(tHevTun, sHevTun) ||
        show(tHevLog) || show(tHevTimeout)
    if (!sectionVisible) return

    val mode = state.string(AppConfig.PREF_MODE, VPN)
    val isVpn = mode == VPN || mode == PROXY_TUN
    val localDns = state.bool(AppConfig.PREF_LOCAL_DNS_ENABLED)
    val enableLocalProxy = state.bool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
    val hevTun = state.bool(AppConfig.PREF_USE_HEV_TUNNEL, true)

    val bypassEntries = stringArrayResource(R.array.vpn_bypass_lan).toList()
    val bypassValues = stringArrayResource(R.array.vpn_bypass_lan_value).toList()
    val ifAddrEntries = stringArrayResource(R.array.vpn_interface_address).toList()
    val ifAddrValues = stringArrayResource(R.array.vpn_interface_address_value).toList()
    val hevLogLevels = stringArrayResource(R.array.hev_tunnel_loglevel).toList()

    PreferenceGroupHeader(title = sectionTitle)
    SettingsSection {
        if (show(tIpv6, sIpv6)) {
            SettingsSwitchRow(state, AppConfig.PREF_IPV6_ENABLED, tIpv6, sIpv6, enabled = isVpn)
        }
        if (show(tPreferIpv6, sPreferIpv6)) {
            SettingsSwitchRow(state, AppConfig.PREF_PREFER_IPV6, tPreferIpv6, sPreferIpv6, enabled = isVpn)
        }
        if (show(tLocalDns, sLocalDns)) {
            SettingsSwitchRow(state, AppConfig.PREF_LOCAL_DNS_ENABLED, tLocalDns, sLocalDns, enabled = isVpn)
        }
        if (show(tFakeDns, sFakeDns)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_FAKE_DNS_ENABLED, tFakeDns, sFakeDns,
                enabled = isVpn && localDns
            )
        }
        if (show(tAppendProxy, sAppendProxy)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_APPEND_HTTP_PROXY, tAppendProxy, sAppendProxy,
                enabled = enableLocalProxy && mode == VPN
            )
        }
        if (show(tVpnDns)) {
            TextRow(
                state, AppConfig.PREF_VPN_DNS, tVpnDns,
                default = AppConfig.DNS_VPN, enabled = isVpn && !localDns
            )
        }
        if (show(tOlcrtcDns)) {
            TextRow(
                state, AppConfig.PREF_OLCRTC_DNS, tOlcrtcDns,
                default = "", emptyDescription = sOlcrtcAuto
            )
        }
        if (show(tBypassLan)) {
            ListRow(
                state, AppConfig.PREF_VPN_BYPASS_LAN, tBypassLan,
                bypassEntries, bypassValues, default = "1", enabled = isVpn
            )
        }
        if (show(tIfAddr)) {
            ListRow(
                state, AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, tIfAddr,
                ifAddrEntries, ifAddrValues, default = "0", enabled = isVpn
            )
        }
        if (show(tMtu)) {
            TextRow(
                state, AppConfig.PREF_VPN_MTU, tMtu,
                default = AppConfig.VPN_MTU.toString(), enabled = isVpn, keyboardNumber = true
            )
        }
        if (show(tHevTun, sHevTun)) {
            SettingsSwitchItem(
                title = tHevTun,
                summary = sHevTun,
                checked = hevTun,
                enabled = isVpn,
                onCheckedChange = { checked ->
                    state.setBool(AppConfig.PREF_USE_HEV_TUNNEL, checked)
                    if (checked && !state.bool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)) {
                        state.setBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
                    }
                }
            )
        }
        if (show(tHevLog)) {
            ListRow(
                state, AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, tHevLog,
                hevLogLevels, hevLogLevels, default = "warn", enabled = isVpn && hevTun
            )
        }
        if (show(tHevTimeout)) {
            TextRow(
                state, AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, tHevTimeout,
                default = AppConfig.HEVTUN_RW_TIMEOUT, enabled = isVpn && hevTun
            )
        }
    }
}

@Composable
private fun CoreSettingsSection(
    state: SettingsState,
    query: String,
    onGenerateCredentials: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    val sectionTitle = stringResource(R.string.title_core_settings)

    val tSniffing = stringResource(R.string.title_pref_sniffing_enabled)
    val sSniffing = stringResource(R.string.summary_pref_sniffing_enabled)
    val tRouteOnly = stringResource(R.string.title_pref_route_only_enabled)
    val sRouteOnly = stringResource(R.string.summary_pref_route_only_enabled)
    val tLocalProxy = stringResource(R.string.title_pref_enable_local_proxy)
    val sLocalProxy = stringResource(R.string.summary_pref_enable_local_proxy)
    val tProxySharing = stringResource(R.string.title_pref_proxy_sharing_enabled)
    val sProxySharing = stringResource(R.string.summary_pref_proxy_sharing_enabled)
    val tSocksPort = stringResource(R.string.title_pref_socks_port)
    val tDynamicPort = stringResource(R.string.title_pref_dynamic_socks_port)
    val sDynamicPort = stringResource(R.string.summary_pref_dynamic_socks_port)
    val tSocksUser = stringResource(R.string.title_pref_socks_username)
    val sSocksUser = stringResource(R.string.summary_pref_socks_username)
    val tSocksPass = stringResource(R.string.title_pref_socks_password)
    val sSocksPass = stringResource(R.string.summary_pref_socks_password)
    val tGenerate = stringResource(R.string.title_pref_socks_generate_credentials)
    val sGenerate = stringResource(R.string.summary_pref_socks_generate_credentials)
    val tTelegram = stringResource(R.string.title_pref_add_to_telegram)
    val sTelegram = stringResource(R.string.summary_pref_add_to_telegram)
    val tUdp = stringResource(R.string.title_pref_socks_enable_udp)
    val sUdp = stringResource(R.string.summary_pref_socks_enable_udp)
    val tRemoteDns = stringResource(R.string.title_pref_remote_dns)
    val sRemoteDns = stringResource(R.string.summary_pref_remote_dns)
    val tDomesticDns = stringResource(R.string.title_pref_domestic_dns)
    val sDomesticDns = stringResource(R.string.summary_pref_domestic_dns)
    val tDnsHosts = stringResource(R.string.title_pref_dns_hosts)
    val sDnsHosts = stringResource(R.string.summary_pref_dns_hosts)
    val tLogLevel = stringResource(R.string.title_core_loglevel)
    val tResolveMethod = stringResource(R.string.title_outbound_domain_resolve_method)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    val sectionVisible = show(tSniffing, sSniffing) || show(tRouteOnly, sRouteOnly) ||
        show(tLocalProxy, sLocalProxy) || show(tProxySharing, sProxySharing) ||
        show(tSocksPort) || show(tDynamicPort, sDynamicPort) || show(tSocksUser, sSocksUser) ||
        show(tSocksPass, sSocksPass) || show(tGenerate, sGenerate) || show(tTelegram, sTelegram) ||
        show(tUdp, sUdp) || show(tRemoteDns, sRemoteDns) || show(tDomesticDns, sDomesticDns) ||
        show(tDnsHosts, sDnsHosts) || show(tLogLevel) || show(tResolveMethod)
    if (!sectionVisible) return

    val mode = state.string(AppConfig.PREF_MODE, VPN)
    val hevTun = state.bool(AppConfig.PREF_USE_HEV_TUNNEL, true)
    val localProxy = state.bool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
    val dynamicPort = state.bool(AppConfig.PREF_DYNAMIC_SOCKS_PORT)

    val logLevels = stringArrayResource(R.array.core_loglevel).toList()
    val resolveEntries = stringArrayResource(R.array.outbound_domain_resolve_method).toList()
    val resolveValues = stringArrayResource(R.array.outbound_domain_resolve_method_value).toList()

    val localProxyRowEnabled = !(mode == VPN || mode == PROXY_TUN) || !hevTun

    PreferenceGroupHeader(title = sectionTitle)
    SettingsSection {
        if (show(tSniffing, sSniffing)) {
            SettingsSwitchRow(state, AppConfig.PREF_SNIFFING_ENABLED, tSniffing, sSniffing, default = true)
        }
        if (show(tRouteOnly, sRouteOnly)) {
            SettingsSwitchRow(state, AppConfig.PREF_ROUTE_ONLY_ENABLED, tRouteOnly, sRouteOnly)
        }
        if (show(tLocalProxy, sLocalProxy)) {
            SettingsSwitchItem(
                title = tLocalProxy,
                summary = sLocalProxy,
                checked = localProxy,
                enabled = localProxyRowEnabled,
                onCheckedChange = { checked ->
                    state.setBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, checked)
                    if (!checked && state.bool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                        state.setBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)
                    }
                }
            )
        }
        if (show(tProxySharing, sProxySharing)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_PROXY_SHARING, tProxySharing, sProxySharing,
                enabled = localProxy
            )
        }
        if (show(tSocksPort)) {
            TextRow(
                state, AppConfig.PREF_SOCKS_PORT, tSocksPort,
                default = AppConfig.PORT_SOCKS, enabled = localProxy && !dynamicPort,
                keyboardNumber = true
            )
        }
        if (show(tDynamicPort, sDynamicPort)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_DYNAMIC_SOCKS_PORT, tDynamicPort, sDynamicPort,
                enabled = localProxy
            )
        }
        if (show(tSocksUser, sSocksUser)) {
            TextRow(
                state, AppConfig.PREF_SOCKS_USERNAME, tSocksUser,
                default = "", enabled = localProxy
            )
        }
        if (show(tSocksPass, sSocksPass)) {
            TextRow(
                state, AppConfig.PREF_SOCKS_PASSWORD, tSocksPass,
                default = "", enabled = localProxy, isPassword = true
            )
        }
        if (show(tGenerate, sGenerate)) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_lock_24dp),
                title = tGenerate,
                subtitle = sGenerate,
                enabled = localProxy,
                onClick = onGenerateCredentials
            )
        }
        if (show(tTelegram, sTelegram)) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_telegram_24dp),
                title = tTelegram,
                subtitle = sTelegram,
                enabled = localProxy,
                onClick = onOpenTelegram
            )
        }
        if (show(tUdp, sUdp)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_SOCKS_ENABLE_UDP, tUdp, sUdp,
                default = true, enabled = localProxy
            )
        }
        if (show(tRemoteDns, sRemoteDns)) {
            TextRow(state, AppConfig.PREF_REMOTE_DNS, tRemoteDns, default = AppConfig.DNS_PROXY)
        }
        if (show(tDomesticDns, sDomesticDns)) {
            TextRow(state, AppConfig.PREF_DOMESTIC_DNS, tDomesticDns, default = AppConfig.DNS_DIRECT)
        }
        if (show(tDnsHosts, sDnsHosts)) {
            TextRow(
                state, AppConfig.PREF_DNS_HOSTS, tDnsHosts,
                default = "", singleLine = false
            )
        }
        if (show(tLogLevel)) {
            ListRow(
                state, AppConfig.PREF_LOGLEVEL, tLogLevel,
                logLevels, logLevels, default = "warning"
            )
        }
        if (show(tResolveMethod)) {
            ListRow(
                state, AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, tResolveMethod,
                resolveEntries, resolveValues, default = "1"
            )
        }
    }
}

@Composable
private fun MuxSettingsSection(state: SettingsState, query: String) {
    val sectionTitle = stringResource(R.string.title_mux_settings)
    val tEnabled = stringResource(R.string.title_pref_mux_enabled)
    val sEnabled = stringResource(R.string.summary_pref_mux_enabled)
    val tConcurrency = stringResource(R.string.title_pref_mux_concurency)
    val tXudpConcurrency = stringResource(R.string.title_pref_mux_xudp_concurency)
    val tQuic = stringResource(R.string.title_pref_mux_xudp_quic)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    if (!(show(tEnabled, sEnabled) || show(tConcurrency) || show(tXudpConcurrency) || show(tQuic))) return

    val enabled = state.bool(AppConfig.PREF_MUX_ENABLED)
    val quicEntries = stringArrayResource(R.array.mux_xudp_quic_entries).toList()
    val quicValues = stringArrayResource(R.array.mux_xudp_quic_value).toList()

    var expanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = expanded || query.isNotEmpty()

    CollapsiblePreferenceGroupHeader(
        title = sectionTitle,
        expanded = isExpanded,
        onExpandedChange = { expanded = it }
    )
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column {
            if (show(tEnabled, sEnabled)) {
                SettingsSwitchRow(state, AppConfig.PREF_MUX_ENABLED, tEnabled, sEnabled)
            }
            if (show(tConcurrency)) {
                TextRow(
                    state, AppConfig.PREF_MUX_CONCURRENCY, tConcurrency,
                    default = "8", enabled = enabled, keyboardNumber = true, drawCard = true
                )
            }
            if (show(tXudpConcurrency)) {
                TextRow(
                    state, AppConfig.PREF_MUX_XUDP_CONCURRENCY, tXudpConcurrency,
                    default = "8", enabled = enabled, keyboardNumber = true, drawCard = true
                )
            }
            if (show(tQuic)) {
                ListRow(
                    state, AppConfig.PREF_MUX_XUDP_QUIC, tQuic,
                    quicEntries, quicValues, default = "reject", enabled = enabled, drawCard = true
                )
            }
        }
    }
}

@Composable
private fun FragmentSettingsSection(state: SettingsState, query: String) {
    val sectionTitle = stringResource(R.string.title_fragment_settings)
    val tEnabled = stringResource(R.string.title_pref_fragment_enabled)
    val tLength = stringResource(R.string.title_pref_fragment_length)
    val tInterval = stringResource(R.string.title_pref_fragment_interval)
    val tMaxSplit = stringResource(R.string.title_pref_fragment_maxsplit)
    val tPackets = stringResource(R.string.title_pref_fragment_packets)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    if (!(show(tEnabled) || show(tLength) || show(tInterval) || show(tMaxSplit) || show(tPackets))) return

    val enabled = state.bool(AppConfig.PREF_FRAGMENT_ENABLED)
    val packets = stringArrayResource(R.array.fragment_packets).toList()

    var expanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = expanded || query.isNotEmpty()

    CollapsiblePreferenceGroupHeader(
        title = sectionTitle,
        expanded = isExpanded,
        onExpandedChange = { expanded = it }
    )
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column {
            if (show(tEnabled)) {
                SettingsSwitchRow(state, AppConfig.PREF_FRAGMENT_ENABLED, tEnabled)
            }
            if (show(tLength)) {
                TextRow(state, AppConfig.PREF_FRAGMENT_LENGTH, tLength, default = "50-100", enabled = enabled, drawCard = true)
            }
            if (show(tInterval)) {
                TextRow(state, AppConfig.PREF_FRAGMENT_INTERVAL, tInterval, default = "10-20", enabled = enabled, drawCard = true)
            }
            if (show(tMaxSplit)) {
                TextRow(state, AppConfig.PREF_FRAGMENT_MAXSPLIT, tMaxSplit, default = "10", enabled = enabled, drawCard = true)
            }
            if (show(tPackets)) {
                ListRow(
                    state, AppConfig.PREF_FRAGMENT_PACKETS, tPackets,
                    packets, packets, default = "tlshello", enabled = enabled, drawCard = true
                )
            }
        }
    }
}

@Composable
private fun AdvancedSettingsSection(state: SettingsState, query: String) {
    val sectionTitle = stringResource(R.string.title_advanced)
    val tBoot = stringResource(R.string.title_pref_is_booted)
    val sBoot = stringResource(R.string.summary_pref_is_booted)
    val tRemoveInvalid = stringResource(R.string.title_pref_auto_remove_invalid_after_test)
    val sRemoveInvalid = stringResource(R.string.summary_pref_auto_remove_invalid_after_test)
    val tSort = stringResource(R.string.title_pref_auto_sort_after_test)
    val sSort = stringResource(R.string.summary_pref_auto_sort_after_test)
    val tDelayUrl = stringResource(R.string.title_pref_delay_test_url)
    val sDelayUrl = stringResource(R.string.summary_pref_delay_test_url)
    val tPingConcurrency = stringResource(R.string.title_pref_real_ping_concurrency)
    val tIpApi = stringResource(R.string.title_pref_ip_api_url)
    val sIpApi = stringResource(R.string.summary_pref_ip_api_url)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    val sectionVisible = show(tBoot, sBoot) || show(tRemoveInvalid, sRemoveInvalid) ||
        show(tSort, sSort) || show(tDelayUrl, sDelayUrl) ||
        show(tPingConcurrency) || show(tIpApi, sIpApi)
    if (!sectionVisible) return

    PreferenceGroupHeader(title = sectionTitle)
    SettingsSection {
        if (show(tBoot, sBoot)) {
            SettingsSwitchRow(state, AppConfig.PREF_IS_BOOTED, tBoot, sBoot)
        }
        if (show(tRemoveInvalid, sRemoveInvalid)) {
            SettingsSwitchRow(
                state, AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, tRemoveInvalid, sRemoveInvalid
            )
        }
        if (show(tSort, sSort)) {
            SettingsSwitchRow(state, AppConfig.PREF_AUTO_SORT_AFTER_TEST, tSort, sSort)
        }
        if (show(tDelayUrl, sDelayUrl)) {
            TextRow(state, AppConfig.PREF_DELAY_TEST_URL, tDelayUrl, default = AppConfig.DELAY_TEST_URL)
        }
        if (show(tPingConcurrency)) {
            TextRow(
                state, AppConfig.PREF_REAL_PING_CONCURRENCY, tPingConcurrency,
                default = "16", keyboardNumber = true
            )
        }
        if (show(tIpApi, sIpApi)) {
            TextRow(state, AppConfig.PREF_IP_API_URL, tIpApi, default = AppConfig.IP_API_URL)
        }
    }
}

@Composable
private fun ModeSettingsSection(
    state: SettingsState,
    query: String,
    requestRoot: (() -> Unit) -> Unit
) {
    val sectionTitle = stringResource(R.string.title_mode_settings)
    val tMode = stringResource(R.string.title_mode)
    val tRoot = stringResource(R.string.title_root_mode_enabled)
    val sRoot = stringResource(R.string.summary_root_mode_enabled)
    val tLanSharing = stringResource(R.string.title_root_lan_sharing)
    val sLanSharing = stringResource(R.string.summary_root_lan_sharing)

    fun show(title: String, summary: String? = null) =
        visible(query, title, summary.orEmpty())

    if (!(show(tMode) || show(tRoot, sRoot) || show(tLanSharing, sLanSharing))) return

    val modeEntries = stringArrayResource(R.array.mode_entries).toList()
    val modeValues = stringArrayResource(R.array.mode_value).toList()

    PreferenceGroupHeader(title = sectionTitle)
    SettingsSection {
        if (show(tMode)) {
            ListRow(
                state, AppConfig.PREF_MODE, tMode,
                modeEntries, modeValues, default = VPN
            )
        }
        if (show(tRoot, sRoot)) {
            SettingsSwitchItem(
                title = tRoot,
                summary = sRoot,
                checked = state.bool(AppConfig.PREF_ROOT_MODE_ENABLE),
                onCheckedChange = { checked ->
                    if (!checked) {
                        state.setBool(AppConfig.PREF_ROOT_MODE_ENABLE, false)
                    } else {
                        requestRoot { state.setBool(AppConfig.PREF_ROOT_MODE_ENABLE, true) }
                    }
                }
            )
        }
        if (show(tLanSharing, sLanSharing)) {
            SettingsSwitchItem(
                title = tLanSharing,
                summary = sLanSharing,
                checked = state.bool(AppConfig.PREF_ROOT_LAN_SHARING),
                onCheckedChange = { checked ->
                    if (!checked) {
                        state.setBool(AppConfig.PREF_ROOT_LAN_SHARING, false)
                    } else {
                        requestRoot { state.setBool(AppConfig.PREF_ROOT_LAN_SHARING, true) }
                    }
                }
            )
        }
    }
}
