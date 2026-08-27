package com.shisan.campuspro.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.shisan.campuspro.core.model.DarkThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusThemePolicyTest {
    @Test
    fun usesBlueSecondaryRolesInLightAndDarkThemes() {
        val lightColors = campusLightColorScheme()
        val darkColors = campusDarkColorScheme()

        assertEquals(lightColors.primary, lightColors.secondary)
        assertEquals(lightColors.onPrimary, lightColors.onSecondary)
        assertEquals(lightColors.primary, lightColors.secondaryContainer)
        assertEquals(lightColors.onPrimary, lightColors.onSecondaryContainer)
        assertEquals(darkColors.primary, darkColors.secondary)
        assertEquals(darkColors.onPrimary, darkColors.onSecondary)
        assertEquals(darkColors.primary, darkColors.secondaryContainer)
        assertEquals(darkColors.onPrimary, darkColors.onSecondaryContainer)
    }

    @Test
    fun followsSystemDarkThemeWhenConfiguredToFollowSystem() {
        assertTrue(
            shouldUseCampusDarkTheme(
                systemDarkTheme = true,
                darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
            ),
        )
        assertFalse(
            shouldUseCampusDarkTheme(
                systemDarkTheme = false,
                darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
            ),
        )
    }

    @Test
    fun usesDarkThemeWhenUserSelectsDarkTheme() {
        assertTrue(
            shouldUseCampusDarkTheme(
                systemDarkTheme = false,
                darkThemeConfig = DarkThemeConfig.DARK,
            ),
        )
    }

    @Test
    fun usesLightThemeWhenUserSelectsLightTheme() {
        assertFalse(
            shouldUseCampusDarkTheme(
                systemDarkTheme = true,
                darkThemeConfig = DarkThemeConfig.LIGHT,
            ),
        )
    }

    @Test
    fun noLightSlotFallsBackToMaterial3Baseline() {
        val theme = campusLightColorScheme()
        val baseline = lightColorScheme()
        // 这些槽位设计上就与 baseline 相同（白色前景/纯白容器/M3 默认红），不属于"漏定义"
        assertNoBaselineFallback(theme, baseline, allowBaselineEqual = setOf("onPrimary", "onSecondary", "onTertiary", "surfaceContainerLowest", "error", "onError"))
    }

    @Test
    fun noDarkSlotFallsBackToMaterial3Baseline() {
        val theme = campusDarkColorScheme()
        val baseline = darkColorScheme()
        // 暗色下 error/onError 沿用 M3 默认红，其余前景色已自定义为深蓝
        assertNoBaselineFallback(theme, baseline, allowBaselineEqual = setOf("error", "onError"))
    }

    private fun assertNoBaselineFallback(
        theme: androidx.compose.material3.ColorScheme,
        baseline: androidx.compose.material3.ColorScheme,
        allowBaselineEqual: Set<String>,
    ) {
        val slots = listOf(
            "primary" to { theme.primary },
            "onPrimary" to { theme.onPrimary },
            "primaryContainer" to { theme.primaryContainer },
            "onPrimaryContainer" to { theme.onPrimaryContainer },
            "inversePrimary" to { theme.inversePrimary },
            "secondary" to { theme.secondary },
            "onSecondary" to { theme.onSecondary },
            "secondaryContainer" to { theme.secondaryContainer },
            "onSecondaryContainer" to { theme.onSecondaryContainer },
            "tertiary" to { theme.tertiary },
            "onTertiary" to { theme.onTertiary },
            "tertiaryContainer" to { theme.tertiaryContainer },
            "onTertiaryContainer" to { theme.onTertiaryContainer },
            "background" to { theme.background },
            "onBackground" to { theme.onBackground },
            "surface" to { theme.surface },
            "onSurface" to { theme.onSurface },
            "surfaceVariant" to { theme.surfaceVariant },
            "onSurfaceVariant" to { theme.onSurfaceVariant },
            "surfaceTint" to { theme.surfaceTint },
            "surfaceContainerLowest" to { theme.surfaceContainerLowest },
            "surfaceContainerLow" to { theme.surfaceContainerLow },
            "surfaceContainer" to { theme.surfaceContainer },
            "surfaceContainerHigh" to { theme.surfaceContainerHigh },
            "surfaceContainerHighest" to { theme.surfaceContainerHighest },
            "surfaceBright" to { theme.surfaceBright },
            "surfaceDim" to { theme.surfaceDim },
            "inverseSurface" to { theme.inverseSurface },
            "inverseOnSurface" to { theme.inverseOnSurface },
            "outline" to { theme.outline },
            "outlineVariant" to { theme.outlineVariant },
            "error" to { theme.error },
            "onError" to { theme.onError },
            "errorContainer" to { theme.errorContainer },
            "onErrorContainer" to { theme.onErrorContainer },
        )
        val baselineSlots = listOf(
            baseline.primary,
            baseline.onPrimary,
            baseline.primaryContainer,
            baseline.onPrimaryContainer,
            baseline.inversePrimary,
            baseline.secondary,
            baseline.onSecondary,
            baseline.secondaryContainer,
            baseline.onSecondaryContainer,
            baseline.tertiary,
            baseline.onTertiary,
            baseline.tertiaryContainer,
            baseline.onTertiaryContainer,
            baseline.background,
            baseline.onBackground,
            baseline.surface,
            baseline.onSurface,
            baseline.surfaceVariant,
            baseline.onSurfaceVariant,
            baseline.surfaceTint,
            baseline.surfaceContainerLowest,
            baseline.surfaceContainerLow,
            baseline.surfaceContainer,
            baseline.surfaceContainerHigh,
            baseline.surfaceContainerHighest,
            baseline.surfaceBright,
            baseline.surfaceDim,
            baseline.inverseSurface,
            baseline.inverseOnSurface,
            baseline.outline,
            baseline.outlineVariant,
            baseline.error,
            baseline.onError,
            baseline.errorContainer,
            baseline.onErrorContainer,
        )
        slots.forEachIndexed { index, (name, valueProvider) ->
            if (name in allowBaselineEqual) return@forEachIndexed
            val value = valueProvider()
            val defaultValue = baselineSlots[index]
            assertTrue(
                "色槽 $name 回退到了 Material3 默认色（$defaultValue），请在主题中显式定义蓝色系派生色",
                value != defaultValue,
            )
        }
    }
}
