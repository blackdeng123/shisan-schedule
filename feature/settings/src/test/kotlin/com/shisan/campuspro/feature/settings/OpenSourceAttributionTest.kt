package com.shisan.campuspro.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceAttributionTest {
    @Test
    fun `WakeUp attribution identifies the original project and license`() {
        assertEquals("WakeUp课程表", WakeUpAttribution.projectName)
        assertEquals("YZune（杨增）及贡献者", WakeUpAttribution.author)
        assertEquals("Apache-2.0", WakeUpAttribution.license)
        assertEquals(
            "https://github.com/YZune/WakeupSchedule_Kotlin",
            WakeUpAttribution.projectUrl,
        )
        assertEquals(
            "https://github.com/YZune/WakeupSchedule_Kotlin/blob/master/LICENSE",
            WakeUpAttribution.licenseUrl,
        )
        assertTrue(WakeUpAttribution.description.contains("界面、交互方式与部分功能设计"))
        assertTrue(WakeUpAttribution.description.contains("感谢原作者与社区的开源贡献"))
    }
}
