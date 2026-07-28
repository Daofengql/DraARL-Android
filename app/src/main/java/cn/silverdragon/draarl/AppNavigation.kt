package cn.silverdragon.draarl

enum class AppPage {
    DEVICES,
    GROUPS,
    RADIO,
    TOOLS,
    PROFILE,
    EDIT_PROFILE,
    RADIO_PRESETS,
    SETTINGS,
    SYSTEM_SETTINGS,
    ACCOUNT_SECURITY,
    STORAGE_SETTINGS,
    LOCATION_MAP,
}

internal val MAIN_NAVIGATION_PAGES = listOf(
    AppPage.DEVICES,
    AppPage.GROUPS,
    AppPage.RADIO,
    AppPage.TOOLS,
    AppPage.PROFILE,
)

internal val APPROVAL_REQUIRED_PAGES = setOf(
    AppPage.DEVICES,
    AppPage.GROUPS,
    AppPage.RADIO,
    AppPage.RADIO_PRESETS,
    AppPage.LOCATION_MAP,
)

internal fun pagePosition(page: AppPage): Int {
    val mainIndex = MAIN_NAVIGATION_PAGES.indexOf(page)
    if (mainIndex >= 0) return mainIndex
    return when (page) {
        AppPage.EDIT_PROFILE -> MAIN_NAVIGATION_PAGES.size
        AppPage.RADIO_PRESETS -> MAIN_NAVIGATION_PAGES.size + 1
        AppPage.SETTINGS -> MAIN_NAVIGATION_PAGES.size + 2
        AppPage.SYSTEM_SETTINGS -> MAIN_NAVIGATION_PAGES.size + 3
        AppPage.ACCOUNT_SECURITY -> MAIN_NAVIGATION_PAGES.size + 4
        AppPage.STORAGE_SETTINGS -> MAIN_NAVIGATION_PAGES.size + 5
        AppPage.LOCATION_MAP -> MAIN_NAVIGATION_PAGES.size + 6
        else -> error("Main navigation page is missing from MAIN_NAVIGATION_PAGES: $page")
    }
}
