package com.v2ray.ang.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.contracts.Tun2SocksControl
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils

class CoreTunToggleService : VpnService() {

    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var tun2SocksService: Tun2SocksControl? = null

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivity by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    private val stopTunReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.i(AppConfig.TAG, "StartCore-TunToggle: Stop broadcast received")
            stopAllService()
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-TunToggle: Service created")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        registerReceiver(
            stopTunReceiver,
            IntentFilter(AppConfig.ACTION_STOP_TUN),
            Utils.receiverFlags()
        )
    }

    override fun onRevoke() {
        LogUtil.w(AppConfig.TAG, "StartCore-TunToggle: Permission revoked")
        stopAllService()
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-TunToggle: Service destroyed")

        try {
            unregisterReceiver(stopTunReceiver)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "StartCore-TunToggle: Failed to unregister receiver", e)
        }

        if (isRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    connectivity.unregisterNetworkCallback(defaultNetworkCallback)
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "StartCore-TunToggle: Failed to unregister callback in onDestroy", e)
                }
            }

            tun2SocksService?.stopTun2Socks()
            tun2SocksService = null

            try {
                if (::mInterface.isInitialized) {
                    mInterface.close()
                    LogUtil.i(AppConfig.TAG, "StartCore-TunToggle: TUN interface closed in onDestroy")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Failed to close interface in onDestroy", e)
            }
        }

        SettingsManager.setTunEnabled(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "StartCore-TunToggle: Service command received")
        if (intent?.action == AppConfig.ACTION_STOP_TUN) {
            stopAllService()
            return START_NOT_STICKY
        }
        setupTunService()
        return START_STICKY
    }

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    private fun setupTunService() {
        val prepare = prepare(this)
        if (prepare != null) {
            LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Permission not granted")
            stopSelf()
            return
        }

        if (configureVpnService() != true) {
            LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Configuration failed")
            stopSelf()
            return
        }

        runTun2socks()
        SettingsManager.setTunEnabled(true)
    }

    private fun configureVpnService(): Boolean {
        val builder = Builder()

        configureNetworkSettings(builder)
        configurePerAppProxy(builder)

        try {
            if (::mInterface.isInitialized) {
                mInterface.close()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "StartCore-TunToggle: Failed to close old interface", e)
        }

        configurePlatformFeatures(builder)

        try {
            mInterface = builder.establish()!!
            isRunning = true
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Failed to establish TUN interface", e)
            stopAllService()
        }
        return false
    }

    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 18)
            } else {
                builder.addRoute("::", 0)
            }
        }

        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }
    }

    private fun configurePlatformFeatures(builder: Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Failed to request network", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort()))
            }
        }
    }

    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    builder.addDisallowedApplication(it)
                } else {
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Failed to configure app", e)
            }
        }
    }

    private fun runTun2socks() {
        tun2SocksService = TProxyService(
            context = applicationContext,
            vpnInterface = mInterface,
            isRunningProvider = { isRunning },
            restartCallback = { runTun2socks() }
        )
        tun2SocksService?.startTun2Socks()
    }

    private fun stopAllService() {
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "StartCore-TunToggle: Failed to unregister callback", e)
            }
        }

        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null

        try {
            if (::mInterface.isInitialized) {
                mInterface.close()
                LogUtil.i(AppConfig.TAG, "StartCore-TunToggle: TUN interface closed")
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-TunToggle: Failed to close interface", e)
        }

        stopSelf()

        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            LogUtil.w(AppConfig.TAG, "StartCore-TunToggle: Sleep interrupted", e)
        }

        SettingsManager.setTunEnabled(false)
    }
}
