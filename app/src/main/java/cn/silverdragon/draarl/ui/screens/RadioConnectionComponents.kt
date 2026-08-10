package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.formatRadioIdentifiers
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.radio.session.RadioSessionUiState
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
internal fun ConnectionPanel(controller: AppController, onToggleDevices: () -> Unit) {
    var accessMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var routingMenu by remember { mutableStateOf(false) }
    val groupNames by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { groupNamesById(controller.groups) }
    }

    if (accessMenu) {
        ControllerAccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    }
    if (groupMenu) {
        ControllerGroupDialog(controller = controller, onDismiss = { groupMenu = false })
    }
    if (routingMenu) {
        ControllerRoutingDialog(controller = controller, onDismiss = { routingMenu = false })
    }

    val panelState by remember(controller, groupNames) {
        derivedStateOf(structuralEqualityPolicy()) {
            radioConnectionPanelState(
                controller = controller,
                sessionState = controller.radioSession.uiState,
                groupNames = groupNames
            )
        }
    }
    RadioConnectionPanel(
        state = panelState,
        audioLevel = { modifier ->
            ControllerAudioLevelMeter(
                controller = controller,
                receiving = panelState.receiving,
                transmitting = panelState.transmitting,
                modifier = modifier
            )
        },
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
private fun ControllerAccessPointDialog(controller: AppController, onDismiss: () -> Unit) {
    val state by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) {
            AccessPointDialogState.from(controller.radioSession.uiState)
        }
    }
    AccessPointDialog(
        state = state,
        onSelect = controller.radioSession::selectAccessPoint,
        onDismiss = onDismiss
    )
}

@Composable
private fun ControllerGroupDialog(controller: AppController, onDismiss: () -> Unit) {
    GroupDialog(
        groups = controller.groups,
        selectedGroupId = controller.radioSession.uiState.selectedGroupId,
        onSelect = controller.radioSession::switchGroup,
        onDismiss = onDismiss
    )
}

@Composable
private fun ControllerRoutingDialog(controller: AppController, onDismiss: () -> Unit) {
    val state by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) {
            RoutingDialogState.from(controller.radioSession.uiState)
        }
    }
    RoutingDialog(
        groups = controller.groups,
        state = state,
        onApply = controller.radioSession::updateRouting,
        onDismiss = onDismiss
    )
}

private fun radioConnectionPanelState(
    controller: AppController,
    sessionState: RadioSessionUiState,
    groupNames: Map<Int, String>
): RadioConnectionPanelState {
    val status = sessionState.status
    val user = controller.session.uiState.user
    val radioSettings = controller.settings.uiState
    val callsign = user?.let { it.callsign.ifBlank { it.displayName } }.orEmpty().ifBlank { "DraARL" }
    val receivingAudio = status.speaker.isNotBlank() ||
        controller.playingMessageId != null ||
        controller.playbackLevel >= 0.02f
    return RadioConnectionPanelState(
        strip = RadioStatusStripState(
            stationIdentity = formatRadioIdentity(callsign, status.ssid),
            radioIdentifiers = formatRadioIdentifiers(user?.mdcId.orEmpty(), user?.dmrId ?: 0),
            connectionText =
                "${connectionText(status.phase)} · ${sessionState.selectedAccessPoint?.displayName ?: "选择节点"}",
            connectionTone = connectionTone(status.phase),
            nodeSelectionEnabled =
                sessionState.accessPoints.isNotEmpty() && status.phase !in NODE_SELECTION_BLOCKED_PHASES,
            onlineCount = controller.onlineDevices.size,
            receiving = receivingAudio,
            transmitting = status.transmitting,
            denoiseEnabled = radioSettings.playbackDenoiseEnabled,
            muted = radioSettings.muted,
            sendChannel = groupNames[sessionState.selectedGroupId] ?: "群组 ${sessionState.selectedGroupId}",
            sendChannelEnabled = groupNames.isNotEmpty() && !sessionState.routingUpdating,
            receiveChannelCount = sessionState.receiveGroupIds.size,
            receiveChannelsEnabled =
                groupNames.isNotEmpty() && status.connected && !sessionState.routingUpdating,
            speaker = status.speaker,
            error = status.error
        ),
        avatarUrl = user?.avatarUrl.orEmpty(),
        receiving = receivingAudio,
        transmitting = status.transmitting
    )
}

@Composable
private fun ControllerAudioLevelMeter(
    controller: AppController,
    receiving: Boolean,
    transmitting: Boolean,
    modifier: Modifier = Modifier
) {
    RadioAudioLevelMeter(
        receiveLevel = controller.playbackLevel,
        transmitLevel = controller.transmitLevel,
        receiving = receiving,
        transmitting = transmitting,
        modifier = modifier
    )
}

@Composable
internal fun RadioConnectionPanel(
    state: RadioConnectionPanelState,
    onAction: (RadioConnectionPanelAction) -> Unit,
    audioLevel: @Composable (Modifier) -> Unit
) {
    RadioStatusStrip(
        state = state.strip,
        avatar = { UserAvatar(state.avatarUrl, Modifier.size(40.dp)) },
        audioLevel = audioLevel,
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
