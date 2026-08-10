package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.structuralEqualityPolicy
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.radio.AccessPointProbe
import cn.silverdragon.draarl.radio.session.RadioSessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RadioSelectionDialogStateTest {
    @Test
    fun `unrelated radio updates do not invalidate the access point dialog observer`() {
        val point = accessPoint("edge-a")
        val session = mutableStateOf(
            RadioSessionUiState(
                accessPoints = listOf(point),
                accessPointProbes = listOf(AccessPointProbe(point, 42))
            )
        )
        val state = derivedStateOf(structuralEqualityPolicy()) {
            AccessPointDialogState.from(session.value)
        }
        val observer = SnapshotStateObserver { command -> command() }
        var invalidations = 0

        observer.start()
        try {
            observer.observeReads(Unit, { invalidations += 1 }) { state.value }

            session.value = session.value.copy(
                selectedGroupId = 73,
                routingUpdating = true,
                status = session.value.status.copy(
                    transmitting = true,
                    speaker = "BH1ABC",
                    error = "transient notice"
                )
            )
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            session.value = session.value.copy(selectedAccessPoint = point)
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
        }
    }

    @Test
    fun `access point dialog state follows rendered node fields`() {
        val point = accessPoint("edge-a")
        val state = RadioSessionUiState()

        assertNotEquals(
            AccessPointDialogState.from(state),
            AccessPointDialogState.from(state.copy(accessPoints = listOf(point)))
        )
        assertNotEquals(
            AccessPointDialogState.from(state),
            AccessPointDialogState.from(state.copy(accessPointProbes = listOf(AccessPointProbe(point, 42))))
        )
        assertNotEquals(
            AccessPointDialogState.from(state),
            AccessPointDialogState.from(state.copy(selectedAccessPoint = point))
        )
    }

    @Test
    fun `unrelated radio updates do not invalidate the routing dialog observer`() {
        val point = accessPoint("edge-a")
        val session = mutableStateOf(
            RadioSessionUiState(
                selectedGroupId = 999,
                receiveGroupIds = setOf(999),
                status = RadioStatus(
                    phase = RadioConnectionPhase.CONNECTED,
                    sessionId = "session-a",
                    endpoint = "radio-a.example.com"
                )
            )
        )
        val state = derivedStateOf(structuralEqualityPolicy()) {
            RoutingDialogState.from(session.value)
        }
        val observer = SnapshotStateObserver { command -> command() }
        var invalidations = 0

        observer.start()
        try {
            observer.observeReads(Unit, { invalidations += 1 }) { state.value }

            session.value = session.value.copy(
                accessPoints = listOf(point),
                accessPointProbes = listOf(AccessPointProbe(point, 42)),
                status = session.value.status.copy(
                    endpoint = "radio-b.example.com",
                    transmitting = true,
                    speaker = "BH1ABC",
                    error = "transient notice"
                )
            )
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            session.value = session.value.copy(receiveGroupIds = setOf(999, 73))
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
        }
    }

    @Test
    fun `routing dialog state follows rendered routing fields`() {
        val state = RadioSessionUiState()

        assertNotEquals(
            RoutingDialogState.from(state),
            RoutingDialogState.from(state.copy(selectedGroupId = 73))
        )
        assertNotEquals(
            RoutingDialogState.from(state),
            RoutingDialogState.from(state.copy(receiveGroupIds = setOf(73)))
        )
        assertNotEquals(
            RoutingDialogState.from(state),
            RoutingDialogState.from(state.copy(status = state.status.copy(sessionId = "session-a")))
        )
        assertNotEquals(
            RoutingDialogState.from(state),
            RoutingDialogState.from(
                state.copy(status = state.status.copy(phase = RadioConnectionPhase.CONNECTED))
            )
        )
        assertNotEquals(
            RoutingDialogState.from(state),
            RoutingDialogState.from(state.copy(routingUpdating = true))
        )
    }

    private fun accessPoint(id: String) = AccessPoint(
        id = id,
        displayName = "杭州边缘节点",
        host = "$id.example.com",
        port = 9000
    )
}
