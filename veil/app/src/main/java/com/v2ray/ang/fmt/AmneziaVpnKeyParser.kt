package com.v2ray.ang.fmt

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Imports the `vpn://` keys produced by the AmneziaVPN client.
 *
 * The payload is base64url (unpadded) of either a JSON document or Qt's
 * qCompress output (4-byte big-endian size followed by a zlib stream). A
 * connection export stores the raw AmneziaWG `.conf` in
 * `containers[].awg.last_config.config`.
 */
object AmneziaVpnKeyParser {

    private const val PREFIX = "vpn://"
    private const val MAX_DECODED_BYTES = 4 * 1024 * 1024

    fun isVpnKey(str: String): Boolean = str.trim().startsWith(PREFIX, ignoreCase = true)

    fun parse(str: String): ProfileItem? {
        val payload = decodePayload(str) ?: return null
        val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return null
        val found = findConfig(root) ?: return null
        val config = WgConfParser.parse(
            found.conf,
            forcedType = if (found.awg) EConfigType.AMNEZIAWG else null
        ) ?: return null
        val description = root.get("description")?.takeIf { it.isJsonPrimitive }?.asString
            ?: root.get("hostName")?.takeIf { it.isJsonPrimitive }?.asString
        if (!description.isNullOrBlank()) {
            config.remarks = description
        }
        return config
    }

    private data class FoundConfig(val conf: String, val awg: Boolean)

    private fun findConfig(root: JsonObject): FoundConfig? {
        root.getAsJsonObject("awg")?.let { awg ->
            extractConf(awg)?.let { return FoundConfig(it, true) }
        }
        val containers = root.get("containers")
        if (containers != null) {
            val entries = mutableListOf<Pair<String, JsonObject>>()
            if (containers.isJsonArray) {
                containers.asJsonArray.forEachIndexed { index, element ->
                    val container = element as? JsonObject ?: return@forEachIndexed
                    entries.add((containerName(container) ?: index.toString()) to container)
                }
            } else if (containers.isJsonObject) {
                containers.asJsonObject.entrySet().forEach { entry ->
                    val container = entry.value as? JsonObject ?: return@forEach
                    entries.add(entry.key to container)
                }
            }

            // Prefer an AmneziaWG container, then a plain WireGuard one.
            entries.firstOrNull { (name, container) -> isAwgContainer(name, container) }?.let { (_, container) ->
                extractConf(container)?.let { return FoundConfig(it, true) }
            }
            entries.firstOrNull { (name, container) -> isWireGuardContainer(name, container) }?.let { (_, container) ->
                extractConf(container)?.let { return FoundConfig(it, false) }
            }
        }
        return null
    }

    private fun isAwgContainer(name: String, container: JsonObject): Boolean {
        return name.contains("awg", ignoreCase = true) ||
                container.has("awg") ||
                container.getAsJsonObject("protocolConfig")?.has("awg") == true
    }

    private fun isWireGuardContainer(name: String, container: JsonObject): Boolean {
        return name.contains("wireguard", ignoreCase = true) ||
                container.has("wireguard") ||
                container.getAsJsonObject("protocolConfig")?.has("wireguard") == true
    }

    private fun containerName(container: JsonObject): String? {
        return container.get("container")?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun extractConf(container: JsonObject): String? {
        val protocol = container.getAsJsonObject("awg")
            ?: container.getAsJsonObject("wireguard")
            ?: container
        val protocolConfig = protocol.getAsJsonObject("protocolConfig")
            ?: container.getAsJsonObject("protocolConfig")
        for (candidate in listOf(protocol, protocolConfig)) {
            if (candidate == null) continue
            lastConfig(candidate)?.let { last ->
                last.get("config")?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
                if (last.has("config") && last.get("config").isJsonObject) {
                    return last.getAsJsonObject("config").toString()
                }
            }
            protocolConfig?.get("config")?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
            protocol.get("config")?.takeIf { it.isJsonPrimitive }?.asString?.let { return it }
        }
        return null
    }

    private fun lastConfig(protocol: JsonObject): JsonObject? {
        val last = protocol.get("last_config") ?: return null
        if (last.isJsonObject) return last.asJsonObject
        if (!last.isJsonPrimitive) return null
        return runCatching { JsonParser.parseString(last.asString).asJsonObject }.getOrNull()
    }

    private fun decodePayload(str: String): String? {
        val trimmed = str.trim()
        val payload = when {
            trimmed.startsWith(PREFIX, ignoreCase = true) -> trimmed.substring(PREFIX.length)
            else -> trimmed
        }
        if (payload.isBlank()) return null
        val bytes = decodeBase64(payload) ?: return null
        val inflated = tryInflate(bytes) ?: bytes
        return inflated.toString(Charsets.UTF_8)
    }

    private fun decodeBase64(payload: String): ByteArray? {
        val decoders = listOf(
            runCatching { Base64.getUrlDecoder() }.getOrNull(),
            runCatching { Base64.getDecoder() }.getOrNull()
        )
        decoders.filterNotNull().forEach { decoder ->
            runCatching { decoder.decode(payload) }.getOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Qt's qCompress prepends the uncompressed size to a zlib stream. Some
     * exporters also emit raw JSON, so a failed inflate is not fatal.
     */
    private fun tryInflate(bytes: ByteArray): ByteArray? {
        if (bytes.size <= 4) return null
        val candidates = listOf(4, 0)
        candidates.forEach { offset ->
            runCatching {
                val input = ByteArrayInputStream(bytes, offset, bytes.size - offset)
                InflaterInputStream(input).use { inflater ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= MAX_DECODED_BYTES) {
                        val read = inflater.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    if (output.size() > MAX_DECODED_BYTES) return null
                    output.toByteArray()
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }
}
