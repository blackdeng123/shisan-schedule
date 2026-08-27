package com.shisan.campuspro.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.Grade

// ────────────────────────────────────────────────
// 统计概览卡片
// ────────────────────────────────────────────────

data class StatsItem(
    val value: String,
    val label: String,
)

@Composable
fun StatsCard(
    items: List<StatsItem>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────
// 成绩卡片
// ────────────────────────────────────────────────

@Composable
fun GradeCard(grade: Grade, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = grade.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (grade.type.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                text = grade.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = "${grade.credits} 学分",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = grade.scoreText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "GPA ${grade.gpa}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────
// 考试卡片
// ────────────────────────────────────────────────

@Composable
fun ExamCard(exam: Exam, modifier: Modifier = Modifier) {
    val countdownInfo = remember(exam.daysLeft) { formatExamCountdown(exam.daysLeft) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：倒计时
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp),
            ) {
                Text(
                    text = countdownInfo.first,
                    style = if (exam.daysLeft < 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = countdownInfo.second,
                    textAlign = TextAlign.Center,
                )
                if (exam.daysLeft >= 0) {
                    Text(
                        text = "天",
                        style = MaterialTheme.typography.labelSmall,
                        color = countdownInfo.second.copy(alpha = 0.7f),
                    )
                }
            }
            // 右侧：详情
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (exam.type.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                text = exam.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 地点
                if (exam.location.isNotBlank()) {
                    Text(
                        text = "📍 ${exam.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 时间
                if (exam.date.isNotBlank()) {
                    Text(
                        text = "🕐 ${formatExamDate(exam.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 格式化考试倒计时
 * @return Pair(显示文字, 颜色)
 */
private fun formatExamCountdown(daysLeft: Int): Pair<String, Color> {
    return when {
        daysLeft < 0 -> "已结束" to Color(0xFF94A3B8)       // 灰色
        daysLeft == 0 -> "今天" to Color(0xFFEF4444)         // 红色
        daysLeft == 1 -> "明天" to Color(0xFFEF4444)         // 红色
        daysLeft in 2..3 -> "$daysLeft" to Color(0xFFF97316) // 橙色
        daysLeft in 4..7 -> "$daysLeft" to Color(0xFFF59E0B) // 琥珀色
        else -> "$daysLeft" to Color(0xFF3B82F6)             // 主色
    }
}

/**
 * 格式化考试日期字段
 * 从原始字符串中提取日期和时间，格式化为 "M月d日 HH:mm"
 */
private fun formatExamDate(raw: String): String {
    // 尝试匹配 "YYYY-MM-DD HH:MM" 或 "YYYY/MM/DD HH:MM"
    val fullMatch = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})").find(raw)
    if (fullMatch != null) {
        val (year, month, day, hour, minute) = fullMatch.destructured
        return "${month.toInt()}月${day.toInt()}日 ${hour.padStart(2, '0')}:${minute}"
    }
    // 尝试匹配只有日期 "YYYY-MM-DD"
    val dateMatch = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").find(raw)
    if (dateMatch != null) {
        val (_, month, day) = dateMatch.destructured
        return "${month.toInt()}月${day.toInt()}日"
    }
    // 无法解析，返回原始文本
    return raw
}

// ────────────────────────────────────────────────
// 设置列表项
// ────────────────────────────────────────────────

@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        modifier = modifier.clickable(onClick = onClick),
    )
}

// ────────────────────────────────────────────────
// Snackbar 辅助
// ────────────────────────────────────────────────

@Composable
fun SyncStatusSnackbar(
    hostState: SnackbarHostState,
    message: String?,
) {
    LaunchedEffect(message) {
        if (message != null) hostState.showSnackbar(message)
    }
}
