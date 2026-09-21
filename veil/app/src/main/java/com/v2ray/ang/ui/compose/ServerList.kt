package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.emoji2.text.EmojiCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.OlcrtcManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A subscription section shown in the grouped "All" tab: header info plus the
 * servers belonging to that subscription.
 */
data class ServerSection(
    val subscriptionId: String,
    val name: String,
    val descText: String,
    val trafficUsed: String,
    val trafficTotal: String,
    val trafficProgress: Int,
    val expiryText: String,
    val isExpired: Boolean,
    val servers: List<ServersCache>,
)

/** Builds subscription sections for the grouped "All" tab, mirroring the legacy adapter. */
fun buildServerSections(servers: List<ServersCache>): List<ServerSection> {
    val grouped = linkedMapOf<String, MutableList<ServersCache>>()
    for (sc in servers) {
        val subId = sc.profile.subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        grouped.getOrPut(subId) { mutableListOf() }.add(sc)
    }

    val subsOrder = MmkvManager.decodeSubsList()
    val remaining = grouped.toMutableMap()
    val sections = mutableListOf<ServerSection>()

    for (subId in subsOrder) {
        val list = remaining.remove(subId) ?: continue
        buildServerSection(subId, list)?.let { sections.add(it) }
    }
    for ((subId, list) in remaining) {
        buildServerSection(subId, list)?.let { sections.add(it) }
    }
    return sections
}

private fun buildServerSection(subId: String, servers: List<ServersCache>): ServerSection? {
    if (servers.isEmpty()) return null
    val subItem = MmkvManager.decodeSubscription(subId) ?: return null
    val name = if (subItem.profileTitle.isNotEmpty()) subItem.profileTitle else subItem.remarks.ifEmpty { subId }
    val descText = subItem.announce.ifEmpty { subItem.supportUrl }.ifEmpty { subItem.profileWebPageUrl }
    val userinfo = Utils.parseSubscriptionUserinfo(subItem.subscriptionUserinfo)
    val trafficUsed: String
    val trafficTotal: String
    val trafficProgress: Int
    val expiryText: String
    val isExpired: Boolean

    if (userinfo != null && (userinfo.upload > 0 || userinfo.download > 0 || userinfo.total > 0 || userinfo.expire > 0)) {
        val used = userinfo.upload + userinfo.download
        trafficUsed = Utils.formatBytes(used)
        trafficTotal = if (userinfo.total > 0) " / ${Utils.formatBytes(userinfo.total)}" else ""
        trafficProgress = if (userinfo.total > 0) ((used.toDouble() / userinfo.total) * 100).toInt().coerceIn(0, 100) else 0

        if (userinfo.expire > 0) {
            expiryText = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(userinfo.expire * 1000))
            isExpired = userinfo.expire < System.currentTimeMillis() / 1000
        } else {
            expiryText = ""
            isExpired = false
        }
    } else {
        trafficUsed = ""
        trafficTotal = ""
        trafficProgress = 0
        expiryText = ""
        isExpired = false
    }

    return ServerSection(
        subscriptionId = if (subId == AppConfig.DEFAULT_SUBSCRIPTION_ID) "" else subId,
        name = name,
        descText = descText,
        trafficUsed = trafficUsed,
        trafficTotal = trafficTotal,
        trafficProgress = trafficProgress,
        expiryText = expiryText,
        isExpired = isExpired,
        servers = servers,
    )
}

/**
 * Wraps the server list with an entrance/exit animation that plays whenever a
 * tab becomes active or inactive, giving a subtle fade + slide-in effect driven
 * by the theme's expressive motion tokens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListTabTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { transitionState.targetState = visible }

    val fadeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val slideSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val exitFadeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    AnimatedVisibility(
        visibleState = transitionState,
        modifier = modifier,
        enter = fadeIn(fadeSpec) + slideInVertically(slideSpec) { it / 12 },
        exit = fadeOut(exitFadeSpec),
    ) {
        content()
    }
}

/**
 * Shared server-list UI used by both [com.v2ray.ang.ui.AllServerFragment] and
 * [com.v2ray.ang.ui.GroupServerFragment]. Renders the server cards in one of three
 * layouts: flat list, double-column grid, or grouped sections.
 */
