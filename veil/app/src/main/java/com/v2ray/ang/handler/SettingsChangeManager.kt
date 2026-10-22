package com.v2ray.ang.handler

import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single entry point for reacting to a persisted setting change.
 *
 * Any code that writes to MMKV settings should call [notifySettingChanged] with the
 * changed key. This keeps side effects (log level refresh, night mode, activity
 * recreation, service restart flags) in one place instead of duplicating them at
 * every call site.
 */
object SettingsChangeManager {
    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)

    // Keys that affect only UI behavior and do not require core service restart.
    private val uiOnlyKeys = setOf(
        AppConfig.PREF_CONFIRM_REMOVE,
        AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
        AppConfig.PREF_GROUP_ALL_DISPLAY,
        AppConfig.PREF_GROUP_ALL_MODE,
        AppConfig.PREF_GROUP_ALL_ICONS_VISIBLE,
        AppConfig.PREF_LANGUAGE,
        AppConfig.PREF_UI_MODE_NIGHT,
        AppConfig.PREF_DYNAMIC_COLOR,
        AppConfig.PREF_AMOLED_THEME,
        AppConfig.PREF_GOOGLE_SANS,
        AppConfig.PREF_IS_BOOTED,
        AppConfig.PREF_SPEED_TOOLBAR_ENABLED,
        AppConfig.PREF_SERVER_CARD_STYLE,
        AppConfig.PREF_SERVER_EMOJI_AVATAR,
        AppConfig.PREF_SERVER_SEARCH_BUTTON_ENABLED,
        AppConfig.PREF_QUICK_ACTION_TEST_ALL,
        AppConfig.PREF_QUICK_ACTION_SORT,
        AppConfig.PREF_QUICK_ACTION_UPDATE,
        AppConfig.PREF_UNSUPPORTED_PING_PILL,
        AppConfig.PREF_MAIN_BOLDER_FONT,
    )

    /**
     * Called when a setting value changes.
     * Applies key-specific side effects, triggers a service restart for keys that
     * require it, and always refreshes the UI tabs.
     */
    fun notifySettingChanged(key: String) {
        applyKeySideEffects(key)

        if (key !in uiOnlyKeys) {
            makeRestartService()
        }
        makeSetupGroupTab()
    }

    /**
     * Side effects that must run immediately after a specific key changes.
     * These used to live in the now-removed PreferenceDataStore bridge.
     */
    private fun applyKeySideEffects(key: String) {
        if (key == AppConfig.PREF_LOGLEVEL) {
            LogUtil.refreshLogLevel()
        }

        // Night mode is handled by AppCompatDelegate, which recreates activities itself.
        if (key == AppConfig.PREF_UI_MODE_NIGHT) {
            SettingsManager.setNightMode()
        }

        // Color/font changes recreate all live activities so the new theme applies everywhere.
        if (key == AppConfig.PREF_DYNAMIC_COLOR ||
            key == AppConfig.PREF_AMOLED_THEME ||
            key == AppConfig.PREF_GOOGLE_SANS
        ) {
            AngApplication.application.recreateAllActivities()
        }

        // When language changes, force Google Sans on/off and recreate everything.
        if (key == AppConfig.PREF_LANGUAGE) {
            val isEnglish = SettingsManager.getLocale().language == "en"
            MmkvManager.encodeSettings(AppConfig.PREF_GOOGLE_SANS, isEnglish)
            AngApplication.application.recreateAllActivities()
        }
    }

    // Mark restartService as requiring a restart
    fun makeRestartService() {
        _restartService.value = true
    }

    // Read and clear the restartService flag
    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    // Mark reinitGroupTab as requiring tab reinitialization
    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
    }

    // Read and clear the reinitGroupTab flag
    fun consumeSetupGroupTab(): Boolean {
        val v = _setupGroupTab.value
        _setupGroupTab.value = false
        return v
    }
}
