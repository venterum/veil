package com.v2ray.ang.ui.base

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.ThemeUtil

/**
 * Compose-capable base activity. Wraps the screen in the app's [AppTheme]
 * and enables edge-to-edge drawing. Subclasses must implement [ScreenContent].
 */
abstract class BaseComponentActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    override fun getTheme(): Resources.Theme {
        val theme = super.getTheme()
        ThemeUtil.applyAmoledIfNeeded(theme, this)
        return theme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                ScreenContent()
            }
        }
    }

    @Composable
    protected abstract fun ScreenContent()
}
