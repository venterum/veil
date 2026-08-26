package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.FragmentGroupServerBinding
import com.v2ray.ang.databinding.ItemQrcodeBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.launchWithMaterialTransition
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.ServerListScreen
import com.v2ray.ang.ui.compose.ServerListTabTransition
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GroupServerFragment : BaseFragment<FragmentGroupServerBinding>() {
    private val ownerActivity: MainActivity
        get() = requireActivity() as MainActivity
    private val mainViewModel: MainViewModel by activityViewModels()
    private val subId: String by lazy { arguments?.getString(ARG_SUB_ID).orEmpty() }

    private val listState = LazyListState()
    private val gridState = LazyGridState()

    private var selectedGuid by mutableStateOf(MmkvManager.getSelectServer().orEmpty())
    private var doubleColumn by mutableStateOf(false)
    private var cardStyleNew by mutableStateOf(true)
    private var cachedServers by mutableStateOf<List<ServersCache>>(emptyList())
    private var hasShown by mutableStateOf(false)

    private val share_method: Array<out String> by lazy {
        ownerActivity.resources.getStringArray(R.array.share_method)
    }
    private val share_method_more: Array<out String> by lazy {
        ownerActivity.resources.getStringArray(R.array.share_method_more)
    }
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            ownerActivity.restartV2Ray()
        }
    }

    companion object {
        private const val ARG_SUB_ID = "subscriptionId"
        fun newInstance(subId: String) = GroupServerFragment().apply {
            arguments = Bundle().apply { putString(ARG_SUB_ID, subId) }
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentGroupServerBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshDisplayFlags()

        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    val servers by mainViewModel.serversCacheFlow.collectAsStateWithLifecycle()
                    val running by mainViewModel.isRunningFlow.collectAsStateWithLifecycle()
                    val activeSubscriptionId by mainViewModel.subscriptionIdFlow.collectAsStateWithLifecycle()
                    @Suppress("UNUSED_VARIABLE")
                    val updateTick = mainViewModel.updateListActionFlow.collectAsStateWithLifecycle().value

                    val isActive = activeSubscriptionId == subId
                    LaunchedEffect(isActive, servers) {
                        if (isActive) {
                            cachedServers = servers
                            hasShown = true
                        }
                    }
                    val displayedServers = if (isActive) servers else cachedServers

                    ServerListTabTransition(
                        visible = hasShown,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        ServerListScreen(
                            servers = displayedServers,
                            isRunning = running,
                            cardStyleNew = cardStyleNew,
                            doubleColumn = doubleColumn,
                            grouped = false,
                            showIcons = true,
                            showSubscriptionChip = false,
                            sections = emptyList(),
                            collapsedIds = emptySet(),
                            selectedGuid = selectedGuid,
                            listState = listState,
                            gridState = gridState,
                            onSelectServer = ::setSelectServer,
                            onShare = { guid, profile, position, more ->
                                shareServer(guid, profile, position, more)
                            },
                            onEdit = { guid, profile -> editServer(guid, profile) },
                            onRemove = { guid, position -> removeServer(guid, position) },
                            onSwap = { from, to -> mainViewModel.swapServer(from, to) },
                            onToggleGroup = {},
                        )
                    }
                }
            }
        }
    }

    private fun refreshDisplayFlags() {
        doubleColumn = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
        cardStyleNew = SettingsManager.getServerCardStyle() == "new"
    }

    override fun onResume() {
        super.onResume()
        selectedGuid = MmkvManager.getSelectServer().orEmpty()
        refreshDisplayFlags()
        mainViewModel.subscriptionIdChanged(subId)
    }

    private fun shareServer(guid: String, profile: ProfileItem, position: Int, more: Boolean) {
        val isCustom = profile.configType.isComplexType()
        val (shareOptions, skip) = if (more) {
            val options = if (isCustom) share_method_more.asList().takeLast(3) else share_method_more.asList()
            options to if (isCustom) 2 else 0
        } else {
            val options = if (isCustom) share_method.asList().takeLast(1) else share_method.asList()
            options to if (isCustom) 2 else 0
        }

        MaterialAlertDialogBuilder(ownerActivity).setItems(shareOptions.toTypedArray()) { _, i ->
            try {
                when (i + skip) {
                    0 -> showQRCode(guid)
                    1 -> share2Clipboard(guid)
                    2 -> shareFullContent(guid)
                    3 -> editServer(guid, profile)
                    4 -> removeServer(guid, position)
                    else -> ownerActivity.toast("else")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error when sharing server", e)
            }
        }.show()
    }

    private fun showQRCode(guid: String) {
        val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(ownerActivity))
        ivBinding.ivQcode.setImageBitmap(AngConfigManager.share2QRCode(guid))
        if (share_method.isNotEmpty()) {
            ivBinding.ivQcode.contentDescription = share_method[0]
        } else {
            ivBinding.ivQcode.contentDescription = "QR Code"
        }
        MaterialAlertDialogBuilder(ownerActivity).setView(ivBinding.root).show()
    }

    private fun share2Clipboard(guid: String) {
        if (AngConfigManager.share2Clipboard(ownerActivity, guid) == 0) {
            ownerActivity.toastSuccess(R.string.toast_success)
        } else {
            ownerActivity.toastError(R.string.toast_failure)
        }
    }

    private fun shareFullContent(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(ownerActivity, guid)
            launch(Dispatchers.Main) {
                if (result == 0) {
                    ownerActivity.toastSuccess(R.string.toast_success)
                } else {
                    ownerActivity.toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            else -> ServerActivity::class.java
        }

        val intent = Intent(ownerActivity, activityClass)
            .putExtra("guid", guid)
            .putExtra("isRunning", mainViewModel.isRunning.value)
            .putExtra("createConfigType", profile.configType.value)
            .putExtra("subscriptionId", subId)

        launcher.launchWithMaterialTransition(ownerActivity, intent)
    }

    private fun removeServer(guid: String, position: Int) {
        if (guid == MmkvManager.getSelectServer()) {
            ownerActivity.toast(R.string.toast_action_not_allowed)
            return
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            MaterialAlertDialogBuilder(ownerActivity).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    removeServerSub(guid)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    //do noting
                }
                .show()
        } else {
            removeServerSub(guid)
        }
    }

    private fun removeServerSub(guid: String) {
        mainViewModel.removeServer(guid)
        ownerActivity.refreshGroupTabTitles()
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid != selected) {
            MmkvManager.setSelectServer(guid)
            selectedGuid = guid
            ownerActivity.onSelectedServerChanged()

            if (mainViewModel.isRunning.value == true) {
                ownerActivity.restartV2Ray()
            }
        }
    }

    fun scrollToSelectedServer() {
        val target = MmkvManager.getSelectServer()
        if (target.isNullOrEmpty()) {
            ownerActivity.toast(R.string.title_file_chooser)
            return
        }

        val position = mainViewModel.serversCache.indexOfFirst { it.guid == target }
        if (position >= 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (doubleColumn) gridState.animateScrollToItem(position)
                else listState.animateScrollToItem(position)
            }
        } else {
            ownerActivity.toast(R.string.toast_server_not_found_in_group)
        }
    }
}
