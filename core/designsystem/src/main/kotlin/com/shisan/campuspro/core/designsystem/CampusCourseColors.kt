package com.shisan.campuspro.core.designsystem

import androidx.compose.ui.graphics.Color

object CampusCourseColors {
    val fallback = Color(0xFF9F1239)

    private val semanticColors = linkedMapOf(
        "blue" to Color(0xFF1D4ED8),
        "indigo" to Color(0xFF4338CA),
        "purple" to Color(0xFF7E22CE),
        "pink" to Color(0xFFBE185D),
        "orange" to Color(0xFFC2410C),
        "emerald" to Color(0xFF047857),
        "cyan" to Color(0xFF0E7490),
        "slate" to Color(0xFF475569),
        "red" to Color(0xFFB91C1C),
        "amber" to Color(0xFF92400E),
        "teal" to Color(0xFF0F766E),
        "brown" to Color(0xFF6D4C41),
    )

    val tokens: List<String> = semanticColors.keys.toList()

    fun resolve(value: String): Color =
        semanticColors[value.lowercase()]
            ?: parseHexColor(value)
            ?: fallback

    private fun parseHexColor(value: String): Color? {
        if (!value.startsWith('#')) return null
        return runCatching {
            when (value.length) {
                7 -> Color(0xFF000000 or value.drop(1).toLong(16))
                9 -> Color(value.drop(1).toLong(16))
                else -> error("Unsupported color format")
            }
        }.getOrNull()
    }
}
