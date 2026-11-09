package com.v2ray.ang.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full subscription info sheet: every piece of text and traffic metadata the
 * subscription provides, without truncation.
 *
 * Opened by tapping a subscription in the subscription list and, optionally,
 * by the round info button on the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionInfoSheet(
    subItem: SubscriptionItem,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val userinfo = remember(subItem.subscriptionUserinfo) {
        Utils.parseSubscriptionUserinfo(subItem.subscriptionUserinfo)
    }
    val upload = userinfo?.upload ?: 0L
    val download = userinfo?.download ?: 0L
    val used = upload + download
    val total = userinfo?.total ?: 0L
    val expire = userinfo?.expire ?: 0L
    val now = remember { System.currentTimeMillis() }
    val hasTraffic = used > 0 || total > 0
    val hasExpiry = expire > 0
    val hasUpdated = subItem.lastUpdated > 0
    val hasAnnounce = subItem.announce.isNotBlank()
    val hasSupport = subItem.supportUrl.isNotBlank()
    val hasWebPage = subItem.profileWebPageUrl.isNotBlank()
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
    val progressColor = if (progress >= 0.9f) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val displayName = subItem.profileTitle.ifBlank {
        subItem.remarks.ifBlank { stringResource(R.string.sub_meta_title) }
    }
    val expiryText = when {
        !hasExpiry -> ""
        expired -> "$expireDate · ${stringResource(R.string.sub_meta_expired)}"
        daysLeft <= 0 -> "$expireDate · ${stringResource(R.string.sub_meta_less_than_day)}"
        else -> "$expireDate · ${stringResource(R.string.sub_meta_days_left, daysLeft)}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subItem.remarks.isNotBlank() && subItem.remarks != displayName) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subItem.remarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!hasTraffic && !hasExpiry && !hasUpdated && !hasAnnounce && !hasSupport && !hasWebPage) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.sub_meta_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            if (hasTraffic) {
                InfoRow(
                    label = stringResource(R.string.sub_meta_traffic),
                    value = if (total > 0) {
                        "${Utils.formatBytes(used)} / ${Utils.formatBytes(total)} · ${(progress * 100).toInt()}%"
                    } else {
                        Utils.formatBytes(used)
                    },
                )
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            if (upload > 0) {
                InfoRow(stringResource(R.string.sub_meta_uploaded), Utils.formatBytes(upload))
            }
            if (download > 0) {
                InfoRow(stringResource(R.string.sub_meta_downloaded), Utils.formatBytes(download))
            }
            if (hasExpiry) {
                InfoRow(
                    label = stringResource(R.string.sub_meta_expires),
                    value = expiryText,
                    valueColor = if (expired) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (hasUpdated) {
                InfoRow(
                    label = stringResource(R.string.sub_meta_updated),
                    value = Utils.formatTimestamp(subItem.lastUpdated),
                )
            }
            if (hasAnnounce) {
                SectionLabel(stringResource(R.string.sub_meta_announce))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = subItem.announce,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            if (hasSupport) {
                SectionLabel(stringResource(R.string.title_support_url))
                LinkText(subItem.supportUrl) { Utils.openUri(context, it) }
            }
            if (hasWebPage) {
                SectionLabel(stringResource(R.string.title_web_page_url))
                LinkText(subItem.profileWebPageUrl) { Utils.openUri(context, it) }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LinkText(url: String, onOpen: (String) -> Unit) {
    Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(url) }
            .padding(vertical = 4.dp),
    )
}
