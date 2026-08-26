package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.finishWithMaterialTransition
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.server.ServerEditScreen
import com.v2ray.ang.ui.server.ServerEditUiState
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value))
            ?: EConfigType.VMESS
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val configType: EConfigType by lazy {
        currentConfig?.configType ?: createConfigType
    }
    private val currentConfig: ProfileItem? by lazy {
        if (editGuid.isNotEmpty()) MmkvManager.decodeServerConfig(editGuid) else null
    }

    private var isFetchingCertificate by mutableStateOf(false)

    private val editState: ServerEditUiState by lazy {
        val res = resources
        ServerEditUiState(
            configType = configType,
            source = currentConfig,
            securitys = res.getStringArray(R.array.securitys).toList(),
            shadowsocksSecuritys = res.getStringArray(R.array.ss_securitys).toList(),
            flowOptions = res.getStringArray(R.array.flows).toList(),
            networkOptions = res.getStringArray(R.array.networks).toList(),
            tcpTypes = res.getStringArray(R.array.header_type_tcp).toList(),
            kcpAndQuicTypes = res.getStringArray(R.array.header_type_kcp_and_quic).toList(),
            grpcModes = res.getStringArray(R.array.mode_type_grpc).toList(),
            streamSecurityOptions = res.getStringArray(R.array.streamsecurityxs).toList(),
            allowInsecureOptions = res.getStringArray(R.array.allowinsecures).toList(),
            fingerprintOptions = res.getStringArray(R.array.streamsecurity_utls).toList(),
            alpnOptions = res.getStringArray(R.array.streamsecurity_alpn).toList(),
            xhttpModes = res.getStringArray(R.array.xhttp_mode).toList(),
            browserDialerOptions = res.getStringArray(R.array.browser_dialer_mode).toList(),
        )
    }

    private val canDelete: Boolean get() = editGuid.isNotEmpty() && !isRunning

    private val confirmBeforeDelete: Boolean
        get() = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (currentConfig == null && editGuid.isNotEmpty()) {
            finishWithMaterialTransition()
        }
    }

    @Composable
    override fun ScreenContent() {
        ServerEditScreen(
            uiState = editState,
            canDelete = canDelete,
            confirmBeforeDelete = confirmBeforeDelete,
            isFetchingCertificate = isFetchingCertificate,
            onBack = { finishWithMaterialTransition() },
            onSave = { saveServer() },
            onDelete = { deleteServer() },
            onFetchCertificate = { fetchPinnedCA256ForCurrentConfig() },
        )
    }

    private fun saveServer() {
        val errorRes = editState.validate() ?: run {
            performSave()
            return
        }
        toast(errorRes)
    }

    private fun performSave() {
        val config = currentConfig ?: ProfileItem.create(configType)

        editState.applyTo(config)
        config.description = AngConfigManager.generateDescription(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        MmkvManager.encodeServerConfig(editGuid, config)
        if (isRunning) {
            SettingsChangeManager.makeRestartService()
        }
        toastSuccess(R.string.toast_success)
        finishWithMaterialTransition()
    }

    private fun deleteServer() {
        if (editGuid.isEmpty()) return
        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        MmkvManager.removeServer(editGuid)
        finishWithMaterialTransition()
    }

    private fun fetchPinnedCA256ForCurrentConfig() {
        if (editState.address.isBlank()) {
            toast(R.string.server_lab_address)
            return
        }
        if (configType != EConfigType.HYSTERIA2 && editState.showAddressPort) {
            if (Utils.parseInt(editState.port) <= 0) {
                toast(R.string.server_lab_port)
                return
            }
        }

        val config = ProfileItem.create(configType)
        editState.applyTo(config)

        lifecycleScope.launch {
            isFetchingCertificate = true
            try {
                val sha256 = withContext(Dispatchers.IO) {
                    CertificateFingerprintManager.fetchForManualFill(config)
                }
                if (sha256.isNullOrBlank()) {
                    toast(R.string.toast_fetch_cert_sha256_failed)
                } else {
                    editState.pinnedCA256 = sha256
                    toastSuccess(R.string.toast_fetch_cert_sha256_success)
                }
            } finally {
                isFetchingCertificate = false
            }
        }
    }
}
