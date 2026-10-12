package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * Marks a composable as the first visible row of a settings section so the leading
 * item divider can be suppressed.
 */
private val LocalIsFirstRow = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * Rounded card container that groups related settings rows.
 * Rows are drawn edge-to-edge inside the card and separated by M3 expressive dividers.
 */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalIsFirstRow provides true) {
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

/**
 * Expressive separator between two option rows inside a settings card.
 * Uses the M3 divider tokens (outlineVariant, 1dp) and insets the line so it
 * starts where the row content starts.
 */
@Composable
fun SettingsItemDivider(
    startIndent: androidx.compose.ui.unit.Dp = 16.dp,
    endIndent: androidx.compose.ui.unit.Dp = 16.dp
) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = endIndent),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsItemRow(
    icon: Painter?,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    drawCard: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val firstRow = LocalIsFirstRow.current
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val descriptionColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (drawCard) {
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!firstRow) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    thickness = 1.dp,
                    color = dividerColor
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(enabled = enabled, onClick = onClick)
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = titleColor
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor
                    )
                    if (!description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = descriptionColor
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CompositionLocalProvider(LocalContentColor provides titleColor) {
                        trailing()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsEditItem(
    icon: Painter? = null,
    title: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false,
    emptyDescription: String? = null,
    singleLine: Boolean = true,
    drawCard: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = when {
        isPassword -> if (value.isEmpty()) null else "******"
        value.isEmpty() -> emptyDescription
        else -> value
    }

    SettingsItemRow(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier,
        drawCard = drawCard
    )

    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        InputDialog(
            title = title,
            fields = listOf(
                InputField(
                    label = title,
                    value = text,
                    singleLine = singleLine,
                    visualTransformation = if (isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardType = if (keyboardNumber) KeyboardType.Number else KeyboardType.Text
                )
            ),
            onFieldChange = { _, v -> text = v },
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { showDialog = false; onValueChanged(text) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SettingsListItem(
    icon: Painter? = null,
    title: String,
    entries: List<String>,
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    drawCard: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedIndex = values.indexOf(selectedValue).let { if (it >= 0) it else 0 }
    val summary = entries.getOrNull(selectedIndex).orEmpty()

    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary.ifEmpty { null },
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier,
        drawCard = drawCard
    )

    if (showDialog) {
        SelectListDialog(
            title = title,
            options = entries,
            selectedOption = summary,
            onSelected = { index, _ ->
                showDialog = false
                values.getOrNull(index)?.let(onSelected)
            },
            onDismiss = { showDialog = false },
            showRadio = true
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: Painter? = null,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    drawCard: Boolean = false
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = subtitle,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        drawCard = drawCard
    )
}

@Composable
fun SettingsSwitchItem(
    icon: Painter? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    drawCard: Boolean = false
) {
    SettingsItemRow(
        icon = icon,
        title = title,
        description = summary,
        enabled = enabled,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        modifier = modifier,
        drawCard = drawCard,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                ),
                enabled = enabled
            )
        }
    )
}
