package com.shisan.campuspro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.model.DarkThemeConfig
import com.shisan.campuspro.core.ui.SettingsListItem
import com.shisan.campuspro.core.ui.LocalCampusAdaptiveInfo
import com.shisan.campuspro.core.ui.usesTwoPane

// ────────────────────────────────────────────────
// Route
// ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    darkThemeConfig: DarkThemeConfig,
    onDarkThemeConfigChange: (DarkThemeConfig) -> Unit,
    updateStatus: String,
    updateChecking: Boolean,
    onCheckForUpdates: () -> Unit,
    onNavigateToClassTimeSettings: () -> Unit = {},
    onNavigateToScheduleSettings: () -> Unit = {},
    onNavigateToMore: () -> Unit = {},
    extraContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = if (LocalCampusAdaptiveInfo.current.usesTwoPane) GridCells.Fixed(2) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // 外观组
            item {
                SettingsSectionCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsIconCircle(icon = CampusIcons.DarkMode)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "深色模式",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "默认跟随系统，也可以手动切换浅色或深色",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ThemeConfigSelector(
                            selected = darkThemeConfig,
                            onSelected = onDarkThemeConfigChange,
                        )
                    }
                }
            }

            // 课表设置组
            item {
                SettingsSectionCard {
                    SettingsListItem(
                        title = "上课时间",
                        subtitle = "夏令时、节次和课程时长",
                        onClick = onNavigateToClassTimeSettings,
                        leadingContent = {
                            SettingsIconCircle(icon = Icons.Rounded.Notifications)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    SettingsListItem(
                        title = "课表设置",
                        subtitle = "名称、周数、开学日期和显示",
                        onClick = onNavigateToScheduleSettings,
                        leadingContent = {
                            SettingsIconCircle(icon = CampusIcons.Calendar)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            // 更多组
            item {
                SettingsSectionCard {
                    SettingsListItem(
                        title = "检查更新",
                        subtitle = updateStatus,
                        onClick = onCheckForUpdates,
                        leadingContent = {
                            SettingsIconCircle(icon = CampusIcons.Refresh)
                        },
                        trailingContent = {
                            if (updateChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    SettingsListItem(
                        title = "更多",
                        subtitle = "隐私政策、用户协议、信息清单与关于我们",
                        onClick = onNavigateToMore,
                        leadingContent = {
                            SettingsIconCircle(icon = CampusIcons.More)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { extraContent() }
        }
    }
}

// ────────────────────────────────────────────────
// 辅助组件
// ────────────────────────────────────────────────

@Composable
private fun SettingsSectionCard(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column {
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeConfigSelector(
    selected: DarkThemeConfig,
    onSelected: (DarkThemeConfig) -> Unit,
) {
    val options = listOf(
        DarkThemeConfig.FOLLOW_SYSTEM to "系统",
        DarkThemeConfig.LIGHT to "浅色",
        DarkThemeConfig.DARK to "深色",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option.first,
                onClick = { onSelected(option.first) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                label = { Text(option.second) },
            )
        }
    }
}

@Composable
private fun SettingsIconCircle(
    icon: ImageVector,
) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
