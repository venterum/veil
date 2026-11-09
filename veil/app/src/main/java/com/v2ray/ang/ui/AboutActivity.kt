@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.v2ray.ang.ui

import android.content.Intent
import android.webkit.WebView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.shapes.Morph
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.VeilMorphBadge
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ENTRANCE_STAGGER_MS = 70L
private const val HERO_SIZE_DP = 148
private const val HERO_LOGO_DP = 112
private const val HERO_PRESS_SCALE = 0.88f
private const val HERO_ROTATION_STEP_DEG = 45f

class AboutActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        AboutScreen(onBackClick = { finish() })
    }
}

@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var showOssDialog by remember { mutableStateOf(false) }

    val versionText = "v${BuildConfig.VERSION_NAME}"
    val coreVersion = remember { CoreNativeManager.getLibVersion() }
    val appId = BuildConfig.APPLICATION_ID
    val versionDetails = "Veil $versionText • $coreVersion • $appId"

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_about),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AboutHero()

            VersionChip(
                versionText = versionText,
                versionDetails = versionDetails
            )

            val connectItems = listOf(
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_source_code_24dp),
                    title = stringResource(R.string.title_source_code),
                    subtitle = AppConfig.APP_URL.removePrefix("https://"),
                    external = true,
                    onClick = { Utils.openUri(context, AppConfig.APP_URL) }
                ),
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_v2rayng_logo),
                    title = stringResource(R.string.title_original_repo),
                    subtitle = AppConfig.APP_ORIGINAL_URL.removePrefix("https://"),
                    external = true,
                    iconSize = 18.dp,
                    onClick = { Utils.openUri(context, AppConfig.APP_ORIGINAL_URL) }
                ),
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_telegram_24dp),
                    title = stringResource(R.string.title_tg_channel),
                    subtitle = AppConfig.TG_CHANNEL_URL.removePrefix("https://"),
                    external = true,
                    onClick = { Utils.openUri(context, AppConfig.TG_CHANNEL_URL) }
                ),
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_feedback_24dp),
                    title = stringResource(R.string.title_pref_feedback),
                    subtitle = stringResource(R.string.summary_pref_feedback),
                    external = true,
                    onClick = { Utils.openUri(context, AppConfig.APP_ISSUES_URL) }
                ),
            )
            ExpressiveEntrance(index = 1) {
                AboutTileSection(title = stringResource(R.string.about_section_connect)) {
                    AboutTileGrid(items = connectItems)
                }
            }

            val updateItems = listOf(
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_check_update_24dp),
                    title = stringResource(R.string.update_check_for_update),
                    subtitle = null,
                    external = false,
                    onClick = {
                        context.startActivity(Intent(context, CheckUpdateActivity::class.java))
                    }
                ),
            )
            ExpressiveEntrance(index = 2) {
                AboutTileSection(title = stringResource(R.string.about_section_update)) {
                    updateItems.forEach { item ->
                        AboutWideTile(item = item)
                    }
                }
            }

            val legalItems = listOf(
                AboutLinkItem(
                    icon = painterResource(R.drawable.license_24px),
                    title = stringResource(R.string.title_oss_license),
                    subtitle = null,
                    external = false,
                    onClick = { showOssDialog = true }
                ),
                AboutLinkItem(
                    icon = painterResource(R.drawable.ic_privacy_24dp),
                    title = stringResource(R.string.title_privacy_policy),
                    subtitle = null,
                    external = true,
                    onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }
                ),
            )
            ExpressiveEntrance(index = 3) {
                AboutTileSection(title = stringResource(R.string.about_section_legal)) {
                    AboutTileGrid(items = legalItems)
                }
            }

            ExpressiveEntrance(index = 4) {
                AboutFooter(coreVersion = coreVersion, appId = appId)
            }
        }
    }

    if (showOssDialog) {
        AlertDialog(
            onDismissRequest = { showOssDialog = false },
            title = { Text(stringResource(R.string.title_oss_license)) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/open_source_licenses.html")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showOssDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}

@Composable
private fun AboutHero() {
    val motionScheme = MaterialTheme.motionScheme

    val entranceScale = remember { Animatable(0.4f) }
    val entranceAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { entranceScale.animateTo(1f, motionScheme.slowSpatialSpec()) }
        launch { entranceAlpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
                alpha = entranceAlpha.value
            }
    ) {
        VeilMorphBadge(
            size = HERO_SIZE_DP.dp,
            logoSize = HERO_LOGO_DP.dp,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.summary_app_fork),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun VersionChip(
    versionText: String,
    versionDetails: String
) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraExtraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(
                    onClick = {
                        Utils.setClipboard(context, versionDetails)
                        context.toast(R.string.about_copied_to_clipboard)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private data class AboutLinkItem(
    val icon: Painter,
    val title: String,
    val subtitle: String?,
    val external: Boolean,
    val onClick: () -> Unit,
    val iconSize: Dp = 24.dp
)

private val iconContainerColors: List<Pair<Color, Color>>
    @Composable
    get() = with(MaterialTheme.colorScheme) {
        listOf(
            primaryContainer to onPrimaryContainer,
            secondaryContainer to onSecondaryContainer,
            tertiaryContainer to onTertiaryContainer,
        )
    }

@Composable
private fun AboutSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun AboutTileSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AboutSectionHeader(title = title)
        content()
    }
}

@Composable
private fun AboutTileGrid(items: List<AboutLinkItem>, modifier: Modifier = Modifier) {
    val containerColors = iconContainerColors
    val rows = items.chunked(2)
    val tileShape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { columnIndex, item ->
                    val index = rowIndex * 2 + columnIndex
                    val (containerColor, contentColor) = containerColors[index % containerColors.size]
                    AboutTile(
                        item = item,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        shape = tileShape,
                        modifier = if (row.size == 1) {
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutTile(
    item: AboutLinkItem,
    containerColor: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = item.onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.heightIn(min = 118.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(containerColor, MaterialTheme.shapes.largeIncreased),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(item.iconSize)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.external) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_in_browser_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutWideTile(item: AboutLinkItem, modifier: Modifier = Modifier) {
    val containerColors = iconContainerColors
    val (containerColor, contentColor) = containerColors.first()
    Surface(
        onClick = item.onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(containerColor, MaterialTheme.shapes.largeIncreased),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = item.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(item.iconSize)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.external) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_in_browser_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutFooter(
    coreVersion: String,
    appId: String
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Text(
            text = coreVersion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = appId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    Utils.setClipboard(context, appId)
                    context.toast(R.string.about_copied_to_clipboard)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ExpressiveEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ENTRANCE_STAGGER_MS * index)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "entranceAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 32f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "entranceOffsetY"
    )
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY.dp.toPx()
        }
    ) {
        content()
    }
}

private class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
