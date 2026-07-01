package com.v2ray.ang.util

object IPv6Util {

    fun compressIPv6(ip: String?, maxDisplayLength: Int = Int.MAX_VALUE): String? {
        val compressed = compressInternal(ip) ?: return ip
        if (compressed.length <= maxDisplayLength) return compressed
        return truncateMiddle(compressed, maxDisplayLength)
    }

    private fun compressInternal(ip: String?): String? {
        if (ip == null || !ip.contains(':')) return ip

        val expanded = expandIPv6(ip.lowercase()) ?: return ip

        val parts = expanded.split(':').toMutableList()

        val ipv4Suffix = if (parts.last().contains('.')) parts.removeLast() else null

        for (i in parts.indices) {
            if (parts[i].length > 4) return ip
            parts[i] = parts[i].trimStart('0').ifEmpty { "0" }
        }

        var bestStart = -1
        var bestLen = 0
        var curStart = -1
        var curLen = 0
        for (i in parts.indices) {
            if (parts[i] == "0") {
                if (curStart == -1) curStart = i
                curLen++
                if (curLen > bestLen) {
                    bestStart = curStart
                    bestLen = curLen
                }
            } else {
                curStart = -1
                curLen = 0
            }
        }

        val sb = StringBuilder()
        if (bestStart <= 0 && bestLen >= 2) {
            sb.append("::")
            parts.subList(bestStart + bestLen, parts.size).joinToString(":")
                .let { if (it.isNotEmpty()) sb.append(it) }
        } else if (bestStart > 0 && bestStart + bestLen >= parts.size && bestLen >= 2) {
            parts.subList(0, bestStart).joinToString(":").let { sb.append(it) }
            sb.append("::")
        } else if (bestLen >= 2) {
            parts.subList(0, bestStart).joinToString(":").let { sb.append(it) }
            sb.append("::")
            parts.subList(bestStart + bestLen, parts.size).joinToString(":").let { sb.append(it) }
        } else {
            parts.joinToString(":").let { sb.append(it) }
        }

        if (ipv4Suffix != null) {
            sb.append(":$ipv4Suffix")
        }

        return sb.toString()
    }

    private fun truncateMiddle(s: String, maxLen: Int): String {
        if (s.length <= maxLen) return s
        val ellipsis = "…"
        val available = maxLen - ellipsis.length
        val front = (available + 1) / 2
        val back = available / 2
        return s.take(front) + ellipsis + s.takeLast(back)
    }

    private fun expandIPv6(ip: String): String? {
        if (!ip.contains("::")) {
            if (ip.count { it == ':' } != 7) return null
            return ip
        }

        val sides = ip.split("::", limit = 2)
        val left = sides[0].split(':').filter { it.isNotEmpty() }
        val right = sides.getOrNull(1)?.split(':')?.filter { it.isNotEmpty() } ?: emptyList()
        val total = left.size + right.size
        if (total > 7) return null

        val zeros = List(8 - total) { "0" }
        val result = mutableListOf<String>()
        result.addAll(left)
        result.addAll(zeros)
        result.addAll(right)
        return result.joinToString(":")
    }
}
