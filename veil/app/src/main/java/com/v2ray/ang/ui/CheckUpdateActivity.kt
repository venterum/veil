package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModelEvent
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.colorConnected
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.CheckStatus
import com.v2ray.ang.viewmodel.CheckUpdateViewModel
import kotlinx.coroutines.launch

class CheckUpdateActivity : BaseComponentActivity() {

    private val viewModel: CheckUpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    if (event is BaseViewModelEvent.FinishActivity) {
                        finish()
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        CheckUpdateScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
fun CheckUpdateScreen(
    viewModel: CheckUpdateViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val checkPreRelease by viewModel.checkPreRelease.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()

    val versionText = "v${BuildConfig.VERSION_NAME}"
    val coreVersion = remember { CoreNativeManager.getLibVersion() }
    val versionDetails = "Veil $versionText • $coreVersion"

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.update_check_for_update),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VersionHeader(
                versionText = versionText,
                coreVersion = coreVersion,
                versionDetails = versionDetails
            )

            Spacer(modifier = Modifier.height(20.dp))

            UpdateStatusCard(
                status = status,
                result = updateResult,
                onUpdate = {
                    updateResult?.downloadUrl?.let { Utils.openUri(context, it) }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OptionsCard(
                checkPreRelease = checkPreRelease,
                onCheckPreReleaseChange = viewModel::toggleCheckPreRelease,
                autoCheckUpdate = autoCheckUpdate,
                onAutoCheckUpdateChange = viewModel::toggleAutoCheckUpdate
            )

            Spacer(modifier = Modifier.height(20.dp))

            CheckUpdateButton(
                isLoading = isLoading,
                onClick = viewModel::checkForUpdates
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionHeader(
    versionText: String,
    coreVersion: String,
    versionDetails: String
) {
    val context = LocalContext.current
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, motionScheme.slowSpatialSpec()) }
        launch { alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.about_copied_to_clipboard)
            ) {
                Utils.setClipboard(context, versionDetails)
                context.toast(R.string.about_copied_to_clipboard)
            }
    ) {
        Text(
            text = stringResource(R.string.update_current_version).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = versionText,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = coreVersion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateStatusCard(
    status: CheckStatus,
    result: CheckUpdateResult?,
    onUpdate: () -> Unit
) {
    val sizeSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntSize>()
    val containerColor = if (status == CheckStatus.UPDATE_AVAILABLE) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (status == CheckStatus.UPDATE_AVAILABLE) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(sizeSpec),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Crossfade(
                targetState = status,
                animationSpec = spring(stiffness = 400f, dampingRatio = 0.7f),
                label = "statusContent"
            ) { s ->
                when (s) {
                    CheckStatus.IDLE -> StatusContent(
                        icon = painterResource(R.drawable.ic_check_update_24dp),
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.update_check_for_update),
                        message = stringResource(R.string.update_checking_for_update),
                        contentColor = contentColor
                    )

                    CheckStatus.CHECKING -> StatusLoading(
                        title = stringResource(R.string.update_checking_for_update),
                        contentColor = contentColor
                    )

                    CheckStatus.UP_TO_DATE -> StatusContent(
                        icon = painterResource(R.drawable.ic_fab_check),
                        iconTint = colorConnected,
                        title = stringResource(R.string.update_already_latest_version),
                        message = stringResource(R.string.update_current_version),
                        contentColor = contentColor
                    )

                    CheckStatus.UPDATE_AVAILABLE -> StatusContent(
                        icon = painterResource(R.drawable.ic_cloud_download_24dp),
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.update_new_version_found, result?.latestVersion ?: ""),
                        message = if (result?.isPreRelease == true) {
                            stringResource(R.string.update_check_pre_release)
                        } else {
                            stringResource(R.string.update_now)
                        },
                        contentColor = contentColor
                    )

                    CheckStatus.ERROR -> StatusContent(
                        icon = painterResource(R.drawable.ic_reload_24dp),
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.update_check_failed),
                        message = stringResource(R.string.update_check_for_update),
                        contentColor = contentColor
                    )
                }
            }

            if (status == CheckStatus.UPDATE_AVAILABLE) {
                val notes = result?.releaseNotes
                if (!notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.update_release_notes),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            // heightIn must come before verticalScroll: the screen's outer
                            // scroll hands infinite max height to its children, and a scroll
                            // modifier measured with infinite constraints throws.
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_download_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.update_now))
                }
            }
        }
    }
}

@Composable
private fun StatusContent(
    icon: Painter,
    iconTint: Color,
    title: String,
    message: String,
    contentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.largeIncreased)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusLoading(
    title: String,
    contentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor
        )
    }
}

@Composable
private fun OptionsCard(
    checkPreRelease: Boolean,
    onCheckPreReleaseChange: (Boolean) -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column {
            ToggleRow(
                icon = painterResource(R.drawable.ic_source_code_24dp),
                title = stringResource(R.string.update_check_pre_release),
                summary = null,
                checked = checkPreRelease,
                onCheckedChange = onCheckPreReleaseChange
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            ToggleRow(
                icon = painterResource(R.drawable.ic_reload_24dp),
                title = stringResource(R.string.update_auto_check),
                summary = stringResource(R.string.update_auto_check_summary),
                checked = autoCheckUpdate,
                onCheckedChange = onAutoCheckUpdateChange
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: Painter,
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.largeIncreased)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckUpdateButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val pressSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val releaseSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(isPressed) {
        scale.animateTo(
            targetValue = if (isPressed) 0.94f else 1f,
            animationSpec = if (isPressed) pressSpec else releaseSpec
        )
    }

    Button(
        onClick = onClick,
        enabled = !isLoading,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (isLoading) {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.update_checking_for_update))
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_check_update_24dp),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.update_check_for_update),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
