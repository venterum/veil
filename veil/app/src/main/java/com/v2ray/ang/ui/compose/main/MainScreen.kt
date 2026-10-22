package com.v2ray.ang.ui.compose.main

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.ConnectionInfoSheet
import com.v2ray.ang.ui.compose.ConnectionInfoSheetState
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ExpressiveBottomBar
import com.v2ray.ang.ui.compose.ExpressiveBottomBarState
import com.v2ray.ang.ui.compose.ExpressiveToolbarActions
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.ServerListScreen
import com.v2ray.ang.ui.compose.VeilSearchBar
import com.v2ray.ang.ui.compose.buildServerSections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Immutable
data class MainScreenActions(
    val onStartStop: () -> Unit,
    val onTunToggle: () -> Unit,
    val onTest: () -> Unit,
    val onConnectionInfo: () -> Unit,
    val onModeSelected: (String) -> Unit,
    val onFilter: (String) -> Unit,
    val onGroupSelected: (String) -> Unit,
    val onSelectServer: (String) -> Unit,
    val onSwapServers: (Int, Int) -> Unit,
    val onEditServer: (String, ProfileItem) -> Unit,
    val onRemoveServer: (String) -> Unit,
    val onDrawerNavigate: (MainDrawerDestination) -> Unit,
    val onImportAction: (Int) -> Unit,
    val onOverflowAction: (Int) -> Unit,
    val onDestructiveAction: (Int) -> Unit,
    val onConnectionInfoDismiss: () -> Unit,
    val onCopyIp: () -> Unit,
    val onPingFromInfo: () -> Unit,
    val onShareFromInfo: () -> Unit,
    val onClipboardImportConfirm: () -> Unit,
    val onClipboardImportDismiss: () -> Unit,
)

