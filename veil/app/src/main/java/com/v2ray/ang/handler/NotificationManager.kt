package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.toMetricSpeed
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val TUN_NOTIFICATION_ID = 2
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_PENDING_INTENT_TOGGLE_TUN = 3
    private const val QUERY_INTERVAL_MS = 3000L
    // Two live channels differing only by importance: LOCKSCREEN (IMPORTANCE_DEFAULT) surfaces the
    // live update on the lock screen; SHADE (IMPORTANCE_LOW) keeps it in the shade / status-bar chip
    // only. A channel's importance can't change after creation, so the toggle switches between them.
    private const val RAY_NG_CHANNEL_ID_LIVE_LOCKSCREEN = "RAY_NG_M_CH_ID_LIVE_LOCK_V3"
    private const val RAY_NG_CHANNEL_ID_LIVE_SHADE = "RAY_NG_M_CH_ID_LIVE_SHADE_V3"

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var mBuilderPlatform: Notification.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null
    private var currentConfig: ProfileItem? = null
    private var isMetricStyleActive = false
    private var lastLockScreenEnabled: Boolean? = null
    private var lastTunEnabled: Boolean? = null
    private var lastChannelId: String? = null

    /**
     * Starts the speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun startSpeedNotification() {
        val notificationEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true
        val toolbarEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_TOOLBAR_ENABLED) == true
        val statusBarLiveEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_STATUS_BAR_LIVE) == true
        if (!notificationEnabled && !toolbarEnabled && !statusBarLiveEnabled) return
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        var lastZeroSpeed = false

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                lastZeroSpeed = updateSpeedNotificationOnce(lastZeroSpeed)
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    /**
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        this.currentConfig = currentConfig

        // Reset last query time to avoid querying stats too soon after showing the notification
        lastQueryTime = System.currentTimeMillis()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val tunEnabled = SettingsManager.isProxyTunMode() && SettingsManager.isTunEnabled()
        lastTunEnabled = tunEnabled
        val toggleTunIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        toggleTunIntent.`package` = AppConfig.ANG_PACKAGE
        toggleTunIntent.putExtra("key", AppConfig.MSG_STATE_TUN_TOGGLE)
        val toggleTunPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_TOGGLE_TUN, toggleTunIntent, flags)

        val statusBarLiveEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_STATUS_BAR_LIVE) == true
        val useLiveChannel = statusBarLiveEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        val useMetricStyle = statusBarLiveEnabled && Build.VERSION.SDK_INT >= 37
        isMetricStyleActive = useMetricStyle

        val showOnLockScreen = statusBarLiveEnabled &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_LOCK_SCREEN) == true
        lastLockScreenEnabled = showOnLockScreen
        // Promotion drives the status-bar chip and rich buttons, so keep it on whenever the live
        // channel is used, regardless of the lock-screen toggle. Lock-screen display is controlled
        // separately by the channel's importance (DEFAULT surfaces on the lock screen, LOW doesn't).
        val canPromote = useLiveChannel
        val lockScreenVisibility =
            if (showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(useLiveChannel, showOnLockScreen)
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        // Android won't move an already-posted foreground notification to a different channel, so
        // when the lock-screen toggle flips the channel while running, detach the old notification
        // first and let the startForeground() below re-post it under the new channel. cancel() alone
        // is ignored while the notification backs the foreground service, hence stopForeground().
        if (lastChannelId != null && lastChannelId != channelId) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        }
        lastChannelId = channelId

        val showTunToggle = SettingsManager.isProxyTunMode()
        val tunActionLabel = if (tunEnabled) {
            service.getString(R.string.toggle_tun_off)
        } else {
            service.getString(R.string.toggle_tun_on)
        }

        if (useMetricStyle) {
            mBuilder = null
            val platformBuilder = Notification.Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(currentConfig?.remarks)
                .setContentText("\u2193 ${0L.toSpeedString()}   \u2191 ${0L.toSpeedString()}")
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setVisibility(lockScreenVisibility)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(service, R.drawable.ic_stat_name),
                        service.getString(R.string.notification_action_stop_v2ray),
                        stopV2RayPendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(service, R.drawable.ic_stat_name),
                        service.getString(R.string.title_service_restart),
                        restartV2RayPendingIntent
                    ).build()
                )
                .setStyle(buildMetricStyle(0L, 0L))

            if (showTunToggle) {
                platformBuilder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(service, R.drawable.ic_stat_name),
                        tunActionLabel,
                        toggleTunPendingIntent
                    ).build()
                )
            }

            mBuilderPlatform = platformBuilder

            if (canPromote) {
                mBuilderPlatform?.extras?.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            }

            service.startForeground(NOTIFICATION_ID, mBuilderPlatform?.build())
        } else {
            mBuilderPlatform = null
            val compatBuilder = NotificationCompat.Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(currentConfig?.remarks)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setVisibility(lockScreenVisibility)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    R.drawable.ic_stat_name,
                    service.getString(R.string.notification_action_stop_v2ray),
                    stopV2RayPendingIntent
                )
                .addAction(
                    R.drawable.ic_stat_name,
                    service.getString(R.string.title_service_restart),
                    restartV2RayPendingIntent
                )

            if (showTunToggle) {
                compatBuilder.addAction(
                    R.drawable.ic_stat_name,
                    tunActionLabel,
                    toggleTunPendingIntent
                )
            }

            mBuilder = compatBuilder

            if (canPromote) {
                mBuilder?.extras?.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            }

            service.startForeground(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Shows a minimal foreground notification for the standalone TUN service.
     * This is used by CoreTunToggleService so it satisfies the foreground-service
     * requirement without colliding with the main CoreProxyOnlyService notification.
     */
    fun showTunNotification(service: Service) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopTunIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopTunIntent.`package` = AppConfig.ANG_PACKAGE
        stopTunIntent.putExtra("key", AppConfig.MSG_STATE_TUN_TOGGLE)
        val stopTunPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_TOGGLE_TUN, stopTunIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(false)
        } else {
            ""
        }

        val notification = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(service.getString(R.string.title_tun_enabled))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_stat_name,
                service.getString(R.string.toggle_tun_off),
                stopTunPendingIntent
            )
            .build()

        service.startForeground(TUN_NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        mBuilderPlatform = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
        currentConfig = null
        isMetricStyleActive = false
        lastTunEnabled = null
        lastLockScreenEnabled = null
        lastChannelId = null
    }

    /**
     * Stops the speed notification.
     */
    fun stopSpeedNotification() {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            updateNotification("", 0, 0)
        }
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(liveUpdate: Boolean, showOnLockScreen: Boolean = false): String {
        val channelId = when {
            liveUpdate && showOnLockScreen -> RAY_NG_CHANNEL_ID_LIVE_LOCKSCREEN
            liveUpdate -> RAY_NG_CHANNEL_ID_LIVE_SHADE
            else -> AppConfig.RAY_NG_CHANNEL_ID
        }
        val channelName = if (liveUpdate) "${AppConfig.RAY_NG_CHANNEL_NAME} Live" else AppConfig.RAY_NG_CHANNEL_NAME
        // The status-bar chip and rich buttons come from promotion (kept on for both live channels).
        // Lock-screen display is driven by importance: DEFAULT surfaces the live update on the lock
        // screen, LOW keeps it in the shade / status bar chip only. A channel's importance can't be
        // changed after creation, so the toggle picks between two channels. Sound is muted so neither
        // channel makes noise despite IMPORTANCE_DEFAULT.
        val importance = when {
            liveUpdate && showOnLockScreen -> NotificationManager.IMPORTANCE_DEFAULT
            liveUpdate -> NotificationManager.IMPORTANCE_LOW
            else -> NotificationManager.IMPORTANCE_NONE
        }
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.DKGRAY
        chan.importance = importance
        chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        if (liveUpdate) {
            chan.setSound(null, null)
            chan.enableVibration(false)
        }
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    /**
     * Updates the notification with the given content text and traffic data.
     * @param contentText The content text.
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(
        contentText: String?,
        proxyTraffic: Long,
        directTraffic: Long,
        upSpeed: Long = 0L,
        downSpeed: Long = 0L
    ) {
        val smallIconRes = R.drawable.ic_stat_name

        if (mBuilderPlatform != null && Build.VERSION.SDK_INT >= 37) {
            val speedText = "\u2193 ${downSpeed.toSpeedString()}   \u2191 ${upSpeed.toSpeedString()}"
            mBuilderPlatform?.setSmallIcon(smallIconRes)
            mBuilderPlatform?.setContentText(speedText)
            mBuilderPlatform?.setStyle(buildMetricStyle(upSpeed, downSpeed))
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilderPlatform?.build())
        } else if (mBuilder != null) {
            mBuilder?.setSmallIcon(smallIconRes)
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Builds a MetricStyle notification style for live status-bar updates.
     * @param upSpeed The uplink speed in bytes per second.
     * @param downSpeed The downlink speed in bytes per second.
     * @return The MetricStyle instance.
     */
    private fun buildMetricStyle(upSpeed: Long, downSpeed: Long): Notification.MetricStyle {
        val (downValue, downUnit) = downSpeed.toMetricSpeed()
        val (upValue, upUnit) = upSpeed.toMetricSpeed()
        return Notification.MetricStyle().apply {
            addMetric(
                Notification.Metric(
                    Notification.Metric.FixedFloat(downValue, "", 0, 1),
                    "\u2193 $downUnit"
                )
            )
            addMetric(
                Notification.Metric(
                    Notification.Metric.FixedFloat(upValue, "", 0, 1),
                    "\u2191 $upUnit"
                )
            )
            setCriticalMetric(if (downSpeed >= upSpeed) 0 else 1)
        }
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Appends the speed string to the given text.
     * @param text The text to append to.
     * @param name The name of the tag.
     * @param up The uplink speed.
     * @param down The downlink speed.
     */
    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.take(min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    /**
     * Updates the speed notification once.
     * Queries traffic stats, separates proxy and direct, and updates the notification.
     * @param lastZeroSpeed The previous zero speed state.
     * @return The current zero speed state.
     */
    private fun updateSpeedNotificationOnce(lastZeroSpeed: Boolean): Boolean {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime)

        // If the query interval is too short, skip this round to avoid excessive CPU usage
        if (sinceLastQueryIn < QUERY_INTERVAL_MS) {
            LogUtil.w(AppConfig.TAG, "Query interval too short: ${sinceLastQueryIn}ms, skipping")
            lastQueryTime = queryTime
            return lastZeroSpeed
        }
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L

        CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
            when {
                stat.tag == AppConfig.TAG_DIRECT -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> directUplink += stat.value
                        AppConfig.DOWNLINK -> directDownlink += stat.value
                    }
                }

                stat.tag.startsWith(AppConfig.TAG_PROXY) -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> proxyUplink += stat.value
                        AppConfig.DOWNLINK -> proxyDownlink += stat.value
                    }
                }
            }
        }

        val proxyTotal = proxyUplink + proxyDownlink
        val directTotal = directUplink + directDownlink
        val zeroSpeed = proxyTotal + directTotal == 0L
        val upSpeed = ((proxyUplink + directUplink) / sinceLastQueryInSeconds).toLong()
        val downSpeed = ((proxyDownlink + directDownlink) / sinceLastQueryInSeconds).toLong()

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_TOOLBAR_ENABLED) == true) {
            getService()?.let { MessageUtil.sendMsg2UI(it, AppConfig.MSG_NET_SPEED, "$upSpeed;$downSpeed") }
        }

        // Rebuild the notification if the live-update style preference or TUN state changed while running.
        val statusBarLiveEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_STATUS_BAR_LIVE) == true
        val desiredMetricStyle = statusBarLiveEnabled && Build.VERSION.SDK_INT >= 37
        val currentTunEnabled = SettingsManager.isProxyTunMode() && SettingsManager.isTunEnabled()
        val desiredLockScreen = statusBarLiveEnabled &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_LOCK_SCREEN) == true
        if (desiredMetricStyle != isMetricStyleActive ||
            currentTunEnabled != lastTunEnabled ||
            desiredLockScreen != lastLockScreenEnabled
        ) {
            showNotification(currentConfig)
        }

        val showBigTextSpeed = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true
            || (statusBarLiveEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)

        if (statusBarLiveEnabled && Build.VERSION.SDK_INT >= 37) {
            updateNotification(null, proxyTotal, directTotal, upSpeed, downSpeed)
        } else if (showBigTextSpeed && (!zeroSpeed || !lastZeroSpeed)) {
            val text = StringBuilder()
            appendSpeedString(
                text, AppConfig.TAG_PROXY,
                proxyUplink / sinceLastQueryInSeconds,
                proxyDownlink / sinceLastQueryInSeconds
            )

            appendSpeedString(
                text, AppConfig.TAG_DIRECT,
                directUplink / sinceLastQueryInSeconds,
                directDownlink / sinceLastQueryInSeconds
            )
            updateNotification(text.toString(), proxyTotal, directTotal)
        }
        lastQueryTime = queryTime
        return zeroSpeed
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}