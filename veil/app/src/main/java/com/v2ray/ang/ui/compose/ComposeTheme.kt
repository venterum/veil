package com.v2ray.ang.ui.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8F4C00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF745A42),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2A1706),
    tertiary = Color(0xFF5C6300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFE971),
    onTertiaryContainer = Color(0xFF1A1D00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A14),
    surfaceDim = Color(0xFFE8D6CA),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceVariant = Color(0xFFF3DFD1),
    onSurfaceVariant = Color(0xFF52443A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFFEBDF),
    surfaceContainerHigh = Color(0xFFFCE5D9),
    surfaceContainerHighest = Color(0xFFF6E0D3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF847469),
    outlineVariant = Color(0xFFD6C3B6),
    inverseSurface = Color(0xFF382F28),
    inverseOnSurface = Color(0xFFFFEDE2),
    inversePrimary = Color(0xFFFFB77B),
    surfaceTint = Color(0xFF8F4C00),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB77B),
    onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFF6D3900),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE2C1A5),
    onSecondary = Color(0xFF412C19),
    secondaryContainer = Color(0xFF5A422D),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFC3CD58),
    onTertiary = Color(0xFF2F3300),
    tertiaryContainer = Color(0xFF454B00),
    onTertiaryContainer = Color(0xFFDFE971),
    background = Color(0xFF1A120C),
    onBackground = Color(0xFFF0E0D4),
    surface = Color(0xFF1A120C),
    onSurface = Color(0xFFF0E0D4),
    surfaceDim = Color(0xFF1A120C),
    surfaceBright = Color(0xFF3D332B),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD6C3B6),
    surfaceContainerLowest = Color(0xFF140D07),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF271E17),
    surfaceContainerHigh = Color(0xFF322821),
    surfaceContainerHighest = Color(0xFF3D332B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9F8D80),
    outlineVariant = Color(0xFF52443A),
    inverseSurface = Color(0xFFF0E0D4),
    inverseOnSurface = Color(0xFF382F28),
    inversePrimary = Color(0xFF8F4C00),
    surfaceTint = Color(0xFFFFB77B),
)

// Accent color used by active FAB / progress indicators.
val colorFabActive = Color(0xFFf97910)

// Green used by the "connected" dot on the selected running server card.
val colorConnected = Color(0xFF009966)

// Divider colors for list dividers.
val dividerColorLight = Color(0xFFE0E0E0)
val dividerColorDark = Color(0xFF424242)

// Composition local for whether the app is in dark theme.
val LocalDarkTheme = compositionLocalOf { false }

// True when the pure-black AMOLED palette is active.
val LocalAmoledTheme = compositionLocalOf { false }

/**
 * Keeps the accent colours of [base] but flattens every surface token to black or
 * a near-black tier, so cards and sheets remain distinguishable on OLED panels.
 */
private fun amoledDarkColorScheme(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1C1C1C),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF2A2A2A),
    outline = Color(0xFF6F6F6F),
    outlineVariant = Color(0xFF2E2E2E),
)

// Google Sans Flex is a variable font: declare the weights explicitly so
// Semibold renders as 600 instead of being synthesised as a heavier bold.
private val GoogleSans = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private fun typography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    val baseScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val amoled = darkTheme && SettingsManager.isAmoledThemeEnabled()
    val colorScheme = if (amoled) amoledDarkColorScheme(baseScheme) else baseScheme
    val fontFamily = if (MmkvManager.decodeSettingsBool(AppConfig.PREF_GOOGLE_SANS, true)) {
        GoogleSans
    } else {
        FontFamily.Default
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAmoledTheme provides amoled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography(fontFamily),
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
