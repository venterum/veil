package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import openflux.Client
import openflux.Openflux
import openflux.SocketProtector
import java.net.ServerSocket

/**
 * Owns the OpenFlux client runtime. OpenFlux exposes a loopback SOCKS5 listener
 * that the Xray core consumes as a plain SOCKS outbound (see
 * [CoreOutboundBuilder.toOutboundOpenflux]).
 */
object OpenFluxManager {

    private const val WAIT_READY_MS = 15000L
    private const val PING_TIMEOUT_MS = 15000L

    const val TRANSPORT_YANDEX = "yandex"
    const val TRANSPORT_VYANDEX = "vyandex"
    const val TRANSPORT_ONEME = "oneme"
    const val TRANSPORT_CUPSONLINE = "cupsonline"

    val TRANSPORTS = listOf(TRANSPORT_YANDEX, TRANSPORT_VYANDEX, TRANSPORT_ONEME, TRANSPORT_CUPSONLINE)

    @Volatile
    var socketProtector: ((Int) -> Boolean)? = null

    private val client: Client by lazy { Client() }

    private val protector = object : SocketProtector {
        override fun protect(fd: Long): Boolean =
            socketProtector?.invoke(fd.toInt()) ?: true
    }

    @Volatile
    private var logPump: Thread? = null

    val isRunning: Boolean
        get() = try {
            client.isRunning
        } catch (e: Exception) {
            false
        }

    fun start(context: Context, config: ProfileItem): Boolean {
        if (isRunning) {
            LogUtil.i(AppConfig.TAG, "OpenFluxManager: already running")
            return true
        }

        val transport = config.openfluxTransport?.takeIf { it.isNotBlank() } ?: TRANSPORT_YANDEX
        val url = config.openfluxUrl.orEmpty().trim()
        if (url.isBlank()) {
            LogUtil.e(AppConfig.TAG, "OpenFluxManager: url is empty")
            return false
        }
        if (transport == TRANSPORT_ONEME && config.openfluxMaxToken.isNullOrBlank()) {
            LogUtil.e(AppConfig.TAG, "OpenFluxManager: MAX token is empty")
            return false
        }

        val preferredPort = (config.serverPort ?: AppConfig.PORT_OPENFLUX_SOCKS)
            .toIntOrNull() ?: AppConfig.PORT_OPENFLUX_SOCKS.toInt()
        val socksPort = findAvailablePort(preferredPort)
        config.serverPort = socksPort.toString()

        return try {
            client.setProtector(protector)
            client.setTransport(transport)
            client.setURL(url)
            client.setMaxCredentials(config.openfluxMaxToken.orEmpty(), config.openfluxMaxUid?.toLongOrNull() ?: 0L)
            client.setEncryptionKey(config.openfluxKey.orEmpty())
            client.setSocks(AppConfig.LOOPBACK, socksPort.toLong())
            client.setDebug(false)

            LogUtil.d(
                AppConfig.TAG,
                "OpenFluxManager: start transport=$transport socks=${AppConfig.LOOPBACK}:$socksPort url=${url.take(48)}"
            )
            client.start()
            // Transports connect asynchronously (Yandex fetches its document in
            // the background). A slow/blocked handshake must not tear down the
            // whole VPN: the SOCKS listener is already bound, so let Xray start
            // and report "connecting" until the link comes up.
            try {
                client.waitReady(WAIT_READY_MS)
                LogUtil.i(AppConfig.TAG, "OpenFluxManager: connected on ${AppConfig.LOOPBACK}:$socksPort")
            } catch (e: Exception) {
                LogUtil.w(
                    AppConfig.TAG,
                    "OpenFluxManager: transport not ready yet (${e.message}); continuing"
                )
            }
            startLogPump()
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OpenFluxManager: start failed: ${e.javaClass.simpleName}: ${e.message}", e)
            try {
                client.stop()
            } catch (_: Exception) {
            }
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
            client.stop()
            LogUtil.i(AppConfig.TAG, "OpenFluxManager: stopped")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OpenFluxManager: stop failed", e)
        }
    }

    /** Measures HTTP latency through a temporary OpenFlux client. Returns -1 on failure. */
    fun ping(config: ProfileItem, url: String): Long {
        return try {
            val probe = configureProbe(config)
            probe.ping(PING_TIMEOUT_MS, url)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "OpenFluxManager: ping failed", e)
            -1L
        }
    }

    /** Starts a temporary client and returns "ok" or an error message. */
    fun check(config: ProfileItem): String {
        return try {
            configureProbe(config).check(PING_TIMEOUT_MS)
        } catch (e: Exception) {
            e.message ?: "check failed"
        }
    }

    fun readLog(): String = try {
        Openflux.readLog()
    } catch (e: Exception) {
        ""
    }

    /** Pipes native OpenFlux logs into logcat while the client is running. */
    private fun startLogPump() {
        if (logPump?.isAlive == true) return
        logPump = Thread {
            while (isRunning) {
                val batch = readLog()
                if (batch.isNotBlank()) {
                    batch.lineSequence().forEach { LogUtil.w(AppConfig.TAG, "OpenFlux: $it") }
                }
                try {
                    Thread.sleep(1500)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "openflux-log"
            isDaemon = true
            start()
        }
    }

    private fun configureProbe(config: ProfileItem): Client {
        val probe = Client()
        val preferredPort = (config.serverPort ?: AppConfig.PORT_OPENFLUX_SOCKS)
            .toIntOrNull() ?: AppConfig.PORT_OPENFLUX_SOCKS.toInt()
        val port = findAvailablePort(preferredPort)
        probe.setTransport(config.openfluxTransport?.takeIf { it.isNotBlank() } ?: TRANSPORT_YANDEX)
        probe.setURL(config.openfluxUrl.orEmpty().trim())
        probe.setMaxCredentials(config.openfluxMaxToken.orEmpty(), config.openfluxMaxUid?.toLongOrNull() ?: 0L)
        probe.setEncryptionKey(config.openfluxKey.orEmpty())
        probe.setSocks(AppConfig.LOOPBACK, port.toLong())
        return probe
    }

    private fun findAvailablePort(preferred: Int): Int {
        for (port in preferred..preferred + 100) {
            try {
                ServerSocket(port).use { return port }
            } catch (_: Exception) {
                // port in use, try next
            }
        }
        LogUtil.e(AppConfig.TAG, "OpenFluxManager: no available port in range $preferred..${preferred + 100}")
        return preferred
    }
}
