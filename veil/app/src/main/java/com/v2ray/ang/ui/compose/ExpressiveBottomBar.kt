package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.v2ray.ang.R

data class ExpressiveBottomBarState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val status: String = "",
    val serverName: String? = null,
    val connectionIp: String? = null,
    val uploadSpeed: String = "0 B/s",
    val downloadSpeed: String = "0 B/s",
    val uptime: String = "00:00",
    val showSpeed: Boolean = false,
    val showTun: Boolean = false,
    val tunEnabled: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveBottomBar(
    state: ExpressiveBottomBarState,
    onStartStop: () -> Unit,
    onModeSelector: () -> Unit,
    onTunToggle: () -> Unit,
    onTest: () -> Unit,
    onConnectionInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startInteraction = remember { MutableInteractionSource() }
    val tunInteraction = remember { MutableInteractionSource() }
    val statusInteraction = remember { MutableInteractionSource() }
    val startPressed by startInteraction.collectIsPressedAsState()
    val tunPressed by tunInteraction.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val floatSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntSize>()
    val paddingSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.Dp>()
    val startScale = remember { Animatable(1f) }
    val tunScale = remember { Animatable(1f) }
    val density = LocalDensity.current
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    val targetEndPadding = with(density) {
        if (toolbarHeightPx == 0) 16.dp
        else ((toolbarHeightPx.toDp() - 52.dp) / 2).coerceAtLeast(12.dp)
    }
    val endPadding by animateDpAsState(targetEndPadding, paddingSpec, label = "toolbar end padding")
    val status = state.status.ifEmpty { stringResource(R.string.connection_not_connected) }

    LaunchedEffect(startPressed) {
        if (startPressed) {
            startScale.animateTo(0.92f, floatSpec)
        } else {
            startScale.animateTo(1f, floatSpec)
        }
    }

    LaunchedEffect(tunPressed) {
        if (tunPressed) {
            tunScale.animateTo(0.92f, floatSpec)
        } else {
            tunScale.animateTo(1f, floatSpec)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
    ) {
        // A 360dp phone leaves ~320dp for the card; the fixed TUN + start
        // cluster then squeezes the status text and clips the toggle. Switch
        // to compact metrics on narrow screens and at large system font sizes.
        val compact = maxWidth < 380.dp || LocalDensity.current.fontScale > 1.2f
        val outerPadding = if (compact) 12.dp else 20.dp
        val contentStart = if (compact) 16.dp else 22.dp
        val startWidth by animateDpAsState(
            targetValue = if (startPressed) {
                if (compact) 70.dp else 88.dp
            } else {
                if (compact) 64.dp else 80.dp
            },
            animationSpec = spatialSpec,
            label = "start width",
        )
        val tunWidth by animateDpAsState(
            targetValue = if (tunPressed) {
                if (compact) 44.dp else 48.dp
            } else {
                if (compact) 40.dp else 44.dp
            },
            animationSpec = spatialSpec,
            label = "tun width",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = outerPadding, end = outerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = sizeSpec)
                .onSizeChanged { toolbarHeightPx = it.height },
            shape = RoundedCornerShape(percent = 50),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = contentStart, top = 16.dp, end = endPadding, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            interactionSource = statusInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onTest()
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConnectionInfo()
                            },
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = status,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compact) {
                            state.connectionIp?.takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    text = it,
                                    modifier = Modifier
                                        .padding(start = 14.dp)
                                        .widthIn(max = 96.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    AnimatedVisibility(visible = !state.serverName.isNullOrEmpty()) {
                        Text(
                            text = state.serverName.orEmpty(),
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AnimatedVisibility(visible = state.showSpeed && state.isRunning && !state.isLoading) {
                        Row(modifier = Modifier.padding(top = 2.dp)) {
                            Text("↓ ${state.downloadSpeed}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("↑ ${state.uploadSpeed}", modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            if (!compact) {
                                Text(state.uptime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (state.showTun && !state.isLoading) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(width = tunWidth, height = 52.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (state.tunEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer
                            )
                            .graphicsLayer {
                                scaleX = tunScale.value
                                scaleY = tunScale.value
                            }
                            .combinedClickable(
                                interactionSource = tunInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(
                                        if (state.tunEnabled) HapticFeedbackType.ToggleOff
                                        else HapticFeedbackType.ToggleOn
                                    )
                                    onTunToggle()
                                },
                                onClickLabel = stringResource(if (state.tunEnabled) R.string.title_tun_enabled else R.string.title_tun_disabled),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (state.tunEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(width = startWidth, height = 52.dp)
                            .clip(CircleShape)
                            .background(if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                            .graphicsLayer {
                                scaleX = startScale.value
                                scaleY = startScale.value
                            }
                            .combinedClickable(
                                interactionSource = startInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    onStartStop()
                                },
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onModeSelector()
                                },
                                onClickLabel = stringResource(if (state.isRunning) R.string.action_stop_service else R.string.tasker_start_service),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = if (state.isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(width = startWidth, height = 52.dp)
                            .clip(CircleShape)
                            .background(if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                            .graphicsLayer {
                                scaleX = startScale.value
                                scaleY = startScale.value
                            }
                            .combinedClickable(
                                interactionSource = startInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    onStartStop()
                                },
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onModeSelector()
                                },
                                onClickLabel = stringResource(if (state.isRunning) R.string.action_stop_service else R.string.tasker_start_service),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isLoading) {
                            LoadingIndicator(
                                modifier = Modifier.size(34.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = if (state.isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
}
