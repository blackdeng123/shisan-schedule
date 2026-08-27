package com.shisan.campuspro.core.model

/**
 * 教务数据更新差异摘要，供通知发布使用。
 */
data class AcademicUpdateDiff(
    val addedCount: Int,
    val updatedCount: Int,
    private val subject: String,
) {
    val hasChanges: Boolean = addedCount > 0 || updatedCount > 0
    val summary: String
        get() = buildList {
            if (addedCount > 0) add("$addedCount 条新$subject")
            if (updatedCount > 0) add("$updatedCount 条${subject}已更新")
        }.joinToString("，").let { if (it.isBlank()) "没有更新" else "有 $it" }
}
