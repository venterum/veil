package com.v2ray.ang.receiver

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.currentState
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
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Ключ состояния специально для Jetpack Glance
val isRunningKey = booleanPreferencesKey("isRunning")
private const val ACTION_RELOAD = "com.v2ray.ang.widget.RELOAD"

class WidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VeilWidget

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAllWidgetsState(context, CoreServiceManager.isRunning())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            // 1. Клик по кнопке ВКЛ/ВЫКЛ
            AppConfig.BROADCAST_ACTION_WIDGET_CLICK -> {
                val currentlyRunning = CoreServiceManager.isRunning()

                // Мгновенно обновляем UI через официальный State Glance
                updateAllWidgetsState(context, !currentlyRunning)

                // Вызываем надежный системный запуск из оригинала
                if (currentlyRunning) {
                    CoreServiceManager.stopVService(context)
                } else {
                    CoreServiceManager.startVServiceFromToggle(context)
                }
            }

            // 2. Клик по кнопке ПЕРЕЗАГРУЗКА
            ACTION_RELOAD -> {
                updateAllWidgetsState(context, true)

                if (CoreServiceManager.isRunning()) {
                    MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_RESTART, "")
                } else {
                    CoreServiceManager.startVServiceFromToggle(context)
                }
            }

            // 3. Синхронизация статуса от самого ядра
            AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_RUNNING,
                    AppConfig.MSG_STATE_START_SUCCESS -> {
                        updateAllWidgetsState(context, true)
                    }
                    AppConfig.MSG_STATE_NOT_RUNNING,
                    AppConfig.MSG_STATE_START_FAILURE,
                    AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        updateAllWidgetsState(context, false)
                    }
                }
            }
        }
    }

    // Единственный правильный способ заставить Glance перерисовать виджет!
    private fun updateAllWidgetsState(context: Context, isRunning: Boolean) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(VeilWidget::class.java)

                glanceIds.forEach { glanceId ->
                    // Записываем состояние в кэш Glance
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[isRunningKey] = isRunning
                    }
                    // Вызываем обновление — теперь Compose увидит изменение и запустит анимацию
                    VeilWidget.update(context, glanceId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    // Игнорируем
                }
            }
        }
    }
}

object VeilWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(DynamicThemeColorProviders) {
                // Подписываемся на изменения состояния!
                val prefs = currentState<Preferences>()
                // Берем состояние из виджета, либо (если это первый запуск) у ядра
                val isRunning = prefs[isRunningKey] ?: CoreServiceManager.isRunning()

                VeilWidgetContent(isRunning = isRunning)
            }
        }
    }
}

@Composable
private fun VeilWidgetContent(isRunning: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors

    val toggleIntent = Intent(context, WidgetProvider::class.java).apply {
        action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
        data = Uri.parse("veil://widget/toggle?state=$isRunning")
    }

    val reloadIntent = Intent(context, WidgetProvider::class.java).apply {
        action = ACTION_RELOAD
        data = Uri.parse("veil://widget/reload?state=$isRunning")
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // widgetBackground — фон как у стандартных виджетов Google/системы (светлее surface)
            .background(colors.widgetBackground)
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
                    .clickable(actionSendBroadcast(toggleIntent)),
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
                    .clickable(actionSendBroadcast(reloadIntent)),
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
