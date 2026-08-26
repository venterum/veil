package com.v2ray.ang.ui.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

class ServerEditUiState(
    val configType: EConfigType,
    private val source: ProfileItem?,
    securitys: List<String>,
    private val shadowsocksSecuritys: List<String>,
    val flowOptions: List<String>,
    val networkOptions: List<String>,
    private val tcpTypes: List<String>,
    private val kcpAndQuicTypes: List<String>,
    private val grpcModes: List<String>,
    val streamSecurityOptions: List<String>,
    val allowInsecureOptions: List<String>,
    val fingerprintOptions: List<String>,
    val alpnOptions: List<String>,
    private val xhttpModes: List<String>,
    val browserDialerOptions: List<String>,
) {
    companion object {
        val OLCRTC_CARRIERS = listOf("jitsi", "wbstream", "telemost")
        val OLCRTC_TRANSPORTS = listOf("datachannel", "vp8channel", "seichannel", "videochannel")
        private val TRANSPORT_TYPES =
            listOf(EConfigType.VMESS, EConfigType.SHADOWSOCKS, EConfigType.VLESS, EConfigType.TROJAN)
    }

    val securityOptions: List<String> =
        if (configType == EConfigType.SHADOWSOCKS) shadowsocksSecuritys else securitys

    val showAddressPort = configType != EConfigType.OLCRTC
    val showCredential = configType !in setOf(
        EConfigType.SOCKS, EConfigType.HTTP, EConfigType.WIREGUARD, EConfigType.OLCRTC
    )
    val showSocksAuth = configType == EConfigType.SOCKS || configType == EConfigType.HTTP
    val showVlessExtras = configType == EConfigType.VLESS
    val showSecurityDropdown = configType == EConfigType.VMESS || configType == EConfigType.SHADOWSOCKS
    val showTransport = configType in TRANSPORT_TYPES
    val showFullTlsSection = configType in TRANSPORT_TYPES
    val showHysteriaTlsSection = configType == EConfigType.HYSTERIA2
    val showWireguardSection = configType == EConfigType.WIREGUARD
    val showHysteriaSection = configType == EConfigType.HYSTERIA2
    val showOlcrtcSection = configType == EConfigType.OLCRTC

    var remarks by mutableStateOf("")
    var address by mutableStateOf("")
    var port by mutableStateOf(AppConfig.DEFAULT_PORT.toString())
    var password by mutableStateOf("")

    var username by mutableStateOf("")
    var encryption by mutableStateOf(if (configType == EConfigType.VLESS) "none" else "")
    var flowIndex by mutableStateOf(0)
    var securityIndex by mutableStateOf(0)

    var networkIndex by mutableStateOf(0)
    var headerTypeIndex by mutableStateOf(0)
    var requestHost by mutableStateOf("")
    var path by mutableStateOf("")
    var kcpMtu by mutableStateOf("")
    var kcpTti by mutableStateOf("")
    var xhttpExtra by mutableStateOf("")
    var finalMask by mutableStateOf("")
    var browserDialerIndex by mutableStateOf(0)

    var streamSecurityIndex by mutableStateOf(0)
    var sni by mutableStateOf("")
    var fingerprintIndex by mutableStateOf(0)
    var alpnIndex by mutableStateOf(0)
    var allowInsecureIndex by mutableStateOf(0)
    var echConfigList by mutableStateOf("")
    var verifyPeerCertByName by mutableStateOf("")
    var pinnedCA256 by mutableStateOf("")

    var realityPublicKey by mutableStateOf("")
    var shortId by mutableStateOf("")
    var spiderX by mutableStateOf("")
    var mldsa65Verify by mutableStateOf("")

    var wireguardSecretKey by mutableStateOf("")
    var wireguardPublicKey by mutableStateOf("")
    var wireguardPreSharedKey by mutableStateOf("")
    var wireguardReserved by mutableStateOf("0,0,0")
    var wireguardLocalAddress by mutableStateOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4)
    var wireguardMtu by mutableStateOf(AppConfig.WIREGUARD_LOCAL_MTU)

    var obfsPassword by mutableStateOf("")
    var portHopping by mutableStateOf("")
    var portHoppingInterval by mutableStateOf("")
    var bandwidthDown by mutableStateOf("")
    var bandwidthUp by mutableStateOf("")

    var olcrtcCarrierIndex by mutableStateOf(0)
    var olcrtcTransportIndex by mutableStateOf(0)
    var olcrtcServerUrl by mutableStateOf("")
    var olcrtcRoomId by mutableStateOf("")
    var olcrtcClientId by mutableStateOf("")
    var olcrtcKeyHex by mutableStateOf("")
    var olcrtcEngine by mutableStateOf("")

    val network: String get() = networkOptions.getOrElse(networkIndex) { networkOptions[0] }
    val flow: String get() = flowOptions.getOrElse(flowIndex) { flowOptions[0] }
    val securityMethod: String get() = securityOptions.getOrElse(securityIndex) { securityOptions[0] }
    val streamSecurity: String get() = streamSecurityOptions.getOrElse(streamSecurityIndex) { "" }
    val headerTypeOptions: List<String> get() = transportTypes(network)

    init {
        if (source != null) bind(source) else resetForCreate()
    }

    private fun resetForCreate() {
        remarks = ""
        address = ""
        port = AppConfig.DEFAULT_PORT.toString()
        password = ""
        username = ""
        encryption = if (configType == EConfigType.VLESS) "none" else ""
        flowIndex = 0
        securityIndex = 0
        networkIndex = 0
        browserDialerIndex = 0
        streamSecurityIndex = 0
        wireguardReserved = "0,0,0"
        wireguardLocalAddress = AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
        wireguardMtu = AppConfig.WIREGUARD_LOCAL_MTU
        onNetworkChanged()
    }

    private fun bind(config: ProfileItem) {
        remarks = config.remarks
        address = config.server.orEmpty()
        port = config.serverPort ?: AppConfig.DEFAULT_PORT.toString()
        password = config.password.orEmpty()

        when (config.configType) {
            EConfigType.SOCKS, EConfigType.HTTP -> username = config.username.orEmpty()

            EConfigType.VLESS -> {
                encryption = config.method?.takeIf { it.isNotBlank() } ?: "none"
                val idx = Utils.arrayFind(flowOptions.toTypedArray(), config.flow.orEmpty())
                if (idx >= 0) flowIndex = idx
            }

            EConfigType.WIREGUARD -> {
                wireguardSecretKey = config.secretKey.orEmpty()
                wireguardPublicKey = config.publicKey.orEmpty()
                wireguardPreSharedKey = config.preSharedKey.orEmpty()
                wireguardReserved = config.reserved ?: "0,0,0"
                wireguardLocalAddress = config.localAddress ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
                wireguardMtu = config.mtu?.toString() ?: AppConfig.WIREGUARD_LOCAL_MTU
            }

            EConfigType.HYSTERIA2 -> {
                obfsPassword = config.obfsPassword.orEmpty()
                portHopping = config.portHopping.orEmpty()
                portHoppingInterval = config.portHoppingInterval.orEmpty()
                bandwidthDown = config.bandwidthDown.orEmpty()
                bandwidthUp = config.bandwidthUp.orEmpty()
            }

            else -> Unit
        }

        if (config.configType == EConfigType.VMESS || config.configType == EConfigType.SHADOWSOCKS) {
            val idx = Utils.arrayFind(securityOptions.toTypedArray(), config.method.orEmpty())
            if (idx >= 0) securityIndex = idx
        }

        val secIdx = Utils.arrayFind(streamSecurityOptions.toTypedArray(), config.security.orEmpty())
        if (secIdx >= 0) {
            streamSecurityIndex = secIdx
            sni = config.sni.orEmpty()
            val fpIdx = Utils.arrayFind(fingerprintOptions.toTypedArray(), config.fingerPrint.orEmpty())
            fingerprintIndex = if (fpIdx >= 0) fpIdx else 0
            val alpnIdx = Utils.arrayFind(alpnOptions.toTypedArray(), config.alpn.orEmpty())
            alpnIndex = if (alpnIdx >= 0) alpnIdx else 0
            if (config.security == AppConfig.TLS) {
                val aiIdx = Utils.arrayFind(allowInsecureOptions.toTypedArray(), config.insecure.toString())
                if (aiIdx >= 0) allowInsecureIndex = aiIdx
                echConfigList = config.echConfigList.orEmpty()
                verifyPeerCertByName = config.verifyPeerCertByName.orEmpty()
                pinnedCA256 = config.pinnedCA256.orEmpty()
            } else if (config.security == AppConfig.REALITY) {
                realityPublicKey = config.publicKey.orEmpty()
                shortId = config.shortId.orEmpty()
                spiderX = config.spiderX.orEmpty()
                mldsa65Verify = config.mldsa65Verify.orEmpty()
            }
        }

        val netIdx = Utils.arrayFind(networkOptions.toTypedArray(), config.network.orEmpty())
        if (netIdx >= 0) networkIndex = netIdx
        onNetworkChanged()

        val bdIdx = Utils.arrayFind(browserDialerOptions.toTypedArray(), config.browserDialerMode.orEmpty())
        if (bdIdx >= 0) browserDialerIndex = bdIdx

        if (config.configType == EConfigType.OLCRTC) {
            val carrierIdx = OLCRTC_CARRIERS.indexOf(config.olcrtcCarrier.orEmpty())
            if (carrierIdx >= 0) olcrtcCarrierIndex = carrierIdx
            val transportIdx = OLCRTC_TRANSPORTS.indexOf(config.olcrtcTransport.orEmpty())
            if (transportIdx >= 0) olcrtcTransportIndex = transportIdx
            olcrtcServerUrl = config.olcrtcServerUrl.orEmpty()
            olcrtcRoomId = config.olcrtcRoomId.orEmpty()
            olcrtcClientId = config.olcrtcClientId.orEmpty()
            olcrtcKeyHex = config.olcrtcKeyHex.orEmpty()
            olcrtcEngine = config.olcrtcEngine.orEmpty()
        }
    }

    fun transportTypes(networkValue: String): List<String> = when (networkValue) {
        NetworkType.TCP.type -> tcpTypes
        NetworkType.KCP.type -> kcpAndQuicTypes
        NetworkType.GRPC.type -> grpcModes
        NetworkType.XHTTP.type -> xhttpModes
        else -> listOf("---")
    }

    fun onNetworkChanged() {
        val types = headerTypeOptions
        val currentRaw = when (network) {
            NetworkType.GRPC.type -> source?.mode
            NetworkType.XHTTP.type -> source?.xhttpMode
            else -> source?.headerType
        }.orEmpty()
        headerTypeIndex = Utils.arrayFind(types.toTypedArray(), currentRaw).coerceIn(0, types.size - 1)

        requestHost = when (network) {
            NetworkType.GRPC.type -> source?.authority
            else -> source?.host
        }.orEmpty()
        path = when (network) {
            NetworkType.KCP.type -> source?.seed
            NetworkType.GRPC.type -> source?.serviceName
            else -> source?.path
        }.orEmpty()
        kcpMtu = source?.kcpMtu?.toString().orEmpty()
        kcpTti = source?.kcpTti?.toString().orEmpty()
        xhttpExtra = if (network == NetworkType.XHTTP.type) source?.xhttpExtra.orEmpty() else ""
        finalMask = source?.finalMask.orEmpty()
    }

    val headerTypeLabelRes: Int
        get() = when (network) {
            NetworkType.GRPC.type -> R.string.server_lab_mode_type
            NetworkType.XHTTP.type -> R.string.server_lab_xhttp_mode
            else -> R.string.server_lab_head_type
        }
    val requestHostLabelRes: Int
        get() = when (network) {
            NetworkType.TCP.type -> R.string.server_lab_request_host_http
            NetworkType.WS.type -> R.string.server_lab_request_host_ws
            NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_request_host_httpupgrade
            NetworkType.XHTTP.type -> R.string.server_lab_request_host_xhttp
            NetworkType.H2.type -> R.string.server_lab_request_host_h2
            NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
            else -> R.string.server_lab_request_host
        }
    val pathLabelRes: Int
        get() = when (network) {
            NetworkType.KCP.type -> R.string.server_lab_path_kcp
            NetworkType.WS.type -> R.string.server_lab_path_ws
            NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
            NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
            NetworkType.H2.type -> R.string.server_lab_path_h2
            NetworkType.GRPC.type -> R.string.server_lab_path_grpc
            else -> R.string.server_lab_path
        }

    val headerTypeEnabled: Boolean get() = headerTypeOptions.size > 1
    val showKcpSettings: Boolean get() = network == NetworkType.KCP.type
    val showXhttpExtra: Boolean get() = network == NetworkType.XHTTP.type
    val showBrowserDialer: Boolean get() = network == NetworkType.WS.type || network == NetworkType.XHTTP.type

    val showStreamSubFields: Boolean get() = streamSecurity.isNotBlank()
    val showAlpn: Boolean get() = streamSecurity == AppConfig.TLS && !showHysteriaTlsSection
    val showAllowInsecure: Boolean get() = streamSecurity == AppConfig.TLS
    val showEchAndVerifyPeer: Boolean get() = streamSecurity == AppConfig.TLS && !showHysteriaTlsSection
    val showPinnedCa256: Boolean get() = streamSecurity == AppConfig.TLS
    val showRealityFields: Boolean get() = streamSecurity == AppConfig.REALITY && !showHysteriaTlsSection

    val showOlcrtcServerUrl: Boolean
        get() = OLCRTC_CARRIERS.getOrElse(olcrtcCarrierIndex) { "" } in setOf("jitsi", "telemost")
    val showOlcrtcEngine: Boolean
        get() = OLCRTC_TRANSPORTS.getOrElse(olcrtcTransportIndex) { "" } != "datachannel"

    fun validate(): Int? {
        if (remarks.isBlank()) return R.string.server_lab_remarks
        if (showAddressPort) {
            if (address.isBlank()) return R.string.server_lab_address
            if (configType != EConfigType.HYSTERIA2 && Utils.parseInt(port) <= 0) {
                return R.string.server_lab_port
            }
        }
        if (showCredential && password.isBlank()) {
            return if (configType in setOf(EConfigType.TROJAN, EConfigType.SHADOWSOCKS, EConfigType.HYSTERIA2)) {
                R.string.server_lab_id3
            } else {
                R.string.server_lab_id
            }
        }
        if (configType == EConfigType.OLCRTC) {
            if (olcrtcRoomId.isBlank()) return R.string.olcrtc_lab_room_id
            if (olcrtcKeyHex.isBlank()) return R.string.olcrtc_lab_key_hex
        }
        if (configType == EConfigType.TROJAN && streamSecurity.isBlank()) {
            return R.string.server_lab_stream_security
        }
        if (showXhttpExtra && xhttpExtra.isNotBlank() && JsonUtil.parseString(xhttpExtra) == null) {
            return R.string.server_lab_xhttp_extra
        }
        if (finalMask.isNotBlank() && JsonUtil.parseString(finalMask) == null) {
            return R.string.server_lab_final_mask
        }
        return null
    }

    fun applyTo(config: ProfileItem) {
        saveCommon(config)
        saveStreamSettings(config)
        saveTls(config)
    }

    private fun saveCommon(config: ProfileItem) {
        config.remarks = remarks.trim()
        config.password = password.trim()

        when (config.configType) {
            EConfigType.VMESS -> config.method = securityMethod

            EConfigType.VLESS -> {
                config.method = encryption.trim()
                config.flow = flow
            }

            EConfigType.SHADOWSOCKS -> config.method = securityMethod

            EConfigType.SOCKS, EConfigType.HTTP -> {
                if (username.isNotBlank() || password.isNotBlank()) {
                    config.username = username.trim()
                }
            }

            EConfigType.WIREGUARD -> {
                config.secretKey = wireguardSecretKey.trim()
                config.publicKey = wireguardPublicKey.trim()
                config.preSharedKey = wireguardPreSharedKey.trim()
                config.reserved = wireguardReserved.trim()
                config.localAddress = wireguardLocalAddress.trim()
                config.mtu = Utils.parseInt(wireguardMtu)
            }

            EConfigType.HYSTERIA2 -> {
                config.obfsPassword = obfsPassword.ifBlank { null }
                config.portHopping = portHopping.ifBlank { null }
                config.portHoppingInterval = portHoppingInterval.trim().ifBlank { null }
                config.bandwidthDown = bandwidthDown.ifBlank { null }
                config.bandwidthUp = bandwidthUp.ifBlank { null }
            }

            EConfigType.OLCRTC -> {
                config.olcrtcCarrier = OLCRTC_CARRIERS.getOrElse(olcrtcCarrierIndex) { OLCRTC_CARRIERS[0] }
                config.olcrtcTransport = OLCRTC_TRANSPORTS.getOrElse(olcrtcTransportIndex) { OLCRTC_TRANSPORTS[0] }
                config.olcrtcRoomId = olcrtcRoomId.trim()
                config.olcrtcServerUrl = olcrtcServerUrl.trim().nullIfBlank()
                config.olcrtcClientId = olcrtcClientId.trim().nullIfBlank()
                config.olcrtcKeyHex = olcrtcKeyHex.trim()
                config.olcrtcEngine = olcrtcEngine.trim().nullIfBlank()
            }

            else -> Unit
        }

        if (showAddressPort) {
            config.server = address.trim()
            config.serverPort = port.trim()
        }
    }

    private fun saveStreamSettings(profileItem: ProfileItem) {
        if (!showTransport) return
        val types = headerTypeOptions
        val typeValue = types.getOrElse(headerTypeIndex) { types[0] }

        profileItem.network = network
        profileItem.headerType = typeValue
        profileItem.host = requestHost.trim()
        profileItem.path = path.trim()
        profileItem.seed = path.trim()
        profileItem.quicSecurity = requestHost.trim()
        profileItem.quicKey = path.trim()
        profileItem.mode = typeValue
        profileItem.serviceName = path.trim()
        profileItem.authority = requestHost.trim()
        profileItem.xhttpMode = typeValue
        profileItem.xhttpExtra = xhttpExtra.trim().nullIfBlank()
        profileItem.finalMask = finalMask.trim().nullIfBlank()
        profileItem.kcpMtu = kcpMtu.toIntOrNull()
        profileItem.kcpTti = kcpTti.toIntOrNull()
        if (network == NetworkType.WS.type || network == NetworkType.XHTTP.type) {
            val mode = browserDialerOptions.getOrElse(browserDialerIndex) { browserDialerOptions[0] }
            profileItem.browserDialerMode = if (mode != browserDialerOptions[0]) mode else null
        } else {
            profileItem.browserDialerMode = null
        }
    }

    private fun saveTls(config: ProfileItem) {
        if (!showFullTlsSection && !showHysteriaTlsSection) return

        val allowInsecure = allowInsecureOptions.getOrNull(allowInsecureIndex)
            ?.takeIf { it.isNotBlank() }?.toBoolean() ?: false
        val utlsValue = fingerprintOptions.getOrElse(fingerprintIndex) { fingerprintOptions[0] }
        val alpnValue = alpnOptions.getOrElse(alpnIndex) { alpnOptions[0] }

        config.security = streamSecurity
        config.insecure = allowInsecure
        config.sni = sni.trim()
        config.fingerPrint = utlsValue
        config.alpn = alpnValue
        config.publicKey = realityPublicKey
        config.shortId = shortId
        config.spiderX = spiderX
        config.mldsa65Verify = mldsa65Verify
        config.echConfigList = echConfigList
        config.verifyPeerCertByName = verifyPeerCertByName
        config.pinnedCA256 = pinnedCA256
    }
}
