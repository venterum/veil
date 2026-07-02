package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils

object OlcrtcFmt : FmtBase() {

    fun parse(text: String): ProfileItem? {
        val uri = text.trim()
        if (!uri.startsWith(AppConfig.OLCRTC)) return null

        val payload = uri.removePrefix(AppConfig.OLCRTC)
        val config = ProfileItem.create(EConfigType.OLCRTC)

        var remaining = payload

        // <Auth> - before ?
        val qIdx = remaining.indexOf('?')
        if (qIdx <= 0) return null
        config.olcrtcCarrier = remaining.substring(0, qIdx).takeIf { it.isNotBlank() } ?: return null
        remaining = remaining.substring(qIdx + 1)

        // <Transport> - before < or @
        val transportEnd = remaining.indexOfFirst { it == '<' || it == '@' }
        if (transportEnd <= 0) return null
        config.olcrtcTransport = remaining.substring(0, transportEnd).takeIf { it.isNotBlank() } ?: return null
        remaining = remaining.substring(transportEnd)

        // optional <key=value&...> in angle brackets
        if (remaining.startsWith("<")) {
            val close = remaining.indexOf('>')
            if (close > 1) {
                val paramsStr = remaining.substring(1, close)
                remaining = remaining.substring(close + 1)
                config.olcrtcEngine = paramsStr.takeIf { it.isNotBlank() }
                extractClientIdFromEngine(paramsStr, config)
            } else {
                return null
            }
        }

        // @<RoomID>
        if (!remaining.startsWith("@")) return null
        remaining = remaining.substring(1)
        val hashIdx = remaining.indexOf('#')
        if (hashIdx < 0) return null
        config.olcrtcRoomId = remaining.substring(0, hashIdx).takeIf { it.isNotBlank() } ?: return null
        remaining = remaining.substring(hashIdx + 1)

        // #<EncryptionKey>$<MIMO>
        val dollarIdx = remaining.indexOf('$')
        if (dollarIdx >= 0) {
            config.olcrtcKeyHex = remaining.substring(0, dollarIdx).takeIf { it.isNotBlank() }
            config.remarks = Utils.decodeURIComponent(remaining.substring(dollarIdx + 1).takeIf { it.isNotBlank() } ?: "")
        } else {
            config.olcrtcKeyHex = remaining.takeIf { it.isNotBlank() }
        }

        // Fallback: use roomId as remarks if empty
        if (config.remarks.isBlank()) {
            config.remarks = config.olcrtcRoomId.orEmpty()
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val sb = StringBuilder()
        sb.append(config.olcrtcCarrier.orEmpty())
        sb.append("?")
        sb.append(config.olcrtcTransport.orEmpty())

        val engineWithClient = buildEngineWithClient(config)
        engineWithClient?.let {
            sb.append("<")
            sb.append(it)
            sb.append(">")
        }

        sb.append("@")
        sb.append(config.olcrtcRoomId.orEmpty())
        sb.append("#")
        sb.append(config.olcrtcKeyHex.orEmpty())

        if (!config.remarks.isNullOrBlank()) {
            sb.append("$")
            sb.append(Utils.encodeURIComponent(config.remarks))
        }

        return sb.toString()
    }

    private fun extractClientIdFromEngine(engine: String, config: ProfileItem) {
        engine.split("&").forEach { pair ->
            val kv = pair.split("=", limit = 2)
            val key = kv.getOrNull(0)?.trim()
            val value = kv.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            when (key) {
                "clientId" -> if (config.olcrtcClientId == null) config.olcrtcClientId = value
                "serverUrl" -> if (config.olcrtcServerUrl == null) config.olcrtcServerUrl = value
            }
        }
    }

    private fun buildEngineWithClient(config: ProfileItem): String? {
        val parts = mutableListOf<String>()
        config.olcrtcEngine?.nullIfBlank()?.let { parts.add(it) }
        config.olcrtcClientId?.nullIfBlank()?.let { cid ->
            val engine = config.olcrtcEngine.orEmpty()
            if (!engine.contains("clientId=")) parts.add("clientId=$cid")
        }
        config.olcrtcServerUrl?.nullIfBlank()?.let { url ->
            val all = parts.joinToString("&")
            if (!all.contains("serverUrl=")) parts.add("serverUrl=$url")
        }
        return parts.joinToString("&").takeIf { it.isNotBlank() }
    }
}
