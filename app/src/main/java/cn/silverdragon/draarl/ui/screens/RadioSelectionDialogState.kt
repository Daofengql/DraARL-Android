package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.radio.AccessPointProbe
import cn.silverdragon.draarl.radio.session.RadioSessionUiState

@Immutable
internal data class AccessPointDialogState(
    val accessPoints: List<AccessPoint>,
    val probes: List<AccessPointProbe>,
    val selectedAccessPointId: String?
) {
    companion object {
        fun from(state: RadioSessionUiState): AccessPointDialogState = AccessPointDialogState(
            accessPoints = state.accessPoints,
            probes = state.accessPointProbes,
            selectedAccessPointId = state.selectedAccessPoint?.id
        )
    }
}

@Immutable
internal data class RoutingDialogState(
    val primaryGroupId: Int,
    val receiveGroupIds: Set<Int>,
    val sessionId: String,
    val connected: Boolean,
    val updating: Boolean
) {
    companion object {
        fun from(state: RadioSessionUiState): RoutingDialogState = RoutingDialogState(
            primaryGroupId = state.selectedGroupId,
            receiveGroupIds = state.receiveGroupIds,
            sessionId = state.status.sessionId,
            connected = state.status.connected,
            updating = state.routingUpdating
        )
    }
}
