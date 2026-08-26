package com.v2ray.ang.ui.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    uiState: ServerEditUiState,
    canDelete: Boolean,
    confirmBeforeDelete: Boolean,
    isFetchingCertificate: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onFetchCertificate: () -> Unit,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun requestDelete() {
        if (confirmBeforeDelete) showDeleteDialog = true else onDelete()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = uiState.configType.toString(),
                onBackClick = onBack,
                isLoading = isFetchingCertificate,
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.server_help_title)
                        )
                    }
                    if (canDelete) {
                        IconButton(onClick = { requestDelete() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_24dp),
                                contentDescription = stringResource(R.string.menu_item_del_config)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { SaveBar(isBusy = isFetchingCertificate, onSave = onSave) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
        ) {
            ProtocolHero(uiState = uiState)
            SectionContent(
                uiState = uiState,
                isFetchingCertificate = isFetchingCertificate,
                onFetchCertificate = onFetchCertificate
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showHelpDialog) {
        ProtocolHelpDialog(
            configType = uiState.configType,
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = null
                )
            },
            title = { Text(text = stringResource(R.string.menu_item_del_config)) },
            text = { Text(text = stringResource(R.string.del_config_comfirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveBar(isBusy: Boolean, onSave: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onSave()
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_done),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.menu_item_save_config),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ProtocolHero(uiState: ServerEditUiState) {    val newServerLabel = stringResource(R.string.server_title_new_server)
    val untitledLabel = stringResource(R.string.server_hint_untitled)

    val summary = if (uiState.configType == EConfigType.OLCRTC) {
        "${ServerEditUiState.OLCRTC_CARRIERS.getOrElse(uiState.olcrtcCarrierIndex) { "" }}" +
                "  •  ${ServerEditUiState.OLCRTC_TRANSPORTS.getOrElse(uiState.olcrtcTransportIndex) { "" }}"
    } else if (uiState.address.isNotBlank()) {
        if (uiState.port.isNotBlank()) "${uiState.address}:${uiState.port}" else uiState.address
    } else {
        untitledLabel
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = protocolIcon(uiState.configType),
                    contentDescription = uiState.configType.toString(),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.remarks.ifBlank { newServerLabel },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${uiState.configType}  •  $summary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun ProtocolHelpDialog(configType: EConfigType, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val docsUrl = protocolDocsUrl(configType)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = protocolIcon(configType),
                    contentDescription = configType.toString(),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = { Text(text = configType.toString()) },
        text = {
            Column {
                Text(
                    text = protocolDescriptionRes(configType).let { stringResource(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (docsUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = docsUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (docsUrl != null) {
                TextButton(onClick = {
                    onDismiss()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(docsUrl)))
                    } catch (_: ActivityNotFoundException) {
                        context.toast(R.string.toast_failure)
                    }
                }) {
                    Text(text = stringResource(R.string.server_help_open_docs))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun protocolDocsUrl(configType: EConfigType): String? = when (configType) {
    EConfigType.VMESS -> "https://www.v2fly.org/en_US/v5/config/proxy/vmess.html"
    EConfigType.VLESS -> "https://xtls.github.io/config/outbound/vless.html"
    EConfigType.TROJAN -> "https://trojan-gfw.github.io/trojan/protocol"
    EConfigType.SHADOWSOCKS -> "https://shadowsocks.org/doc/what-is-shadowsocks.html"
    EConfigType.SOCKS -> "https://datatracker.ietf.org/doc/html/rfc1928"
    EConfigType.HTTP -> "https://developer.mozilla.org/en-US/docs/Web/HTTP/Proxy_servers_and_tunneling"
    EConfigType.WIREGUARD -> "https://www.wireguard.com/quickstart/"
    EConfigType.HYSTERIA2 -> "https://v2.hysteria.network/docs/"
    EConfigType.OLCRTC -> "https://github.com/openlibrecommunity/olcrtc#readme"
    else -> null
}

private fun protocolDescriptionRes(configType: EConfigType): Int = when (configType) {
    EConfigType.VMESS -> R.string.server_help_desc_vmess
    EConfigType.VLESS -> R.string.server_help_desc_vless
    EConfigType.TROJAN -> R.string.server_help_desc_trojan
    EConfigType.SHADOWSOCKS -> R.string.server_help_desc_shadowsocks
    EConfigType.SOCKS -> R.string.server_help_desc_socks
    EConfigType.HTTP -> R.string.server_help_desc_http
    EConfigType.WIREGUARD -> R.string.server_help_desc_wireguard
    EConfigType.HYSTERIA2 -> R.string.server_help_desc_hysteria2
    EConfigType.OLCRTC -> R.string.server_help_desc_olcrtc
    else -> R.string.server_hint_untitled
}

private fun protocolIcon(configType: EConfigType): ImageVector = when (configType) {
    EConfigType.VMESS -> Icons.Outlined.Dns
    EConfigType.VLESS -> Icons.Outlined.Bolt
    EConfigType.TROJAN -> Icons.Outlined.Shield
    EConfigType.SHADOWSOCKS -> Icons.Outlined.EnhancedEncryption
    EConfigType.SOCKS, EConfigType.HTTP -> Icons.Outlined.SwapHoriz
    EConfigType.WIREGUARD -> Icons.Outlined.VpnLock
    EConfigType.HYSTERIA2 -> Icons.Outlined.RocketLaunch
    EConfigType.OLCRTC -> Icons.Outlined.Videocam
    else -> Icons.Outlined.Lan
}

@Composable
private fun SectionContent(
    uiState: ServerEditUiState,
    isFetchingCertificate: Boolean,
    onFetchCertificate: () -> Unit,
) {
    val noneLabel = stringResource(R.string.server_option_none)

    SectionHeader(title = stringResource(R.string.server_section_general))
    FormCard {
        EditTextField(
            label = stringResource(R.string.server_lab_remarks),
            value = uiState.remarks,
            onValueChange = { uiState.remarks = it }
        )
        if (uiState.showAddressPort) {
            Row {
                Box(modifier = Modifier.weight(1f)) {
                    EditTextField(
                        label = stringResource(R.string.server_lab_address3),
                        value = uiState.address,
                        onValueChange = { uiState.address = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.width(116.dp)) {
                    EditTextField(
                        label = stringResource(R.string.server_lab_port3),
                        value = uiState.port,
                        onValueChange = { uiState.port = it.filter(Char::isDigit) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (uiState.showCredential) {
            val credentialLabel = stringResource(
                if (uiState.configType in setOf(EConfigType.SHADOWSOCKS, EConfigType.TROJAN, EConfigType.HYSTERIA2)) {
                    R.string.server_lab_id3
                } else {
                    R.string.server_lab_id
                }
            )
            EditTextField(
                label = credentialLabel,
                value = uiState.password,
                onValueChange = { uiState.password = it },
                trailingIcon = { CopyToClipboardButton(value = uiState.password) }
            )
        }
        if (uiState.showSecurityDropdown) {
            EditDropdownField(
                label = stringResource(R.string.server_lab_security),
                selectedIndex = uiState.securityIndex,
                options = uiState.securityOptions,
                noneLabel = noneLabel,
                onSelectedIndex = { uiState.securityIndex = it }
            )
        }
        if (uiState.showVlessExtras) {
            EditTextField(
                label = stringResource(R.string.server_lab_encryption),
                value = uiState.encryption,
                onValueChange = { uiState.encryption = it }
            )
            EditDropdownField(
                label = stringResource(R.string.server_lab_flow),
                selectedIndex = uiState.flowIndex,
                options = uiState.flowOptions,
                noneLabel = noneLabel,
                onSelectedIndex = { uiState.flowIndex = it }
            )
        }
        if (uiState.showSocksAuth) {
            EditTextField(
                label = stringResource(R.string.server_lab_security4),
                value = uiState.username,
                onValueChange = { uiState.username = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_id4),
                value = uiState.password,
                onValueChange = { uiState.password = it }
            )
        }
    }

    if (uiState.showWireguardSection) {
        SectionHeader(title = stringResource(R.string.server_section_wireguard))
        FormCard {
            EditTextField(
                label = stringResource(R.string.server_lab_secret_key),
                value = uiState.wireguardSecretKey,
                onValueChange = { uiState.wireguardSecretKey = it },
                trailingIcon = { CopyToClipboardButton(value = uiState.wireguardSecretKey) }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_public_key),
                value = uiState.wireguardPublicKey,
                onValueChange = { uiState.wireguardPublicKey = it },
                trailingIcon = { CopyToClipboardButton(value = uiState.wireguardPublicKey) }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_preshared_key),
                value = uiState.wireguardPreSharedKey,
                onValueChange = { uiState.wireguardPreSharedKey = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_reserved),
                value = uiState.wireguardReserved,
                onValueChange = { uiState.wireguardReserved = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_local_address),
                value = uiState.wireguardLocalAddress,
                onValueChange = { uiState.wireguardLocalAddress = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_local_mtu),
                value = uiState.wireguardMtu,
                onValueChange = { uiState.wireguardMtu = it.filter(Char::isDigit) },
                keyboardType = KeyboardType.Number
            )
        }
    }

    if (uiState.showHysteriaSection) {
        SectionHeader(title = stringResource(R.string.server_section_hysteria2))
        FormCard {
            EditTextField(
                label = stringResource(R.string.server_obfs_password),
                value = uiState.obfsPassword,
                onValueChange = { uiState.obfsPassword = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_port_hop),
                value = uiState.portHopping,
                onValueChange = { uiState.portHopping = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_port_hop_interval),
                value = uiState.portHoppingInterval,
                onValueChange = { uiState.portHoppingInterval = it },
                keyboardType = KeyboardType.Number
            )
            EditTextField(
                label = stringResource(R.string.server_lab_bandwidth_down),
                value = uiState.bandwidthDown,
                onValueChange = { uiState.bandwidthDown = it }
            )
            EditTextField(
                label = stringResource(R.string.server_lab_bandwidth_up),
                value = uiState.bandwidthUp,
                onValueChange = { uiState.bandwidthUp = it }
            )
        }
    }

    if (uiState.showOlcrtcSection) {
        SectionHeader(title = stringResource(R.string.server_section_olcrtc))
        FormCard {
            EditDropdownField(
                label = stringResource(R.string.olcrtc_lab_carrier),
                selectedIndex = uiState.olcrtcCarrierIndex,
                options = ServerEditUiState.OLCRTC_CARRIERS,
                noneLabel = noneLabel,
                onSelectedIndex = { uiState.olcrtcCarrierIndex = it }
            )
            AnimatedField(visible = uiState.showOlcrtcServerUrl) {
                EditTextField(
                    label = stringResource(R.string.olcrtc_lab_server_url),
                    value = uiState.olcrtcServerUrl,
                    onValueChange = { uiState.olcrtcServerUrl = it },
                    placeholder = stringResource(R.string.olcrtc_hint_server_url)
                )
            }
            EditTextField(
                label = stringResource(R.string.olcrtc_lab_room_id),
                value = uiState.olcrtcRoomId,
                onValueChange = { uiState.olcrtcRoomId = it }
            )
            EditTextField(
                label = stringResource(R.string.olcrtc_lab_client_id),
                value = uiState.olcrtcClientId,
                onValueChange = { uiState.olcrtcClientId = it },
                placeholder = stringResource(R.string.olcrtc_hint_client_id)
            )
            EditTextField(
                label = stringResource(R.string.olcrtc_lab_key_hex),
                value = uiState.olcrtcKeyHex,
                onValueChange = { uiState.olcrtcKeyHex = it },
                trailingIcon = { CopyToClipboardButton(value = uiState.olcrtcKeyHex) }
            )
            AnimatedField(visible = uiState.showOlcrtcEngine) {
                EditTextField(
                    label = stringResource(R.string.olcrtc_lab_engine),
                    value = uiState.olcrtcEngine,
                    onValueChange = { uiState.olcrtcEngine = it },
                    placeholder = "key=value&key=value"
                )
            }
        }
    }

    if (uiState.showTransport) {
        SectionHeader(title = stringResource(R.string.server_lab_more_function))
        FormCard {
            EditDropdownField(
                label = stringResource(R.string.server_lab_network),
                selectedIndex = uiState.networkIndex,
                options = uiState.networkOptions,
                noneLabel = noneLabel,
                onSelectedIndex = { index ->
                    uiState.networkIndex = index
                    uiState.onNetworkChanged()
                }
            )
            EditDropdownField(
                label = stringResource(uiState.headerTypeLabelRes),
                selectedIndex = uiState.headerTypeIndex,
                options = uiState.headerTypeOptions,
                noneLabel = noneLabel,
                enabled = uiState.headerTypeEnabled,
                onSelectedIndex = { uiState.headerTypeIndex = it }
            )
            EditTextField(
                label = stringResource(uiState.requestHostLabelRes),
                value = uiState.requestHost,
                onValueChange = { uiState.requestHost = it }
            )
            EditTextField(
                label = stringResource(uiState.pathLabelRes),
                value = uiState.path,
                onValueChange = { uiState.path = it }
            )
            AnimatedField(visible = uiState.showKcpSettings) {
                EditTextField(
                    label = stringResource(R.string.server_lab_kcp_mtu),
                    value = uiState.kcpMtu,
                    onValueChange = { uiState.kcpMtu = it.filter(Char::isDigit) },
                    keyboardType = KeyboardType.Number
                )
                EditTextField(
                    label = stringResource(R.string.server_lab_kcp_tti),
                    value = uiState.kcpTti,
                    onValueChange = { uiState.kcpTti = it.filter(Char::isDigit) },
                    keyboardType = KeyboardType.Number
                )
            }
            AnimatedField(visible = uiState.showXhttpExtra) {
                EditTextField(
                    label = stringResource(R.string.server_lab_xhttp_extra),
                    value = uiState.xhttpExtra,
                    onValueChange = { uiState.xhttpExtra = it },
                    minLines = 3,
                    maxLines = 10
                )
            }
            EditTextField(
                label = stringResource(R.string.server_lab_final_mask),
                value = uiState.finalMask,
                onValueChange = { uiState.finalMask = it },
                minLines = 2,
                maxLines = 10
            )
            AnimatedField(visible = uiState.showBrowserDialer) {
                EditDropdownField(
                    label = stringResource(R.string.server_lab_browser_dialer),
                    selectedIndex = uiState.browserDialerIndex,
                    options = uiState.browserDialerOptions,
                    noneLabel = noneLabel,
                    onSelectedIndex = { uiState.browserDialerIndex = it }
                )
                Text(
                    text = stringResource(R.string.server_lab_browser_dialer_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)
                )
            }
        }
    }

    if (uiState.showFullTlsSection || uiState.showHysteriaTlsSection) {
        SectionHeader(title = stringResource(R.string.server_section_security))
        FormCard {
            EditDropdownField(
                label = stringResource(R.string.server_lab_stream_security),
                selectedIndex = uiState.streamSecurityIndex,
                options = uiState.streamSecurityOptions,
                noneLabel = noneLabel,
                onSelectedIndex = { uiState.streamSecurityIndex = it }
            )
            AnimatedField(visible = uiState.showStreamSubFields) {
                EditTextField(
                    label = stringResource(R.string.server_lab_sni),
                    value = uiState.sni,
                    onValueChange = { uiState.sni = it }
                )
                EditDropdownField(
                    label = stringResource(R.string.server_lab_stream_fingerprint),
                    selectedIndex = uiState.fingerprintIndex,
                    options = uiState.fingerprintOptions,
                    noneLabel = noneLabel,
                    onSelectedIndex = { uiState.fingerprintIndex = it }
                )
            }
            AnimatedField(visible = uiState.showAlpn) {
                EditDropdownField(
                    label = stringResource(R.string.server_lab_stream_alpn),
                    selectedIndex = uiState.alpnIndex,
                    options = uiState.alpnOptions,
                    noneLabel = noneLabel,
                    onSelectedIndex = { uiState.alpnIndex = it }
                )
            }
            AnimatedField(visible = uiState.showRealityFields) {
                SectionSubHeader(title = stringResource(R.string.server_section_reality))
                EditTextField(
                    label = stringResource(R.string.server_lab_public_key),
                    value = uiState.realityPublicKey,
                    onValueChange = { uiState.realityPublicKey = it },
                    trailingIcon = { CopyToClipboardButton(value = uiState.realityPublicKey) }
                )
                EditTextField(
                    label = stringResource(R.string.server_lab_short_id),
                    value = uiState.shortId,
                    onValueChange = { uiState.shortId = it }
                )
                EditTextField(
                    label = stringResource(R.string.server_lab_spider_x),
                    value = uiState.spiderX,
                    onValueChange = { uiState.spiderX = it }
                )
                EditTextField(
                    label = stringResource(R.string.server_lab_mldsa65_verify),
                    value = uiState.mldsa65Verify,
                    onValueChange = { uiState.mldsa65Verify = it }
                )
            }
            AnimatedField(visible = uiState.showAllowInsecure) {
                EditDropdownField(
                    label = stringResource(R.string.server_lab_allow_insecure),
                    selectedIndex = uiState.allowInsecureIndex,
                    options = uiState.allowInsecureOptions,
                    noneLabel = noneLabel,
                    onSelectedIndex = { uiState.allowInsecureIndex = it }
                )
            }
            AnimatedField(visible = uiState.showEchAndVerifyPeer) {
                SectionSubHeader(title = stringResource(R.string.server_section_tls_advanced))
                EditTextField(
                    label = stringResource(R.string.server_lab_ech_config_list),
                    value = uiState.echConfigList,
                    onValueChange = { uiState.echConfigList = it }
                )
                EditTextField(
                    label = stringResource(R.string.server_lab_verify_peer_cert_by_name),
                    value = uiState.verifyPeerCertByName,
                    onValueChange = { uiState.verifyPeerCertByName = it }
                )
            }
            AnimatedField(visible = uiState.showPinnedCa256) {
                EditTextField(
                    label = stringResource(R.string.server_lab_pinned_ca256),
                    value = uiState.pinnedCA256,
                    onValueChange = { uiState.pinnedCA256 = it },
                    trailingIcon = { CopyToClipboardButton(value = uiState.pinnedCA256) }
                )
                TextButton(
                    onClick = onFetchCertificate,
                    enabled = !isFetchingCertificate,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                ) {
                    Text(text = stringResource(R.string.pinned_ca256_action_fetch))
                }
            }
        }
    }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionSubHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedField(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val fadeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spatialSpec) + fadeIn(fadeSpec),
        exit = shrinkVertically(spatialSpec) + fadeOut(fadeSpec)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun EditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { p -> { Text(p) } },
        enabled = enabled,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = maxLines == 1 && minLines == 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (maxLines == 1) ImeAction.Next else ImeAction.Default
        ),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        trailingIcon = trailingIcon,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditDropdownField(
    label: String,
    selectedIndex: Int,
    options: List<String>,
    noneLabel: String,
    onSelectedIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dropdownChevron"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = options.getOrNull(selectedIndex)?.ifBlank { noneLabel }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (!enabled) return@IconButton
                        keyboardController?.hide()
                        expanded = !expanded
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation)
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!enabled) return@onFocusChanged
                    if (focusState.isFocused) {
                        keyboardController?.hide()
                        expanded = true
                    }
                }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(option.ifBlank { noneLabel })
                    },
                    onClick = {
                        onSelectedIndex(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CopyToClipboardButton(value: String) {
    if (value.isBlank()) return
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            Utils.setClipboard(context, value)
            context.toast(R.string.about_copied_to_clipboard)
        }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = stringResource(R.string.logcat_copy),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
