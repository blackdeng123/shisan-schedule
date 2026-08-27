package com.shisan.campuspro.core.data

import java.time.LocalDate

object TermPolicy {
    fun currentTermId(today: LocalDate = LocalDate.now()): String {
        val year = today.year
        return if (today.monthValue >= 9) {
            "$year-${year + 1}-1"
        } else {
            "${year - 1}-$year-2"
        }
    }

    fun inferStartDate(termId: String): String {
        val parts = termId.split("-")
        if (parts.size != 3) return "${LocalDate.now().year}-09-01"
        val startYear = parts[0]
        val endYear = parts[1]
        return when (parts[2]) {
            "1" -> "$startYear-09-01"
            "2" -> if (termId == "2025-2026-2") "2026-03-09" else "$endYear-03-01"
            else -> "${LocalDate.now().year}-09-01"
        }
    }
}
