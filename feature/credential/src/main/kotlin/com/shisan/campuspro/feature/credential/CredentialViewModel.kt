package com.shisan.campuspro.feature.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shisan.campuspro.core.data.CredentialRepository
import com.shisan.campuspro.core.model.CredentialEmail
import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialFailure
import com.shisan.campuspro.core.model.CredentialFailureKind
import com.shisan.campuspro.core.model.CredentialOrder
import com.shisan.campuspro.core.model.CredentialSessionState
import com.shisan.campuspro.core.model.CredentialService
import com.shisan.campuspro.core.model.CredentialTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CredentialSection { Services, Emails, Orders }

data class CredentialUiState(
    val section: CredentialSection = CredentialSection.Services,
    val services: List<CredentialService> = emptyList(),
    val emails: List<CredentialEmail> = emptyList(),
    val orders: List<CredentialOrder> = emptyList(),
    val orderPage: Int = 1,
    val canLoadMoreOrders: Boolean = false,
    val selectedService: CredentialService? = null,
    val templates: List<CredentialTemplate> = emptyList(),
    val selectedTemplate: CredentialTemplate? = null,
    val selectedEmailId: String? = null,
    val previewBytes: ByteArray? = null,
    val previewConfirmed: Boolean = false,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val requiresWebFallback: Boolean = false,
    val sessionState: CredentialSessionState = CredentialSessionState.Unauthenticated,
    val message: String? = null,
)

