package com.shisan.campuspro.feature.settings

import com.shisan.campuspro.core.model.PrivacyConsentState
import com.shisan.campuspro.core.model.canUseOnlineFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceCatalogTest {
    @Test
    fun `personal information catalog covers ten required categories with unique complete entries`() {
        val items = ComplianceCatalog.personalInformationItems

        assertEquals(10, items.size)
        assertEquals(items.size, items.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                "student_id",
                "password",
                "session",
                "profile",
                "courses",
                "exams",
                "grades",
                "custom_schedule",
                "reminders",
                "updates",
            ),
            items.map { it.id }.toSet(),
        )
        items.forEach { item ->
            assertTrue(item.category.isNotBlank())
            assertTrue(item.information.isNotBlank())
            assertTrue(item.purpose.isNotBlank())
            assertTrue(item.processingScene.isNotBlank())
            assertTrue(item.storageLocation.isNotBlank())
            assertTrue(item.retentionPeriod.isNotBlank())
            assertTrue(item.externalTransmission.isNotBlank())
            assertTrue(item.controlMethod.isNotBlank())
        }
    }

    @Test
    fun `sharing catalog always includes both university recipients`() {
        val items = ComplianceCatalog.thirdPartySharingItems(updateManifestUrl = "")

        assertEquals(items.size, items.map { it.id }.distinct().size)
        assertTrue(items.any { it.domain == "authserver.jsu.edu.cn" })
        assertTrue(items.any { it.domain == "jwxt.jsu.edu.cn" })
        items.forEach { item ->
            assertTrue(item.recipient.isNotBlank())
            assertTrue(item.information.isNotBlank())
            assertTrue(item.purpose.isNotBlank())
            assertTrue(item.processingMethod.isNotBlank())
            assertTrue(item.consentRequirement.isNotBlank())
            assertTrue(item.stopMethod.isNotBlank())
        }
    }

    @Test
    fun `official https update endpoint exposes only its host`() {
        val item = ComplianceCatalog.updateServiceItem(
            "https://updates.example.com/releases/latest.json",
        )

        assertEquals("updates.example.com", item?.domain)
        assertFalse(item?.domain.orEmpty().contains("latest.json"))
    }

    @Test
    fun `blank localhost and non-https update endpoints are not third party recipients`() {
        assertNull(ComplianceCatalog.updateServiceItem(""))
        assertNull(ComplianceCatalog.updateServiceItem("http://127.0.0.1:8080/latest.json"))
        assertNull(ComplianceCatalog.updateServiceItem("http://updates.example.com/latest.json"))
        assertNull(ComplianceCatalog.updateServiceItem("not a url"))
    }

    @Test
    fun `only current accepted policy enables online features`() {
        assertTrue(PrivacyConsentState("1.0", 123L).isAccepted("1.0"))
        assertFalse(PrivacyConsentState("0.9", 123L).isAccepted("1.0"))
        assertFalse(PrivacyConsentState(null, null).isAccepted("1.0"))
        assertFalse(canUseOnlineFeatures(privacyReady = false, PrivacyConsentState("1.0", 123L)))
        assertTrue(canUseOnlineFeatures(privacyReady = true, PrivacyConsentState("1.0", 123L)))
    }

    @Test
    fun `personal information table has the same eight rows for every item`() {
        ComplianceCatalog.personalInformationItems.forEach { item ->
            assertEquals(
                listOf("具体信息", "处理目的", "处理场景", "是否必要", "存储位置", "保存期限", "外部传输", "关闭或删除"),
                personalInformationTableRows(item).map { it.label },
            )
            assertTrue(personalInformationTableRows(item).all { it.value.isNotBlank() })
        }
    }
}
