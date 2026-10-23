package com.v2ray.ang.core

import android.content.Context
import android.os.Build
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import amneziawg.Client
import amneziawg.SocketProtector
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Owns the AmneziaWG runtime. The native client runs amneziawg-go over a
 * gVisor netstack and serves SOCKS5 on loopback; Xray consumes it as a plain
 * SOCKS outbound (see [CoreOutboundBuilder.toOutboundAmneziawg]).
 */
object AmneziaWgManager {

    private const val READY_TIMEOUT_MS = 5000L
    private const val PING_TIMEOUT_MS = 15000L

    @Volatile
    var socketProtector: ((Int) -> Boolean)? = null

    private val protector = object : SocketProtector {
        override fun protect(fd: Long): Boolean {
            return socketProtector?.invoke(fd.toInt()) ?: true
        }
    }

    @Volatile
    private var client: Client? = null

    @Volatile
    private var currentConfigJson: String? = null

    @Volatile
    private var logPump: Thread? = null

    val isRunning: Boolean
        get() = try {
            client?.isRunning == true
        } catch (_: Exception) {
            false
        }

    fun start(context: Context, config: ProfileItem): Boolean {
        if (isRunning) {
            LogUtil.i(AppConfig.TAG, "AmneziaWgManager: already running")
            return true
        }
        LogUtil.w(AppConfig.TAG, "AmneziaWgManager: device ABIs=${Build.SUPPORTED_ABIS.joinToString()}")
        val preferredPort = (config.localSocksPort ?: AppConfig.PORT_AMNEZIAWG_SOCKS)
            .toString()
            .toIntOrNull() ?: AppConfig.PORT_AMNEZIAWG_SOCKS.toInt()
        val socksPort = findAvailablePort(preferredPort)

        var nativeClient: Client? = null
        return try {
            val created = Client()
            nativeClient = created
            val configJson = buildConfigJson(config)
            created.setProtector(protector)
            created.setConfigJSON(configJson)
            created.setSocks(AppConfig.LOOPBACK, socksPort.toLong())
            LogUtil.w(
                AppConfig.TAG,
                "AmneziaWgManager: start endpoint=${config.server}:${config.serverPort} socks=${AppConfig.LOOPBACK}:$socksPort " +
                    "awg(jc=${config.awgJc},jmin=${config.awgJmin},jmax=${config.awgJmax}," +
                    "s1=${config.awgS1},s2=${config.awgS2},s3=${config.awgS3},s4=${config.awgS4}," +
                    "h1=${config.awgH1},h2=${config.awgH2},h3=${config.awgH3},h4=${config.awgH4}," +
                    "i1=${!config.awgI1.isNullOrBlank()},hp=${!config.awgHeaderProtectionKey.isNullOrBlank()}," +
                    "trailers=${config.awgRandomTrailers},cookies=${config.awgDisableCookies})"
            )
            created.start()
            try {
                created.waitReady(READY_TIMEOUT_MS)
            } catch (e: Exception) {
                // The listener is already bound; keep the tunnel and let Xray
                // report "connecting" until the handshake succeeds.
                LogUtil.w(AppConfig.TAG, "AmneziaWgManager: not ready yet (${e.message}); continuing")
            }
            val nativeLog = runCatching { created.readLog() }.getOrDefault("")
            if (nativeLog.isNotBlank()) {
                nativeLog.lineSequence().filter { it.isNotBlank() }.forEach {
                    LogUtil.w(AppConfig.TAG, "AmneziaWG native: $it")
                }
            }
            client = created
            currentConfigJson = configJson
            config.localSocksPort = socksPort
            startLogPump(created)
            LogUtil.i(AppConfig.TAG, "AmneziaWgManager: listening on ${AppConfig.LOOPBACK}:$socksPort")
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AmneziaWgManager: start failed: ${e.javaClass.simpleName}: ${e.message}", e)
            try {
                nativeClient?.stop()
            } catch (_: Exception) {
            }
            client = null
            currentConfigJson = null
            false
        }
    }

    fun stop() {
        try {
            logPump?.interrupt()
        } catch (_: Exception) {
        }
        logPump = null
        try {
            client?.stop()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AmneziaWgManager: stop failed", e)
        }
        client = null
        currentConfigJson = null
    }

