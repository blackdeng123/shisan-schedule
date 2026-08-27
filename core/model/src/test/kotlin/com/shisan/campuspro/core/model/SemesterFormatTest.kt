package com.shisan.campuspro.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SemesterFormatTest {

    @Test
    fun `formats jwxt semester code into readable label`() {
        assertEquals("2025-2026学年 第一学期", formatSemesterLabel("2025-2026-1"))
        assertEquals("2025-2026学年 第二学期", formatSemesterLabel("2025-2026-2"))
        assertEquals("2026-2027学年 第三学期", formatSemesterLabel("2026-2027-3"))
    }

    @Test
    fun `unknown formats are returned unchanged`() {
        assertEquals("第一学期", formatSemesterLabel("第一学期"))
        assertEquals("", formatSemesterLabel(""))
    }

    @Test
    fun `terms beyond three fall back to digit label`() {
        assertEquals("2025-2026学年 第4学期", formatSemesterLabel("2025-2026-4"))
    }

    @Test
    fun `trims surrounding whitespace before formatting`() {
        assertEquals("2025-2026学年 第一学期", formatSemesterLabel(" 2025-2026-1 "))
    }
}
