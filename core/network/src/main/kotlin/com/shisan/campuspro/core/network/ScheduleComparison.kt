package com.shisan.campuspro.core.network

import com.shisan.campuspro.core.model.Course

data class NormalizedCourse(
    val name: String,
    val day: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weeks: String,
    val teacher: String,
    val location: String,
)

data class ScheduleDiff(
    val onlyInPortal: List<NormalizedCourse>,
    val onlyInJwxtDirect: List<NormalizedCourse>,
) {
    val isEmpty: Boolean = onlyInPortal.isEmpty() && onlyInJwxtDirect.isEmpty()

    fun redactedSummary(): String =
        "portalOnly=${onlyInPortal.size}, jwxtOnly=${onlyInJwxtDirect.size}, " +
            "portalSample=${onlyInPortal.take(3)}, jwxtSample=${onlyInJwxtDirect.take(3)}"
}

object ScheduleComparison {
    fun normalize(course: Course): NormalizedCourse =
        NormalizedCourse(
            name = course.name.cleanText(),
            day = course.day,
            startSlot = course.startSlot,
            endSlot = course.endSlot,
            weeks = course.weeks.sorted().joinToString(","),
            teacher = course.teacher.cleanText(),
            location = course.location.cleanText(),
        )

    fun diff(
        portalCourses: List<Course>,
        jwxtDirectCourses: List<Course>,
    ): ScheduleDiff {
        val portal = portalCourses.map(::normalize).toSet()
        val direct = jwxtDirectCourses.map(::normalize).toSet()
        return ScheduleDiff(
            onlyInPortal = (portal - direct).sortedCourseKeys(),
            onlyInJwxtDirect = (direct - portal).sortedCourseKeys(),
        )
    }

    private fun String.cleanText(): String =
        trim()
            .replace(Regex("\\s+"), "")
            .replace("，", ",")

    private fun Set<NormalizedCourse>.sortedCourseKeys(): List<NormalizedCourse> =
        sortedWith(
            compareBy<NormalizedCourse> { it.day }
                .thenBy { it.startSlot }
                .thenBy { it.name }
                .thenBy { it.location },
        )
}
