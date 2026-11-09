@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import java.text.Collator

private const val FILTER_ALL = 0
private const val FILTER_SELECTED = 1
private const val FILTER_SYSTEM = 2
private const val FILTER_RUSSIAN = 3

/**
 * Per-app proxy picker: two mode switches, search/filter controls and the app
 * list grouped into user and system apps (or filtered to the current choice).
 *
 * All selection state lives in the activity/ViewModel; this screen only renders
 * it and reports intents, so a configuration change keeps MMKV as the single
 * source of truth.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    loading: Boolean,
    selected: Set<String>,
    perAppEnabled: Boolean,
    bypassApps: Boolean,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSetPerAppEnabled: (Boolean) -> Unit,
    onSetBypassApps: (Boolean) -> Unit,
    onAddAll: (Collection<String>) -> Unit,
    onRemoveAll: (Collection<String>) -> Unit,
    onAutoSelect: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var menuExpanded by remember { mutableStateOf(false) }

    val visibleApps = remember(apps, selected, searchQuery, filter) {
        val query = searchQuery.trim()
        val collator = Collator.getInstance()
        apps.filter { app ->
            val matchesQuery = query.isEmpty() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesQuery && when (filter) {
                FILTER_SELECTED -> selected.contains(app.packageName)
                FILTER_SYSTEM -> app.isSystemApp
                FILTER_RUSSIAN -> app.packageName.startsWith("ru.")
                else -> true
            }
        }.sortedWith { a, b ->
            when {
                a.isSystemApp != b.isSystemApp -> if (a.isSystemApp) 1 else -1
                else -> collator.compare(a.appName, b.appName)
            }
        }
    }

    fun selectAllVisible() {
        val packages = visibleApps.map { it.packageName }
        if (packages.isNotEmpty() && packages.all { selected.contains(it) }) {
            onRemoveAll(packages)
        } else {
            onAddAll(packages)
        }
    }

    fun invertVisible() {
        onAddAll(visibleApps.filter { !selected.contains(it.packageName) }.map { it.packageName })
        onRemoveAll(visibleApps.filter { selected.contains(it.packageName) }.map { it.packageName })
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
                onBackClick = onBackClick,
                isLoading = loading,
                isSearchActive = searchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = {
                    searchActive = false
                    searchQuery = ""
                },
                searchPlaceholder = stringResource(R.string.hint_search),
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.menu_item_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.notification_action_more),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                shape = MaterialTheme.shapes.extraLarge,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                PickerMenuItem(
                                    icon = Icons.Filled.SelectAll,
                                    text = stringResource(R.string.menu_item_select_all),
                                ) {
                                    menuExpanded = false
                                    selectAllVisible()
                                }
                                PickerMenuItem(
                                    icon = Icons.Filled.SwapVert,
                                    text = stringResource(R.string.menu_item_invert_selection),
                                ) {
                                    menuExpanded = false
                                    invertVisible()
                                }
                                PickerMenuItem(
                                    icon = Icons.Filled.Translate,
                                    text = stringResource(R.string.menu_item_select_russian_apps),
                                ) {
                                    menuExpanded = false
                                    onAddAll(
                                        visibleApps
                                            .filter { it.packageName.startsWith("ru.") }
                                            .map { it.packageName }
                                    )
                                }
                                PickerMenuItem(
                                    icon = Icons.Filled.SelectAll,
                                    text = stringResource(R.string.menu_item_select_proxy_app),
                                ) {
                                    menuExpanded = false
                                    onAutoSelect()
                                }
                                PickerMenuItem(
                                    icon = Icons.Filled.ContentPaste,
                                    text = stringResource(R.string.menu_item_import_proxy_app),
                                ) {
                                    menuExpanded = false
                                    onImport()
                                }
                                PickerMenuItem(
                                    icon = Icons.Filled.ContentCopy,
                                    text = stringResource(R.string.menu_item_export_proxy_app),
                                ) {
                                    menuExpanded = false
                                    onExport()
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ModeCard(
                perAppEnabled = perAppEnabled,
                bypassApps = bypassApps,
                onSetPerAppEnabled = onSetPerAppEnabled,
                onSetBypassApps = onSetBypassApps,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickerFilterChip(
                    selected = filter == FILTER_ALL,
                    label = stringResource(R.string.per_app_filter_all),
                    onClick = { filter = FILTER_ALL },
                )
                PickerFilterChip(
                    selected = filter == FILTER_SELECTED,
                    label = stringResource(R.string.per_app_filter_selected) + " (${selected.size})",
                    onClick = { filter = FILTER_SELECTED },
                )
                PickerFilterChip(
                    selected = filter == FILTER_SYSTEM,
                    label = stringResource(R.string.per_app_filter_system),
                    onClick = { filter = FILTER_SYSTEM },
                )
                PickerFilterChip(
                    selected = filter == FILTER_RUSSIAN,
                    label = stringResource(R.string.per_app_filter_russian),
                    onClick = { filter = FILTER_RUSSIAN },
                )
            }

            Text(
                text = stringResource(R.string.per_app_selected_count, selected.size, apps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            when {
                loading && apps.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                }

                visibleApps.isEmpty() -> EmptyPickerState(searchQuery.isNotBlank())
                filter == FILTER_ALL -> UserAndSystemList(
                    userApps = visibleApps.filter { !it.isSystemApp },
                    systemApps = visibleApps.filter { it.isSystemApp },
                    selected = selected,
                    onToggleApp = onToggleApp,
                )

                else -> AppList(
                    apps = visibleApps,
                    selected = selected,
                    onToggleApp = onToggleApp,
                )
            }
        }
    }
}

@Composable
private fun PickerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        onClick = onClick,
    )
}

@Composable
private fun PickerFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ModeCard(
    perAppEnabled: Boolean,
    bypassApps: Boolean,
    onSetPerAppEnabled: (Boolean) -> Unit,
    onSetBypassApps: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            ToggleRow(
                title = stringResource(R.string.per_app_proxy_settings_enable),
                summary = null,
                checked = perAppEnabled,
                onCheckedChange = onSetPerAppEnabled,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            ToggleRow(
                title = stringResource(R.string.switch_bypass_apps_mode),
                summary = if (bypassApps) {
                    stringResource(R.string.per_app_bypass_summary)
                } else {
                    stringResource(R.string.per_app_general_summary)
                },
                checked = bypassApps,
                onCheckedChange = onSetBypassApps,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!summary.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserAndSystemList(
    userApps: List<AppInfo>,
    systemApps: List<AppInfo>,
    selected: Set<String>,
    onToggleApp: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (userApps.isNotEmpty()) {
            stickyHeader(key = "user") {
                PickerSectionHeader(stringResource(R.string.per_app_section_user))
            }
            items(userApps, key = { it.packageName }) { app ->
                PickerAppRow(
                    app = app,
                    checked = selected.contains(app.packageName),
                    onToggle = { onToggleApp(app.packageName) },
                )
            }
        }
        if (systemApps.isNotEmpty()) {
            stickyHeader(key = "system") {
                PickerSectionHeader(stringResource(R.string.per_app_section_system))
            }
            items(systemApps, key = { it.packageName }) { app ->
                PickerAppRow(
                    app = app,
                    checked = selected.contains(app.packageName),
                    onToggle = { onToggleApp(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<AppInfo>,
    selected: Set<String>,
    onToggleApp: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.packageName }) { app ->
            PickerAppRow(
                app = app,
                checked = selected.contains(app.packageName),
                onToggle = { onToggleApp(app.packageName) },
            )
        }
    }
}

@Composable
private fun PickerSectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun PickerAppRow(
    app: AppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val icon = remember(app.packageName) {
        runCatching { app.appIcon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = ListItemDefaults.colors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            },
        ),
        leadingContent = {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary,
                ),
            )
        },
    )
}

@Composable
private fun EmptyPickerState(searching: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (searching) R.string.per_app_empty_search else R.string.per_app_empty_list
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