class CredentialViewModel(private val repository: CredentialRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CredentialUiState())
    val uiState: StateFlow<CredentialUiState> = mutableState

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        mutableState.update {
            it.copy(
                isLoading = true,
                message = null,
                sessionState = CredentialSessionState.Authenticating,
            )
        }
        runCatching {
            val orderPage = repository.orders()
            Triple(repository.services(), repository.emails(), orderPage)
        }.onSuccess { (services, emails, orderPage) ->
            mutableState.update {
                it.copy(
                    services = services,
                    emails = emails,
                    orders = orderPage.items,
                    orderPage = orderPage.page,
                    canLoadMoreOrders = orderPage.page * orderPage.pageSize < orderPage.total,
                    isLoading = false,
                    sessionState = CredentialSessionState.Ready,
                )
            }
        }.onFailure(::showError)
    }

    fun showSection(section: CredentialSection) {
        mutableState.update { it.copy(section = section) }
    }

    fun startApplication(service: CredentialService) = viewModelScope.launch {
        if (!service.enabled) {
            mutableState.update { it.copy(message = "该凭证服务当前未开放") }
            return@launch
        }
        if (!service.nativeSupported) {
            mutableState.update { it.copy(requiresWebFallback = true) }
            return@launch
        }
        mutableState.update { it.copy(selectedService = service, isLoading = true, message = null) }
        runCatching { repository.templates(service.id) }
            .onSuccess { templates ->
                mutableState.update { it.copy(templates = templates, isLoading = false) }
            }
            .onFailure(::showError)
    }

    fun selectTemplate(template: CredentialTemplate) {
        if (!template.isFree) {
            mutableState.update { it.copy(selectedTemplate = template, requiresWebFallback = true) }
        } else {
            mutableState.update {
                it.copy(
                    selectedTemplate = template,
                    previewBytes = null,
                    previewConfirmed = false,
                )
            }
        }
    }

    fun requestPreview() = viewModelScope.launch {
        val template = uiState.value.selectedTemplate ?: return@launch
        mutableState.update { it.copy(isLoading = true) }
        runCatching { repository.preview(template) }
            .onSuccess { bytes ->
                mutableState.update { it.copy(previewBytes = bytes, isLoading = false) }
            }
            .onFailure(::showError)
    }

    fun confirmPreview(confirmed: Boolean) {
        mutableState.update { it.copy(previewConfirmed = confirmed) }
    }

    fun selectEmail(id: String) {
        mutableState.update { it.copy(selectedEmailId = id) }
    }

    fun addEmail(draft: CredentialEmailDraft) = mutateEmails { repository.addEmail(draft) }

    fun addEmailForApplication(draft: CredentialEmailDraft) = viewModelScope.launch {
        runCatching {
            repository.addEmail(draft)
            repository.emails()
        }.onSuccess { emails ->
            val selected = emails.firstOrNull {
                it.email.equals(draft.email.trim(), ignoreCase = true)
            }
            mutableState.update {
                it.copy(
                    emails = emails,
                    selectedEmailId = selected?.id,
                    message = "邮箱已添加并选中",
                )
            }
        }.onFailure(::showError)
    }
    fun editEmail(id: String, draft: CredentialEmailDraft) = mutateEmails {
        repository.editEmail(id, draft)
    }
    fun deleteEmail(id: String) = mutateEmails {
        repository.deleteEmail(id)
        if (uiState.value.selectedEmailId == id) {
            mutableState.update { it.copy(selectedEmailId = null) }
        }
    }

    fun submitOrder() = viewModelScope.launch {
        val state = uiState.value
        val template = state.selectedTemplate ?: return@launch
        val emailId = state.selectedEmailId ?: return@launch
        if (!state.previewConfirmed || state.isSubmitting) return@launch
        mutableState.update { it.copy(isSubmitting = true, message = null) }
        try {
            val order = repository.createOrder(template, emailId)
            val page = repository.orders()
            mutableState.update {
                it.applicationClosed().copy(
                    orders = page.items,
                    orderPage = page.page,
                    canLoadMoreOrders = page.page * page.pageSize < page.total,
                    section = CredentialSection.Orders,
                    message = "申请成功，订单号 ${order.orderNumber}",
                )
            }
        } catch (failure: CredentialFailure) {
            if (failure.kind == CredentialFailureKind.SubmissionUncertain) {
                reconcileUncertainSubmission()
            } else {
                showError(failure)
            }
        } catch (error: Throwable) {
            showError(error)
        }
    }

    fun refreshOrders() = loadOrders(reset = true)

    fun loadMoreOrders() {
        if (uiState.value.canLoadMoreOrders && !uiState.value.isLoading) {
            loadOrders(reset = false)
        }
    }

    fun dismissApplication() {
        mutableState.update { it.applicationClosed() }
    }

    fun webFallbackHandled() {
        mutableState.update { it.copy(requiresWebFallback = false) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun mutateEmails(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching {
            block()
            repository.emails()
        }.onSuccess { emails ->
            mutableState.update { it.copy(emails = emails, message = "邮箱设置已更新") }
        }
            .onFailure(::showError)
    }

    private fun loadOrders(reset: Boolean) = viewModelScope.launch {
        val pageNumber = if (reset) 1 else uiState.value.orderPage + 1
        mutableState.update { it.copy(isLoading = true) }
        runCatching { repository.orders(pageNumber) }
            .onSuccess { page ->
                mutableState.update {
                    it.copy(
                        orders = if (reset) page.items else it.orders + page.items,
                        orderPage = page.page,
                        canLoadMoreOrders = page.page * page.pageSize < page.total,
                        isLoading = false,
                    )
                }
            }
            .onFailure(::showError)
    }

    private suspend fun reconcileUncertainSubmission() {
        val page = runCatching { repository.orders() }.getOrNull()
        mutableState.update {
            it.applicationClosed().copy(
                orders = page?.items ?: it.orders,
                orderPage = page?.page ?: it.orderPage,
                canLoadMoreOrders = page?.let { result ->
                    result.page * result.pageSize < result.total
                } ?: it.canLoadMoreOrders,
                section = CredentialSection.Orders,
                message = "提交结果暂时无法确认，已刷新订单，请核对后再操作，请勿重复提交",
            )
        }
    }

    private fun showError(error: Throwable) {
        val failure = error as? CredentialFailure
        val sessionState = when (failure?.kind) {
            CredentialFailureKind.PortalLoginRequired -> CredentialSessionState.PortalLoginRequired
            CredentialFailureKind.NetworkUnavailable -> CredentialSessionState.WebFallbackRequired
            CredentialFailureKind.Authentication -> CredentialSessionState.Failed("统一认证失败")
            CredentialFailureKind.ServiceMaintenance -> CredentialSessionState.Failed("学校服务维护中")
            else -> CredentialSessionState.Failed(error.message ?: "可信电子凭证服务暂不可用")
        }
        mutableState.update {
            it.copy(
                isLoading = false,
                isSubmitting = false,
                requiresWebFallback = failure?.kind == CredentialFailureKind.NetworkUnavailable,
                sessionState = sessionState,
                message = error.message ?: "可信电子凭证服务暂不可用",
            )
        }
    }

    private fun CredentialUiState.applicationClosed() = copy(
        isSubmitting = false,
        selectedService = null,
        selectedTemplate = null,
        templates = emptyList(),
        previewBytes = null,
        previewConfirmed = false,
        selectedEmailId = null,
    )
}

class CredentialViewModelFactory(
    private val repository: CredentialRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CredentialViewModel(repository) as T
}
