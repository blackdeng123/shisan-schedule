package com.shisan.campuspro.core.model

/**
 * 学期展示格式化：教务系统的学期编码（如 "2025-2026-1"）直接展示可读性差，
 * 统一转换为"2025-2026学年 第一学期"这类标签；无法识别的格式原样返回，
 * 保证异常学期数据仍可在筛选中显示。
 */

private val SEMESTER_CODE_PATTERN = Regex("""^(\d{4})-(\d{4})-(\d+)$""")

fun formatSemesterLabel(semester: String): String {
    val match = SEMESTER_CODE_PATTERN.find(semester.trim()) ?: return semester
    val (startYear, endYear, termNo) = match.destructured
    val termText = when (termNo.toIntOrNull()) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        else -> termNo
    }
    // 必须用 ${} 包裹：紧跟的中文字符会被 Kotlin 词法器并入标识符导致编译失败。
    return "$startYear-${endYear}学年 第${termText}学期"
}
