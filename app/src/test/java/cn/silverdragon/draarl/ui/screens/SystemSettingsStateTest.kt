package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.structuralEqualityPolicy
import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import cn.silverdragon.draarl.radio.TransmitTailTone
import cn.silverdragon.draarl.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SystemSettingsStateTest {
    @Test
    fun `slider updates do not invalidate the system settings root observer`() {
        val settings = mutableStateOf(SettingsUiState())
        val state = derivedStateOf(structuralEqualityPolicy()) {
            SystemSettingsRootState.from(settings.value)
        }
        val observer = SnapshotStateObserver { command -> command() }
        var invalidations = 0

        observer.start()
        try {
            observer.observeReads(Unit, { invalidations += 1 }) { state.value }

            settings.value = settings.value.copy(
                transmitTimeoutSeconds = 360,
                playbackDenoiseStrengthPercent = 85
            )
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            settings.value = settings.value.copy(appThemeMode = AppThemeMode.DARK)
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
        }
    }

    @Test
    fun `root state ignores slider and unrelated controller fields`() {
        val state = SettingsUiState()
        val highFrequencyUpdate = state.copy(
            transmitTimeoutSeconds = 360,
            playbackDenoiseStrengthPercent = 85,
            muted = true,
            playbackDenoiseEnabled = true,
            storageBusy = true
        )

        assertEquals(SystemSettingsRootState.from(state), SystemSettingsRootState.from(highFrequencyUpdate))
    }

    @Test
    fun `root state follows settings rendered outside slider scopes`() {
        val state = SettingsUiState()
        val updates = listOf(
            state.copy(appDisplayScale = AppDisplayScale.COMFORTABLE),
            state.copy(appThemeMode = AppThemeMode.DARK),
            state.copy(transmitTailTone = TransmitTailTone.SHORT_BEEP),
            state.copy(transmitTailToneToRemoteEnabled = false),
            state.copy(receiveTailToneEnabled = true),
            state.copy(pttOverlayEnabled = true),
            state.copy(autoCheckAppUpdate = false)
        )

        updates.forEach { update ->
            assertNotEquals(SystemSettingsRootState.from(state), SystemSettingsRootState.from(update))
        }
    }
}
