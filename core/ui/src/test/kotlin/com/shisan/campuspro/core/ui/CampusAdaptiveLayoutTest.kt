package com.shisan.campuspro.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusAdaptiveLayoutTest {
    @Test
    fun `599dp remains compact while 600dp becomes medium`() {
        assertEquals(CampusWindowWidthClass.Compact, classifyCampusAdaptiveInfo(599, 800).widthClass)
        assertEquals(CampusWindowWidthClass.Medium, classifyCampusAdaptiveInfo(600, 800).widthClass)
    }

    @Test
    fun `839dp remains medium while 840dp becomes expanded`() {
        assertEquals(CampusWindowWidthClass.Medium, classifyCampusAdaptiveInfo(839, 800).widthClass)
        assertEquals(CampusWindowWidthClass.Expanded, classifyCampusAdaptiveInfo(840, 800).widthClass)
    }

    @Test
    fun `height below 480dp uses compact height`() {
        assertTrue(classifyCampusAdaptiveInfo(800, 479).isHeightCompact)
        assertFalse(classifyCampusAdaptiveInfo(800, 480).isHeightCompact)
    }

    @Test
    fun `navigation rail starts at medium while two pane starts at expanded`() {
        val compact = classifyCampusAdaptiveInfo(599, 800)
        val medium = classifyCampusAdaptiveInfo(600, 800)
        val expanded = classifyCampusAdaptiveInfo(840, 800)

        assertFalse(compact.usesNavigationRail)
        assertTrue(medium.usesNavigationRail)
        assertFalse(medium.usesTwoPane)
        assertTrue(expanded.usesTwoPane)
    }
}
