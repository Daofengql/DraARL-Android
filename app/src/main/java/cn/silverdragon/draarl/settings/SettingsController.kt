package cn.silverdragon.draarl.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.radio.PLAYBACK_DENOISE_MAX_STRENGTH_PERCENT
import cn.silverdragon.draarl.radio.PLAYBACK_DENOISE_MIN_STRENGTH_PERCENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsController internal constructor(
    private val store: SettingsStore,
    private val storage: SettingsStorage,
    private val effects: SettingsEffects,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private var storageJob: Job? = null
    private var closed = false

    var uiState by mutableStateOf(store.load(effects.canDrawPttOverlay()))
        private set

    fun onEvent(event: SettingsEvent) {
        val state = uiState
        val changed = when (event) {
            SettingsEvent.ToggleMuted -> update(
                SettingsChange.Muted(!state.muted),
                state.copy(muted = !state.muted),
                appliesRadioAudio = true
            )

            SettingsEvent.TogglePlaybackDenoise -> update(
                SettingsChange.PlaybackDenoiseEnabled(!state.playbackDenoiseEnabled),
                state.copy(playbackDenoiseEnabled = !state.playbackDenoiseEnabled),
                appliesRadioAudio = true
            )

            is SettingsEvent.PlaybackDenoiseStrengthChanged -> {
                val value = event.percent.coerceIn(
                    PLAYBACK_DENOISE_MIN_STRENGTH_PERCENT,
                    PLAYBACK_DENOISE_MAX_STRENGTH_PERCENT
                )
                update(
                    SettingsChange.PlaybackDenoiseStrength(value),
                    state.copy(playbackDenoiseStrengthPercent = value),
                    appliesRadioAudio = true
                )
            }

            is SettingsEvent.DisplayScaleChanged -> update(
                SettingsChange.DisplayScale(event.scale),
                state.copy(appDisplayScale = event.scale)
            )

            is SettingsEvent.ThemeModeChanged -> update(
                SettingsChange.ThemeMode(event.mode),
                state.copy(appThemeMode = event.mode)
            )

            is SettingsEvent.TransmitTimeoutChanged -> {
                val value = event.seconds.coerceIn(MIN_TRANSMIT_TIMEOUT_SECONDS, MAX_TRANSMIT_TIMEOUT_SECONDS)
                update(
                    SettingsChange.TransmitTimeout(value),
                    state.copy(transmitTimeoutSeconds = value),
                    appliesRadioAudio = true
                )
            }

            is SettingsEvent.TransmitTailToneChanged -> update(
                SettingsChange.TailTone(event.tone),
                state.copy(transmitTailTone = event.tone),
                appliesRadioAudio = true
            )

            is SettingsEvent.TransmitTailToneToRemoteChanged -> update(
                SettingsChange.TailToneToRemote(event.enabled),
                state.copy(transmitTailToneToRemoteEnabled = event.enabled),
                appliesRadioAudio = true
            )

            is SettingsEvent.ReceiveTailToneChanged -> update(
                SettingsChange.ReceiveTailTone(event.enabled),
                state.copy(receiveTailToneEnabled = event.enabled),
                appliesRadioAudio = true
            )

            is SettingsEvent.AutoCheckAppUpdateChanged -> update(
                SettingsChange.AutoCheckAppUpdate(event.enabled),
                state.copy(autoCheckAppUpdate = event.enabled)
            )
        }
        if (changed && event is SettingsEvent.AutoCheckAppUpdateChanged && event.enabled) {
            effects.requestAppUpdateCheck()
        }
    }

    fun setPttOverlayEnabled(enabled: Boolean): Boolean {
        val allowed = when {
            enabled && !effects.isPttOverlayAllowed() -> {
                effects.showNotice("账号审核通过后才能开启悬浮 PTT")
                false
            }

            enabled && !effects.canDrawPttOverlay() -> false

            else -> true
        }
        if (allowed && uiState.pttOverlayEnabled != enabled) {
            uiState = uiState.copy(pttOverlayEnabled = enabled)
            store.write(SettingsChange.PttOverlayEnabled(enabled))
        }
        if (allowed) effects.syncPttOverlay()
        return allowed
    }

    fun canDrawPttOverlay(): Boolean = effects.canDrawPttOverlay()

    fun reconcilePttOverlayPermission() {
        if (uiState.pttOverlayEnabled && !effects.canDrawPttOverlay()) {
            uiState = uiState.copy(pttOverlayEnabled = false)
            store.write(SettingsChange.PttOverlayEnabled(false))
        }
        effects.syncPttOverlay()
    }

    fun refreshStorageUsage() = launchStorageOperation {
        refreshStorageUsageState()
    }

    fun clearStorage(category: StorageCategory) = launchStorageOperation {
        val clearsMessages = category == StorageCategory.MESSAGES || category == StorageCategory.ALL
        if (clearsMessages) effects.beforeMessageCacheClear()
        withContext(ioDispatcher) { storage.clear(category) }
        if (clearsMessages) effects.afterMessageCacheClear()
        refreshStorageUsageState()
    }

    fun close() {
        if (closed) return
        closed = true
        storageJob?.cancel()
        storageJob = null
    }

    private fun applyRadioAudioSettings() {
        effects.applyRadioAudioSettings(uiState.radioAudioSettings())
    }

    private fun update(change: SettingsChange, state: SettingsUiState, appliesRadioAudio: Boolean = false): Boolean {
        val changed = state != uiState
        if (changed) {
            uiState = state
            store.write(change)
            if (appliesRadioAudio) applyRadioAudioSettings()
        }
        return changed
    }

    private fun launchStorageOperation(operation: suspend () -> Unit) {
        if (closed || storageJob?.isActive == true) return
        uiState = uiState.copy(storageBusy = true)
        storageJob = scope.launch {
            try {
                runCatching { operation() }.onFailure { error ->
                    if (error is CancellationException) throw error
                    effects.showNotice("清理缓存失败：${effects.friendlyError(error)}")
                }
            } finally {
                if (!closed) uiState = uiState.copy(storageBusy = false)
                storageJob = null
            }
        }
    }

    private suspend fun refreshStorageUsageState() {
        val usage = withContext(ioDispatcher) { storage.calculateUsage() }
        if (!closed) uiState = uiState.copy(storageUsage = usage)
    }

    private companion object {
        const val MIN_TRANSMIT_TIMEOUT_SECONDS = 10
        const val MAX_TRANSMIT_TIMEOUT_SECONDS = 600
    }
}
