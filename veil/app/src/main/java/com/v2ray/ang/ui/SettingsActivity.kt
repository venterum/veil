package com.v2ray.ang.ui

import androidx.compose.runtime.Composable
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.SettingsScreen

/**
 * Application settings screen. The whole UI lives in [SettingsScreen]; this activity
 * only hosts it, so there is no XML layout or PreferenceFragment left.
 */
class SettingsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        SettingsScreen(onBackClick = { finish() })
    }
}
