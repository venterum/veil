package com.v2ray.ang.fmt

import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.json.JSONObject

/**
 * OpenFlux share-URI codec. The payload is a URL-safe base64 JSON object:
 * `openflux://<base64>`. Kept opaque for forward compatibility.
 */
object OpenFluxFmt {

    private const val FLAG = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun parse(text: String): ProfileItem? {
        val uri = text.trim()
        if (!uri.startsWith(AppConfig.OPENFLUX)) return null

        val json = try {
            String(Base64.decode(uri.removePrefix(AppConfig.OPENFLUX), FLAG))
        } catch (_: Exception) {
            return null
        }
        val obj = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }

        val config = ProfileItem.create(EConfigType.OPENFLUX)
        config.openfluxTransport = obj.optString("transport").takeIf { it.isNotBlank() } ?: "yandex"
        config.openfluxUrl = obj.optString("url").takeIf { it.isNotBlank() }
        config.openfluxMaxToken = obj.optString("token").takeIf { it.isNotBlank() }
        config.openfluxMaxUid = obj.optString("uid").takeIf { it.isNotBlank() }
        config.openfluxKey = obj.optString("key").takeIf { it.isNotBlank() }
        config.serverPort = obj.optString("socks").takeIf { it.isNotBlank() } ?: AppConfig.PORT_OPENFLUX_SOCKS
        config.remarks = obj.optString("remarks").takeIf { it.isNotBlank() } ?: "OpenFlux"

        return config
    }

    fun toUri(config: ProfileItem): String {
        val obj = JSONObject()
        obj.put("transport", config.openfluxTransport ?: "yandex")
        config.openfluxUrl?.takeIf { it.isNotBlank() }?.let { obj.put("url", it) }
        config.openfluxMaxToken?.takeIf { it.isNotBlank() }?.let { obj.put("token", it) }
        config.openfluxMaxUid?.takeIf { it.isNotBlank() }?.let { obj.put("uid", it) }
        config.openfluxKey?.takeIf { it.isNotBlank() }?.let { obj.put("key", it) }
        config.serverPort?.takeIf { it.isNotBlank() }?.let { obj.put("socks", it) }
        config.remarks.takeIf { it.isNotBlank() }?.let { obj.put("remarks", it) }
        return Base64.encodeToString(obj.toString().toByteArray(), FLAG)
    }
}
