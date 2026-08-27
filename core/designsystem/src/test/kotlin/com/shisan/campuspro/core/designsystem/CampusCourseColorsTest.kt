package com.shisan.campuspro.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusCourseColorsTest {
    @Test
    fun `resolves all semantic tokens to distinct colors with readable white text`() {
        val resolved = CampusCourseColors.tokens.map(CampusCourseColors::resolve)

        assertEquals(12, resolved.distinct().size)
        resolved.forEach { color ->
            val contrastWithWhite = 1.05f / (color.luminance() + 0.05f)
            assertTrue("白色文字对比度不足: $contrastWithWhite", contrastWithWhite >= 4.5f)
        }
    }

    @Test
    fun `supports custom hex and only falls back for unknown values`() {
        assertEquals(Color(0xFF123456), CampusCourseColors.resolve("#123456"))
        assertEquals(Color(0x80123456), CampusCourseColors.resolve("#80123456"))
        assertNotEquals(CampusCourseColors.fallback, CampusCourseColors.resolve("indigo"))
        assertEquals(CampusCourseColors.fallback, CampusCourseColors.resolve("not-a-color"))
    }
}
