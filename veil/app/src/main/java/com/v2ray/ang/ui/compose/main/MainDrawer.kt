package com.v2ray.ang.ui.compose.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R

enum class MainDrawerDestination(val titleRes: Int, val iconRes: Int) {
    SUBSCRIPTIONS(R.string.title_sub_setting, R.drawable.ic_subscriptions_24dp),
    PER_APP_PROXY(R.string.per_app_proxy_settings, R.drawable.ic_per_apps_24dp),
    ROUTING(R.string.routing_settings_title, R.drawable.ic_routing_24dp),
    SETTINGS(R.string.title_settings, R.drawable.ic_settings_24dp),
    LOGCAT(R.string.title_logcat, R.drawable.ic_logcat_24dp),
    BACKUP(R.string.title_configuration_backup_restore, R.drawable.ic_restore_24dp),
    ABOUT(R.string.title_about, R.drawable.ic_about_24dp),
    KILL_APP(R.string.title_kill_app, R.drawable.ic_kill_app_24dp),
}

private val mainDestinations = listOf(
    MainDrawerDestination.SUBSCRIPTIONS,
    MainDrawerDestination.PER_APP_PROXY,
    MainDrawerDestination.ROUTING,
    MainDrawerDestination.SETTINGS,
)

private val moreDestinations = listOf(
    MainDrawerDestination.LOGCAT,
    MainDrawerDestination.BACKUP,
    MainDrawerDestination.ABOUT,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDrawer(
    mode: String,
    onModeClick: () -> Unit,
    onNavigate: (MainDrawerDestination) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val haptic = LocalHapticFeedback.current

        Column(modifier = Modifier.fillMaxSize()) {
            DrawerHeader()

            ModeCard(
                mode = mode,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onModeClick()
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                DrawerSectionTitle(R.string.title_drawer_section_main)
                DrawerSection(
                    destinations = mainDestinations,
                    onClick = { destination ->
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onNavigate(destination)
                    },
                )

                DrawerSectionTitle(R.string.title_drawer_section_more)
                DrawerSection(
                    destinations = moreDestinations,
                    onClick = { destination ->
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onNavigate(destination)
                    },
                )

                Spacer(Modifier.height(12.dp))
                KillAppCard(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigate(MainDrawerDestination.KILL_APP)
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            AppInfoFooter()
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_cookie_bg),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primaryContainer),
            )
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ModeCard(mode: String, onClick: () -> Unit) {
    val accent = modeAccent(mode)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ModeIcon(
                    mode = mode,
                    modifier = Modifier.size(22.dp),
                    tint = accent.content,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = modeLabel(mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DrawerSectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun DrawerSection(
    destinations: List<MainDrawerDestination>,
    onClick: (MainDrawerDestination) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        destinations.forEachIndexed { index, destination ->
            val isFirst = index == 0
            val isLast = index == destinations.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (isFirst) 24.dp else 4.dp,
                topEnd = if (isFirst) 24.dp else 4.dp,
                bottomStart = if (isLast) 24.dp else 4.dp,
                bottomEnd = if (isLast) 24.dp else 4.dp,
            )
            Surface(
                onClick = { onClick(destination) },
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(destination.titleRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun KillAppCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_kill_app_24dp),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.title_kill_app),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AppInfoFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            ),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = BuildConfig.APPLICATION_ID,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
