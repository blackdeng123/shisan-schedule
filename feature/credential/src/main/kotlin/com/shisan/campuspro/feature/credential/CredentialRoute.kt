package com.shisan.campuspro.feature.credential

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shisan.campuspro.core.data.CredentialRepository
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.model.CredentialEmail
import com.shisan.campuspro.core.model.CredentialEmailDraft
import com.shisan.campuspro.core.model.CredentialOrder
import com.shisan.campuspro.core.model.CredentialOrderProgress
import com.shisan.campuspro.core.model.CredentialPaymentStatus
import com.shisan.campuspro.core.model.CredentialService
import com.shisan.campuspro.core.model.CredentialSessionState
import com.shisan.campuspro.core.model.CredentialTemplate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialRoute(
    repository: CredentialRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    onRequirePortalLogin: () -> Unit,
) {
    val viewModel: CredentialViewModel = viewModel(
        factory = CredentialViewModelFactory(repository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.previewBytes) {
        state.previewBytes?.let { bytes ->
            if (!openCredentialPreview(context, bytes)) {
                val result = snackbar.showSnackbar(
                    message = "未找到 PDF 查看器",
                    actionLabel = "学校网页",
                )
                if (result == SnackbarResult.ActionPerformed) onOpenWeb()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("可信电子凭证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CampusIcons.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(CampusIcons.Refresh, "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CredentialSection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = { viewModel.showSection(section) },
                        label = { Text(section.label()) },
                    )
                }
            }
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.section) {
                    CredentialSection.Services -> ServiceList(
                        state.services,
                        viewModel::startApplication,
                        onOpenWeb,
                    )
                    CredentialSection.Emails -> EmailList(
                        state.emails,
                        viewModel::addEmail,
                        viewModel::editEmail,
                        viewModel::deleteEmail,
                    )
                    CredentialSection.Orders -> OrderList(
                        orders = state.orders,
                        canLoadMore = state.canLoadMoreOrders,
                        onRefresh = viewModel::refreshOrders,
                        onLoadMore = viewModel::loadMoreOrders,
                    )
                }
            }
        }
    }
    state.selectedService?.let { service ->
        ApplicationDialog(
            service = service,
            state = state,
            onTemplate = viewModel::selectTemplate,
            onPreview = viewModel::requestPreview,
            onConfirmPreview = viewModel::confirmPreview,
            onEmail = viewModel::selectEmail,
            onAddEmail = viewModel::addEmailForApplication,
            onSubmit = viewModel::submitOrder,
            onDismiss = viewModel::dismissApplication,
        )
    }
    if (state.requiresWebFallback) {
        AlertDialog(
            onDismissRequest = viewModel::webFallbackHandled,
            title = { Text("通过学校网页继续") },
            text = { Text("当前服务需要在学校网页中完成。将使用系统安全浏览器打开 WebVPN。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.webFallbackHandled()
                        onOpenWeb()
                    },
                ) {
                    Text("打开学校网页")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::webFallbackHandled) { Text("取消") }
            },
        )
    }
    if (state.sessionState == CredentialSessionState.PortalLoginRequired) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("需要门户登录") },
            text = { Text("可信电子凭证只能复用门户统一认证，请重新使用门户模式登录。") },
            confirmButton = {
                Button(onClick = onRequirePortalLogin) { Text("前往门户登录") }
            },
            dismissButton = { TextButton(onClick = onBack) { Text("返回") } },
        )
    }
}

