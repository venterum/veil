package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.extension.removeWhiteSpace
import com.v2ray.ang.util.Utils
import java.net.URI

/**
 * AmneziaWG URI and wg-quick configuration support.
 *
 * `amneziawg://` is used for single-line sharing / QR codes and subscriptions.
 * Raw `.conf` files are handled by [WgConfParser].
 */
object AmneziaWgFmt : FmtBase() {

    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.AMNEZIAWG)
        val uri = runCatching { URI(Utils.fixIllegalUrl(str)) }.getOrNull() ?: return null
        if (uri.rawQuery.isNullOrEmpty()) return null
        val query = getQueryParam(uri)

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).ifEmpty { "AmneziaWG" }
        config.server = uri.idnHost
        if (uri.port <= 0) return null
        config.serverPort = uri.port.toString()
        config.secretKey = uri.userInfo.orEmpty()
        config.publicKey = query["publickey"].orEmpty()
        if (config.secretKey.isNullOrBlank() || config.publicKey.isNullOrBlank()) return null

        config.localAddress = query["address"] ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
        config.preSharedKey = query["presharedkey"]?.nullIfBlank()
        config.dns = query["dns"]?.nullIfBlank()
        config.allowedIps = query["allowedips"]?.nullIfBlank()
        config.persistentKeepalive = query["keepalive"]?.nullIfBlank()
        config.mtu = Utils.parseInt(query["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
        config.reserved = "0,0,0"

        config.awgJc = query["jc"]?.toIntOrNull()
        config.awgJmin = query["jmin"]?.toIntOrNull()
        config.awgJmax = query["jmax"]?.toIntOrNull()
        config.awgS1 = query["s1"]?.toIntOrNull()
        config.awgS2 = query["s2"]?.toIntOrNull()
        config.awgS3 = query["s3"]?.toIntOrNull()
        config.awgS4 = query["s4"]?.toIntOrNull()
        config.awgH1 = query["h1"]?.nullIfBlank()
        config.awgH2 = query["h2"]?.nullIfBlank()
        config.awgH3 = query["h3"]?.nullIfBlank()
        config.awgH4 = query["h4"]?.nullIfBlank()
        config.awgI1 = query["i1"]?.nullIfBlank()
        config.awgI2 = query["i2"]?.nullIfBlank()
        config.awgI3 = query["i3"]?.nullIfBlank()
        config.awgI4 = query["i4"]?.nullIfBlank()
        config.awgI5 = query["i5"]?.nullIfBlank()
        config.awgHeaderProtectionKey = query["headerprotectionkey"]?.nullIfBlank()
        config.awgContentPaddingAddition = query["contentpaddingaddition"]?.nullIfBlank()
        config.awgRekeyAfterTime = query["rekeyaftertime"]?.nullIfBlank()
        config.awgRekeyTimeout = query["rekeytimeout"]?.nullIfBlank()
        config.awgRejectAfterTime = query["rejectaftertime"]?.nullIfBlank()
        config.awgKeepaliveTimeout = query["keepalivetimeout"]?.nullIfBlank()
        config.awgMaxHandshakeAttempts = query["maxhandshakeattempts"]?.nullIfBlank()
        config.awgRandomTrailers = query["randomtrailers"]?.toBooleanStrictOrNull()
        config.awgDisableCookies = query["disablecookies"]?.toBooleanStrictOrNull()

        return config
    }

    fun toUri(config: ProfileItem): String {
        val query = HashMap<String, String>()
        query["publickey"] = config.publicKey.orEmpty()
        query["address"] = config.localAddress.removeWhiteSpace().orEmpty()
        if (!config.preSharedKey.isNullOrBlank()) {
            query["presharedkey"] = config.preSharedKey.removeWhiteSpace().orEmpty()
        }
        if (!config.dns.isNullOrBlank()) {
            query["dns"] = config.dns.removeWhiteSpace().orEmpty()
        }
        if (!config.allowedIps.isNullOrBlank()) {
            query["allowedips"] = config.allowedIps.removeWhiteSpace().orEmpty()
        }
        if (!config.persistentKeepalive.isNullOrBlank()) {
            query["keepalive"] = config.persistentKeepalive.orEmpty()
        }
        query["mtu"] = (config.mtu ?: AppConfig.WIREGUARD_LOCAL_MTU).toString()

        putInt(query, "jc", config.awgJc)
        putInt(query, "jmin", config.awgJmin)
        putInt(query, "jmax", config.awgJmax)
        putInt(query, "s1", config.awgS1)
        putInt(query, "s2", config.awgS2)
        putInt(query, "s3", config.awgS3)
        putInt(query, "s4", config.awgS4)
        putString(query, "h1", config.awgH1)
        putString(query, "h2", config.awgH2)
        putString(query, "h3", config.awgH3)
        putString(query, "h4", config.awgH4)
        putString(query, "i1", config.awgI1)
        putString(query, "i2", config.awgI2)
        putString(query, "i3", config.awgI3)
        putString(query, "i4", config.awgI4)
        putString(query, "i5", config.awgI5)
        putString(query, "headerprotectionkey", config.awgHeaderProtectionKey)
        putString(query, "contentpaddingaddition", config.awgContentPaddingAddition)
        putString(query, "rekeyaftertime", config.awgRekeyAfterTime)
        putString(query, "rekeytimeout", config.awgRekeyTimeout)
        putString(query, "rejectaftertime", config.awgRejectAfterTime)
        putString(query, "keepalivetimeout", config.awgKeepaliveTimeout)
        putString(query, "maxhandshakeattempts", config.awgMaxHandshakeAttempts)
        config.awgRandomTrailers?.let { query["randomtrailers"] = it.toString() }
        config.awgDisableCookies?.let { query["disablecookies"] = it.toString() }

        return toUri(config, config.secretKey, query)
    }

    /** Serializes the profile as a wg-quick / AmneziaWG `.conf` file. */
    fun toConf(config: ProfileItem): String {
        val builder = StringBuilder()
        builder.appendLine("[Interface]")
        builder.appendLine("PrivateKey = ${config.secretKey.orEmpty()}")
        if (!config.localAddress.isNullOrBlank()) {
            builder.appendLine("Address = ${config.localAddress}")
        }
        if (!config.dns.isNullOrBlank()) {
            builder.appendLine("DNS = ${config.dns}")
        }
        builder.appendLine("MTU = ${config.mtu ?: AppConfig.WIREGUARD_LOCAL_MTU}")
        putConfInt(builder, "Jc", config.awgJc)
        putConfInt(builder, "Jmin", config.awgJmin)
        putConfInt(builder, "Jmax", config.awgJmax)
        putConfInt(builder, "S1", config.awgS1)
        putConfInt(builder, "S2", config.awgS2)
        putConfInt(builder, "S3", config.awgS3)
        putConfInt(builder, "S4", config.awgS4)
        putConfString(builder, "H1", config.awgH1)
        putConfString(builder, "H2", config.awgH2)
        putConfString(builder, "H3", config.awgH3)
        putConfString(builder, "H4", config.awgH4)
        putConfString(builder, "I1", config.awgI1)
        putConfString(builder, "I2", config.awgI2)
        putConfString(builder, "I3", config.awgI3)
        putConfString(builder, "I4", config.awgI4)
        putConfString(builder, "I5", config.awgI5)
        putConfString(builder, "HeaderProtectionKey", config.awgHeaderProtectionKey)
        putConfString(builder, "ContentPaddingAddition", config.awgContentPaddingAddition)
        putConfString(builder, "RekeyAfterTime", config.awgRekeyAfterTime)
        putConfString(builder, "RekeyTimeout", config.awgRekeyTimeout)
        putConfString(builder, "RejectAfterTime", config.awgRejectAfterTime)
        putConfString(builder, "KeepaliveTimeout", config.awgKeepaliveTimeout)
        putConfString(builder, "MaxHandshakeAttempts", config.awgMaxHandshakeAttempts)
        config.awgRandomTrailers?.let { builder.appendLine("RandomTrailers = ${if (it) "on" else "off"}") }
        config.awgDisableCookies?.let { builder.appendLine("DisableCookies = ${if (it) "on" else "off"}") }
        builder.appendLine()
        builder.appendLine("[Peer]")
        builder.appendLine("PublicKey = ${config.publicKey.orEmpty()}")
        if (!config.preSharedKey.isNullOrBlank()) {
            builder.appendLine("PresharedKey = ${config.preSharedKey}")
        }
        builder.appendLine("Endpoint = ${Utils.getIpv6Address(config.server)}:${config.serverPort}")
        if (!config.allowedIps.isNullOrBlank()) {
            builder.appendLine("AllowedIPs = ${config.allowedIps}")
        }
        if (!config.persistentKeepalive.isNullOrBlank()) {
            builder.appendLine("PersistentKeepalive = ${config.persistentKeepalive}")
        }
        return builder.toString()
    }

    private fun putInt(query: HashMap<String, String>, key: String, value: Int?) {
        if (value != null) query[key] = value.toString()
    }

    private fun putString(query: HashMap<String, String>, key: String, value: String?) {
        if (!value.isNullOrBlank()) query[key] = value
    }

    private fun putConfInt(builder: StringBuilder, key: String, value: Int?) {
        if (value != null) builder.appendLine("$key = $value")
    }

    private fun putConfString(builder: StringBuilder, key: String, value: String?) {
        if (!value.isNullOrBlank()) builder.appendLine("$key = $value")
    }
}
