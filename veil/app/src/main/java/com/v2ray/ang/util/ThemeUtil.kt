package com.v2ray.ang.util

import android.content.Context
import android.content.res.Resources
import com.v2ray.ang.R
import com.v2ray.ang.handler.SettingsManager

object ThemeUtil {
    /**
     * Applies the pure-black XML surface overlay when AMOLED is enabled and the
     * activity is actually running in dark mode. Compose reads the same preference
     * through [com.v2ray.ang.ui.compose.AppTheme].
     */
    fun applyAmoledIfNeeded(theme: Resources.Theme, context: Context) {
        if (SettingsManager.isAmoledThemeEnabled() && Utils.getDarkModeStatus(context)) {
            theme.applyStyle(R.style.ThemeOverlay_App_Amoled, true)
        }
    }
}
