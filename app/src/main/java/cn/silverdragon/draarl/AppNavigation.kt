package cn.silverdragon.draarl

enum class AppPage {
    DEVICES,
    GROUPS,
    RADIO,
    TOOLS,
    PROFILE,
    SETTINGS,
    ACCOUNT_SECURITY,
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
)

internal fun authenticatedStartPage(isApproved: Boolean): AppPage =
    if (isApproved) AppPage.RADIO else AppPage.PROFILE

internal fun pagePosition(page: AppPage): Int {
    val mainIndex = MAIN_NAVIGATION_PAGES.indexOf(page)
    if (mainIndex >= 0) return mainIndex
    return when (page) {
        AppPage.SETTINGS -> MAIN_NAVIGATION_PAGES.size
        AppPage.ACCOUNT_SECURITY -> MAIN_NAVIGATION_PAGES.size + 1
        else -> error("Main navigation page is missing from MAIN_NAVIGATION_PAGES: $page")
    }
}