private data class ShareTarget(
    val guid: String,
    val profile: ProfileItem,
    val more: Boolean,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun MainScreen(
    groups: List<GroupMapItem>,
    serversFlow: StateFlow<List<ServersCache>>,
    subscriptionIdFlow: StateFlow<String>,
    listRevisionFlow: StateFlow<Int>,
    connection: ConnectionUiState,
    selectedGuid: String,
    display: MainDisplayFlags,
    currentMode: String,
    searchButtonEnabled: Boolean,
    importing: Boolean,
    infoState: ConnectionInfoSheetState,
    clipboardImport: ClipboardImportPreview?,
    actions: MainScreenActions,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val focusManager = LocalFocusManager.current
    val servers by serversFlow.collectAsStateWithLifecycle()
    val currentSubscriptionId by subscriptionIdFlow.collectAsStateWithLifecycle()
    val listRevision by listRevisionFlow.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showModeSheet by rememberSaveable { mutableStateOf(false) }
    var addMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var pendingRemoveGuid by remember { mutableStateOf<String?>(null) }
    var pendingDestructive by remember { mutableStateOf<Int?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }
    var pendingScrollGuid by remember { mutableStateOf<String?>(null) }
    var collapsedIds by remember { mutableStateOf(loadCollapsedIds()) }

    val serverCaches = remember { mutableStateMapOf<String, List<ServersCache>>() }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }

    val initialPage = remember(groups) {
        groups.indexOfFirst { it.id == currentSubscriptionId }.takeIf { it >= 0 }
            ?: (groups.size - 1).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { groups.size }

    val counts = remember(groups, servers, listRevision) {
        groups.associate { group ->
            group.id to if (group.id.isEmpty()) {
                MmkvManager.decodeAllServerList().size
            } else {
                MmkvManager.decodeServerList(group.id).size
            }
        }
    }

    fun closeSearch() {
        searchVisible = false
        searchQuery = ""
        actions.onFilter("")
    }

    fun toggleCollapsed(id: String) {
        val newSet = collapsedIds.toMutableSet()
        if (id in newSet) newSet.remove(id) else newSet.add(id)
        collapsedIds = newSet
        MmkvManager.encodeSettings(AppConfig.PREF_GROUP_COLLAPSED_IDS, newSet.joinToString(","))
    }

    fun requestRemove(guid: String) {
        if (guid == MmkvManager.getSelectServer()) {
            context.toast(R.string.toast_action_not_allowed)
            return
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            pendingRemoveGuid = guid
        } else {
            actions.onRemoveServer(guid)
        }
    }

    fun locateSelected() {
        val guid = MmkvManager.getSelectServer()
        if (guid.isNullOrEmpty()) {
            context.toast(R.string.title_file_chooser)
            return
        }
        if (searchVisible) closeSearch()
        val subscriptionId = MmkvManager.decodeServerConfig(guid)?.subscriptionId.orEmpty()
        val targetPage = findTargetGroupIndex(groups, subscriptionId)
        if (targetPage < 0) {
            context.toast(R.string.toast_server_not_found_in_group)
            return
        }
        scope.launch {
            pagerState.animateScrollToPage(targetPage)
            pendingScrollGuid = guid
        }
    }

    fun handleOverflow(actionId: Int) {
        when (actionId) {
            R.id.search_view -> searchVisible = true
            R.id.locate_selected_config -> locateSelected()
            R.id.del_all_config,
            R.id.del_duplicate_config,
            R.id.del_invalid_config -> pendingDestructive = actionId

            else -> actions.onOverflowAction(actionId)
        }
    }

    LaunchedEffect(servers, currentSubscriptionId) {
        serverCaches[currentSubscriptionId] = servers
    }

    LaunchedEffect(groups, currentSubscriptionId) {
        val target = groups.indexOfFirst { it.id == currentSubscriptionId }
        if (target >= 0) {
            if (groups.getOrNull(pagerState.currentPage)?.id != currentSubscriptionId) {
                pagerState.scrollToPage(target)
            }
        } else if (groups.isNotEmpty() && pagerState.currentPage !in groups.indices) {
            pagerState.scrollToPage(groups.lastIndex)
        }
    }

    LaunchedEffect(pagerState, groups, currentSubscriptionId) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val group = groups.getOrNull(page) ?: return@collect
            if (group.id != currentSubscriptionId) {
                actions.onGroupSelected(group.id)
            }
        }
    }

    LaunchedEffect(pendingScrollGuid, currentSubscriptionId, servers) {
        val guid = pendingScrollGuid ?: return@LaunchedEffect
        val page = pagerState.currentPage
        val group = groups.getOrNull(page) ?: return@LaunchedEffect
        if (group.id != currentSubscriptionId) return@LaunchedEffect

        val listState = listStates.getOrPut(group.id) { LazyListState() }
        val gridState = gridStates.getOrPut(group.id) { LazyGridState() }
        if (group.id.isEmpty() && display.grouped) {
            val sectionIndex = buildServerSections(servers).indexOfFirst { section ->
                section.servers.any { it.guid == guid }
            }
            if (sectionIndex >= 0) {
                listState.animateScrollToItem(sectionIndex)
                pendingScrollGuid = null
            }
        } else {
            val index = servers.indexOfFirst { it.guid == guid }
            if (index >= 0) {
                if (display.doubleColumn) {
                    gridState.animateScrollToItem(index)
                } else {
                    listState.animateScrollToItem(index)
                }
                pendingScrollGuid = null
            }
        }
    }

    LaunchedEffect(searchButtonEnabled) {
        if (!searchButtonEnabled && searchVisible) closeSearch()
    }

    val selectedGroup = groups.getOrNull(pagerState.currentPage)
    val selectedGroupId = selectedGroup?.id ?: currentSubscriptionId
    val selectedGroupCount = counts[selectedGroupId] ?: 0
    val selectedGroupLabel = selectedGroup?.remarks ?: stringResource(R.string.app_name)
    val headerSubtitle = if (groups.isEmpty()) {
        stringResource(R.string.main_empty_group_title)
    } else {
        pluralStringResource(
            R.plurals.main_header_group_count,
            selectedGroupCount,
            selectedGroupLabel,
            selectedGroupCount,
        )
    }
    val subtitleFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val drawerModeClick = remember {
        {
            scope.launch { drawerState.close() }
            showModeSheet = true
        }
    }
    val drawerNavigate = remember {
        { destination: MainDrawerDestination ->
            scope.launch { drawerState.close() }
            actions.onDrawerNavigate(destination)
        }
    }

    val lastTabHapticAt = remember { mutableLongStateOf(0L) }
    fun tabHaptic() {
        val now = System.currentTimeMillis()
        if (now - lastTabHapticAt.longValue >= 250L) {
            lastTabHapticAt.longValue = now
            haptic.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey
            )
        }
    }

    val onGroupSelect: (Int) -> Unit = remember {
        { index: Int ->
            tabHaptic()
            scope.launch { pagerState.animateScrollToPage(index) }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { tabHaptic() }
    }

    BackHandler(enabled = searchVisible && !drawerState.isOpen) {
        closeSearch()
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Transparent,
        drawerContent = {
            MainDrawer(
                mode = currentMode,
                onModeClick = drawerModeClick,
                onNavigate = drawerNavigate,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    val offset = drawerState.currentOffset
                    val progress = if (offset.isNaN()) {
                        0f
                    } else {
                        1f - (abs(offset) / 300.dp.toPx()).coerceIn(0f, 1f)
                    }
                    translationX = progress * 300.dp.toPx() * direction
                }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            CompositionLocalProvider(
                LocalBolderMainFont provides display.bolderFont,
                LocalShowUnsupportedPingPill provides display.showUnsupportedPingPill,
            ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    Column {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        scope.launch { drawerState.open() }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = stringResource(R.string.navigation_drawer_open),
                                    )
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = if (display.bolderFont) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    )
                                    AnimatedContent(
                                        targetState = headerSubtitle,
                                        transitionSpec = {
                                            fadeIn(subtitleFadeSpec) togetherWith fadeOut(subtitleFadeSpec)
                                        },
                                        label = "groupSubtitle",
                                    ) { subtitle ->
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                            actions = {
                                ExpressiveToolbarActions(
                                    onFilterClick = {
                                        if (searchVisible) closeSearch() else searchVisible = true
                                    },
                                    onImportMenuAction = actions.onImportAction,
                                    onOverflowAction = ::handleOverflow,
                                    addMenuExpanded = addMenuExpanded,
                                    onAddMenuExpandedChange = { addMenuExpanded = it },
                                    showSearchButton = searchButtonEnabled,
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                        )
                        VeilSearchBar(
                            query = searchQuery,
                            onQueryChange = { query ->
                                searchQuery = query
                                actions.onFilter(query)
                            },
                            visible = searchVisible,
                            onDismiss = { closeSearch() },
                        )
                        AnimatedVisibility(visible = importing) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            )
                        }
                    }
                },
            bottomBar = {
                val stateTitle = when {
                    connection.isLoading -> stringResource(R.string.connection_connecting)
                    connection.isRunning -> stringResource(R.string.connection_connected)
                    else -> stringResource(R.string.connection_not_connected)
                }
                ExpressiveBottomBar(
                    state = ExpressiveBottomBarState(
                        isRunning = connection.isRunning,
                        isLoading = connection.isLoading,
                        status = connection.statusDetail.ifEmpty { stateTitle },
                        serverName = connection.serverName,
                        connectionIp = connection.connectionIp,
                        uploadSpeed = connection.uploadSpeed,
                        downloadSpeed = connection.downloadSpeed,
                        uptime = connection.uptime,
                        showSpeed = connection.showSpeed,
                        showTun = connection.showTun,
                        tunEnabled = connection.tunEnabled,
                    ),
                    onStartStop = actions.onStartStop,
                    onModeSelector = { showModeSheet = true },
                    onTunToggle = actions.onTunToggle,
                    onTest = actions.onTest,
                    onConnectionInfo = actions.onConnectionInfo,
                    modifier = Modifier.navigationBarsPadding(),
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (groups.isEmpty()) {
                    EmptyGroupState(
                        searching = searchQuery.isNotBlank(),
                        onAddConfig = { addMenuExpanded = true },
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = groups.size > 1) {
                            GroupRail(
                                groups = groups,
                                counts = counts,
                                selectedId = selectedGroupId,
                                onSelect = onGroupSelect,
                            )
                        }
                        AnimatedVisibility(visible = !searchVisible && groups.isNotEmpty()) {
                            MainQuickActions(
                                showTestAll = display.showQuickTestAll,
                                showSort = display.showQuickSort,
                                showUpdate = display.showQuickUpdate,
                                importing = importing,
                                onAction = ::handleOverflow,
                            )
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            beyondViewportPageCount = 1,
                            key = { page -> groups.getOrNull(page)?.id ?: page },
                        ) { page ->
                            val group = groups.getOrNull(page) ?: return@HorizontalPager
                            val isActive = group.id == currentSubscriptionId
                            val pageServers = if (isActive) servers else serverCaches[group.id].orEmpty()
                            val listState = listStates.getOrPut(group.id) { LazyListState() }
                            val gridState = gridStates.getOrPut(group.id) { LazyGridState() }
                            val groupedPage = group.id.isEmpty() && display.grouped
                            val pageCount = counts[group.id] ?: 0
                            val onShareServer = remember {
                                { guid: String, profile: ProfileItem, _: Int, more: Boolean ->
                                    shareTarget = ShareTarget(guid, profile, more)
                                }
                            }
                            val onRemoveServer = remember {
                                { guid: String, _: Int -> requestRemove(guid) }
                            }
                            val onToggleGroup = remember {
                                { id: String -> toggleCollapsed(id) }
                            }
                            val sections = remember(pageServers, groupedPage) {
                                if (groupedPage) buildServerSections(pageServers) else emptyList()
                            }

                            when {
                                pageServers.isEmpty() && !isActive && pageCount > 0 -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        LoadingIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.main_loading_servers),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                pageServers.isEmpty() -> EmptyGroupState(
                                    searching = searchQuery.isNotBlank(),
                                    onAddConfig = { addMenuExpanded = true },
                                )

                                else -> ServerListScreen(
                                    servers = pageServers,
                                    isRunning = connection.isRunning,
                                    cardStyleNew = display.cardStyleNew,
                                    showEmojiAvatar = display.showEmojiAvatar,
                                    doubleColumn = display.doubleColumn,
                                    grouped = groupedPage,
                                    showIcons = if (group.id.isEmpty()) display.showIcons else true,
                                    showSubscriptionChip = group.id.isEmpty() && !groupedPage,
                                    sections = sections,
                                    collapsedIds = collapsedIds,
                                    selectedGuid = selectedGuid,
                                    listState = listState,
                                    gridState = gridState,
                                    onSelectServer = actions.onSelectServer,
                                    onShare = onShareServer,
                                    onEdit = actions.onEditServer,
                                    onRemove = onRemoveServer,
                                    onSwap = actions.onSwapServers,
                                    onToggleGroup = onToggleGroup,
                                )
                            }
                        }
                    }
                }
            }
        }
            }
        }
    }

    shareTarget?.let { target ->
        val isCustom = target.profile.configType.isComplexType()
        val options = if (target.more) {
            stringArrayResource(R.array.share_method_more).let { all ->
                if (isCustom) all.takeLast(3) else all.toList()
            }
        } else {
            stringArrayResource(R.array.share_method).let { all ->
                if (isCustom) all.takeLast(1) else all.toList()
            }
        }
        val skip = if (isCustom) 2 else 0

        SelectListDialog(
            title = stringResource(R.string.title_configuration_share),
            options = options,
            onSelected = { index, _ ->
                shareTarget = null
                when (index + skip) {
                    0 -> {
                        val bitmap = AngConfigManager.share2QRCode(target.guid)
                        if (bitmap == null) {
                            context.toastError(R.string.toast_failure)
                        } else {
                            qrBitmap = bitmap
                            showQrDialog = true
                        }
                    }

                    1 -> {
                        if (AngConfigManager.share2Clipboard(context, target.guid) == 0) {
                            context.toastSuccess(R.string.toast_success)
                        } else {
                            context.toastError(R.string.toast_failure)
                        }
                    }

                    2 -> {
                        scope.launch(Dispatchers.IO) {
                            val result = AngConfigManager.shareFullContent2Clipboard(context, target.guid)
                            withContext(Dispatchers.Main) {
                                if (result == 0) context.toastSuccess(R.string.toast_success)
                                else context.toastError(R.string.toast_failure)
                            }
                        }
                    }

                    3 -> actions.onEditServer(target.guid, target.profile)
                    4 -> requestRemove(target.guid)
                }
            },
            onDismiss = { shareTarget = null },
        )
    }

    if (showQrDialog && qrBitmap != null) {
        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = {
                showQrDialog = false
                qrBitmap = null
            },
        )
    }

    pendingRemoveGuid?.let { guid ->
        DeleteConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            onConfirm = {
                pendingRemoveGuid = null
                actions.onRemoveServer(guid)
            },
            onDismiss = { pendingRemoveGuid = null },
        )
    }

    pendingDestructive?.let { actionId ->
        DeleteConfirmDialog(
            message = stringResource(
                if (actionId == R.id.del_invalid_config) {
                    R.string.del_invalid_config_comfirm
                } else {
                    R.string.del_config_comfirm
                }
            ),
            onConfirm = {
                pendingDestructive = null
                actions.onDestructiveAction(actionId)
            },
            onDismiss = { pendingDestructive = null },
        )
    }

    clipboardImport?.let { preview ->
        ConfirmDialog(
            title = stringResource(R.string.import_clipboard_title),
            message = preview.message,
            confirmText = stringResource(R.string.action_import),
            onConfirm = actions.onClipboardImportConfirm,
            onDismiss = actions.onClipboardImportDismiss,
        )
    }

    ConnectionInfoSheet(
        state = infoState,
        onDismiss = actions.onConnectionInfoDismiss,
        onCopyIp = actions.onCopyIp,
        onPing = actions.onPingFromInfo,
        onShare = actions.onShareFromInfo,
    )

    if (showModeSheet) {
        ModeSheet(
            currentMode = currentMode,
            onSelect = actions.onModeSelected,
            onDismiss = { showModeSheet = false },
        )
    }
}

