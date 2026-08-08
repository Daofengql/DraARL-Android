package cn.silverdragon.draarl.settings

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.data.StorageUsage
import cn.silverdragon.draarl.radio.TransmitTailTone

@Immutable
data class SettingsUiState(
    val muted: Boolean = false,
    val playbackDenoiseEnabled: Boolean = false,
    val playbackDenoiseStrengthPercent: Int = 50,
    val pttOverlayEnabled: Boolean = false,
    val appDisplayScale: AppDisplayScale = AppDisplayScale.COMPACT,
    val appThemeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val transmitTimeoutSeconds: Int = 120,
    val transmitTailTone: TransmitTailTone = TransmitTailTone.OFF,
    val transmitTailToneToRemoteEnabled: Boolean = true,
    val receiveTailToneEnabled: Boolean = false,
    val autoCheckAppUpdate: Boolean = true,
    val storageUsage: StorageUsage = StorageUsage(),
    val storageBusy: Boolean = false
)

@Immutable
data class RadioAudioSettings(
    val muted: Boolean,
    val playbackDenoiseEnabled: Boolean,
    val playbackDenoiseStrengthPercent: Int,
    val transmitTimeoutSeconds: Int,
    val transmitTailTone: TransmitTailTone,
    val transmitTailToneToRemoteEnabled: Boolean,
    val receiveTailToneEnabled: Boolean
)

sealed interface SettingsEvent {
    data object ToggleMuted : SettingsEvent
    data object TogglePlaybackDenoise : SettingsEvent
    data class PlaybackDenoiseStrengthChanged(val percent: Int) : SettingsEvent
    data class DisplayScaleChanged(val scale: AppDisplayScale) : SettingsEvent
    data class ThemeModeChanged(val mode: AppThemeMode) : SettingsEvent
    data class TransmitTimeoutChanged(val seconds: Int) : SettingsEvent
    data class TransmitTailToneChanged(val tone: TransmitTailTone) : SettingsEvent
    data class TransmitTailToneToRemoteChanged(val enabled: Boolean) : SettingsEvent
    data class ReceiveTailToneChanged(val enabled: Boolean) : SettingsEvent
    data class AutoCheckAppUpdateChanged(val enabled: Boolean) : SettingsEvent
}

internal fun SettingsUiState.radioAudioSettings() = RadioAudioSettings(
    muted = muted,
    playbackDenoiseEnabled = playbackDenoiseEnabled,
    playbackDenoiseStrengthPercent = playbackDenoiseStrengthPercent,
    transmitTimeoutSeconds = transmitTimeoutSeconds,
    transmitTailTone = transmitTailTone,
    transmitTailToneToRemoteEnabled = transmitTailToneToRemoteEnabled,
    receiveTailToneEnabled = receiveTailToneEnabled
)

internal sealed interface SettingsChange {
    data class Muted(val value: Boolean) : SettingsChange
    data class PlaybackDenoiseEnabled(val value: Boolean) : SettingsChange
    data class PlaybackDenoiseStrength(val value: Int) : SettingsChange
    data class PttOverlayEnabled(val value: Boolean) : SettingsChange
    data class DisplayScale(val value: AppDisplayScale) : SettingsChange
    data class ThemeMode(val value: AppThemeMode) : SettingsChange
    data class TransmitTimeout(val value: Int) : SettingsChange
    data class TailTone(val value: TransmitTailTone) : SettingsChange
    data class TailToneToRemote(val value: Boolean) : SettingsChange
    data class ReceiveTailTone(val value: Boolean) : SettingsChange
    data class AutoCheckAppUpdate(val value: Boolean) : SettingsChange
}

internal interface SettingsStore {
    fun load(canDrawPttOverlay: Boolean): SettingsUiState
    fun write(change: SettingsChange)
}

internal interface SettingsStorage {
    fun calculateUsage(): StorageUsage
    fun clear(category: StorageCategory)
}

internal interface SettingsEffects {
    fun isPttOverlayAllowed(): Boolean
    fun canDrawPttOverlay(): Boolean
    fun applyRadioAudioSettings(settings: RadioAudioSettings)
    fun syncPttOverlay()
    fun requestAppUpdateCheck()
    fun beforeMessageCacheClear()
    fun afterMessageCacheClear()
    fun friendlyError(error: Throwable): String
    fun showNotice(message: String)
}
