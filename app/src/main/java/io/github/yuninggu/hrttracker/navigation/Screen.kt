package io.github.yuninggu.hrttracker.navigation

/**
 * 应用导航路由
 */
enum class Screen(val route: String, val title: String) {
    HOME("home", "主页"),
    RECORDS("records", "用药记录"),
    MEDICATION_PLANS("medication_plans", "用药方案"),
    SETTINGS("settings", "设置")
}
