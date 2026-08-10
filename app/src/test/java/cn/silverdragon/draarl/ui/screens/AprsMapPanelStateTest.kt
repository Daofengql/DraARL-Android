package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.structuralEqualityPolicy
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.radio.session.RadioSessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AprsMapPanelStateTest {
    @Test
    fun `unrelated radio updates do not invalidate the map PTT observer`() {
        val session = mutableStateOf(
            RadioSessionUiState(
                status = RadioStatus(
                    phase = RadioConnectionPhase.CONNECTED,
                    endpoint = "radio-a.example.com",
                    speaker = "BH1ABC"
                )
            )
        )
        val state = derivedStateOf(structuralEqualityPolicy()) { MapPttState.from(session.value) }
        val observer = SnapshotStateObserver { command -> command() }
        var invalidations = 0

        observer.start()
        try {
            observer.observeReads(Unit, { invalidations += 1 }) { state.value }

            session.value = session.value.copy(
                status = session.value.status.copy(
                    endpoint = "radio-b.example.com",
                    speaker = "BH1XYZ",
                    error = "transient notice"
                )
            )
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            session.value = session.value.copy(status = session.value.status.copy(speaker = ""))
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
        }
    }

    @Test
    fun `map PTT state ignores status details unrelated to its controls`() {
        val state = RadioSessionUiState(
            status = RadioStatus(
                phase = RadioConnectionPhase.CONNECTED,
                endpoint = "radio-a.example.com",
                callsign = "BH1ABC",
                speaker = ""
            )
        )
        val detailUpdate = state.copy(
            status = state.status.copy(
                endpoint = "radio-b.example.com",
                callsign = "BH1XYZ",
                error = "transient notice"
            ),
            routingUpdating = true
        )

        assertEquals(MapPttState.from(state), MapPttState.from(detailUpdate))
    }

    @Test
    fun `map PTT state follows connection transmit and receive controls`() {
        val state = RadioSessionUiState(status = RadioStatus())

        assertNotEquals(
            MapPttState.from(state),
            MapPttState.from(state.copy(status = state.status.copy(phase = RadioConnectionPhase.CONNECTED)))
        )
        assertNotEquals(
            MapPttState.from(state),
            MapPttState.from(state.copy(status = state.status.copy(transmitting = true)))
        )
        assertNotEquals(
            MapPttState.from(state),
            MapPttState.from(state.copy(status = state.status.copy(speaker = "BH1ABC")))
        )
    }
}
