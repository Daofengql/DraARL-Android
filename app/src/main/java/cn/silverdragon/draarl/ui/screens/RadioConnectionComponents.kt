package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.formatRadioIdentifiers
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.settings.SettingsEvent
import cn.silverdragon.draarl.ui.components.RadioAudioLevelMeter
import cn.silverdragon.draarl.ui.components.RadioStatusStrip
import cn.silverdragon.draarl.ui.components.RadioStatusStripState
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.components.UserAvatar
import cn.silverdragon.draarl.ui.state.groupNamesById

internal data class RadioConnectionPanelState(
    val strip: RadioStatusStripState,
    val avatarUrl: String,
    val receiveLevel: Float,
    val transmitLevel: Float,
    val receiving: Boolean,
    val transmitting: Boolean
)

internal enum class RadioConnectionPanelAction {
    SELECT_NODE,
    SHOW_ONLINE_DEVICES,
    TOGGLE_DENOISE,
    TOGGLE_MUTED,
    SELECT_SEND_CHANNEL,
    SELECT_RECEIVE_CHANNELS
}

@Composable
internal fun ConnectionPanel(controller: AppController, status: RadioStatus, onToggleDevices: () -> Unit) {
    var accessMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var routingMenu by remember { mutableStateOf(false) }
    val groupNames = remember(controller.groups) { groupNamesById(controller.groups) }
    val user = controller.user
    val radioSettings by remember {
        derivedStateOf {
            controller.settings.uiState.let { state -> state.playbackDenoiseEnabled to state.muted }
        }
    }
    val callsign = user?.let {
        it.callsign.ifBlank { it.displayName }
    }.orEmpty().ifBlank { "DraARL" }
    val receivingAudio = status.speaker.isNotBlank() || controller.playingMessageId != null

    if (accessMenu) AccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    if (groupMenu) GroupDialog(controller = controller, onDismiss = { groupMenu = false })
    if (routingMenu) RoutingDialog(controller = controller, onDismiss = { routingMenu = false })

    RadioConnectionPanel(
        state = RadioConnectionPanelState(
            strip = RadioStatusStripState(
                stationIdentity = formatRadioIdentity(callsign, status.ssid),
                radioIdentifiers = formatRadioIdentifiers(user?.mdcId.orEmpty(), user?.dmrId ?: 0),
                connectionText = "${connectionText(
                    status.phase
                )} · ${controller.selectedAccessPoint?.displayName ?: "选择节点"}",
                connectionTone = connectionTone(status.phase),
                nodeSelectionEnabled =
                    controller.accessPoints.isNotEmpty() &&
                        status.phase !in NODE_SELECTION_BLOCKED_PHASES,
                onlineCount = controller.onlineDevices.size,
                receiving = receivingAudio,
                transmitting = status.transmitting,
                denoiseEnabled = radioSettings.first,
                muted = radioSettings.second,
                sendChannel =
                    groupNames[controller.selectedGroupId] ?: "群组 ${controller.selectedGroupId}",
                sendChannelEnabled = controller.groups.isNotEmpty() && !controller.radioRoutingUpdating,
                receiveChannelCount = controller.receiveGroupIds.size,
                receiveChannelsEnabled =
                    controller.groups.isNotEmpty() && status.connected &&
                        !controller.radioRoutingUpdating,
                speaker = status.speaker,
                error = status.error
            ),
            avatarUrl = user?.avatarUrl.orEmpty(),
            receiveLevel = controller.playbackLevel,
            transmitLevel = controller.transmitLevel,
            receiving = receivingAudio,
            transmitting = status.transmitting
        ),
        onAction = { action ->
            when (action) {
                RadioConnectionPanelAction.SELECT_NODE -> accessMenu = true

                RadioConnectionPanelAction.SHOW_ONLINE_DEVICES -> onToggleDevices()

                RadioConnectionPanelAction.TOGGLE_DENOISE -> {
                    controller.settings.onEvent(SettingsEvent.TogglePlaybackDenoise)
                }

                RadioConnectionPanelAction.TOGGLE_MUTED -> controller.settings.onEvent(SettingsEvent.ToggleMuted)

                RadioConnectionPanelAction.SELECT_SEND_CHANNEL -> groupMenu = true

                RadioConnectionPanelAction.SELECT_RECEIVE_CHANNELS -> routingMenu = true
            }
        }
    )
}

@Composable
internal fun RadioConnectionPanel(state: RadioConnectionPanelState, onAction: (RadioConnectionPanelAction) -> Unit) {
    RadioStatusStrip(
        state = state.strip,
        avatar = { UserAvatar(state.avatarUrl, Modifier.size(40.dp)) },
        audioLevel = { modifier ->
            RadioAudioLevelMeter(
                receiveLevel = state.receiveLevel,
                transmitLevel = state.transmitLevel,
                receiving = state.receiving,
                transmitting = state.transmitting,
                modifier = modifier
            )
        },
        onSelectNode = { onAction(RadioConnectionPanelAction.SELECT_NODE) },
        onShowOnlineDevices = { onAction(RadioConnectionPanelAction.SHOW_ONLINE_DEVICES) },
        onToggleDenoise = { onAction(RadioConnectionPanelAction.TOGGLE_DENOISE) },
        onToggleMuted = { onAction(RadioConnectionPanelAction.TOGGLE_MUTED) },
        onSelectSendChannel = { onAction(RadioConnectionPanelAction.SELECT_SEND_CHANNEL) },
        onSelectReceiveChannels = { onAction(RadioConnectionPanelAction.SELECT_RECEIVE_CHANNELS) }
    )
}

private fun connectionTone(phase: RadioConnectionPhase): StatusTone = when (phase) {
    RadioConnectionPhase.CONNECTED -> StatusTone.CONNECTED

    RadioConnectionPhase.CONNECTING,
    RadioConnectionPhase.AUTHENTICATING,
    RadioConnectionPhase.RECONNECTING,
    RadioConnectionPhase.DISCOVERING
    -> StatusTone.CONNECTING

    RadioConnectionPhase.ERROR -> StatusTone.ERROR

    else -> StatusTone.NEUTRAL
}

private fun connectionText(phase: RadioConnectionPhase): String = when (phase) {
    RadioConnectionPhase.DISCOVERING -> "正在优选入口"
    RadioConnectionPhase.CONNECTING -> "正在连接"
    RadioConnectionPhase.AUTHENTICATING -> "正在认证"
    RadioConnectionPhase.CONNECTED -> "UDP 已连接"
    RadioConnectionPhase.RECONNECTING -> "连接中断，正在重连"
    RadioConnectionPhase.ERROR -> "连接失败"
    else -> "UDP 未连接"
}

private val NODE_SELECTION_BLOCKED_PHASES = setOf(
    RadioConnectionPhase.CONNECTING,
    RadioConnectionPhase.AUTHENTICATING,
    RadioConnectionPhase.RECONNECTING
)
