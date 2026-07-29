package cn.silverdragon.draarl.data

enum class AppThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    ;

    fun isDark(systemDarkTheme: Boolean): Boolean = when (this) {
        FOLLOW_SYSTEM -> systemDarkTheme
        LIGHT -> false
        DARK -> true
    }
}
