package cn.silverdragon.draarl.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.structuralEqualityPolicy
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.session.SessionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DraarlAppSessionGateTest {
    @Test
    fun `unrelated session changes do not invalidate the routing observer`() {
        val state = mutableStateOf(SessionUiState(initializing = false, authenticated = true))
        val gate = derivedStateOf(structuralEqualityPolicy()) { state.value.sessionGateState() }
        val observer = SnapshotStateObserver { command -> command() }
        var invalidations = 0

        observer.start()
        try {
            observer.observeReads(Unit, { invalidations += 1 }) { gate.value }

            state.value = state.value.copy(loginBusy = true, loginError = "request failed")
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)

            state.value = state.value.copy(authenticated = false)
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
        } finally {
            observer.stop()
        }
    }

    @Test
    fun `session gate ignores fields that do not control routing`() {
        val state = SessionUiState(initializing = false, authenticated = true)
        val profileUpdate = state.copy(
            loginBusy = true,
            loginError = "request failed",
            user = User(id = 1, username = "BH1ABC")
        )

        assertEquals(state.sessionGateState(), profileUpdate.sessionGateState())
    }

    @Test
    fun `session gate follows initialization and authentication`() {
        val state = SessionUiState(initializing = true, authenticated = false)

        assertNotEquals(
            state.sessionGateState(),
            state.copy(initializing = false).sessionGateState()
        )
        assertNotEquals(
            state.sessionGateState(),
            state.copy(authenticated = true).sessionGateState()
        )
    }
}
