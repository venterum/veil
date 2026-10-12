package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager

/**
 * Reactive facade over the MMKV-backed settings store.
 *
 * Every read subscribes the calling composable to [version], so any row re-reads the
 * current value whenever *any* setting changes. This keeps cross-row dependencies
 * (e.g. "mode" enabling VPN-only fields) reactive without threading individual state
 * objects through the whole tree.
 *
 * All writes go through [SettingsChangeManager.notifySettingChanged], so service-restart
 * flags and key-specific side effects (log level, night mode, activity recreation) are
 * handled in one place.
 */
@Stable
class SettingsState {
    var version by mutableIntStateOf(0)
        private set

    fun bool(key: String, default: Boolean = false): Boolean =
        version.let { MmkvManager.decodeSettingsBool(key, default) }

    fun string(key: String, default: String = ""): String =
        version.let { MmkvManager.decodeSettingsString(key, default) ?: default }

    fun setBool(key: String, value: Boolean) {
        MmkvManager.encodeSettings(key, value)
        SettingsChangeManager.notifySettingChanged(key)
        version++
    }

    fun setString(key: String, value: String) {
        MmkvManager.encodeSettings(key, value)
        SettingsChangeManager.notifySettingChanged(key)
        version++
    }

    /**
     * Persists a value without triggering a service restart / side effects.
     * Used only for legacy normalization (e.g. forcing local proxy on when HEV TUN is on).
     */
    fun setBoolSilently(key: String, value: Boolean) {
        MmkvManager.encodeSettings(key, value)
        version++
    }
}

@Composable
fun rememberSettingsState(): SettingsState = remember { SettingsState() }
