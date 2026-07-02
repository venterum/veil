package com.v2ray.ang.core

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import mobile.LogWriter
import mobile.Mobile
import mobile.SocketProtector
import java.net.ServerSocket
import kotlin.random.Random

object OlcrtcManager {

    @Volatile
    var socketProtector: ((Int) -> Boolean)? = null

    private var logWriterInstalled = false

    private val protector = object : SocketProtector {
        override fun protect(fd: Long): Boolean {
            return socketProtector?.invoke(fd.toInt()) ?: true
        }
    }

    private val logWriter = object : LogWriter {
        override fun writeLog(msg: String) {
            val line = msg.trimEnd()
            val priority = when {
                line.startsWith("[D]") -> Log.DEBUG
                line.startsWith("[I]") -> Log.INFO
                line.startsWith("[Warning]") -> Log.WARN
                line.startsWith("[Error]") -> Log.ERROR
                else -> Log.INFO
            }
            Log.println(priority, "GoLog", line)
        }
    }

    val isRunning: Boolean
        get() = try {
            Mobile.isRunning()
        } catch (e: Exception) {
            false
        }

    fun start(config: ProfileItem): Boolean {
        if (isRunning) {
            LogUtil.i(AppConfig.TAG, "OlcrtcManager: already running, returning success")
            return true
        }

        val carrier = config.olcrtcCarrier?.takeIf { it.isNotBlank() } ?: "jitsi"
        val transport = config.olcrtcTransport?.takeIf { it.isNotBlank() } ?: "datachannel"
        val roomId = normalizeRoomURL(carrier, config.olcrtcRoomId, config.olcrtcServerUrl)
        val hadClientId = config.olcrtcClientId?.isNotBlank() == true
        val clientId = config.olcrtcClientId?.takeIf { it.isNotBlank() } ?: persistentDeviceId()
        val keyHex = config.olcrtcKeyHex ?: ""
        val preferredPort = (config.serverPort ?: AppConfig.PORT_OLCRTC_SOCKS).toIntOrNull() ?: AppConfig.PORT_OLCRTC_SOCKS.toInt()
        val socksPort = findAvailablePort(preferredPort)
        val socksHost = AppConfig.LOOPBACK
        val (fps, batchSize) = parseEngine(config.olcrtcEngine)

        if (roomId.isEmpty()) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: roomId is empty")
            return false
        }
        if (keyHex.isEmpty()) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: keyHex is empty")
            return false
        }

        if (!hadClientId) {
            config.olcrtcClientId = clientId
        }
        config.serverPort = socksPort.toString()

        try {
            installCallbacks()
            Mobile.setTransport(transport)
            Mobile.setDNS("1.1.1.1:53")
            Mobile.setSocksListenHost(socksHost)
            Mobile.setVP8Options(fps.toLong(), batchSize.toLong())

            LogUtil.d(AppConfig.TAG, "OlcrtcManager: startWithTransport carrier=$carrier transport=$transport room=$roomId client=$clientId key=${keyHex.take(8)}... socks=${socksHost}:${socksPort}")
            Mobile.startWithTransport(
                carrier, transport, roomId, clientId, keyHex,
                socksPort.toLong(), "", ""
            )

            LogUtil.d(AppConfig.TAG, "OlcrtcManager: waitReady (15s)")
            Mobile.waitReady(15000)
            LogUtil.i(AppConfig.TAG, "OlcrtcManager: started on ${socksHost}:${socksPort}")
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: start failed: ${e.javaClass.simpleName}: ${e.message}", e)
            return false
        }
    }

    fun stop() {
        try {
            Mobile.stop()
            LogUtil.i(AppConfig.TAG, "OlcrtcManager: stopped")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: stop failed", e)
        }
    }

    fun ping(config: ProfileItem, timeoutSec: Int = 5): Boolean {
        val carrier = config.olcrtcCarrier?.takeIf { it.isNotBlank() } ?: "jitsi"
        val transport = config.olcrtcTransport?.takeIf { it.isNotBlank() } ?: "datachannel"
        val roomId = normalizeRoomURL(carrier, config.olcrtcRoomId, config.olcrtcServerUrl)
        val clientId = config.olcrtcClientId?.takeIf { it.isNotBlank() } ?: persistentDeviceId()
        val keyHex = config.olcrtcKeyHex ?: ""
        val socksPort = (config.serverPort ?: AppConfig.PORT_OLCRTC_SOCKS).toLongOrNull() ?: AppConfig.PORT_OLCRTC_SOCKS.toLong()

        return try {
            val result = Mobile.ping(
                carrier, transport, roomId, clientId, keyHex,
                socksPort, timeoutSec.toLong(), "", 0L, 0L
            )
            result > 0
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: ping failed: ${e.message}", e)
            false
        }
    }

    fun check(config: ProfileItem): String {
        val carrier = config.olcrtcCarrier?.takeIf { it.isNotBlank() } ?: "jitsi"
        val transport = config.olcrtcTransport?.takeIf { it.isNotBlank() } ?: "datachannel"
        val roomId = normalizeRoomURL(carrier, config.olcrtcRoomId, config.olcrtcServerUrl)
        val clientId = config.olcrtcClientId?.takeIf { it.isNotBlank() } ?: persistentDeviceId()
        val keyHex = config.olcrtcKeyHex ?: ""
        val socksPort = (config.serverPort ?: AppConfig.PORT_OLCRTC_SOCKS).toLongOrNull() ?: AppConfig.PORT_OLCRTC_SOCKS.toLong()

        return try {
            val result = Mobile.check(
                carrier, transport, roomId, clientId, keyHex,
                socksPort, 15L, 0L, 0L
            )
            if (result >= 0) "ok" else "error: $result"
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OlcrtcManager: check failed: ${e.message}", e)
            e.message ?: "check failed"
        }
    }

    private fun installCallbacks() {
        Mobile.setProtector(protector)
        Mobile.setProviders()
        if (!logWriterInstalled) {
            try {
                Mobile.setLogWriter(logWriter)
                logWriterInstalled = true
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "OlcrtcManager: failed to install LogWriter", e)
            }
        }
    }

    private fun persistentDeviceId(): String {
        val key = "olcrtc_device_id"
        val stored = MmkvManager.decodeSettingsString(key)
        if (!stored.isNullOrBlank()) return stored
        val generated = generateInstallId()
        MmkvManager.encodeSettings(key, generated)
        return generated
    }

    private fun generateInstallId(): String {
        return "install-" + Random.nextBytes(16).joinToString("") { b ->
            (b.toInt() and 0xff).toString(16).padStart(2, '0')
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
        LogUtil.e(AppConfig.TAG, "OlcrtcManager: no available port in range $preferred..${preferred + 100}")
        return preferred
    }

    private fun normalizeRoomURL(carrier: String, roomId: String?, serverUrl: String?): String {
        val room = roomId ?: ""
        if (room.isEmpty()) return ""
        if (room.contains("://") || room.contains("/")) return room
        val server = serverUrl?.takeIf { it.isNotBlank() } ?: when (carrier) {
            "jitsi" -> "meet.egovm.ru"
            "telemost" -> "telemost.yandex.ru/j"
            else -> return room
        }
        return "https://$server/$room"
    }

    /**
     * Returns the provider domain derived from the OLCRTC room identifier.
     *
     * @param carrier The transport carrier.
     * @param roomId The room identifier or full room URL.
     * @param serverUrl Optional custom server URL.
     * @return The provider domain, or an empty string if unavailable.
     */
    fun providerUrl(carrier: String, roomId: String?, serverUrl: String?): String {
        val fullUrl = normalizeRoomURL(carrier, roomId, serverUrl)
        if (fullUrl.isEmpty()) return ""
        return try {
            val url = java.net.URL(fullUrl)
            url.authority
        } catch (_: Exception) {
            fullUrl
        }
    }

    private fun parseEngine(engine: String?): Pair<Int, Int> {
        if (engine.isNullOrBlank()) return 0 to 0
        var fps = 0
        var batchSize = 0
        engine.split("&").forEach { pair ->
            val kv = pair.split("=", limit = 2)
            when (kv.getOrNull(0)?.trim()) {
                "fps" -> fps = kv.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                "batchSize" -> batchSize = kv.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            }
        }
        return fps to batchSize
    }
}
