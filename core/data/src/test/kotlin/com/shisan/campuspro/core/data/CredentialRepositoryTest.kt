package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.CredentialTemplate
import com.shisan.campuspro.core.model.CredentialEmail
import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialOrder
import com.shisan.campuspro.core.model.CredentialOrderPage
import com.shisan.campuspro.core.model.CredentialService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRepositoryTest {
    @Test
    fun `收费模板不会调用学校下单接口`() = runTest {
        val remote = FakeCredentialRemoteDataSource()
        val repository = DefaultCredentialRepository(remote)
        val result = runCatching {
            repository.createOrder(CredentialTemplate("t", "s", "收费证明", 1.0, "1"), "email")
        }
        assertTrue(result.isFailure)
        assertEquals(0, remote.createdOrders)
    }
}

private class FakeCredentialRemoteDataSource : CredentialRemoteDataSource {
    var createdOrders = 0
    override suspend fun services(): List<CredentialService> = emptyList()
    override suspend fun templates(serviceId: String): List<CredentialTemplate> = emptyList()
    override suspend fun emails(): List<CredentialEmail> = emptyList()
    override suspend fun addEmail(draft: CredentialEmailDraft) = Unit
    override suspend fun editEmail(id: String, draft: CredentialEmailDraft) = Unit
    override suspend fun deleteEmail(id: String) = Unit
    override suspend fun preview(template: CredentialTemplate): ByteArray = byteArrayOf()
    override suspend fun createOrder(template: CredentialTemplate, emailId: String): CredentialOrder {
        createdOrders++
        error("not needed")
    }
    override suspend fun orders(page: Int, pageSize: Int) = CredentialOrderPage(emptyList(), page, pageSize, 0)
    override fun clearSession() = Unit
}
