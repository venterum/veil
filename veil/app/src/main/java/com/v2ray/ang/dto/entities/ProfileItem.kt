package com.v2ray.ang.dto.entities

import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.Utils

data class ProfileItem(
    val configVersion: Int = 4,
    val configType: EConfigType,
    var subscriptionId: String = "",
    var addedTime: Long = System.currentTimeMillis(),

    var remarks: String = "",
    var description: String? = null,
    var server: String? = null,
    var serverPort: String? = null,

    var password: String? = null,
    var method: String? = null,
    var flow: String? = null,
    var username: String? = null,

    var network: String? = null,
    var headerType: String? = null,
    var host: String? = null,
    var path: String? = null,
    var seed: String? = null,
    var kcpMtu: Int? = null,
    var kcpTti: Int? = null,

    var quicSecurity: String? = null,
    var quicKey: String? = null,
    var mode: String? = null,
    var serviceName: String? = null,
    var authority: String? = null,
    var xhttpMode: String? = null,
    var xhttpExtra: String? = null,
    var finalMask: String? = null,
    var security: String? = null,
    var sni: String? = null,
    var alpn: String? = null,
    var fingerPrint: String? = null,
    var insecure: Boolean? = null,
    var echConfigList: String? = null,
    var verifyPeerCertByName: String? = null,
    var pinnedCA256: String? = null,

    var publicKey: String? = null,
    var shortId: String? = null,
    var spiderX: String? = null,
    var mldsa65Verify: String? = null,

    var secretKey: String? = null,
    var preSharedKey: String? = null,
    var localAddress: String? = null,
    var reserved: String? = null,
    var mtu: Int? = null,
    var dns: String? = null,
    var allowedIps: String? = null,
    var persistentKeepalive: String? = null,
    /** Runtime loopback SOCKS port for the AmneziaWG bridge. */
    var localSocksPort: Int? = null,

    var awgJc: Int? = null,
    var awgJmin: Int? = null,
    var awgJmax: Int? = null,
    var awgS1: Int? = null,
    var awgS2: Int? = null,
    var awgS3: Int? = null,
    var awgS4: Int? = null,
    var awgH1: String? = null,
    var awgH2: String? = null,
    var awgH3: String? = null,
    var awgH4: String? = null,
    var awgI1: String? = null,
    var awgI2: String? = null,
    var awgI3: String? = null,
    var awgI4: String? = null,
    var awgI5: String? = null,
    var awgHeaderProtectionKey: String? = null,
    var awgContentPaddingAddition: String? = null,
    var awgRekeyAfterTime: String? = null,
    var awgRekeyTimeout: String? = null,
    var awgRejectAfterTime: String? = null,
    var awgKeepaliveTimeout: String? = null,
    var awgMaxHandshakeAttempts: String? = null,
    var awgRandomTrailers: Boolean? = null,
    var awgDisableCookies: Boolean? = null,

    var obfsPassword: String? = null,
    var portHopping: String? = null,
    var portHoppingInterval: String? = null,
    var pinSHA256: String? = null,
    var bandwidthDown: String? = null,
    var bandwidthUp: String? = null,

    var policyGroupType: String? = null,
    var policyGroupSubscriptionId: String? = null,
    var policyGroupFilter: String? = null,
    var proxyChainProfiles: String? = null,

    var browserDialerMode: String? = null,

    var olcrtcCarrier: String? = null,
    var olcrtcTransport: String? = null,
    var olcrtcServerUrl: String? = null,
    var olcrtcRoomId: String? = null,
    var olcrtcClientId: String? = null,
    var olcrtcKeyHex: String? = null,
    var olcrtcEngine: String? = null,

    var openfluxTransport: String? = null,
    var openfluxUrl: String? = null,
    var openfluxMaxToken: String? = null,
    var openfluxMaxUid: String? = null,
    var openfluxKey: String? = null,

    ) {
    companion object {
        fun create(configType: EConfigType): ProfileItem {
            return ProfileItem(configType = configType)
        }
    }

    fun getServerAddressAndPort(): String {
        if (server.isNullOrEmpty() && configType == EConfigType.CUSTOM) {
            return "${AppConfig.LOOPBACK}:${AppConfig.PORT_SOCKS}"
        }
        return Utils.getIpv6Address(server) + ":" + serverPort
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        val obj = other as ProfileItem

        return (this.server == obj.server
                && this.serverPort == obj.serverPort
                && this.password == obj.password
                && this.method == obj.method
                && this.flow == obj.flow
                && this.username == obj.username

                && this.network == obj.network
                && this.headerType == obj.headerType
                && this.host == obj.host
                && this.path == obj.path
                && this.seed == obj.seed
                && this.kcpMtu == obj.kcpMtu
                && this.kcpTti == obj.kcpTti
                && this.quicSecurity == obj.quicSecurity
                && this.quicKey == obj.quicKey
                && this.mode == obj.mode
                && this.serviceName == obj.serviceName
                && this.authority == obj.authority
                && this.xhttpMode == obj.xhttpMode

                && this.security == obj.security
                && this.sni == obj.sni
                && this.alpn == obj.alpn
                && this.fingerPrint == obj.fingerPrint
                && this.publicKey == obj.publicKey
                && this.shortId == obj.shortId

                && this.secretKey == obj.secretKey
                && this.preSharedKey == obj.preSharedKey
                && this.localAddress == obj.localAddress
                && this.reserved == obj.reserved
                && this.mtu == obj.mtu
                && this.dns == obj.dns
                && this.allowedIps == obj.allowedIps
                && this.persistentKeepalive == obj.persistentKeepalive

                && this.awgJc == obj.awgJc
                && this.awgJmin == obj.awgJmin
                && this.awgJmax == obj.awgJmax
                && this.awgS1 == obj.awgS1
                && this.awgS2 == obj.awgS2
                && this.awgS3 == obj.awgS3
                && this.awgS4 == obj.awgS4
                && this.awgH1 == obj.awgH1
                && this.awgH2 == obj.awgH2
                && this.awgH3 == obj.awgH3
                && this.awgH4 == obj.awgH4
                && this.awgI1 == obj.awgI1
                && this.awgI2 == obj.awgI2
                && this.awgI3 == obj.awgI3
                && this.awgI4 == obj.awgI4
                && this.awgI5 == obj.awgI5
                && this.awgHeaderProtectionKey == obj.awgHeaderProtectionKey
                && this.awgContentPaddingAddition == obj.awgContentPaddingAddition
                && this.awgRekeyAfterTime == obj.awgRekeyAfterTime
                && this.awgRekeyTimeout == obj.awgRekeyTimeout
                && this.awgRejectAfterTime == obj.awgRejectAfterTime
                && this.awgKeepaliveTimeout == obj.awgKeepaliveTimeout
                && this.awgMaxHandshakeAttempts == obj.awgMaxHandshakeAttempts
                && this.awgRandomTrailers == obj.awgRandomTrailers
                && this.awgDisableCookies == obj.awgDisableCookies

                && this.obfsPassword == obj.obfsPassword
                && this.portHopping == obj.portHopping
                && this.portHoppingInterval == obj.portHoppingInterval
                && this.pinnedCA256 == obj.pinnedCA256
                && this.proxyChainProfiles == obj.proxyChainProfiles

                && this.olcrtcCarrier == obj.olcrtcCarrier
                && this.olcrtcTransport == obj.olcrtcTransport
                && this.olcrtcServerUrl == obj.olcrtcServerUrl
                && this.olcrtcRoomId == obj.olcrtcRoomId
                && this.olcrtcClientId == obj.olcrtcClientId
                && this.olcrtcKeyHex == obj.olcrtcKeyHex

                && this.openfluxTransport == obj.openfluxTransport
                && this.openfluxUrl == obj.openfluxUrl
                && this.openfluxMaxToken == obj.openfluxMaxToken
                && this.openfluxMaxUid == obj.openfluxMaxUid
                && this.openfluxKey == obj.openfluxKey
                )
    }
}