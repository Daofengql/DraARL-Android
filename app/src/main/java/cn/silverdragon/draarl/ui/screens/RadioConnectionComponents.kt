package cn.silverdragon.draarl.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.formatRadioIdentifiers
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.ui.components.RadioAudioLevelMeter
import cn.silverdragon.draarl.ui.components.RadioStatusStrip
import cn.silverdragon.draarl.ui.components.RadioStatusStripState
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.components.UserAvatar
import cn.silverdragon.draarl.ui.state.groupNamesById

@Composable
internal fun ConnectionPanel(
    controller: AppController,
    status: RadioStatus,
    onToggleDevices: () -> Unit,
) {
    var accessMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var routingMenu by remember { mutableStateOf(false) }
    val groupNames = remember(controller.groups) { groupNamesById(controller.groups) }
    val user = controller.user
    val callsign = user?.let { it.callsign.ifBlank { it.displayName } }.orEmpty().ifBlank { "DraARL" }
    val receivingAudio = status.speaker.isNotBlank() || controller.playingMessageId != null

    if (accessMenu) AccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    if (groupMenu) GroupDialog(controller = controller, onDismiss = { groupMenu = false })
    if (routingMenu) RoutingDialog(controller = controller, onDismiss = { routingMenu = false })

    RadioStatusStrip(
        state = RadioStatusStripState(
            stationIdentity = formatRadioIdentity(callsign, status.ssid),
            radioIdentifiers = formatRadioIdentifiers(user?.mdcId.orEmpty(), user?.dmrId ?: 0),
            connectionText = "${connectionText(status.phase)} · ${controller.selectedAccessPoint?.displayName ?: "选择节点"}",
            connectionTone = connectionTone(status.phase),
            nodeSelectionEnabled = controller.accessPoints.isNotEmpty() && status.phase !in NODE_SELECTION_BLOCKED_PHASES,
            onlineCount = controller.onlineDevices.size,
            receiving = receivingAudio,
            transmitting = status.transmitting,
            denoiseEnabled = controller.playbackDenoiseEnabled,
            muted = controller.muted,
            sendChannel = groupNames[controller.selectedGroupId] ?: "群组 ${controller.selectedGroupId}",
            sendChannelEnabled = controller.groups.isNotEmpty() && !controller.radioRoutingUpdating,
            receiveChannelCount = controller.receiveGroupIds.size,
            receiveChannelsEnabled = controller.groups.isNotEmpty() && status.connected && !controller.radioRoutingUpdating,
            speaker = status.speaker,
            error = status.error,
        ),
        avatar = { UserAvatar(user?.avatarUrl.orEmpty(), Modifier.size(40.dp)) },
        audioLevel = { modifier ->
            ControllerAudioLevelMeter(
                controller = controller,
                receiving = receivingAudio,
                transmitting = status.transmitting,
                modifier = modifier,
            )
        },
        onSelectNode = { accessMenu = true },
        onShowOnlineDevices = onToggleDevices,
        onToggleDenoise = controller::togglePlaybackDenoise,
        onToggleMuted = controller::toggleMuted,
        onSelectSendChannel = { groupMenu = true },
        onSelectReceiveChannels = { routingMenu = true },
    )
}

@Composable
private fun ControllerAudioLevelMeter(
    controller: AppController,
    receiving: Boolean,
    transmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    RadioAudioLevelMeter(
        receiveLevel = controller.playbackLevel,
        transmitLevel = controller.transmitLevel,
        receiving = receiving,
        transmitting = transmitting,
        modifier = modifier,
    )
}

private fun connectionTone(phase: RadioConnectionPhase): StatusTone = when (phase) {
    RadioConnectionPhase.CONNECTED -> StatusTone.CONNECTED
    RadioConnectionPhase.CONNECTING,
    RadioConnectionPhase.AUTHENTICATING,
    RadioConnectionPhase.RECONNECTING,
    RadioConnectionPhase.DISCOVERING,
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
    RadioConnectionPhase.RECONNECTING,
)
