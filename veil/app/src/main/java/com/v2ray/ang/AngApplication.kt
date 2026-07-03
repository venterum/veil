package com.v2ray.ang

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import java.util.Collections

class AngApplication : MultiDexApplication() {
    companion object {
        lateinit var application: AngApplication
    }

    // Keep weak references to live activities so we can recreate them when the
    // dynamic-color preference changes, applying the new palette immediately.
    private val liveActivities = Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)
        SettingsManager.setNightMode()

        // Apply Material You dynamic colors to every activity on Android 12+.
        // The precondition is evaluated on each activity creation, so toggling the
        // preference and recreating activities is enough to switch palettes.
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder()
                .setPrecondition { _, _ ->
                    MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
                }
                .build()
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                liveActivities.add(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                liveActivities.remove(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })

    }

    /**
     * Recreates all currently live activities so theme/color changes (e.g. dynamic
     * colors toggle) take effect immediately across the whole back stack.
     */
    fun recreateAllActivities() {
        liveActivities.toList().forEach { it.recreate() }
    }
}
