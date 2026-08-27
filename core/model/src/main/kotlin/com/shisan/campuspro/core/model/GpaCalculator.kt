package com.shisan.campuspro.core.model

/**
 * 吉首大学教务绩点规则（依据学校官方学业成绩单反推验证）：
 *
 * - 百分制单科绩点 = (成绩 - 60) / 10 + 1，不及格（< 60）绩点为 0；
 * - 等级制单科绩点：优 = 4.5、良 = 3.5、中 = 2.5、及格 = 1.5、不及格 = 0；
 * - 平均学分绩点（总 GPA）= Σ(课程绩点 × 课程学分) / Σ(课程学分)，按学分加权而非简单平均。
 */
object GpaCalculator {

    /** 百分制成绩换算绩点，教务官方口径：绩点一律由原始成绩换算，不直接采信页面绩点列。 */
    fun scoreToGpa(score: Double): Double =
        if (score < 60.0) 0.0 else (score - 60.0) / 10.0 + 1.0

    /** 等级制成绩换算绩点，无法识别的等级返回 null。 */
    fun levelToGpa(scoreText: String): Double? = when {
        "优" in scoreText -> 4.5
        "良" in scoreText -> 3.5
        "中" in scoreText -> 2.5
        // "不及格/不合格"含"及格/合格"子串，必须先判断否定形式，否则会误判为及格档。
        "不及格" in scoreText || "不合格" in scoreText -> 0.0
        "及格" in scoreText || "合格" in scoreText -> 1.5
        else -> null
    }

    /**
     * 平均学分绩点：学分加权平均。零学分课程不参与（避免污染加权结果），
     * 无有效课程时返回 null，由调用方决定展示"—"或其他兜底。
     */
    fun creditWeightedGpa(grades: List<Grade>): Double? {
        val weighted = grades.filter { it.credits > 0.0 }
        val totalCredits = weighted.sumOf { it.credits }
        if (totalCredits <= 0.0) return null
        return weighted.sumOf { it.gpa * it.credits } / totalCredits
    }
}