    /** Measures HTTP latency through a temporary or existing AWG device. */
    fun ping(config: ProfileItem, url: String): Long {
        return try {
            val configJson = buildConfigJson(config)
            val active = client
            if (active != null && active.isRunning && configJson == currentConfigJson) {
                return active.ping(url, PING_TIMEOUT_MS.toLong())
            }
            val probe = Client()
            probe.setProtector(protector)
            probe.setConfigJSON(configJson)
            probe.ping(url, PING_TIMEOUT_MS.toLong())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "AmneziaWgManager: ping failed", e)
            -1L
        }
    }

    private fun startLogPump(nativeClient: Client) {
        if (logPump?.isAlive == true) return
        logPump = Thread {
            var tick = 0
            while (nativeClient.isRunning) {
                val batch = try {
                    nativeClient.readLog()
                } catch (_: Exception) {
                    ""
                }
                if (batch.isNotBlank()) {
                    batch.lineSequence().forEach { LogUtil.w(AppConfig.TAG, "AmneziaWG: $it") }
                }
                if (tick % 3 == 0) {
                    val stats = try {
                        nativeClient.stats()
                    } catch (_: Exception) {
                        ""
                    }
                    if (stats.isNotBlank()) {
                        LogUtil.w(AppConfig.TAG, "AmneziaWG stats: $stats")
                    }
                }
                tick++
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "amneziawg-log"
            isDaemon = true
            start()
        }
    }

    private fun findAvailablePort(preferred: Int): Int {
        for (port in preferred..preferred + 100) {
            try {
                ServerSocket(port).use { return port }
            } catch (_: Exception) {
                // port in use, try next
            }
        }
        LogUtil.e(AppConfig.TAG, "AmneziaWgManager: no available port in range $preferred..${preferred + 100}")
        return preferred
    }

    private fun buildConfigJson(config: ProfileItem): String {
        val root = JsonObject()
        root.addProperty("privateKey", config.secretKey.orEmpty())
        root.add("addresses", JsonArray().apply {
            splitAddresses(config.localAddress).forEach { add(it) }
        })
        if (!config.dns.isNullOrBlank()) {
            root.add("dns", JsonArray().apply {
                splitValues(config.dns).forEach { add(it) }
            })
        }
        root.addProperty("mtu", config.mtu ?: AppConfig.WIREGUARD_LOCAL_MTU.toInt())
        root.add("peer", JsonObject().apply {
            addProperty("publicKey", config.publicKey.orEmpty())
            if (!config.preSharedKey.isNullOrBlank()) {
                addProperty("preSharedKey", config.preSharedKey)
            }
            addProperty(
                "endpoint",
                resolveEndpoint(config.server.orEmpty(), config.serverPort.orEmpty())
            )
            if (!config.allowedIps.isNullOrBlank()) {
                add("allowedIps", JsonArray().apply {
                    splitValues(config.allowedIps).forEach { add(it) }
                })
            }
            if (!config.persistentKeepalive.isNullOrBlank()) {
                addProperty("persistentKeepalive", config.persistentKeepalive)
            }
        })
        val awg = JsonObject()
        addInt(awg, "jc", config.awgJc)
        addInt(awg, "jmin", config.awgJmin)
        addInt(awg, "jmax", config.awgJmax)
        addInt(awg, "s1", config.awgS1)
        addInt(awg, "s2", config.awgS2)
        addInt(awg, "s3", config.awgS3)
        addInt(awg, "s4", config.awgS4)
        addString(awg, "h1", config.awgH1)
        addString(awg, "h2", config.awgH2)
        addString(awg, "h3", config.awgH3)
        addString(awg, "h4", config.awgH4)
        addString(awg, "i1", config.awgI1)
        addString(awg, "i2", config.awgI2)
        addString(awg, "i3", config.awgI3)
        addString(awg, "i4", config.awgI4)
        addString(awg, "i5", config.awgI5)
        addString(awg, "headerProtectionKey", config.awgHeaderProtectionKey)
        addString(awg, "contentPaddingAddition", config.awgContentPaddingAddition)
        addString(awg, "rekeyAfterTime", config.awgRekeyAfterTime)
        addString(awg, "rekeyTimeout", config.awgRekeyTimeout)
        addString(awg, "rejectAfterTime", config.awgRejectAfterTime)
        addString(awg, "keepaliveTimeout", config.awgKeepaliveTimeout)
        addString(awg, "maxHandshakeAttempts", config.awgMaxHandshakeAttempts)
        config.awgRandomTrailers?.let { awg.addProperty("randomTrailers", it) }
        config.awgDisableCookies?.let { awg.addProperty("disableCookies", it) }
        if (awg.size() > 0) {
            root.add("awg", awg)
        }
        return root.toString()
    }

    private fun resolveEndpoint(host: String, port: String): String {
        if (host.isBlank() || Utils.isPureIpAddress(host)) {
            return Utils.getIpv6Address(host) + ":" + port
        }
        val resolved = runCatching { InetAddress.getAllByName(host) }.getOrNull()
        val chosen = resolved?.firstOrNull { it is Inet4Address } ?: resolved?.firstOrNull()
        val address = chosen?.hostAddress ?: host
        return Utils.getIpv6Address(address) + ":" + port
    }

    private fun splitAddresses(value: String?): List<String> {
        val raw = splitValues(value).filter { it.isNotBlank() }
        if (raw.isEmpty()) return listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4)
        val ipv6Enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true
        return if (ipv6Enabled) raw else raw.filter { !it.contains(":") }.ifEmpty { raw }
    }

    private fun splitValues(value: String?): List<String> {
        return value.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun addInt(target: JsonObject, key: String, value: Int?) {
        if (value != null) target.addProperty(key, value)
    }

    private fun addString(target: JsonObject, key: String, value: String?) {
        if (!value.isNullOrBlank()) target.addProperty(key, value)
    }
}
