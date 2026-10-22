package com.v2ray.ang.ui.compose.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

@Composable
fun modeLabel(value: String): String {
    val values = stringArrayResource(R.array.mode_value)
    val entries = stringArrayResource(R.array.mode_entries)
    val index = values.indexOf(value)
    return entries.getOrElse(index) { value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = stringArrayResource(R.array.mode_value)
    val entries = stringArrayResource(R.array.mode_entries)
    val descriptions = listOf(
        stringResource(R.string.main_mode_vpn_desc),
        stringResource(R.string.main_mode_proxy_desc),
        stringResource(R.string.main_mode_hybrid_desc),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.title_mode),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.main_mode_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            values.forEachIndexed { index, value ->
                ModeRow(
                    mode = value,
                    title = entries.getOrElse(index) { value },
                    description = descriptions.getOrElse(index) { "" },
                    selected = value == currentMode,
                    onClick = {
                        onSelect(value)
                        onDismiss()
                    },
                )
            }
        }
    }
}

internal data class ModeAccent(
    val container: Color,
    val content: Color,
    val solid: Color,
    val onSolid: Color,
)

@Composable
internal fun modeAccent(mode: String): ModeAccent = when (mode) {
    AppConfig.VPN -> ModeAccent(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        solid = MaterialTheme.colorScheme.primary,
        onSolid = MaterialTheme.colorScheme.onPrimary,
    )

    AppConfig.PROXY_TUN -> ModeAccent(
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        solid = MaterialTheme.colorScheme.tertiary,
        onSolid = MaterialTheme.colorScheme.onTertiary,
    )

    else -> ModeAccent(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        solid = MaterialTheme.colorScheme.secondary,
        onSolid = MaterialTheme.colorScheme.onSecondary,
    )
}

@Composable
internal fun ModeIcon(
    mode: String,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    when (mode) {
        AppConfig.VPN -> Icon(
            imageVector = Icons.Outlined.VpnKey,
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )

        AppConfig.PROXY_TUN -> Box(modifier = modifier) {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = tint,
            )
            Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = tint,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 1.dp),
            )
        }

        else -> Icon(
            imageVector = Icons.Outlined.Public,
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
    }
}

@Composable
private fun ModeRow(
    mode: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val accent = modeAccent(mode)
    val container by animateColorAsState(
        targetValue = if (selected) {
            accent.container
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "modeRowContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) accent.content else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "modeRowContent",
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (selected) {
                            accent.solid
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ModeIcon(
                    mode = mode,
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) accent.onSolid else accent.solid,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) content.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accent.solid,
                )
            } else {
                RadioButton(selected = false, onClick = null)
            }
        }
    }
}
