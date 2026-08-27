package com.shisan.campuspro.core.network

object JwxtLoginDetector {
    private val successTextMarkers = listOf(
        "个人主页",
        "学生个人中心",
        "mainframe",
        "topframe",
        "leftframe",
        "系统首页",
        "欢迎您",
        "menutree",
        "我的课表",
        "成绩查询",
        "考试安排",
    )

    private val successUrlMarkers = listOf(
        "/main.",
        "/index.",
        "/xsmain.",
        "/framework/",
        "menuid",
    )

    fun isSuccess(text: String, url: String): Boolean {
        val lowerUrl = url.lowercase()
        if (successUrlMarkers.any { lowerUrl.contains(it) }) return true
        val lowerText = text.lowercase()
        return successTextMarkers.any { marker -> lowerText.contains(marker.lowercase()) }
    }

    fun errorMessage(text: String): String? = when {
        text.contains("用户名或密码错误") -> "用户名或密码错误"
        text.contains("验证码错误") -> "需要验证码，请在电脑端登录后再试"
        text.contains("账号被锁定") -> "账号已被锁定，请联系教务处"
        text.contains("不在规定时间段") -> "不在系统开放时间段内"
        else -> null
    }
}
