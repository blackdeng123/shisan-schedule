package com.shisan.campuspro.feature.credential

import com.shisan.campuspro.core.data.CredentialRepository
import com.shisan.campuspro.core.model.CredentialEmail
import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialFailure
import com.shisan.campuspro.core.model.CredentialFailureKind
import com.shisan.campuspro.core.model.CredentialOrder
import com.shisan.campuspro.core.model.CredentialOrderPage
import com.shisan.campuspro.core.model.CredentialService
import com.shisan.campuspro.core.model.CredentialTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `首页加载服务邮箱和最近订单`() = runTest(dispatcher) {
        val viewModel = CredentialViewModel(FakeCredentialRepository())
        advanceUntilIdle()
        assertEquals("成绩单", viewModel.uiState.value.services.single().name)
        assertEquals(1, viewModel.uiState.value.emails.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `收费模板请求网页办理而不提交订单`() = runTest(dispatcher) {
        val repository = FakeCredentialRepository()
        val viewModel = CredentialViewModel(repository)
        advanceUntilIdle()
        viewModel.startApplication(repository.services().single())
        advanceUntilIdle()
        viewModel.selectTemplate(CredentialTemplate("paid", "s", "收费", 1.0, "1"))
        assertTrue(viewModel.uiState.value.requiresWebFallback)
        assertEquals(0, repository.createdOrders)
    }

    @Test
    fun `申请内新增邮箱后自动选中`() = runTest(dispatcher) {
        val repository = FakeCredentialRepository(withEmail = false)
        val viewModel = CredentialViewModel(repository)
        advanceUntilIdle()

        viewModel.addEmailForApplication(
            CredentialEmailDraft("new@example.com", "", "电子凭证", "请查收"),
        )
        advanceUntilIdle()

        assertEquals("new@example.com", viewModel.uiState.value.emails.single().email)
        assertEquals("new", viewModel.uiState.value.selectedEmailId)
    }

    @Test
    fun `提交结果不确定时刷新订单且不保留可重复提交入口`() = runTest(dispatcher) {
        val repository = FakeCredentialRepository(submissionUncertain = true)
        val viewModel = CredentialViewModel(repository)
        advanceUntilIdle()
        val service = repository.services().single()
        viewModel.startApplication(service)
        advanceUntilIdle()
        viewModel.selectTemplate(repository.templates(service.id).single())
        viewModel.requestPreview()
        advanceUntilIdle()
        viewModel.confirmPreview(true)
        viewModel.selectEmail("e")

        viewModel.submitOrder()
        advanceUntilIdle()

        assertEquals(1, repository.createdOrders)
        assertEquals(CredentialSection.Orders, viewModel.uiState.value.section)
        assertEquals(null, viewModel.uiState.value.selectedService)
        assertTrue(viewModel.uiState.value.message!!.contains("请勿重复提交"))
    }
}

private class FakeCredentialRepository(
    private val withEmail: Boolean = true,
    private val submissionUncertain: Boolean = false,
) : CredentialRepository {
    var createdOrders = 0
    private val savedEmails = mutableListOf<CredentialEmail>().apply {
        if (withEmail) add(CredentialEmail("e", "a@example.com", title = "成绩单", content = "请查收"))
    }
    override suspend fun services() = listOf(CredentialService("s", "成绩单", nativeSupported = true))
    override suspend fun templates(serviceId: String) = listOf(CredentialTemplate("t", serviceId, "成绩单", 0.0, "1"))
    override suspend fun emails() = savedEmails.toList()
    override suspend fun addEmail(draft: CredentialEmailDraft) {
        savedEmails += CredentialEmail(
            id = "new",
            email = draft.email,
            ccEmail = draft.ccEmail,
            title = draft.title,
            content = draft.content,
            isDefault = draft.isDefault,
        )
    }
    override suspend fun editEmail(id: String, draft: CredentialEmailDraft) = Unit
    override suspend fun deleteEmail(id: String) = Unit
    override suspend fun preview(template: CredentialTemplate) = byteArrayOf(1)
    override suspend fun createOrder(template: CredentialTemplate, emailId: String): CredentialOrder {
        createdOrders++
        if (submissionUncertain) {
            throw CredentialFailure(
                CredentialFailureKind.SubmissionUncertain,
                "网络中断，订单结果未知",
            )
        }
        error("unused")
    }
    override suspend fun orders(page: Int, pageSize: Int) = CredentialOrderPage(emptyList(), page, pageSize, 0)
    override fun clearSession() = Unit
}
