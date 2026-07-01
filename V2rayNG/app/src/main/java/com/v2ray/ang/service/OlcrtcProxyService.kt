package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.OlcrtcManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import java.lang.ref.SoftReference

class OlcrtcProxyService : Service(), ServiceControl {

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "OlcrtcService: created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "OlcrtcService: command received")
        startService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            OlcrtcManager.stop()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcService: stop failed in onDestroy", e)
        }
    }

    override fun getService(): Service = this

    override fun startService() {
        LogUtil.i(AppConfig.TAG, "OlcrtcService: startService called")
    }

    override fun stopService() {
        stopSelf()
    }

    override fun vpnProtect(socket: Int): Boolean {
        return true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
