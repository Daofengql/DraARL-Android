package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.radio.session.RadioSessionUiState

@Immutable
internal data class MapPttState(val connected: Boolean, val transmitting: Boolean, val receiving: Boolean) {
    companion object {
        fun from(session: RadioSessionUiState): MapPttState = MapPttState(
            connected = session.status.connected,
            transmitting = session.status.transmitting,
            receiving = session.status.speaker.isNotBlank()
        )
    }
}
