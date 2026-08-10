package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import cn.silverdragon.draarl.radio.TransmitTailTone
import cn.silverdragon.draarl.settings.SettingsUiState

@Immutable
internal data class SystemSettingsRootState(
    val appDisplayScale: AppDisplayScale,
    val appThemeMode: AppThemeMode,
    val transmitTailTone: TransmitTailTone,
    val transmitTailToneToRemoteEnabled: Boolean,
    val receiveTailToneEnabled: Boolean,
    val pttOverlayEnabled: Boolean,
    val autoCheckAppUpdate: Boolean
) {
    companion object {
        fun from(state: SettingsUiState): SystemSettingsRootState = SystemSettingsRootState(
            appDisplayScale = state.appDisplayScale,
            appThemeMode = state.appThemeMode,
            transmitTailTone = state.transmitTailTone,
            transmitTailToneToRemoteEnabled = state.transmitTailToneToRemoteEnabled,
            receiveTailToneEnabled = state.receiveTailToneEnabled,
            pttOverlayEnabled = state.pttOverlayEnabled,
            autoCheckAppUpdate = state.autoCheckAppUpdate
        )
    }
}
