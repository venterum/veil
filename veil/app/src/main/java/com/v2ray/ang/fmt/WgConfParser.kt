package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * Shared parser for wg-quick style configuration files.
 *
 * AmneziaWG is wire-compatible with WireGuard when no obfuscation parameters
 * are present, so both profile types use the same parser. The presence of any
 * AmneziaWG-only key selects [EConfigType.AMNEZIAWG].
 */
object WgConfParser {

    private val AWG_KEYS = setOf(
        "jc", "jmin", "jmax",
        "s1", "s2", "s3", "s4",
        "h1", "h2", "h3", "h4",
        "i1", "i2", "i3", "i4", "i5",
        "headerprotectionkey",
        "contentpaddingaddition",
        "rekeyaftertime",
        "rekeytimeout",
        "rejectaftertime",
        "keepalivetimeout",
        "maxhandshakeattempts",
        "randomtrailers",
        "disablecookies"
    )

    fun hasAwgKeys(str: String): Boolean {
        return parseSections(str).values.any { section ->
            section.keys.any { it in AWG_KEYS }
        }
    }

    /**
     * Parses a `[Interface]` / `[Peer]` configuration. When [forcedType] is
     * null, the profile type is detected from the obfuscation keys.
     */
    fun parse(str: String, forcedType: EConfigType? = null): ProfileItem? {
        val sections = parseSections(str)
        val interfaceSection = sections["interface"] ?: return null
        val peer = sections["peer"] ?: return null

        val endpoint = peer["endpoint"].orEmpty()
        val endpointParts = splitEndpoint(endpoint) ?: return null
        val privateKey = interfaceSection["privatekey"].orEmpty()
        val publicKey = peer["publickey"].orEmpty()
        val addresses = interfaceSection["address"].orEmpty()
        if (privateKey.isBlank() || publicKey.isBlank() || addresses.isBlank()) {
            return null
        }

        val detectedType = forcedType ?: if (sections.values.any { section ->
                section.keys.any { it in AWG_KEYS }
            }
        ) {
            EConfigType.AMNEZIAWG
        } else {
            EConfigType.WIREGUARD
        }

        val config = ProfileItem.create(detectedType)
        config.remarks = endpoint
        config.server = endpointParts.first
        config.serverPort = endpointParts.second.toString()
        config.secretKey = privateKey
        config.publicKey = publicKey
        config.preSharedKey = peer["presharedkey"]?.takeIf { it.isNotBlank() }
        config.localAddress = addresses.ifBlank { AppConfig.WIREGUARD_LOCAL_ADDRESS_V4 }
        config.dns = interfaceSection["dns"]?.takeIf { it.isNotBlank() }
        config.allowedIps = peer["allowedips"]?.takeIf { it.isNotBlank() }
        config.persistentKeepalive = peer["persistentkeepalive"]?.takeIf { it.isNotBlank() }
        config.mtu = interfaceSection["mtu"]?.toIntOrNull() ?: AppConfig.WIREGUARD_LOCAL_MTU.toInt()
        config.reserved = "0,0,0"

        if (detectedType == EConfigType.AMNEZIAWG) {
            config.awgJc = interfaceSection["jc"]?.toIntOrNull()
            config.awgJmin = interfaceSection["jmin"]?.toIntOrNull()
            config.awgJmax = interfaceSection["jmax"]?.toIntOrNull()
            config.awgS1 = interfaceSection["s1"]?.toIntOrNull()
            config.awgS2 = interfaceSection["s2"]?.toIntOrNull()
            config.awgS3 = interfaceSection["s3"]?.toIntOrNull()
            config.awgS4 = interfaceSection["s4"]?.toIntOrNull()
            config.awgH1 = interfaceSection["h1"]?.takeIf { it.isNotBlank() }
            config.awgH2 = interfaceSection["h2"]?.takeIf { it.isNotBlank() }
            config.awgH3 = interfaceSection["h3"]?.takeIf { it.isNotBlank() }
            config.awgH4 = interfaceSection["h4"]?.takeIf { it.isNotBlank() }
            config.awgI1 = interfaceSection["i1"]?.takeIf { it.isNotBlank() }
            config.awgI2 = interfaceSection["i2"]?.takeIf { it.isNotBlank() }
            config.awgI3 = interfaceSection["i3"]?.takeIf { it.isNotBlank() }
            config.awgI4 = interfaceSection["i4"]?.takeIf { it.isNotBlank() }
            config.awgI5 = interfaceSection["i5"]?.takeIf { it.isNotBlank() }
            config.awgHeaderProtectionKey = interfaceSection["headerprotectionkey"]?.takeIf { it.isNotBlank() }
            config.awgContentPaddingAddition = interfaceSection["contentpaddingaddition"]?.takeIf { it.isNotBlank() }
            config.awgRekeyAfterTime = interfaceSection["rekeyaftertime"]?.takeIf { it.isNotBlank() }
            config.awgRekeyTimeout = interfaceSection["rekeytimeout"]?.takeIf { it.isNotBlank() }
            config.awgRejectAfterTime = interfaceSection["rejectaftertime"]?.takeIf { it.isNotBlank() }
            config.awgKeepaliveTimeout = interfaceSection["keepalivetimeout"]?.takeIf { it.isNotBlank() }
            config.awgMaxHandshakeAttempts = interfaceSection["maxhandshakeattempts"]?.takeIf { it.isNotBlank() }
            config.awgRandomTrailers = parseBoolean(interfaceSection["randomtrailers"])
            config.awgDisableCookies = parseBoolean(interfaceSection["disablecookies"])
        }

        return config
    }

    /**
     * Returns a lower-cased section map. Repeated values (Address, DNS,
     * AllowedIPs) are appended, matching wg-quick behaviour.
     */
    private fun parseSections(str: String): Map<String, Map<String, String>> {
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        str.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                return@forEach
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                val name = line.substring(1, line.length - 1).trim().lowercase()
                current = sections.getOrPut(name) { linkedMapOf() }
                return@forEach
            }
            val separator = line.indexOf('=')
            if (separator <= 0) {
                return@forEach
            }
            val section = current ?: return@forEach
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            if (value.isEmpty()) {
                section[key] = ""
                return@forEach
            }
            when (key) {
                "address", "dns", "allowedips" -> {
                    val previous = section[key]
                    section[key] = if (previous.isNullOrBlank()) value else "$previous, $value"
                }

                else -> section[key] = value
            }
        }
        return sections
    }

    fun splitEndpoint(endpoint: String): Pair<String, Int>? {
        val value = endpoint.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close <= 1) return null
            val host = value.substring(1, close)
            val port = value.substring(close + 1).removePrefix(":").toIntOrNull() ?: return null
            return host to port
        }
        val separator = value.lastIndexOf(':')
        if (separator <= 0) return null
        val host = value.substring(0, separator)
        val port = value.substring(separator + 1).toIntOrNull() ?: return null
        return host to port
    }

    private fun parseBoolean(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "on", "true", "1", "yes", "t" -> true
            "off", "false", "0", "no", "f" -> false
            else -> null
        }
    }
}