@Composable
private fun MainQuickActions(
    showTestAll: Boolean,
    showSort: Boolean,
    showUpdate: Boolean,
    importing: Boolean,
    onAction: (Int) -> Unit,
) {
    if (!showTestAll && !showSort && !showUpdate) return

    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTestAll) {
            QuickActionButton(
                icon = Icons.Filled.NetworkPing,
                label = stringResource(R.string.main_quick_test_all),
                enabled = !importing,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onAction(R.id.real_ping_all)
                },
            )
        }
        if (showSort) {
            QuickActionButton(
                icon = Icons.AutoMirrored.Filled.Sort,
                label = stringResource(R.string.main_quick_sort),
                enabled = !importing,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onAction(R.id.sort_by_test_results)
                },
            )
        }
        if (showUpdate) {
            QuickActionButton(
                icon = Icons.Filled.CloudDownload,
                label = stringResource(R.string.main_quick_update),
                enabled = !importing,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onAction(R.id.sub_update)
                },
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyGroupState(
    searching: Boolean,
    onAddConfig: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (searching) Icons.Outlined.Search else Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(
                if (searching) R.string.main_empty_search_title else R.string.main_empty_group_title
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (searching) R.string.main_empty_search_message else R.string.main_empty_group_message
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!searching) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAddConfig) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.menu_item_add_config))
            }
        }
    }
}

/**
 * Returns the pager page for the group that owns [subscriptionId], falling back
 * to the "All" group (empty id) when the server has no matching subscription tab.
 */
internal fun findTargetGroupIndex(groups: List<GroupMapItem>, subscriptionId: String): Int =
    groups.indexOfFirst { it.id == subscriptionId }.takeIf { it >= 0 }
        ?: groups.indexOfFirst { it.id.isEmpty() }

private fun loadCollapsedIds(): Set<String> =
    MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_COLLAPSED_IDS, "")
        ?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
