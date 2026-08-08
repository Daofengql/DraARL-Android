package cn.silverdragon.draarl.ui.screens

sealed interface SettingsMenuAction {
    data object Back : SettingsMenuAction
    data object OpenAccountSecurity : SettingsMenuAction
    data object OpenSystemSettings : SettingsMenuAction
    data object OpenStorageSettings : SettingsMenuAction
    data object OpenAprsSettings : SettingsMenuAction
    data object Logout : SettingsMenuAction
}
