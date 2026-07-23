package com.v2ray.ang.receiver

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private object WidgetState {
    private const val KEY_IS_RUNNING = "pref_widget_service_running"

    var isRunning: Boolean
        get() = MmkvManager.decodeSettingsBool(KEY_IS_RUNNING, false)
        set(value) {
            MmkvManager.encodeSettings(KEY_IS_RUNNING, value)
        }
}

class WidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VeilWidget

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Ask the daemon for the current state as a bonus; the ground truth is
        // written by CoreServiceManager directly, so the MMKV flag is already
        // correct even if no reply comes.
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppConfig.BROADCAST_ACTION_ACTIVITY) {
            when (intent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> refresh(context, isRunning = true)

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_START_FAILURE,
                AppConfig.MSG_STATE_STOP_SUCCESS -> refresh(context, isRunning = false)
            }
        }
    }

    private fun refresh(context: Context, isRunning: Boolean) {
        WidgetState.isRunning = isRunning
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            VeilWidget.updateAll(context)
            pendingResult.finish()
        }
    }
}

object VeilWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isRunning = WidgetState.isRunning
        provideContent {
            GlanceTheme(DynamicThemeColorProviders) {
                VeilWidgetContent(isRunning = isRunning)
            }
        }
    }
}

class ToggleVeilAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (WidgetState.isRunning) {
            MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
        } else {
            startVeilService(context)
        }
    }
}

class ReloadVeilAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (WidgetState.isRunning) {
            MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_RESTART, "")
        } else {
            startVeilService(context)
        }
    }
}

private fun startVeilService(context: Context) {
    if (MmkvManager.getSelectServer().isNullOrEmpty()) {
        context.toast(R.string.app_tile_first_use)
        return
    }
    val appContext = context.applicationContext
    val intent = when {
        SettingsManager.isRootMode() -> {
            if (!RootManager.isRootAvailable()) {
                context.toast(R.string.toast_root_required)
                return
            }
            Intent(appContext, CoreRootService::class.java)
        }

        SettingsManager.isVpnMode() -> Intent(appContext, CoreVpnService::class.java)
        else -> Intent(appContext, CoreProxyOnlyService::class.java)
    }
    try {
        ContextCompat.startForegroundService(context, intent)
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Widget: failed to start service", e)
        context.toast(e.message ?: e.javaClass.simpleName)
    }
}

@Composable
private fun VeilWidgetContent(isRunning: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(999.dp)
            .padding(all = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(
                        if (isRunning) colors.error else colors.primary
                    )
                    .cornerRadius(999.dp)
                    .clickable(actionRunCallback<ToggleVeilAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                    ),
                    contentDescription = context.getString(
                        if (isRunning) R.string.app_widget_action_disconnect
                        else R.string.app_widget_action_connect
                    ),
                    modifier = GlanceModifier.size(26.dp),
                    colorFilter = ColorFilter.tint(
                        if (isRunning) colors.onError else colors.onPrimary
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(colors.tertiaryContainer)
                    .cornerRadius(999.dp)
                    .clickable(actionRunCallback<ReloadVeilAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_reload_24dp),
                    contentDescription = context.getString(R.string.app_widget_action_reload),
                    modifier = GlanceModifier.size(26.dp),
                    colorFilter = ColorFilter.tint(colors.onTertiaryContainer)
                )
            }
        }
    }
}
