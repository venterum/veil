package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModelEvent
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubSettingActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            viewModel.viewModelEvent.collect { event ->
                if (event is BaseViewModelEvent.FinishActivity) {
                    finish()
                }
            }
        }

        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = { subId ->
                startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
            },
            onRemoveSub = { subId -> viewModel.remove(subId) },
            onShareQRCode = { url -> QRCodeDecoder.createQRCode(url) },
            onShareClipboard = { url ->
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            },
            shareSubMethodEntries = resources.getStringArray(R.array.share_sub_method).toList()
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> Bitmap?,
    onShareClipboard: (String) -> Unit,
    shareSubMethodEntries: List<String>
) {
    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showQRCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.index, to.index)
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_sub_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(
                            painterResource(R.drawable.ic_add_24dp),
                            contentDescription = stringResource(R.string.menu_item_add_config)
                        )
                    }
                    IconButton(onClick = { showUpdateDialog = true }) {
                        Icon(
                            painterResource(R.drawable.ic_restore_24dp),
                            contentDescription = stringResource(R.string.title_sub_update)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(lazyListState)
        ) {
            itemsIndexed(
                items = subscriptions,
                key = { _, item -> item.guid }
            ) { _, subCache ->
                ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        SubscriptionItemCard(
                            subCache = subCache,
                            confirmRemove = confirmRemove,
                            onEdit = { onEditSub(subCache.guid) },
                            onUpdate = { viewModel.updateOne(subCache.guid) },
                            onPing = { viewModel.pingOne(subCache.guid) },
                            onRemove = {
                                if (confirmRemove) removeTarget = subCache.guid
                                else onRemoveSub(subCache.guid)
                            },
                            onShare = {
                                shareTarget = Pair(subCache.guid, subCache.subscription.url)
                            },
                            onToggle = { checked ->
                                val updated = subCache.subscription.copy()
                                updated.enabled = checked
                                viewModel.update(subCache.guid, updated)
                            }
                        )
                    }
                }
            }
        }
    }

    if (shareTarget != null) {
        val (_, url) = shareTarget!!
        SelectListDialog(
            options = shareSubMethodEntries,
            onSelected = { index, _ ->
                shareTarget = null
                when (index) {
                    0 -> {
                        // QRCode
                        showQRCodeBitmap = onShareQRCode(url)
                    }

                    1 -> {
                        // Export to clipboard
                        onShareClipboard(url)
                    }
                }
            },
            onDismiss = { shareTarget = null }
        )
    }

    // QR Code Dialog
    if (showQRCodeBitmap != null) {
        QRCodeDialog(
            bitmap = showQRCodeBitmap,
            onDismiss = { showQRCodeBitmap = null }
        )
    }

    if (removeTarget != null) {
        DeleteConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            onConfirm = {
                onRemoveSub(removeTarget!!)
                removeTarget = null
            },
            onDismiss = { removeTarget = null }
        )
    }

    if (showUpdateDialog) {
        var updateSubscription by rememberMmkvBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        var autoTestAfterUpdateSubscription by rememberMmkvBool(
            AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION,
            false
        )
        var autoRemoveInvalidAfterTest by rememberMmkvBool(
            AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST,
            false
        )
        var autoSortAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            text = {
                Column {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_sub_update),
                        checked = updateSubscription,
                        onCheckedChange = { updateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                        summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                        checked = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoTestAfterUpdateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                        summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                        checked = autoRemoveInvalidAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoRemoveInvalidAfterTest = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_sort_after_test),
                        summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                        checked = autoSortAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoSortAfterTest = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onSubUpdate()
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SubscriptionItemCard(
    subCache: com.v2ray.ang.dto.entities.SubscriptionCache,
    confirmRemove: Boolean,
    onEdit: () -> Unit,
    onUpdate: () -> Unit,
    onPing: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val subItem = subCache.subscription
    val context = LocalContext.current
    val displayName = subItem.profileTitle.takeIf { it.isNotEmpty() } ?: subItem.remarks
    val userinfo = remember(subItem.subscriptionUserinfo) {
        Utils.parseSubscriptionUserinfo(subItem.subscriptionUserinfo)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = subItem.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            if (!subItem.url.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subItem.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (userinfo != null && (userinfo.upload > 0 || userinfo.download > 0 || userinfo.total > 0 || userinfo.expire > 0)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.title_traffic),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                val used = userinfo.upload + userinfo.download
                val usedStr = Utils.formatBytes(used)
                val totalStr = if (userinfo.total > 0) " / ${Utils.formatBytes(userinfo.total)}" else ""
                Text(
                    text = "$usedStr$totalStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (userinfo.total > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / userinfo.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                if (userinfo.expire > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val dateStr = remember(userinfo.expire) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(userinfo.expire * 1000))
                    }
                    val nowSec = System.currentTimeMillis() / 1000
                    val expired = userinfo.expire < nowSec
                    Text(
                        text = stringResource(R.string.title_expire, dateStr),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (subItem.announce.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subItem.announce,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (subItem.lastUpdated > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = Utils.formatTimestamp(subItem.lastUpdated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (subItem.supportUrl.isNotEmpty() || subItem.profileWebPageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subItem.supportUrl.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(subItem.supportUrl)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_about_24dp),
                            contentDescription = stringResource(R.string.title_support_url),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (subItem.profileWebPageUrl.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(subItem.profileWebPageUrl)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_in_browser_24dp),
                            contentDescription = stringResource(R.string.title_web_page_url),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onPing) {
                    Icon(
                        imageVector = Icons.Filled.NetworkPing,
                        contentDescription = stringResource(R.string.title_sub_ping),
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (!subItem.url.isNullOrEmpty()) {
                    IconButton(onClick = onUpdate) {
                        Icon(
                            painter = painterResource(R.drawable.ic_restore_24dp),
                            contentDescription = stringResource(R.string.title_sub_update)
                        )
                    }
                }
                if (!subItem.url.isNullOrEmpty()) {
                    IconButton(onClick = onShare) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share_24dp),
                            contentDescription = stringResource(R.string.title_configuration_share)
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = stringResource(R.string.menu_item_edit)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.menu_item_del_config)
                    )
                }
            }
        }
    }
}
