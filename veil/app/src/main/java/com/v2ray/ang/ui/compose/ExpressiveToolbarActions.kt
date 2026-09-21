package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.v2ray.ang.R

private const val BUTTON_SIZE_SCALE = 0.90f

@Composable
fun ExpressiveToolbarActions(
    onFilterClick: () -> Unit,
    onImportMenuAction: (Int) -> Unit,
    onOverflowAction: (Int) -> Unit,
    showSearchButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val addIcon = Icons.Filled.Add
    val filterIcon = Icons.Default.Search
    val moreIcon = Icons.Default.MoreVert
    val addLabel = stringResource(R.string.menu_item_add_config)
    val searchLabel = stringResource(R.string.menu_item_search)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonGroup(
                overflowIndicator = {},
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolbarIconItem(
                    icon = addIcon,
                    contentDescription = addLabel,
                    onClick = { addMenuExpanded = true },
                    isFilled = true,
                    widthMultiplier = 2f,
                )

                if (showSearchButton) {
                    toolbarIconItem(
                        icon = filterIcon,
                        contentDescription = searchLabel,
                        onClick = onFilterClick,
                        isFilled = false,
                    )
                }
            }

            Box {
                ToolbarIcon(
                    icon = moreIcon,
                    contentDescription = "More options",
                    isFilled = false,
                    onClick = { overflowExpanded = true },
                )
                OverflowMenu(
                    expanded = overflowExpanded,
                    onDismiss = { overflowExpanded = false },
                    onAction = onOverflowAction,
                )
            }
        }

        AddConfigSheet(
            expanded = addMenuExpanded,
            onDismissRequest = { addMenuExpanded = false },
            onAction = {
                addMenuExpanded = false
                onImportMenuAction(it)
            },
        )
    }
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
) {
    val items = listOf(
        R.id.search_view to (R.string.menu_item_search to Icons.Filled.Search),
        R.id.service_restart to (R.string.title_service_restart to Icons.Filled.RestartAlt),
        R.id.sub_update to (R.string.title_sub_update to Icons.Filled.CloudDownload),
        R.id.real_ping_all to (R.string.title_real_ping_all_server to Icons.Filled.NetworkPing),
        R.id.sort_by_test_results to (R.string.title_sort_by_test_results to Icons.AutoMirrored.Filled.Sort),
        R.id.locate_selected_config to (R.string.title_locate_selected_config to Icons.Filled.MyLocation),
        R.id.export_all to (R.string.title_export_all to Icons.Filled.IosShare),
        R.id.del_all_config to (R.string.title_del_all_config to Icons.Filled.DeleteForever),
        R.id.del_duplicate_config to (R.string.title_del_duplicate_config to Icons.Filled.Delete),
        R.id.del_invalid_config to (R.string.title_del_invalid_config to Icons.Filled.Warning),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        items.forEach { (actionId, labelIcon) ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(spring(stiffness = 400f, dampingRatio = 0.7f), 0.4f) +
                    scaleIn(spring(stiffness = 400f, dampingRatio = 0.7f), 0.92f),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(labelIcon.first)) },
                    trailingIcon = {
                        Icon(labelIcon.second, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        onDismiss()
                        onAction(actionId)
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AddConfigSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAction: (Int) -> Unit,
) {
    if (!expanded) return

    val imports = listOf(
        AddConfigAction(R.id.import_qrcode, R.string.add_config_qr, Icons.Filled.QrCodeScanner),
        AddConfigAction(R.id.import_clipboard, R.string.add_config_clipboard, Icons.Filled.ContentPaste),
        AddConfigAction(R.id.import_local, R.string.add_config_file, Icons.Filled.FolderOpen),
    )
    val featuredBuilders = listOf(
        AddConfigAction(R.id.import_manually_policy_group, R.string.add_config_policy_group, Icons.Filled.AccountTree) to R.string.add_config_policy_group_caption,
        AddConfigAction(R.id.import_manually_proxy_chain, R.string.add_config_proxy_chain, Icons.Filled.Hub) to R.string.add_config_proxy_chain_caption,
    )
    val servers = listOf(
        AddConfigAction(R.id.import_manually_vless, R.string.add_config_vless, Icons.Filled.EnhancedEncryption, MaterialShapes.Cookie9Sided.toShape()),
        AddConfigAction(R.id.import_manually_trojan, R.string.add_config_trojan, Icons.Filled.Cloud, MaterialShapes.Gem.toShape()),
        AddConfigAction(R.id.import_manually_vmess, R.string.add_config_vmess, Icons.Filled.Bolt, MaterialShapes.Sunny.toShape()),
        AddConfigAction(R.id.import_manually_hysteria2, R.string.add_config_hysteria2, Icons.Filled.Speed, MaterialShapes.SoftBurst.toShape()),
        AddConfigAction(R.id.import_manually_ss, R.string.add_config_shadowsocks, Icons.Filled.Security, MaterialShapes.Ghostish.toShape()),
        AddConfigAction(R.id.import_manually_wireguard, R.string.add_config_wireguard, Icons.Filled.VpnKey, MaterialShapes.Cookie6Sided.toShape()),
        AddConfigAction(R.id.import_manually_socks, R.string.add_config_socks, Icons.Filled.Password, MaterialShapes.Oval.toShape()),
        AddConfigAction(R.id.import_manually_http, R.string.add_config_http, Icons.Filled.Language, MaterialShapes.Circle.toShape()),
        AddConfigAction(R.id.import_manually_olcrtc, R.string.add_config_olcrtc, Icons.Filled.Videocam, MaterialShapes.Clover4Leaf.toShape()),
        AddConfigAction(R.id.import_manually_openflux, R.string.add_config_openflux, Icons.Filled.Cloud, MaterialShapes.Ghostish.toShape()),
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_item_add_config),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.add_config_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.add_config_import_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                imports.forEach { action ->
                    ImportTile(
                        action = action,
                        onClick = { onAction(action.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.add_config_create_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                featuredBuilders.forEachIndexed { index, (action, caption) ->
                    FeatureTile(
                        action = action,
                        caption = stringResource(caption),
                        onClick = { onAction(action.id) },
                        modifier = Modifier.weight(1f),
                        isPrimary = index == 0,
                    )
                }
            }
            ConfigListSection(titleRes = R.string.add_config_protocol_section) {
                servers.forEachIndexed { index, action ->
                    ConfigListRow(
                        action = action,
                        onClick = { onAction(action.id) },
                        colorIndex = index,
                        showDivider = index != servers.lastIndex,
                    )
                }
            }
        }
    }
}

private data class AddConfigAction(
    val id: Int,
    val label: Int,
    val icon: ImageVector,
    val shape: Shape? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val releaseSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(isPressed) {
        scale.animateTo(
            targetValue = if (isPressed) 0.94f else 1f,
            animationSpec = if (isPressed) pressSpec else releaseSpec,
        )
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
private fun ImportTile(
    action: AddConfigAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 88.dp)
            .expressivePressScale(interactionSource),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeatureTile(
    action: AddConfigAction,
    caption: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val chipColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val chipContentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onTertiary
    }
    val chipShape = if (isPrimary) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Clover4Leaf.toShape()
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 118.dp)
            .expressivePressScale(interactionSource),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(chipShape)
                    .background(chipColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = chipContentColor,
                )
            }
            Text(stringResource(action.label), style = MaterialTheme.typography.titleSmall)
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun ConfigListSection(
    titleRes: Int,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConfigListRow(
    action: AddConfigAction,
    onClick: () -> Unit,
    colorIndex: Int,
    showDivider: Boolean,
    caption: String? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val containerColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val contentColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val index = ((colorIndex % 3) + 3) % 3

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(action.shape ?: MaterialShapes.Circle.toShape())
                    .background(containerColors[index]),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColors[index],
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(action.label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                caption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private fun ButtonGroupScope.toolbarIconItem(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isFilled: Boolean,
    widthMultiplier: Float = 1f,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressProgress = remember { Animatable(0f) }
            val hapticFeedback = LocalHapticFeedback.current
            val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            val bounceSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

            LaunchedEffect(isPressed) {
                if (isPressed) {
                    pressProgress.animateTo(1f, pressSpec)
                } else {
                    pressProgress.animateTo(0f, bounceSpec)
                }
            }

            val shape = RoundedCornerShape(percent = 50)
            val containerColor =
                if (isFilled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            val contentColor =
                if (isFilled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer

            val iconScale = lerp(1f, 0.85f, pressProgress.value)
            val width = ((42f + 10f * pressProgress.value) * widthMultiplier * BUTTON_SIZE_SCALE).dp

            Box(
                modifier = Modifier
                    .requiredWidth(width)
                    .requiredHeight(52.dp * BUTTON_SIZE_SCALE)
                    .clip(shape)
                    .background(containerColor, shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        },
                    )
                    .padding(horizontal = 10.dp * BUTTON_SIZE_SCALE),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .size(24.dp * BUTTON_SIZE_SCALE)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )
                }
            }
        },
        menuContent = {},
    )
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    isFilled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val bounceSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            pressProgress.animateTo(1f, pressSpec)
        } else {
            pressProgress.animateTo(0f, bounceSpec)
        }
    }

    val shape = RoundedCornerShape(percent = 50)
    val containerColor = if (isFilled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isFilled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSecondaryContainer

    val iconScale = lerp(1f, 0.85f, pressProgress.value)
    val width = ((42f + 10f * pressProgress.value) * BUTTON_SIZE_SCALE).dp

    Box(
        modifier = modifier
            .requiredWidth(width)
            .requiredHeight(52.dp * BUTTON_SIZE_SCALE)
            .clip(shape)
            .background(containerColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(horizontal = 10.dp * BUTTON_SIZE_SCALE),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp * BUTTON_SIZE_SCALE)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
    }
}
