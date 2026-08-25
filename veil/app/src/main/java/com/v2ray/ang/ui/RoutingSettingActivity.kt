package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModelEvent
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.RoutingSettingsViewModel
import com.v2ray.ang.viewmodel.UserAssetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

private const val RULE_HEADER_ITEMS = 6
private const val SECTION_STAGGER_MS = 70L

class RoutingSettingActivity : HelperBaseComponentActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private val assetViewModel: UserAssetViewModel by viewModels()
    private val domainStrategyState = MutableStateFlow("")
    private val geoFilesSourceState = MutableStateFlow("")
    private val refreshTrigger = MutableStateFlow(0)
    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private var activePresetIndex by mutableStateOf<Int?>(null)
    private var activeBlockAds by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainStrategyState.value = getDomainStrategy()
        geoFilesSourceState.value = getGeoFilesSources()
        activePresetIndex = SettingsManager.getRoutingPreset()
        activeBlockAds = SettingsManager.getRoutingBlockAds()
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            viewModel.viewModelEvent.collect { event ->
                if (event is BaseViewModelEvent.FinishActivity) {
                    finish()
                }
            }
        }

        RoutingSettingScreen(
            viewModel = viewModel,
            assetViewModel = assetViewModel,
            domainStrategyState = domainStrategyState,
            geoFilesSourceState = geoFilesSourceState,
            refreshTrigger = refreshTrigger,
            extDir = extDir,
            geoFilesSourcesList = AppConfig.GEO_FILES_SOURCES.toList(),
            activePresetIndex = activePresetIndex,
            activeBlockAds = activeBlockAds,
            onBackClick = { finish() },
            onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
            onEditRule = { position ->
                startActivity(
                    Intent(this, RoutingEditActivity::class.java)
                        .putExtra("position", position)
                )
            },
            onDomainStrategySelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                domainStrategyState.value = value
            },
            onApplyPreset = { index, blockAds -> applyPreset(index, blockAds) },
            onToggleBlockAds = { blockAds ->
                applyPreset(SettingsManager.getRoutingPreset() ?: 0, blockAds)
            },
            onImportClipboard = { importFromClipboard() },
            onImportQRcode = { importQRcode() },
            onExportClipboard = { export2Clipboard() },
            onGeoSourceSelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
                geoFilesSourceState.value = value
                refreshAssetData()
            },
            onAddFileClick = { showFileChooser() },
            onAddUrlClick = { startActivity(Intent(this, UserAssetUrlActivity::class.java)) },
            onAddQrcodeClick = { importAssetFromQRcode() },
            onDownloadClick = { downloadGeoFiles() },
            onEditAsset = { guid ->
                startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra("assetId", guid))
            },
            onRemoveAsset = { guid ->
                val asset = assetViewModel.getAssets().find { it.guid == guid }
                if (asset != null) {
                    extDir.listFiles()?.find { it.name == asset.assetUrl.remarks }?.delete()
                    MmkvManager.removeAssetUrl(guid)
                    initAssets()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
        activePresetIndex = SettingsManager.getRoutingPreset()
        activeBlockAds = SettingsManager.getRoutingBlockAds()
        refreshAssetData()
    }

    private fun getDomainStrategy(): String {
        val strategies = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    private fun getGeoFilesSources(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    private fun applyPreset(index: Int, blockAds: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, index, blockAds)
                withContext(Dispatchers.Main) {
                    activePresetIndex = index
                    activeBlockAds = blockAds
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to apply routing preset", e)
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(this)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SettingsManager.resetRoutingRulesets(clipboard)
            withContext(Dispatchers.Main) {
                if (result) {
                    activePresetIndex = SettingsManager.getRoutingPreset()
                    activeBlockAds = SettingsManager.getRoutingBlockAds()
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(scanResult)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            activePresetIndex = SettingsManager.getRoutingPreset()
                            activeBlockAds = SettingsManager.getRoutingBlockAds()
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
        }
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(
                    getCursorName(uri) ?: uri.toString(),
                    "file"
                )

                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
                    toast(R.string.msg_remark_is_duplicate)
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                toastError(R.string.toast_asset_copy_failed)
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
                contentResolver.openInputStream(uri).use { inputStream ->
                    targetFile.outputStream().use { fileOut ->
                        inputStream?.copyTo(fileOut)
                    }
                }
                withContext(Dispatchers.Main) {
                    toastSuccess(R.string.toast_success)
                    refreshAssetData()
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to copy asset file", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_asset_copy_failed)
                }
            }
        }
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importAsset(scanResult)
            }
        }
        return true
    }

    private fun importAsset(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            startActivity(
                Intent(this, UserAssetUrlActivity::class.java)
                    .putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, url)
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import asset from URL", e)
            return false
        }
        return true
    }

    private fun downloadGeoFiles() {
        if (assetViewModel.downloadState.value.isRunning) return
        refreshAssetData()
        toast(R.string.msg_downloading_content)

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = assetViewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
                withContext(Dispatchers.Main) {
                    if (result.successCount > 0) {
                        toast(getString(R.string.title_update_config_count, result.successCount))
                    } else {
                        toast(getString(R.string.toast_failure))
                    }
                    refreshAssetData()
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to download geo files", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    refreshAssetData()
                }
            }
        }
    }

    private fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@RoutingSettingActivity, assets)
            withContext(Dispatchers.Main) {
                refreshAssetData()
            }
        }
    }

    private fun refreshAssetData() {
        assetViewModel.reload(getGeoFilesSources())
        refreshTrigger.value++
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    assetViewModel: UserAssetViewModel,
    domainStrategyState: MutableStateFlow<String>,
    geoFilesSourceState: MutableStateFlow<String>,
    refreshTrigger: MutableStateFlow<Int>,
    extDir: File,
    geoFilesSourcesList: List<String>,
    activePresetIndex: Int?,
    activeBlockAds: Boolean,
    onBackClick: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Int) -> Unit,
    onDomainStrategySelected: (String) -> Unit,
    onApplyPreset: (Int, Boolean) -> Unit,
    onToggleBlockAds: (Boolean) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit,
    onGeoSourceSelected: (String) -> Unit,
    onAddFileClick: () -> Unit,
    onAddUrlClick: () -> Unit,
    onAddQrcodeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onEditAsset: (String) -> Unit,
    onRemoveAsset: (String) -> Unit
) {
    val rulesets by viewModel.rulesetsFlow.collectAsStateWithLifecycle()
    val assets by assetViewModel.assetsFlow.collectAsStateWithLifecycle()
    val domainStrategy by domainStrategyState.collectAsStateWithLifecycle()
    val geoFilesSource by geoFilesSourceState.collectAsStateWithLifecycle()
    val downloadState by assetViewModel.downloadState.collectAsStateWithLifecycle()
    val trigger by refreshTrigger.collectAsStateWithLifecycle()

    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val presetNames = stringArrayResource(R.array.preset_rulesets).toList()
    val presetDescriptions = stringArrayResource(R.array.preset_rulesets_descriptions).toList()

    val presetIcons = listOf(
        Icons.Filled.Public,
        Icons.Filled.AltRoute,
        Icons.Filled.AltRoute,
        Icons.Filled.AltRoute,
    )
    val presetShapes = listOf(
        MaterialShapes.Circle.toShape(),
        MaterialShapes.Cookie6Sided.toShape(),
        MaterialShapes.Cookie9Sided.toShape(),
        MaterialShapes.Clover4Leaf.toShape(),
    )

    var showMenu by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var pendingPresetIndex by remember { mutableStateOf<Int?>(null) }
    var pendingAdblockValue by remember { mutableStateOf<Boolean?>(null) }
    var deleteTargetGuid by remember { mutableStateOf<String?>(null) }
    var showAddAssetMenu by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index - RULE_HEADER_ITEMS
        val toIndex = to.index - RULE_HEADER_ITEMS
        if (fromIndex in rulesets.indices && toIndex in rulesets.indices) {
            viewModel.swap(fromIndex, toIndex)
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_title),
                onBackClick = onBackClick,
                isLoading = downloadState.isRunning,
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_clipboard)) },
                                onClick = { showMenu = false; onImportClipboard() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_import_rulesets_from_qrcode)) },
                                onClick = { showMenu = false; onImportQRcode() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routing_settings_export_rulesets_to_clipboard)) },
                                onClick = { showMenu = false; onExportClipboard() }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(lazyListState)
        ) {
            item(key = "hero") {
                SectionEntrance(delayMs = 0L) {
                    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                    AnimatedContent(
                        targetState = (activePresetIndex ?: 0) to activeBlockAds,
                        transitionSpec = {
                            (fadeIn(fade) + slideInVertically(slide) { it / 6 }) togetherWith
                                (fadeOut(fade) + slideOutVertically(slide) { -it / 6 })
                        },
                        label = "routing hero"
                    ) { (index, blockAds) ->
                        val baseTitle = presetNames.getOrElse(index) { "" }
                        RoutingHero(
                            title = if (blockAds) {
                                stringResource(R.string.routing_settings_block_ads).let { "$baseTitle + $it" }
                            } else {
                                baseTitle
                            },
                            description = presetDescriptions.getOrNull(index).orEmpty(),
                            icon = presetIcons.getOrElse(index) { Icons.Filled.Public },
                            shape = presetShapes.getOrElse(index) { MaterialShapes.Circle.toShape() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            item(key = "presets_title") {
                SectionEntrance(delayMs = SECTION_STAGGER_MS) {
                    RoutingSectionLabel(stringResource(R.string.routing_settings_presets_title))
                }
            }
            item(key = "presets") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presetNames.indices.toList().chunked(2).forEachIndexed { row, rowIndices ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.height(IntrinsicSize.Min)
                        ) {
                            rowIndices.forEach { index ->
                                SectionEntrance(
                                    delayMs = SECTION_STAGGER_MS * (row * 2 + index + 1),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    PresetCard(
                                        title = presetNames[index],
                                        description = presetDescriptions.getOrNull(index).orEmpty(),
                                        icon = presetIcons.getOrElse(index) { Icons.Filled.Public },
                                        shape = presetShapes.getOrElse(index) { MaterialShapes.Circle.toShape() },
                                        selected = index == activePresetIndex,
                                        onClick = { pendingPresetIndex = index },
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                            if (rowIndices.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item(key = "block_ads") {
                SectionEntrance(delayMs = SECTION_STAGGER_MS) {
                    BlockAdsCard(
                        title = stringResource(R.string.routing_settings_block_ads),
                        description = stringResource(R.string.routing_settings_block_ads_desc),
                        checked = activeBlockAds,
                        onToggle = { newValue ->
                            if (activePresetIndex == null) {
                                pendingAdblockValue = newValue
                            } else {
                                onToggleBlockAds(newValue)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            item(key = "advanced_header") {
                SectionEntrance(delayMs = SECTION_STAGGER_MS * 2L) {
                    RoutingSectionHeader(
                        title = stringResource(R.string.routing_settings_advanced_title),
                        expanded = showAdvanced,
                        onToggle = { showAdvanced = !showAdvanced }
                    )
                }
            }

            if (showAdvanced) {
                item(key = "domain_strategy") {
                    SectionEntrance(delayMs = 0L) {
                        SettingsListItem(
                            title = stringResource(R.string.routing_settings_domain_strategy),
                            entries = domainStrategies,
                            values = domainStrategies,
                            selectedValue = domainStrategy,
                            onSelected = { onDomainStrategySelected(it) }
                        )
                    }
                }
                item(key = "rule_section_header") {
                    SectionEntrance(delayMs = SECTION_STAGGER_MS / 2L) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.routing_settings_rule_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onAddRule) {
                                Icon(
                                    painterResource(R.drawable.ic_add_24dp),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.routing_settings_add_rule))
                            }
                        }
                    }
                }

                if (rulesets.isEmpty()) {
                    item(key = "rules_empty") {
                        SectionEntrance(delayMs = SECTION_STAGGER_MS) {
                            EmptyRulesCard(onAddRule = onAddRule)
                        }
                    }
                } else {
                    itemsIndexed(
                        items = rulesets,
                        key = { _, ruleset -> ruleset.id }
                    ) { index, ruleset ->
                        SectionEntrance(
                            delayMs = (SECTION_STAGGER_MS + SECTION_STAGGER_MS / 2L * index)
                                .coerceAtMost(SECTION_STAGGER_MS * 3L)
                        ) {
                            ReorderableItem(reorderableState, key = ruleset.id) { isDragging ->
                                ReorderableListItem(
                                    scope = this,
                                    isDragging = isDragging
                                ) {
                                    RoutingRulesetItem(
                                        ruleset = ruleset,
                                        onEdit = { onEditRule(index) },
                                        onEnabledChange = { checked ->
                                            val updated = ruleset.copy(enabled = checked)
                                            viewModel.update(index, updated)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "assets_header") {
                    SectionEntrance(delayMs = SECTION_STAGGER_MS / 2L) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.title_user_asset_setting),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(onClick = { showAddAssetMenu = true }) {
                                    Icon(
                                        painterResource(R.drawable.ic_add_24dp),
                                        contentDescription = stringResource(R.string.menu_item_add_asset)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAddAssetMenu,
                                    onDismissRequest = { showAddAssetMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    offset = DpOffset(x = 0.dp, y = 0.dp),
                                    modifier = Modifier.wrapContentWidth(Alignment.End)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_item_add_file)) },
                                        onClick = { showAddAssetMenu = false; onAddFileClick() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_item_add_url)) },
                                        onClick = { showAddAssetMenu = false; onAddUrlClick() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_item_scan_qrcode)) },
                                        onClick = { showAddAssetMenu = false; onAddQrcodeClick() }
                                    )
                                }
                            }
                            IconButton(onClick = onDownloadClick, enabled = !downloadState.isRunning) {
                                Icon(
                                    painterResource(R.drawable.ic_cloud_download_24dp),
                                    contentDescription = stringResource(R.string.menu_item_download_file)
                                )
                            }
                        }
                    }
                }

                item(key = "download_progress") {
                    AnimatedVisibility(visible = downloadState.isRunning) {
                        DownloadProgressCard(
                            state = downloadState,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                item(key = "geo_source") {
                    SectionEntrance(delayMs = SECTION_STAGGER_MS) {
                        SettingsListItem(
                            title = stringResource(R.string.asset_geo_files_sources),
                            entries = geoFilesSourcesList,
                            values = geoFilesSourcesList,
                            selectedValue = geoFilesSource,
                            onSelected = { onGeoSourceSelected(it) }
                        )
                    }
                }

                itemsIndexed(
                    items = assets,
                    key = { _, item -> "${item.guid}_$trigger" }
                ) { index, item ->
                    SectionEntrance(
                        delayMs = (SECTION_STAGGER_MS + SECTION_STAGGER_MS / 4L * index)
                            .coerceAtMost(SECTION_STAGGER_MS * 2L)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            UserAssetItem(
                                item = item,
                                extDir = extDir,
                                onEdit = { onEditAsset(item.guid) },
                                onDeleteClick = { deleteTargetGuid = item.guid },
                                isDownloading = downloadState.isRunning &&
                                    downloadState.currentFile == item.assetUrl.remarks
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingPresetIndex != null) {
        val index = pendingPresetIndex!!
        ConfirmDialog(
            title = presetNames.getOrNull(index),
            message = stringResource(R.string.routing_settings_apply_preset_confirm),
            confirmText = stringResource(R.string.action_apply),
            onConfirm = { onApplyPreset(index, activeBlockAds) },
            onDismiss = { pendingPresetIndex = null }
        )
    }

    if (pendingAdblockValue != null) {
        val value = pendingAdblockValue!!
        ConfirmDialog(
            title = stringResource(R.string.routing_settings_block_ads),
            message = stringResource(R.string.routing_settings_apply_preset_confirm),
            confirmText = stringResource(R.string.action_apply),
            onConfirm = { onToggleBlockAds(value) },
            onDismiss = { pendingAdblockValue = null }
        )
    }

    if (deleteTargetGuid != null) {
        val guid = deleteTargetGuid!!
        val assetName = assets.find { it.guid == guid }?.assetUrl?.remarks ?: ""
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_asset_file, assetName),
            onConfirm = { onRemoveAsset(guid) },
            onDismiss = { deleteTargetGuid = null }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutingSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutingSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "section chevron rotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressivePressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetCard(
    title: String,
    description: String,
    icon: ImageVector,
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    val containerColor = if (selected) cs.primaryContainer else cs.surfaceContainerLow
    val contentColor = if (selected) cs.onPrimaryContainer else cs.onSurface
    val descColor = contentColor.copy(alpha = 0.72f)
    val chipColor = if (selected) cs.primary else cs.surfaceContainerHighest
    val chipContentColor = if (selected) cs.onPrimary else cs.onSurfaceVariant

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .expressivePressScale(interactionSource),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(shape)
                        .background(chipColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = chipContentColor
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
                        scaleIn(
                            MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
                            initialScale = 0.4f
                        ),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
                        scaleOut(
                            MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
                            targetScale = 0.4f
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = cs.onPrimary
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = descColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BlockAdsCard(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    val containerColor = if (checked) cs.secondaryContainer else cs.surfaceContainerLow
    val contentColor = if (checked) cs.onSecondaryContainer else cs.onSurface
    val chipColor = if (checked) cs.secondary else cs.surfaceContainerHighest
    val chipContentColor = if (checked) cs.onSecondary else cs.onSurfaceVariant

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle(!checked)
        },
        modifier = modifier
            .fillMaxWidth()
            .expressivePressScale(interactionSource),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(chipColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = chipContentColor
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadProgressCard(
    state: UserAssetViewModel.GeoDownloadState,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHigh,
        contentColor = cs.onSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(
                    modifier = Modifier.size(32.dp),
                    color = cs.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.msg_downloading_content),
                        style = MaterialTheme.typography.labelLarge
                    )
                    state.currentFile?.let { fileName ->
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "${state.completed}/${state.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant
                )
            }
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.completed.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = cs.primary,
                    trackColor = cs.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

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
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = if (isPressed) pressSpec else releaseSpec,
        )
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionEntrance(
    delayMs: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        launch { alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
        launch { offset.animateTo(0f, motionScheme.defaultSpatialSpec()) }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offset.value.dp.toPx()
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutingHero(
    title: String,
    description: String,
    icon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.primaryContainer,
        contentColor = cs.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(shape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = cs.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.routing_settings_active_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = cs.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyRulesCard(onAddRule: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Route,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.routing_settings_empty_rules),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onAddRule) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.routing_settings_add_rule))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingRulesetItem(
    ruleset: RulesetItem,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ruleset.remarks ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ruleset.locked == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_lock_24dp),
                            contentDescription = "Locked",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val domainIpInfo = (ruleset.domain ?: ruleset.ip ?: ruleset.process ?: ruleset.port)?.toString() ?: ""
                if (domainIpInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = domainIpInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!ruleset.outboundTag.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutboundBadge(tag = ruleset.outboundTag)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = ruleset.enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
private fun OutboundBadge(tag: String) {
    val (container, content) = outboundPalette(tag)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun outboundPalette(tag: String): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (tag) {
        AppConfig.TAG_PROXY -> primaryContainer to onPrimaryContainer
        AppConfig.TAG_DIRECT -> secondaryContainer to onSecondaryContainer
        AppConfig.TAG_BLOCKED -> errorContainer to onErrorContainer
        else -> tertiaryContainer to onTertiaryContainer
    }
}
