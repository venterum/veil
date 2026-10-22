package com.v2ray.ang.ui.compose.main

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Everything the main screen needs to know about the connection, kept in one
 * immutable snapshot so the one-second uptime/speed ticks only invalidate the
 * small parts of the screen that actually read it.
 */
@Immutable
data class ConnectionUiState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val statusDetail: String = "",
    val serverName: String? = null,
    val connectionIp: String? = null,
    val uploadSpeed: String = "0 B/s",
    val downloadSpeed: String = "0 B/s",
    val uploadRate: Long = 0L,
    val downloadRate: Long = 0L,
    val uptime: String = "00:00",
    val showSpeed: Boolean = false,
    val showTun: Boolean = false,
    val tunEnabled: Boolean = false,
)

/** Display preferences read from MMKV on resume. */
@Immutable
data class MainDisplayFlags(
    val grouped: Boolean = false,
    val doubleColumn: Boolean = false,
    val cardStyleNew: Boolean = true,
    val showEmojiAvatar: Boolean = true,
    val showIcons: Boolean = false,
    val showQuickTestAll: Boolean = true,
    val showQuickSort: Boolean = true,
    val showQuickUpdate: Boolean = true,
    val showUnsupportedPingPill: Boolean = true,
    val bolderFont: Boolean = true,
)

/** Whether main-screen headings use the heavier weights. */
val LocalBolderMainFont = compositionLocalOf { true }

/** Whether olcRTC/OpenFlux cards show the "No ping" pill. */
val LocalShowUnsupportedPingPill = compositionLocalOf { true }

/** Clipboard import preview waiting for the user's confirmation. */
@Immutable
data class ClipboardImportPreview(
    val content: String,
    val message: String,
)
