package com.shisan.campuspro.core.data

import com.shisan.campuspro.core.model.CredentialEmail
import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialOrder
import com.shisan.campuspro.core.model.CredentialOrderPage
import com.shisan.campuspro.core.model.CredentialService
import com.shisan.campuspro.core.model.CredentialTemplate
import com.shisan.campuspro.core.network.CredentialClient

interface CredentialRepository {
    suspend fun services(): List<CredentialService>
    suspend fun templates(serviceId: String): List<CredentialTemplate>
    suspend fun emails(): List<CredentialEmail>
    suspend fun addEmail(draft: CredentialEmailDraft)
    suspend fun editEmail(id: String, draft: CredentialEmailDraft)
    suspend fun deleteEmail(id: String)
    suspend fun preview(template: CredentialTemplate): ByteArray
    suspend fun createOrder(template: CredentialTemplate, emailId: String): CredentialOrder
    suspend fun orders(page: Int = 1, pageSize: Int = 10): CredentialOrderPage
    fun clearSession()
}

interface CredentialRemoteDataSource : CredentialRepository

class DefaultCredentialRepository(
    private val remote: CredentialRemoteDataSource,
) : CredentialRepository {
    override suspend fun services() = remote.services()
    override suspend fun templates(serviceId: String) = remote.templates(serviceId)
    override suspend fun emails() = remote.emails()
    override suspend fun addEmail(draft: CredentialEmailDraft) = remote.addEmail(draft)
    override suspend fun editEmail(id: String, draft: CredentialEmailDraft) =
        remote.editEmail(id, draft)
    override suspend fun deleteEmail(id: String) = remote.deleteEmail(id)
    override suspend fun preview(template: CredentialTemplate) = remote.preview(template)
    override suspend fun createOrder(
        template: CredentialTemplate,
        emailId: String,
    ): CredentialOrder {
        require(template.isFree) { "收费凭证必须前往学校网页办理" }
        return remote.createOrder(template, emailId)
    }
    override suspend fun orders(page: Int, pageSize: Int) = remote.orders(page, pageSize)
    override fun clearSession() = remote.clearSession()
}

class NetworkCredentialRemoteDataSource(
    private val client: CredentialClient,
) : CredentialRemoteDataSource {
    override suspend fun services() = client.services()
    override suspend fun templates(serviceId: String) = client.templates(serviceId)
    override suspend fun emails() = client.emails()
    override suspend fun addEmail(draft: CredentialEmailDraft) = client.addEmail(draft)
    override suspend fun editEmail(id: String, draft: CredentialEmailDraft) =
        client.editEmail(id, draft)
    override suspend fun deleteEmail(id: String) = client.deleteEmail(id)
    override suspend fun preview(template: CredentialTemplate) = client.preview(template)
    override suspend fun createOrder(
        template: CredentialTemplate,
        emailId: String,
    ) = client.createFreeOrder(template, emailId)
    override suspend fun orders(page: Int, pageSize: Int) = client.orders(page, pageSize)
    override fun clearSession() = client.clearSession()
}