@Composable
private fun ServiceList(
    services: List<CredentialService>,
    onApply: (CredentialService) -> Unit,
    onOpenWeb: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        items(services, key = { it.id }) { service ->
            Card(
                onClick = {
                    when {
                        !service.enabled -> Unit
                        service.nativeSupported -> onApply(service)
                        else -> onOpenWeb()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(service.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !service.enabled -> "暂未开放"
                            service.nativeSupported -> "原生办理"
                            else -> "通过学校网页办理"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (services.isEmpty()) {
            item { Text("暂无可用凭证服务", modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun EmailList(
    emails: List<CredentialEmail>,
    onAdd: (CredentialEmailDraft) -> Unit,
    onEdit: (String, CredentialEmailDraft) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<CredentialEmail?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            "学校没有邮箱验证码，地址错误会导致收不到凭证。",
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = { creating = true },
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text("添加邮箱")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(emails, key = { it.id }) { email ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(email.email)
                        if (email.isDefault) Text("默认邮箱")
                        Text(email.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { editing = email }) { Text("修改") }
                            TextButton(onClick = { onDelete(email.id) }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        EmailDialog(
            existing = editing,
            onDismiss = {
                creating = false
                editing = null
            },
        ) { draft ->
            editing?.let { onEdit(it.id, draft) } ?: onAdd(draft)
            creating = false
            editing = null
        }
    }
}

@Composable
private fun EmailDialog(
    existing: CredentialEmail?,
    onDismiss: () -> Unit,
    onSave: (CredentialEmailDraft) -> Unit,
) {
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var cc by remember { mutableStateOf(existing?.ccEmail.orEmpty()) }
    var title by remember { mutableStateOf(existing?.title ?: "电子凭证") }
    var content by remember { mutableStateOf(existing?.content ?: "请查收电子凭证") }
    var isDefault by remember { mutableStateOf(existing?.isDefault ?: false) }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加邮箱" else "修改邮箱") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学校不发送验证码，请仔细核对收件地址。")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("收件邮箱") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(isDefault, onCheckedChange = { isDefault = it })
                    Text("设为默认邮箱")
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "收起高级设置" else "高级设置")
                }
                if (advanced) {
                    OutlinedTextField(
                        value = cc,
                        onValueChange = { cc = it },
                        label = { Text("抄送地址（分号分隔）") },
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("邮件标题") },
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("邮件内容") },
                        minLines = 3,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val draft = CredentialEmailDraft(email, cc, title, content, isDefault)
                    val errors = draft.validationErrors()
                    if (errors.isEmpty()) onSave(draft) else error = errors.first()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun OrderList(
    orders: List<CredentialOrder>,
    canLoadMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    var revealedOrderId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        item {
            OutlinedButton(onClick = onRefresh) { Text("刷新订单") }
        }
        items(orders, key = { it.id }) { order ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        order.serviceName.ifBlank { "电子凭证" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("订单 ${order.orderNumber}")
                    Text(if (revealedOrderId == order.id) order.email else order.maskedEmail)
                    Text(order.progress.label())
                    Text(order.paymentStatus.label())
                    TextButton(
                        onClick = {
                            revealedOrderId = if (revealedOrderId == order.id) null else order.id
                        },
                    ) {
                        Text(if (revealedOrderId == order.id) "隐藏完整邮箱" else "确认查看完整邮箱")
                    }
                }
            }
        }
        if (orders.isEmpty()) {
            item { Text("暂无订单", modifier = Modifier.padding(24.dp)) }
        }
        if (canLoadMore) {
            item {
                OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更多")
                }
            }
        }
    }
}

@Composable
private fun ApplicationDialog(
    service: CredentialService,
    state: CredentialUiState,
    onTemplate: (CredentialTemplate) -> Unit,
    onPreview: () -> Unit,
    onConfirmPreview: (Boolean) -> Unit,
    onEmail: (String) -> Unit,
    onAddEmail: (CredentialEmailDraft) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var addingEmail by remember(service.id) { mutableStateOf(false) }
    val selectedEmail = state.emails.firstOrNull { it.id == state.selectedEmailId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("申请${service.name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("1. 选择模板") }
                items(state.templates) { template ->
                    FilterChip(
                        selected = state.selectedTemplate?.id == template.id,
                        onClick = { onTemplate(template) },
                        label = { Text("${template.displayName} · ¥${template.price}") },
                    )
                }
                state.selectedTemplate?.let {
                    item {
                        OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                            Text("预览 PDF")
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.previewConfirmed,
                                onCheckedChange = onConfirmPreview,
                                enabled = state.previewBytes != null,
                            )
                            Text("我已核对凭证内容")
                        }
                    }
                }
                if (state.previewConfirmed) {
                    item { Text("2. 选择接收邮箱") }
                    items(state.emails) { email ->
                        FilterChip(
                            selected = state.selectedEmailId == email.id,
                            onClick = { onEmail(email.id) },
                            label = { Text(email.email) },
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { addingEmail = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.emails.isEmpty()) "添加收件邮箱" else "添加其他邮箱")
                        }
                    }
                }
                if (selectedEmail != null && state.selectedTemplate != null) {
                    item { Text("3. 最终确认", style = MaterialTheme.typography.titleMedium) }
                    item { Text("服务：${service.name}") }
                    item { Text("收件邮箱：${selectedEmail.email}") }
                    item { Text("金额：¥${state.selectedTemplate.price}") }
                    item { Text("正式凭证将通过邮件发送，App 不提供下载或分享。") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = state.previewConfirmed &&
                    state.selectedEmailId != null &&
                    !state.isSubmitting,
            ) {
                Text(if (state.isSubmitting) "提交中" else "确认申请")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (addingEmail) {
        EmailDialog(
            existing = null,
            onDismiss = { addingEmail = false },
        ) { draft ->
            addingEmail = false
            onAddEmail(draft)
        }
    }
}

private fun CredentialSection.label() = when (this) {
    CredentialSection.Services -> "服务"
    CredentialSection.Emails -> "邮箱"
    CredentialSection.Orders -> "订单"
}

private fun CredentialOrderProgress.label() = when (this) {
    CredentialOrderProgress.Pending -> "处理中"
    CredentialOrderProgress.Sent -> "已发送"
    CredentialOrderProgress.Failed -> "发送失败"
    is CredentialOrderProgress.Unknown -> "状态未知，请前往学校网页查看"
}

private fun CredentialPaymentStatus.label() = when (this) {
    CredentialPaymentStatus.NotRequired -> "无需支付"
    CredentialPaymentStatus.Pending -> "待支付"
    CredentialPaymentStatus.Paid -> "已支付"
    is CredentialPaymentStatus.Unknown -> "支付状态未知，请前往学校网页查看"
}

private fun openCredentialPreview(context: Context, bytes: ByteArray): Boolean = runCatching {
    val directory = File(context.cacheDir, "credentials").apply { mkdirs() }
    val file = File(directory, "preview.pdf").apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
    true
}.getOrElse { error ->
    if (error is ActivityNotFoundException) false else false
}
