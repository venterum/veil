package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class AmneziaWgFmtTest {

    private fun key(byte: Int): String =
        Base64.getEncoder().encodeToString(ByteArray(32) { byte.toByte() })

    private fun fullConf(): String = """
        [Interface]
        PrivateKey = ${key(1)}
        Address = 10.66.66.2/32, fd00::2/128
        DNS = 1.1.1.1, 2606:4700:4700::1111
        MTU = 1380
        Jc = 6
        Jmin = 50
        Jmax = 1000
        S1 = 30
        S2 = 31
        S3 = 32
        S4 = 33
        H1 = 100-200
        H2 = 300-400
        H3 = 500-600
        H4 = 700-800
        I1 = <b 0xdeadbeef><r 20>
        I2 = <rc 12>
        HeaderProtectionKey = ${key(3)}
        ContentPaddingAddition = 0-10
        RekeyAfterTime = 100-120
        RekeyTimeout = 5
        RejectAfterTime = 180
        KeepaliveTimeout = 10
        MaxHandshakeAttempts = 5
        RandomTrailers = on
        DisableCookies = on

        [Peer]
        PublicKey = ${key(2)}
        PresharedKey = ${key(4)}
        Endpoint = [2001:db8::1]:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 22-30
    """.trimIndent()

    @Test
    fun parseFullConf() {
        val config = WgConfParser.parse(fullConf())
        assertNotNull(config)
        assertEquals(EConfigType.AMNEZIAWG, config!!.configType)
        assertEquals("2001:db8::1", config.server)
        assertEquals("51820", config.serverPort)
        assertEquals("10.66.66.2/32, fd00::2/128", config.localAddress)
        assertEquals("1.1.1.1, 2606:4700:4700::1111", config.dns)
        assertEquals("0.0.0.0/0, ::/0", config.allowedIps)
        assertEquals("22-30", config.persistentKeepalive)
        assertEquals(1380, config.mtu)
        assertEquals(6, config.awgJc)
        assertEquals(31, config.awgS2)
        assertEquals("100-200", config.awgH1)
        assertEquals("<b 0xdeadbeef><r 20>", config.awgI1)
        assertEquals(key(3), config.awgHeaderProtectionKey)
        assertEquals(true, config.awgRandomTrailers)
        assertEquals(true, config.awgDisableCookies)
    }

    @Test
    fun plainWireGuardConfStaysWireGuard() {
        val conf = """
            [Interface]
            PrivateKey = ${key(1)}
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = ${key(2)}
            Endpoint = 192.0.2.10:51820
        """.trimIndent()
        val config = WgConfParser.parse(conf)
        assertNotNull(config)
        assertEquals(EConfigType.WIREGUARD, config!!.configType)
    }

    @Test
    fun forcedTypeOverridesDetection() {
        val conf = """
            [Interface]
            PrivateKey = ${key(1)}
            Address = 10.0.0.2/32
            Jc = 4
            [Peer]
            PublicKey = ${key(2)}
            Endpoint = 192.0.2.10:51820
        """.trimIndent()
        val config = WgConfParser.parse(conf, EConfigType.WIREGUARD)
        assertNotNull(config)
        assertEquals(EConfigType.WIREGUARD, config!!.configType)
        assertNull(config.awgJc)
    }

    @Test
    fun confRoundTrip() {
        val parsed = WgConfParser.parse(fullConf())!!
        val rendered = AmneziaWgFmt.toConf(parsed)
        val reparsed = WgConfParser.parse(rendered)
        assertNotNull(reparsed)
        assertEquals(parsed.configType, reparsed!!.configType)
        assertEquals(parsed.server, reparsed.server)
        assertEquals(parsed.serverPort, reparsed.serverPort)
        assertEquals(parsed.localAddress, reparsed.localAddress)
        assertEquals(parsed.awgH4, reparsed.awgH4)
        assertEquals(parsed.awgI1, reparsed.awgI1)
        assertEquals(parsed.awgHeaderProtectionKey, reparsed.awgHeaderProtectionKey)
    }

    @Test
    fun uriRoundTrip() {
        val parsed = WgConfParser.parse(fullConf())!!
        parsed.remarks = "AWG test"
        val uri = AmneziaWgFmt.toUri(parsed)
        assertTrue(uri, uri.contains("?") && uri.contains("publickey="))
        val reparsed = AmneziaWgFmt.parse("amneziawg://$uri")
        assertNotNull(reparsed)
        assertEquals(EConfigType.AMNEZIAWG, reparsed!!.configType)
        assertEquals(parsed.server, reparsed.server)
        assertEquals(parsed.serverPort, reparsed.serverPort)
        assertEquals(parsed.publicKey, reparsed.publicKey)
        assertEquals(parsed.awgJc, reparsed.awgJc)
        assertEquals(parsed.awgI1, reparsed.awgI1)
        assertEquals(parsed.awgHeaderProtectionKey, reparsed.awgHeaderProtectionKey)
    }

    @Test
    fun vpnKeyRawJson() {
        val config = AmneziaVpnKeyParser.parse(vpnKey(fullConf(), "Imported AWG", compressed = false))
        assertNotNull(config)
        assertEquals(EConfigType.AMNEZIAWG, config!!.configType)
        assertEquals("Imported AWG", config.remarks)
        assertEquals(6, config.awgJc)
    }

    @Test
    fun vpnKeyQtCompressed() {
        val conf = """
            [Interface]
            PrivateKey = ${key(1)}
            Address = 10.0.0.2/32
            Jc = 4
            [Peer]
            PublicKey = ${key(2)}
            Endpoint = 192.0.2.10:51820
        """.trimIndent()
        val config = AmneziaVpnKeyParser.parse(vpnKey(conf, "Compressed", compressed = true))
        assertNotNull(config)
        assertEquals(EConfigType.AMNEZIAWG, config!!.configType)
        assertEquals("192.0.2.10", config.server)
        assertEquals(4, config.awgJc)
    }

    @Test
    fun vpnKeyRejectsNonAwgPayload() {
        val root = """{"description":"Xray","containers":[{"container":"amnezia-xray","xray":{}}]}"""
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(root.toByteArray())
        assertNull(AmneziaVpnKeyParser.parse("vpn://$payload"))
        assertFalse(AmneziaVpnKeyParser.isVpnKey("https://example.com"))
        assertTrue(AmneziaVpnKeyParser.isVpnKey("vpn://abc"))
    }

    private fun vpnKey(conf: String, description: String, compressed: Boolean): String {
        val lastConfig = JsonObject().apply { addProperty("config", conf) }
        val awg = JsonObject().apply { addProperty("last_config", lastConfig.toString()) }
        val container = JsonObject().apply {
            addProperty("container", "amnezia-awg")
            add("awg", awg)
        }
        val root = JsonObject().apply {
            addProperty("description", description)
            add("containers", JsonArray().apply { add(container) })
        }
        val raw = root.toString().toByteArray()
        val bytes = if (compressed) qCompress(raw) else raw
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun qCompress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { it.writeInt(data.size) }
        DeflaterOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }
}
