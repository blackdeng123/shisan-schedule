package com.shisan.campuspro.core.data

object CourseColors {
    const val Blue = "blue"
    const val Indigo = "indigo"
    const val Purple = "purple"
    const val Pink = "pink"
    const val Orange = "orange"
    const val Emerald = "emerald"
    const val Cyan = "cyan"
    const val Slate = "slate"
    const val Red = "red"
    const val Amber = "amber"
    const val Teal = "teal"
    const val Brown = "brown"

    val all = listOf(Blue, Indigo, Purple, Pink, Orange, Emerald, Cyan, Slate, Red, Amber, Teal, Brown)

    fun isSupported(value: String): Boolean =
        value in all || Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$").matches(value)
}
