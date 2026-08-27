package com.shisan.campuspro.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shisan.campuspro.core.model.DarkThemeConfig

object CampusColors {
    val Primary = Color(0xFF3B82F6)
    val AppBg = Color(0xFFF8F9FA)
    val DarkBg = Color(0xFF020617)
    val ChipSelectedContainer = Primary
    val ChipSelectedOnContainer = Color.White
}

internal fun campusLightColorScheme() = lightColorScheme(
    primary = CampusColors.Primary,
    onPrimary = Color.White,
    secondary = CampusColors.Primary,
    onSecondary = Color.White,
    tertiary = CampusColors.Primary,
    onTertiary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF082F49),
    secondaryContainer = CampusColors.Primary,
    onSecondaryContainer = Color.White,
    tertiaryContainer = Color(0xFFDCEBFF),
    onTertiaryContainer = Color(0xFF082F49),
    background = CampusColors.AppBg,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEFF6FF),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F8FC),
    surfaceContainer = Color(0xFFF0F4FA),
    surfaceContainerHigh = Color(0xFFE9EEF7),
    surfaceContainerHighest = Color(0xFFE2E9F4),
    surfaceBright = Color(0xFFF8FAFD),
    surfaceDim = Color(0xFFD4DDF0),
    inverseSurface = Color(0xFF2E3A4C),
    inverseOnSurface = Color(0xFFEEF2FF),
    inversePrimary = Color(0xFFBFDBFE),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFD7DEE8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239),
)

internal fun campusDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF082F49),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF082F49),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF082F49),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondaryContainer = Color(0xFF93C5FD),
    onSecondaryContainer = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = CampusColors.DarkBg,
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF111A2B),
    surfaceContainer = Color(0xFF162032),
    surfaceContainerHigh = Color(0xFF1C2740),
    surfaceContainerHighest = Color(0xFF222E49),
    surfaceBright = Color(0xFF343E52),
    surfaceDim = Color(0xFF0D1422),
    inverseSurface = Color(0xFFE5E7EB),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = Color(0xFF1D4ED8),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
)

private val LightColors = campusLightColorScheme()
private val DarkColors = campusDarkColorScheme()

val LocalCampusDarkTheme = staticCompositionLocalOf { false }

fun shouldUseCampusDarkTheme(
    systemDarkTheme: Boolean,
    darkThemeConfig: DarkThemeConfig,
): Boolean = when (darkThemeConfig) {
    DarkThemeConfig.FOLLOW_SYSTEM -> systemDarkTheme
    DarkThemeConfig.LIGHT -> false
    DarkThemeConfig.DARK -> true
}

@Composable
fun CampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCampusDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
