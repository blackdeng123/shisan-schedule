package com.shisan.campuspro.feature.grades

import com.shisan.campuspro.core.ui.CampusWindowWidthClass
import org.junit.Assert.assertEquals
import org.junit.Test

class GradesAdaptivePolicyTest {
    @Test
    fun `only expanded width uses vertical semester filters`() {
        assertEquals(SemesterFilterLayout.Horizontal, semesterFilterLayout(CampusWindowWidthClass.Compact))
        assertEquals(SemesterFilterLayout.Horizontal, semesterFilterLayout(CampusWindowWidthClass.Medium))
        assertEquals(SemesterFilterLayout.Vertical, semesterFilterLayout(CampusWindowWidthClass.Expanded))
    }
}
