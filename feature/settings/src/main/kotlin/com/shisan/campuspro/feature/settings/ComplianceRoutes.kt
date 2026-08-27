package com.shisan.campuspro.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.ui.SettingsListItem
import com.shisan.campuspro.core.ui.adaptiveContentWidth

@Composable
fun MoreSettingsRoute(
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToUserAgreement: () -> Unit,
    onNavigateToPersonalInformation: () -> Unit,
    onNavigateToThirdPartySharing: () -> Unit,
    onNavigateToAboutUs: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComplianceListScaffold(title = "更多", onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(padding),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    MoreSettingsItem(
                        title = "隐私政策",
                        subtitle = "了解个人信息处理规则与用户权利",
                        icon = Icons.Rounded.Lock,
                        onClick = onNavigateToPrivacyPolicy,
                    )
                    MoreSettingsItem(
                        title = "用户协议",
                        subtitle = "了解账号授权、使用规范与责任边界",
                        icon = Icons.Rounded.Info,
                        onClick = onNavigateToUserAgreement,
                    )
                    MoreSettingsItem(
                        title = "个人信息收集清单",
                        subtitle = "查看信息类型、用途、存储与删除方式",
                        icon = Icons.Rounded.Lock,
                        onClick = onNavigateToPersonalInformation,
                    )
                    MoreSettingsItem(
                        title = "第三方信息共享清单",
                        subtitle = "查看数据接收方、域名与处理目的",
                        icon = Icons.Rounded.Share,
                        onClick = onNavigateToThirdPartySharing,
                    )
                    MoreSettingsItem(
                        title = "关于我们",
                        subtitle = "应用说明、版本、开发者与开源许可",
                        icon = Icons.Rounded.Person,
                        onClick = onNavigateToAboutUs,
                    )
                }
            }
        }
    }
}

@Composable
fun PersonalInformationCollectionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = ComplianceCatalog.metadata
    ComplianceListScaffold(title = "个人信息收集清单", onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DocumentIntroCard(
                    text = "本清单说明闪电课表在登录、自动登录、同步和检查更新等功能中处理的信息。应用不会将学号、密码、课程、考试或成绩发送给开发者控制的服务器。",
                    metadata = metadata,
                )
            }
            items(ComplianceCatalog.personalInformationItems, key = { it.id }) { item ->
                PersonalInformationTableSection(item)
            }
        }
    }
}

@Composable
fun PrivacyPolicyRoute(
    isAccepted: Boolean,
    onAccept: () -> Unit,
    onRevoke: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRevokeConfirmation by remember { mutableStateOf(false) }
    LegalDocumentRoute(
        title = "隐私政策",
        sections = ComplianceCatalog.privacyPolicySections,
        onBack = onBack,
        modifier = modifier,
        footer = {
            Text(
                if (isAccepted) "你已同意当前版本的隐私政策。" else "你尚未同意当前版本，在线功能处于关闭状态。",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = if (isAccepted) ({ showRevokeConfirmation = true }) else onAccept) {
                Text(if (isAccepted) "撤回同意" else "同意并启用在线功能")
            }
        },
    )
    if (showRevokeConfirmation) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirmation = false },
            title = { Text("确认撤回同意？") },
            text = { Text("撤回后将退出账号、清除已保存凭证，并停止自动登录、同步和在线更新；本地课表会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeConfirmation = false
                    onRevoke()
                }) { Text("确认撤回") }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
fun UserAgreementRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LegalDocumentRoute(
        title = "用户协议",
        sections = ComplianceCatalog.userAgreementSections,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ThirdPartySharingRoute(
    updateManifestUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = ComplianceCatalog.metadata
    val sharingItems = ComplianceCatalog.thirdPartySharingItems(updateManifestUrl)
    val hasUpdateService = ComplianceCatalog.updateServiceItem(updateManifestUrl) != null
    ComplianceListScaffold(title = "第三方信息共享清单", onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DocumentIntroCard(
                    text = "学校系统仅在用户主动登录或同步时接收必要信息，不属于广告或数据销售。当前没有向广告、统计或画像 SDK 提供个人信息；开源依赖也不等同于数据接收方。",
                    metadata = metadata,
                )
            }
            items(sharingItems, key = { it.id }) { item ->
                ThirdPartySharingCard(item)
            }
            if (!hasUpdateService) {
                item {
                    NoticeCard("")
                }
            }
        }
    }
}

@Composable
fun AboutUsRoute(
    appVersionName: String,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val metadata = ComplianceCatalog.metadata
    ComplianceListScaffold(title = "关于我们", onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("闪电课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "面向校园场景的本地课表工具，用于连接教务系统并整理课程、考试和成绩信息。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "非吉首大学官方应用，与吉首大学不存在隶属、代理或官方授权关系。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            DetailLine("当前版本", appVersionName)
                            DetailLine("开发者", metadata.developerName)
                            DetailLine("联系邮箱", metadata.contactEmail)
                            DetailLine("文档版本", metadata.version)
                            DetailLine("生效日期", metadata.effectiveDate)
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    MoreSettingsItem(
                        title = "开源许可",
                        subtitle = "第三方组件许可与开源项目致谢",
                        icon = Icons.Rounded.Info,
                        onClick = onNavigateToOpenSourceLicenses,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    MoreSettingsItem(
                        title = "ICP 备案",
                        subtitle = "湘ICP备2024052551号-3A",
                        icon = Icons.Rounded.Info,
                        onClick = { runCatching { uriHandler.openUri("https://beian.miit.gov.cn/") } },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalInformationTableSection(item: PersonalInformationCollectionItem) {
    val shape = RoundedCornerShape(12.dp)
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(item.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(item.riskLevel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            personalInformationTableRows(item).forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .semantics { contentDescription = "${row.label}，${row.value}" },
                ) {
                    Text(
                        text = row.label,
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = row.value,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentRoute(
    title: String,
    sections: List<LegalDocumentSection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val metadata = ComplianceCatalog.metadata
    ComplianceListScaffold(title = title, onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DocumentIntroCard(
                    text = "开发者：${metadata.developerName}\n联系邮箱：${metadata.contactEmail}",
                    metadata = metadata,
                )
            }
            items(sections, key = { it.title }) { section ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(section.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            footer?.let { footerContent ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = footerContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThirdPartySharingCard(item: ThirdPartySharingItem) {
    val uriHandler = LocalUriHandler.current
    DetailCard(title = item.recipient, badge = item.domain) {
        DetailLine("接收的信息", item.information)
        DetailLine("处理目的", item.purpose)
        DetailLine("处理方式", item.processingMethod)
        DetailLine("同意要求", item.consentRequirement)
        DetailLine("停止方式", item.stopMethod)
        item.policyUrl?.let { url ->
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text("查看接收方页面")
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    badge: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                content()
            }
        }
    }
}

@Composable
private fun DocumentIntroCard(text: String, metadata: ComplianceDocumentMetadata) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "版本 ${metadata.version} · ${metadata.effectiveDate} · ${metadata.contactEmail}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MoreSettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SettingsListItem(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "进入$title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplianceListScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CampusIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        content = { padding ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.fillMaxHeight().adaptiveContentWidth(840.dp)) {
                    content(padding)
                }
            }
        },
    )
}

private fun detailContentPadding(scaffoldPadding: PaddingValues) = PaddingValues(
    start = 16.dp,
    top = scaffoldPadding.calculateTopPadding() + 12.dp,
    end = 16.dp,
    bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
)
