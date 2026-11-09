package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubEditActivity : BaseComponentActivity() {
    private val editSubId by lazy { intent.getStringExtra("subId").orEmpty() }
    private lateinit var suggestions: List<String>
    private lateinit var subItem: SubscriptionItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        suggestions = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
        subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()
    }

    @Composable
    override fun ScreenContent() {
        SubEditScreen(
            editSubId = editSubId,
            initial = subItem,
            profileSuggestions = suggestions,
            onBackClick = { finish() },
            onSave = { saveServer(it) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(subItem: SubscriptionItem): Boolean {
        if (TextUtils.isEmpty(subItem.remarks)) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (!subItem.url.isNullOrEmpty()) {
            if (!Utils.isValidUrl(subItem.url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            if (!Utils.isValidSubUrl(subItem.url)) {
                toast(R.string.toast_insecure_url_protocol)
                if (!subItem.allowInsecureUrl) {
                    return false
                }
            }
        }

        if (subItem.autoUpdate && subItem.updateInterval < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
            toast(R.string.toast_invalid_update_interval)
            return false
        }

        if (!subItem.hwid.isNullOrBlank() && !subItem.hwid!!.matches(Regex("^[a-zA-Z0-9=-]{10,64}$"))) {
            toast(R.string.sub_setting_hwid_validation)
            return false
        }

        MmkvManager.encodeSubscription(editSubId, subItem)
        SubscriptionUpdater.syncOne(subId = editSubId)
        SettingsChangeManager.makeSetupGroupTab()
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editSubId.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                SettingsManager.removeSubscriptionWithDefault(editSubId)
                SettingsChangeManager.makeSetupGroupTab()
                launch(Dispatchers.Main) { finish() }
            }
        }
        return true
    }
}

@Composable
fun SubEditScreen(
    editSubId: String,
    initial: SubscriptionItem,
    profileSuggestions: List<String>,
    onBackClick: () -> Unit,
    onSave: (SubscriptionItem) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by rememberSaveable { mutableStateOf(initial.remarks) }
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var userAgent by rememberSaveable { mutableStateOf(initial.userAgent ?: "") }
    var hwid by rememberSaveable { mutableStateOf(initial.hwid ?: "") }
    var customHeadersText by rememberSaveable {
        mutableStateOf(
            initial.customHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: ""
        )
    }
    var filter by rememberSaveable { mutableStateOf(initial.filter ?: "") }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var autoUpdate by rememberSaveable { mutableStateOf(initial.autoUpdate) }
    var updateInterval by rememberSaveable { mutableStateOf(initial.updateInterval.toString()) }
    var allowInsecureUrl by rememberSaveable { mutableStateOf(initial.allowInsecureUrl) }
    var prevProfile by rememberSaveable { mutableStateOf(initial.prevProfile ?: "") }
    var nextProfile by rememberSaveable { mutableStateOf(initial.nextProfile ?: "") }

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)
    val scrollState = rememberScrollState()

    fun parseCustomHeaders(text: String): Map<String, String>? {
        if (text.isBlank()) return null
        val map = mutableMapOf<String, String>()
        text.lines().forEach { line ->
            val trimmed = line.trim()
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex > 0) {
                val key = trimmed.substring(0, colonIndex).trim()
                val value = trimmed.substring(colonIndex + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map.ifEmpty { null }
    }

    fun buildSubItem(): SubscriptionItem {
        val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()
        subItem.remarks = remarks
        subItem.url = url
        subItem.userAgent = userAgent
        subItem.hwid = hwid.ifBlank { null }
        subItem.customHeaders = parseCustomHeaders(customHeadersText)
        subItem.filter = filter
        subItem.enabled = enabled
        subItem.autoUpdate = autoUpdate
        subItem.updateInterval = updateInterval.toLongOrNull() ?: subItem.updateInterval
        subItem.prevProfile = prevProfile
        subItem.nextProfile = nextProfile
        subItem.allowInsecureUrl = allowInsecureUrl
        return subItem
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            Column {
                AppTopBar(
                    title = stringResource(R.string.title_sub_setting),
                    onBackClick = onBackClick,
                    actions = {
                        if (editSubId.isNotEmpty()) {
                            IconButton(onClick = {
                                if (confirmRemove) showDeleteConfirm = true else onDelete()
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.menu_item_del_config)
                                )
                            }
                        }
                        IconButton(onClick = { onSave(buildSubItem()) }) {
                            Icon(
                                painterResource(R.drawable.ic_fab_check),
                                contentDescription = stringResource(R.string.menu_item_save_config)
                            )
                        }
                    }
                )
                if (editSubId.isNotEmpty()) {
                    SubscriptionMetaStrip(
                        subItem = initial,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .verticalScrollbar(scrollState)
                .padding(vertical = 8.dp)
                .padding(bottom = 36.dp)
        ) {
            FormTextField(stringResource(R.string.sub_setting_remarks), remarks, { remarks = it })
            FormTextField(stringResource(R.string.sub_setting_url), url, { url = it })
            FormTextField(
                stringResource(R.string.sub_setting_user_agent),
                userAgent,
                { userAgent = it }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FormTextField(
                    label = stringResource(R.string.sub_setting_hwid),
                    value = hwid,
                    onValueChange = { hwid = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { hwid = Utils.generateHwid() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.sub_setting_hwid_generate))
                }
            }
            FormTextField(
                label = stringResource(R.string.sub_setting_custom_headers),
                value = customHeadersText,
                onValueChange = { customHeadersText = it },
                placeholder = stringResource(R.string.sub_setting_custom_headers_hint)
            )
            FormTextField(stringResource(R.string.sub_setting_filter), filter, { filter = it })
            SettingsSwitchItem(
                title = stringResource(R.string.sub_setting_enable),
                checked = enabled,
                onCheckedChange = { enabled = it }
            )

            SettingsSwitchItem(
                title = stringResource(R.string.sub_auto_update),
                checked = autoUpdate,
                onCheckedChange = { autoUpdate = it }
            )

            FormTextField(
                stringResource(R.string.title_pref_auto_update_interval),
                updateInterval,
                { updateInterval = it },
                keyboardType = KeyboardType.Number
            )

            SettingsSwitchItem(
                title = stringResource(R.string.sub_allow_insecure_url),
                checked = allowInsecureUrl,
                onCheckedChange = { allowInsecureUrl = it }
            )
            FormDropdownField(
                label = stringResource(R.string.sub_setting_pre_profile),
                placeholder = stringResource(R.string.sub_setting_pre_profile_tip),
                value = prevProfile,
                options = profileSuggestions,
                onValueChange = { prevProfile = it },
                editable = true
            )
            FormDropdownField(
                label = stringResource(R.string.sub_setting_next_profile),
                placeholder = stringResource(R.string.sub_setting_pre_profile_tip),
                value = nextProfile,
                options = profileSuggestions,
                onValueChange = { nextProfile = it },
                editable = true
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun SubscriptionMetaStrip(
    subItem: SubscriptionItem,
    modifier: Modifier = Modifier,
) {
    val userinfo = remember(subItem.subscriptionUserinfo) {
        Utils.parseSubscriptionUserinfo(subItem.subscriptionUserinfo)
    }
    val used = (userinfo?.upload ?: 0L) + (userinfo?.download ?: 0L)
    val total = userinfo?.total ?: 0L
    val expire = userinfo?.expire ?: 0L
    val now = remember { System.currentTimeMillis() }
    val hasTraffic = used > 0 || total > 0
    val hasExpiry = expire > 0
    val hasUpdated = subItem.lastUpdated > 0
    val hasAnnounce = subItem.announce.isNotBlank()
    val hasLinks = subItem.supportUrl.isNotBlank() || subItem.profileWebPageUrl.isNotBlank()
    var expanded by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val expireDate = if (hasExpiry) dateFormat.format(Date(expire * 1000)) else ""
    val expired = hasExpiry && expire * 1000 < now
    val daysLeft = if (hasExpiry && !expired) {
        ((expire * 1000 - now) / 86_400_000L).toInt()
    } else {
        0
    }
    val progress = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val trafficText = when {
        !hasTraffic -> ""
        total > 0 -> "${Utils.formatBytes(used)} / ${Utils.formatBytes(total)}"
        else -> Utils.formatBytes(used)
    }
    val trafficDetail = if (hasTraffic && total > 0) {
        "$trafficText · ${(progress * 100).toInt()}%"
    } else {
        trafficText
    }
    val expiryShort = when {
        !hasExpiry -> ""
        expired -> stringResource(R.string.sub_meta_expired)
        else -> stringResource(R.string.sub_meta_until, expireDate)
    }
    val expiryDetail = when {
        !hasExpiry -> ""
        expired -> "$expireDate · ${stringResource(R.string.sub_meta_expired)}"
        daysLeft <= 0 -> "$expireDate · ${stringResource(R.string.sub_meta_less_than_day)}"
        else -> "$expireDate · ${stringResource(R.string.sub_meta_days_left, daysLeft)}"
    }
    val updatedText = if (hasUpdated) {
        DateUtils.getRelativeTimeSpanString(
            subItem.lastUpdated,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    } else {
        ""
    }
    val summary = listOf(trafficText, expiryShort)
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { stringResource(R.string.sub_meta_title) }
    val progressColor = if (progress >= 0.9f) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    if (!hasTraffic && !hasExpiry && !hasUpdated && !hasAnnounce && !hasLinks) return

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down_24),
                    contentDescription = stringResource(
                        if (expanded) R.string.sub_meta_collapse else R.string.sub_meta_expand
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasTraffic && total > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(50)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    if (hasTraffic) {
                        MetaLine(stringResource(R.string.sub_meta_traffic), trafficDetail)
                    }
                    if (hasExpiry) {
                        MetaLine(
                            label = stringResource(R.string.sub_meta_expires),
                            value = expiryDetail,
                            valueColor = if (expired) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (hasUpdated) {
                        MetaLine(stringResource(R.string.sub_meta_updated), updatedText)
                    }
                    if (hasAnnounce) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = subItem.announce,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (hasLinks) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            if (subItem.supportUrl.isNotBlank()) {
                                TextButton(onClick = { Utils.openUri(context, subItem.supportUrl) }) {
                                    Text(stringResource(R.string.sub_meta_support))
                                }
                            }
                            if (subItem.profileWebPageUrl.isNotBlank()) {
                                TextButton(onClick = { Utils.openUri(context, subItem.profileWebPageUrl) }) {
                                    Text(stringResource(R.string.sub_meta_webpage))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}