@Composable
fun ServerListScreen(
    servers: List<ServersCache>,
    isRunning: Boolean,
    cardStyleNew: Boolean,
    showEmojiAvatar: Boolean,
    doubleColumn: Boolean,
    grouped: Boolean,
    showIcons: Boolean,
    showSubscriptionChip: Boolean,
    sections: List<ServerSection>,
    collapsedIds: Set<String>,
    selectedGuid: String?,
    listState: LazyListState,
    gridState: LazyGridState,
    onSelectServer: (String) -> Unit,
    onShare: (String, ProfileItem, Int, Boolean) -> Unit,
    onEdit: (String, ProfileItem) -> Unit,
    onRemove: (String, Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
    onToggleGroup: (String) -> Unit,
) {
    when {
        grouped -> GroupedServerList(
            sections = sections,
            isRunning = isRunning,
            cardStyleNew = cardStyleNew,
            showEmojiAvatar = showEmojiAvatar,
            selectedGuid = selectedGuid,
            collapsedIds = collapsedIds,
            listState = listState,
            onToggleGroup = onToggleGroup,
            onSelectServer = onSelectServer,
            onShare = onShare,
            onEdit = onEdit,
            onRemove = onRemove,
        )

        doubleColumn -> GridServerList(
            servers = servers,
            isRunning = isRunning,
            cardStyleNew = cardStyleNew,
            showEmojiAvatar = showEmojiAvatar,
            showIcons = showIcons,
            showSubscriptionChip = showSubscriptionChip,
            selectedGuid = selectedGuid,
            gridState = gridState,
            onSelectServer = onSelectServer,
            onShare = onShare,
            onEdit = onEdit,
            onRemove = onRemove,
            onSwap = onSwap,
        )

        else -> FlatServerList(
            servers = servers,
            isRunning = isRunning,
            cardStyleNew = cardStyleNew,
            showEmojiAvatar = showEmojiAvatar,
            showIcons = showIcons,
            showSubscriptionChip = showSubscriptionChip,
            selectedGuid = selectedGuid,
            listState = listState,
            onSelectServer = onSelectServer,
            onShare = onShare,
            onEdit = onEdit,
            onRemove = onRemove,
            onSwap = onSwap,
        )
    }
}

@Composable
private fun FlatServerList(
    servers: List<ServersCache>,
    isRunning: Boolean,
    cardStyleNew: Boolean,
    showEmojiAvatar: Boolean,
    showIcons: Boolean,
    showSubscriptionChip: Boolean,
    selectedGuid: String?,
    listState: LazyListState,
    onSelectServer: (String) -> Unit,
    onShare: (String, ProfileItem, Int, Boolean) -> Unit,
    onEdit: (String, ProfileItem) -> Unit,
    onRemove: (String, Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
) {
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in servers.indices && to.index in servers.indices) {
            onSwap(from.index, to.index)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(listState),
    ) {
        itemsIndexed(servers, key = { _, sc -> sc.guid }) { index, sc ->
            ReorderableItem(reorderableState, key = sc.guid) { isDragging ->
                ReorderableListItem(scope = this, isDragging = isDragging) {
                    ServerCard(
                        server = sc,
                        index = index,
                        total = servers.size,
                        isSelected = sc.guid == selectedGuid,
                        isRunning = isRunning,
                        cardStyleNew = cardStyleNew,
                        showEmojiAvatar = showEmojiAvatar,
                        showIcons = showIcons,
                        doubleColumn = false,
                        grouped = false,
                        showSubscriptionChip = showSubscriptionChip,
                        onSelect = { onSelectServer(sc.guid) },
                        onShare = { more -> onShare(sc.guid, sc.profile, index, more) },
                        onEdit = { onEdit(sc.guid, sc.profile) },
                        onRemove = { onRemove(sc.guid, index) },
                    )
                }
            }
        }
        item(key = "footer") { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun GridServerList(
    servers: List<ServersCache>,
    isRunning: Boolean,
    cardStyleNew: Boolean,
    showEmojiAvatar: Boolean,
    showIcons: Boolean,
    showSubscriptionChip: Boolean,
    selectedGuid: String?,
    gridState: LazyGridState,
    onSelectServer: (String) -> Unit,
    onShare: (String, ProfileItem, Int, Boolean) -> Unit,
    onEdit: (String, ProfileItem) -> Unit,
    onRemove: (String, Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
) {
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        if (from.index in servers.indices && to.index in servers.indices) {
            onSwap(from.index, to.index)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(gridState),
    ) {
        itemsIndexed(servers, key = { _, sc -> sc.guid }) { index, sc ->
            ReorderableItem(reorderableState, key = sc.guid) { isDragging ->
                ReorderableGridItem(scope = this, isDragging = isDragging) {
                    ServerCard(
                        server = sc,
                        index = index,
                        total = servers.size,
                        isSelected = sc.guid == selectedGuid,
                        isRunning = isRunning,
                        cardStyleNew = cardStyleNew,
                        showEmojiAvatar = showEmojiAvatar,
                        showIcons = showIcons,
                        doubleColumn = true,
                        grouped = false,
                        showSubscriptionChip = showSubscriptionChip,
                        onSelect = { onSelectServer(sc.guid) },
                        onShare = { more -> onShare(sc.guid, sc.profile, index, more) },
                        onEdit = { onEdit(sc.guid, sc.profile) },
                        onRemove = { onRemove(sc.guid, index) },
                    )
                }
            }
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun GroupedServerList(
    sections: List<ServerSection>,
    isRunning: Boolean,
    cardStyleNew: Boolean,
    showEmojiAvatar: Boolean,
    selectedGuid: String?,
    collapsedIds: Set<String>,
    listState: LazyListState,
    onToggleGroup: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onShare: (String, ProfileItem, Int, Boolean) -> Unit,
    onEdit: (String, ProfileItem) -> Unit,
    onRemove: (String, Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(listState),
    ) {
        itemsIndexed(sections, key = { _, s -> s.subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID } }) { index, section ->
            GroupSection(
                section = section,
                index = index,
                total = sections.size,
                isRunning = isRunning,
                cardStyleNew = cardStyleNew,
                showEmojiAvatar = showEmojiAvatar,
                selectedGuid = selectedGuid,
                isExpanded = section.subscriptionId !in collapsedIds,
                onToggle = { onToggleGroup(section.subscriptionId) },
                onSelectServer = onSelectServer,
                onShare = onShare,
                onEdit = onEdit,
                onRemove = onRemove,
            )
        }
        item(key = "footer") { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupSection(
    section: ServerSection,
    index: Int,
    total: Int,
    isRunning: Boolean,
    cardStyleNew: Boolean,
    showEmojiAvatar: Boolean,
    selectedGuid: String?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelectServer: (String) -> Unit,
    onShare: (String, ProfileItem, Int, Boolean) -> Unit,
    onEdit: (String, ProfileItem) -> Unit,
    onRemove: (String, Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shape = sectionContainerShape(index, total)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onToggle()
                        },
                    )
                    .padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_down_24),
                            contentDescription = stringResource(
                                if (isExpanded) R.string.collapse_group else R.string.expand_group
                            ),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(if (isExpanded) 0f else -90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (section.descText.isNotEmpty()) {
                    Text(
                        text = section.descText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (section.trafficUsed.isNotEmpty()) {
                    Column(Modifier.padding(top = 6.dp)) {
                        Row {
                            Text(
                                text = stringResource(R.string.title_traffic),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${section.trafficUsed}${section.trafficTotal}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { section.trafficProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                        )
                        if (section.expiryText.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.title_expire, section.expiryText),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (section.isExpired) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(8.dp)) {
                    section.servers.forEachIndexed { i, sc ->
                        ServerCard(
                            server = sc,
                            index = i,
                            total = section.servers.size,
                            isSelected = sc.guid == selectedGuid,
                            isRunning = isRunning,
                            cardStyleNew = cardStyleNew,
                            showEmojiAvatar = showEmojiAvatar,
                            showIcons = false,
                            doubleColumn = false,
                            grouped = true,
                            showSubscriptionChip = false,
                            onSelect = { onSelectServer(sc.guid) },
                            onShare = { more -> onShare(sc.guid, sc.profile, i, more) },
                            onEdit = { onEdit(sc.guid, sc.profile) },
                            onRemove = { onRemove(sc.guid, i) },
                            onLongPress = { onShare(sc.guid, sc.profile, i, true) },
                        )
                        if (i < section.servers.lastIndex) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: ServersCache,
    index: Int,
    total: Int,
    isSelected: Boolean,
    isRunning: Boolean,
    cardStyleNew: Boolean,
    showEmojiAvatar: Boolean,
    showIcons: Boolean,
    doubleColumn: Boolean,
    grouped: Boolean,
    showSubscriptionChip: Boolean,
    onSelect: () -> Unit,
    onShare: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val profile = server.profile
    val guid = server.guid

    val name = profile.remarks
    val (leadingEmoji, displayName) = remember(name) { extractLeadingEmoji(name) }
    val avatarLabel = remember(name, showEmojiAvatar, doubleColumn) {
        if (showEmojiAvatar && !doubleColumn) {
            leadingEmoji ?: initialAvatarLabel(displayName)
        } else {
            null
        }
    }
    // Keep the emoji inline in the title when no avatar block is shown.
    val displayTitle = if (avatarLabel != null) displayName else name
    val address = getAddress(profile)
    val protocolDesc = getProtocolDescription(profile)
    val aff = MmkvManager.decodeServerAffiliationInfo(guid)
    val testResult = aff?.getTestDelayString().orEmpty()
    val testResultColor = if ((aff?.testDelayMillis ?: 0L) < 0L) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val subChip = if (showSubscriptionChip) subscriptionChipText(profile) else ""

    val shape = if (grouped) {
        groupedCardCornerShape(index, total, isSelected)
    } else {
        flatCardCornerShape(index, total, isSelected)
    }

    CardShell(
        shape = shape,
        isSelected = isSelected,
        onSelect = onSelect,
        onLongPress = onLongPress,
        modifier = if (grouped) Modifier else Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        if (cardStyleNew) {
            NewServerCardContent(
                name = displayTitle,
                avatarLabel = avatarLabel,
                subChip = subChip,
                protocolDesc = protocolDesc,
                testResult = testResult,
                testResultColor = testResultColor,
                showConnectedDot = isSelected && isRunning,
                showMoreButton = !grouped,
                onShare = onShare,
                onEdit = onEdit,
                onRemove = onRemove,
            )
        } else {
            ClassicServerCardContent(
                name = name,
                subChip = subChip,
                address = address,
                protocolDesc = protocolDesc,
                testResult = testResult,
                testResultColor = testResultColor,
                showActions = showIcons && !doubleColumn,
                showMoreButton = !grouped,
                onShare = onShare,
                onEdit = onEdit,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun ClassicServerCardContent(
    name: String,
    subChip: String,
    address: String,
    protocolDesc: String,
    testResult: String,
    testResultColor: androidx.compose.ui.graphics.Color,
    showActions: Boolean,
    showMoreButton: Boolean,
    onShare: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (subChip.isNotEmpty()) {
                    SubscriptionChip(subChip)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = protocolDesc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (testResult.isNotEmpty()) {
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.labelMedium,
                        color = testResultColor,
                    )
                }
            }
        }

        if (showActions) {
            IconButton(onClick = { onShare(false) }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.title_configuration_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.menu_item_edit_config),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.menu_item_del_config),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (showMoreButton) {
            var moreExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { moreExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.notification_action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ServerCardOverflowMenu(
                    expanded = moreExpanded,
                    onDismiss = { moreExpanded = false },
                    onShare = { onShare(false) },
                    onEdit = onEdit,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@Composable
private fun NewServerCardContent(
    name: String,
    avatarLabel: String?,
    subChip: String,
    protocolDesc: String,
    testResult: String,
    testResultColor: androidx.compose.ui.graphics.Color,
    showConnectedDot: Boolean,
    showMoreButton: Boolean,
    onShare: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (avatarLabel != null) 8.dp else 16.dp,
                top = 8.dp,
                end = 4.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarLabel != null) {
            ServerAvatarBlock(avatarLabel)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            // A remark made up solely of an emoji leaves no title text once the
            // emoji moved into the avatar, so skip the empty line.
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subChip.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SubscriptionChip(subChip)
                }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showConnectedDot) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colorConnected)
                    )
                }
                Text(
                    text = protocolDesc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (testResult.isNotEmpty()) {
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.labelMedium,
                        color = testResultColor,
                    )
                }
            }
        }

        if (showMoreButton) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.notification_action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ServerCardOverflowMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onShare = { onShare(false) },
                    onEdit = onEdit,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@Composable
private fun ServerCardOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_share)) },
            trailingIcon = {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
                onDismiss()
                onShare()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_edit)) },
            trailingIcon = {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_delete)) },
            trailingIcon = {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
                onDismiss()
                onRemove()
            },
        )
    }
}

@Composable
private fun SubscriptionChip(text: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ServerAvatarBlock(label: String) {
    val isEmoji = label.isEmojiSymbol()
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(MaterialTheme.shapes.large)
            // Tonal container without an outline: the secondary-container fill reads
            // as an avatar on any card background (normal, selected, grouped) and
            // keeps the emoji's own colours dominant.
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (isEmoji) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/**
 * Splits a leading emoji off a remark so it can be shown as a leading block
 * instead of inline in the title. Returns (emoji, remainder) or (null, name).
 */
private fun extractLeadingEmoji(name: String): Pair<String?, String> {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return null to name
    val first = firstGrapheme(trimmed) ?: return null to name
    if (!first.isEmojiSymbol()) return null to name
    return first to trimmed.substring(first.length).trimStart()
}

/** First grapheme of the name, uppercased, for the initial-letter avatar. */
private fun initialAvatarLabel(name: String): String? {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return null
    val first = firstGrapheme(trimmed) ?: return null
    // Uppercase first, then take the leading grapheme: "ß" -> "SS" -> "S",
    // keeping the single-letter intent that per-char uppercase would break.
    val upper = first.uppercase()
    return firstGrapheme(upper) ?: upper
}

/** Leading grapheme cluster, so multi-codepoint emoji stay intact. */
private fun firstGrapheme(text: String): String? {
    if (text.isEmpty()) return null
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val end = iterator.next()
    if (end <= 0 || end == java.text.BreakIterator.DONE) return null
    return text.substring(0, end)
}

private fun String.isEmojiSymbol(): Boolean {
    // EmojiCompat knows keycaps, flags, ZWJ sequences and ©/®/™ that a raw
    // codepoint scan can miss; fall back if the font metadata is not ready yet.
    // Int.MAX_VALUE asks about every metadata version, so EMOJI_SUPPORTED and
    // EMOJI_FALLBACK both count as "an emoji", EMOJI_UNSUPPORTED does not.
    try {
        val match = EmojiCompat.get().getEmojiMatch(this, Int.MAX_VALUE)
        if (match != EmojiCompat.EMOJI_UNSUPPORTED) return true
    } catch (_: Throwable) {
        // Not initialised/loaded — use the codepoint heuristic below.
    }
    if (isEmpty()) return false
    var i = 0
    while (i < length) {
        val cp = Character.codePointAt(this, i)
        if (cp > 0x7F && cp.isEmojiCodePoint()) return true
        i += Character.charCount(cp)
    }
    return false
}

private fun Int.isEmojiCodePoint(): Boolean =
    this in 0x1F000..0x1FAFF || // emoji & pictographs
        this in 0x1F1E6..0x1F1FF || // regional indicators (flags)
        this in 0x2600..0x27BF || // misc symbols & dingbats
        this in 0x2B00..0x2BFF || // misc symbols and arrows
        this in 0x2900..0x297F || // supplemental arrows
        this in 0x2190..0x21FF || // arrows
        this in 0x2300..0x23FF || // misc technical
        this == 0x00A9 || this == 0x00AE || // © ®
        this == 0x203C || this == 0x2049 ||
        this == 0x20E3 || this == 0x2122 || this == 0x2139 || // keycap, ™ ℹ
        this in 0xFE00..0xFE0F // variation selectors

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CardShell(
    shape: Shape,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceBright
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val elevation by animateDpAsState(if (isSelected) 4.dp else 0.dp, label = "cardElevation")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val releaseSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        scale.animateTo(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = if (pressed) pressSpec else releaseSpec,
        )
    }

    Surface(
        shape = shape,
        color = bgColor,
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    ) {
        Box(
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onSelect()
                },
                onLongClick = onLongPress?.let { cb ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb()
                    }
                },
            )
        ) {
            content()
        }
    }
}

private fun flatCardCornerShape(index: Int, total: Int, isSelected: Boolean): RoundedCornerShape {
    val large = 24.dp
    val small = 5.dp
    val (top, bottom) = when {
        isSelected -> large to large
        total <= 1 -> large to large
        index == 0 -> large to small
        index == total - 1 -> small to large
        else -> small to small
    }
    return RoundedCornerShape(top, top, bottom, bottom)
}

private fun groupedCardCornerShape(index: Int, total: Int, isSelected: Boolean): RoundedCornerShape {
    val large = 24.dp
    val container = 16.dp
    val inner = 4.dp
    val top = when {
        isSelected -> large
        index == 0 -> container
        else -> inner
    }
    val bottom = when {
        isSelected -> large
        index == total - 1 -> container
        else -> inner
    }
    return RoundedCornerShape(top, top, bottom, bottom)
}

private fun sectionContainerShape(index: Int, total: Int): RoundedCornerShape {
    val large = 16.dp
    val small = 4.dp
    val top = if (index == 0) large else small
    val bottom = if (index == total - 1) large else small
    return RoundedCornerShape(top, top, bottom, bottom)
}

private fun getAddress(profile: ProfileItem): String {
    if (profile.configType == EConfigType.OLCRTC) {
        return OlcrtcManager.providerUrl(
            profile.olcrtcCarrier.orEmpty(),
            profile.olcrtcRoomId,
            profile.olcrtcServerUrl
        )
    }
    if (profile.configType == EConfigType.OPENFLUX) {
        return profile.openfluxUrl.nullIfBlank() ?: profile.openfluxTransport.orEmpty()
    }
    return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
}

private fun subscriptionChipText(profile: ProfileItem): String {
    if (profile.subscriptionId.isEmpty()) return ""
    return MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()?.toString() ?: ""
}

private fun getProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) {
        return profile.configType.name
    }

    if (profile.configType == EConfigType.OLCRTC) {
        val parts = mutableListOf<String>()
        parts.add("olcRTC")
        profile.olcrtcCarrier?.let { parts.add(it) }
        profile.olcrtcTransport?.let { parts.add(it) }
        return parts.joinToString(" / ")
    }

    if (profile.configType == EConfigType.OPENFLUX) {
        val parts = mutableListOf<String>()
        parts.add("OpenFlux")
        profile.openfluxTransport?.let { parts.add(it) }
        return parts.joinToString(" / ")
    }

    val parts = mutableListOf<String>()
    parts.add(profile.configType.name)

    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
            parts.add(net)
        }
    }

    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }

    return parts.joinToString(" / ")
}
