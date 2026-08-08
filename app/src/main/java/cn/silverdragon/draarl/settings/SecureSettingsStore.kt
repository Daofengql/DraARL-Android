package cn.silverdragon.draarl.settings

import cn.silverdragon.draarl.data.SecureSessionStore

internal class SecureSettingsStore(private val sessionStore: SecureSessionStore) : SettingsStore {
    override fun load(canDrawPttOverlay: Boolean): SettingsUiState = SettingsUiState(
        muted = sessionStore.isMuted(),
        playbackDenoiseEnabled = sessionStore.isPlaybackDenoiseEnabled(),
        playbackDenoiseStrengthPercent = sessionStore.playbackDenoiseStrengthPercent(),
        pttOverlayEnabled = sessionStore.isPttOverlayEnabled() && canDrawPttOverlay,
        appDisplayScale = sessionStore.appDisplayScale(),
        appThemeMode = sessionStore.appThemeMode(),
        transmitTimeoutSeconds = sessionStore.transmitTimeoutSeconds(),
        transmitTailTone = sessionStore.transmitTailTone(),
        transmitTailToneToRemoteEnabled = sessionStore.isTransmitTailToneToRemoteEnabled(),
        receiveTailToneEnabled = sessionStore.isReceiveTailToneEnabled(),
        autoCheckAppUpdate = sessionStore.isAutoCheckAppUpdateEnabled()
    )

    override fun write(change: SettingsChange) {
        when (change) {
            is SettingsChange.Muted -> sessionStore.setMuted(change.value)

            is SettingsChange.PlaybackDenoiseEnabled -> sessionStore.setPlaybackDenoiseEnabled(
                change.value
            )

            is SettingsChange.PlaybackDenoiseStrength -> {
                sessionStore.setPlaybackDenoiseStrengthPercent(change.value)
            }

            is SettingsChange.PttOverlayEnabled -> sessionStore.setPttOverlayEnabled(change.value)

            is SettingsChange.DisplayScale -> sessionStore.setAppDisplayScale(change.value)

            is SettingsChange.ThemeMode -> sessionStore.setAppThemeMode(change.value)

            is SettingsChange.TransmitTimeout -> sessionStore.setTransmitTimeoutSeconds(
                change.value
            )

            is SettingsChange.TailTone -> sessionStore.setTransmitTailTone(change.value)

            is SettingsChange.TailToneToRemote -> sessionStore.setTransmitTailToneToRemoteEnabled(
                change.value
            )

            is SettingsChange.ReceiveTailTone -> sessionStore.setReceiveTailToneEnabled(
                change.value
            )

            is SettingsChange.AutoCheckAppUpdate -> sessionStore.setAutoCheckAppUpdateEnabled(
                change.value
            )
        }
    }
}
