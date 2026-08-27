package com.shisan.campuspro.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.ui.adaptiveContentWidth
import com.shisan.campuspro.core.ui.AdaptiveTwoPane
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.usesTwoPane
import kotlinx.coroutines.launch

@Composable
fun OpenSourceLicensesRoute(
    @RawRes libraryResourceId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val catalogResult = remember(libraryResourceId) {
        runCatching {
            val json = context.resources.openRawResource(libraryResourceId)
                .bufferedReader()
                .use { it.readText() }
            OpenSourceCatalogParser.parse(json)
        }
    }
    val openUri: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
            .onFailure {
                scope.launch { snackbarHostState.showSnackbar("无法打开链接，请检查浏览器设置") }
            }
    }

    OpenSourceLicensesScreen(
        catalog = catalogResult.getOrNull(),
        loadError = catalogResult.exceptionOrNull()?.message,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenUri = openUri,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSourceLicensesScreen(
    catalog: OpenSourceCatalog?,
    loadError: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedLicense by remember { mutableStateOf<OpenSourceLicense?>(null) }
    val apacheLicense = remember(catalog) {
        catalog?.libraries
            ?.asSequence()
            ?.flatMap { it.licenses.asSequence() }
            ?.firstOrNull { it.id == WakeUpAttribution.license }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("开源许可") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CampusIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveTwoPane(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            mainPane = {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().adaptiveContentWidth(840.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WakeUpAttributionCard(
                    onOpenProject = { onOpenUri(WakeUpAttribution.projectUrl) },
                    onOpenLicense = {
                        if (apacheLicense != null) {
                            selectedLicense = apacheLicense
                        } else {
                            onOpenUri(WakeUpAttribution.licenseUrl)
                        }
                    },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "第三方组件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = catalog?.let { "由构建系统自动识别，共 ${it.libraries.size} 项" }
                            ?: "由构建系统自动识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                catalog != null && catalog.libraries.isNotEmpty() -> {
                    items(
                        items = catalog.libraries,
                        key = { it.uniqueId },
                    ) { library ->
                        OpenSourceLibraryCard(
                            library = library,
                            onLicenseClick = { selectedLicense = it },
                            onWebsiteClick = { library.website?.let(onOpenUri) },
                        )
                    }
                }

                loadError != null -> {
                    item {
                        MessageCard("许可证信息加载失败：$loadError")
                    }
                }

                else -> {
                    item { MessageCard("未检测到第三方组件") }
                }
            }
        }
            },
            supportingPane = {
                LicenseDetailPane(
                    license = selectedLicense,
                    onOpenSource = { selectedLicense?.url?.let(onOpenUri) },
                )
            },
        )
    }

    if (!LocalCampusAdaptiveInfo.current.usesTwoPane) selectedLicense?.let { license ->
        LicenseDetailDialog(
            license = license,
            onDismiss = { selectedLicense = null },
            onOpenSource = { license.url?.let(onOpenUri) },
        )
    }
}

@Composable
private fun LicenseDetailPane(
    license: OpenSourceLicense?,
    onOpenSource: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (license == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择许可证以查看全文", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(license.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SelectionContainer(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    Text(license.content.ifBlank { "暂无许可证全文" }, style = MaterialTheme.typography.bodySmall)
                }
                if (license.url != null) {
                    TextButton(onClick = onOpenSource) { Text("查看来源") }
                }
            }
        }
    }
}

@Composable
private fun WakeUpAttributionCard(
    onOpenProject: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "特别致谢",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = WakeUpAttribution.projectName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = WakeUpAttribution.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onOpenLicense,
                    label = { Text(WakeUpAttribution.license) },
                )
                AssistChip(
                    onClick = onOpenProject,
                    label = { Text("访问原项目") },
                )
            }
        }
    }
}

@Composable
private fun OpenSourceLibraryCard(
    library: OpenSourceLibrary,
    onLicenseClick: (OpenSourceLicense) -> Unit,
    onWebsiteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = library.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                library.version?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (library.developers.isNotEmpty()) {
                Text(
                    text = library.developers.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            library.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                library.licenses.forEach { license ->
                    AssistChip(
                        onClick = { onLicenseClick(license) },
                        label = { Text(license.id) },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (library.website != null) {
                    TextButton(onClick = onWebsiteClick) {
                        Text("项目主页")
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseDetailDialog(
    license: OpenSourceLicense,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(license.name) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = license.content.ifBlank { "暂无许可证全文" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = if (license.url != null) {
            { TextButton(onClick = onOpenSource) { Text("查看来源") } }
        } else {
            null
        },
    )
}

@Composable
private fun MessageCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
