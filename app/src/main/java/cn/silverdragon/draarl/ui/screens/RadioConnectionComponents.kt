package cn.silverdragon.draarl.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.formatRadioIdentifiers
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.ui.components.UserAvatar
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.StatusIndicator
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.appMotion
import cn.silverdragon.draarl.ui.theme.dataTypography
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun ConnectionPanel(
    controller: AppController,
    status: RadioStatus,
    onToggleDevices: () -> Unit,
) {
    var accessMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var routingMenu by remember { mutableStateOf(false) }
    val selectedGroup = controller.groups.firstOrNull { it.id == controller.selectedGroupId }
    val user = controller.user
    val callsign = user?.let { it.callsign.ifBlank { it.displayName } }.orEmpty().ifBlank { "DraARL" }
    val stationIdentity = formatRadioIdentity(callsign, status.ssid)
    val radioIdentifiers = formatRadioIdentifiers(user?.mdcId.orEmpty(), user?.dmrId ?: 0)
    if (accessMenu) AccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    if (groupMenu) GroupDialog(controller = controller, onDismiss = { groupMenu = false })
    if (routingMenu) RoutingDialog(controller = controller, onDismiss = { routingMenu = false })
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(user?.avatarUrl.orEmpty(), Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        stationIdentity,
                        style = MaterialTheme.dataTypography.identity,
                        maxLines = 1,
                    )
                    if (radioIdentifiers.isNotBlank()) {
                        Text(
                            radioIdentifiers,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Row(
                        modifier = Modifier.clickable(
                            enabled = controller.accessPoints.isNotEmpty() && status.phase !in setOf(
                                RadioConnectionPhase.CONNECTING,
                                RadioConnectionPhase.AUTHENTICATING,
                                RadioConnectionPhase.RECONNECTING,
                            ),
                        ) { accessMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusIndicator(
                            text = "${connectionText(status.phase)} · ${controller.selectedAccessPoint?.displayName ?: "选择节点"}",
                            tone = connectionTone(status.phase),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择边缘节点", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${controller.onlineDevices.size} 在线",
                        modifier = Modifier.clickable(onClick = onToggleDevices).padding(vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.statusConnected,
                    )
                }
                val receivingAudio = status.speaker.isNotBlank() || controller.playingMessageId != null
                Column(
                    modifier = Modifier.widthIn(min = 72.dp, max = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        when {
                            status.transmitting -> "TX"
                            receivingAudio -> "RX"
                            else -> "RX / TX"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.transmitting) MaterialTheme.appColors.transmit else MaterialTheme.appColors.receive,
                    )
                    AudioLevelMeter(
                        receiveLevel = controller.playbackLevel,
                        transmitLevel = controller.transmitLevel,
                        receiving = receivingAudio,
                        transmitting = status.transmitting,
                        modifier = Modifier.fillMaxWidth().height(18.dp),
                    )
                }
                CommandIconButton(
                    onClick = controller::togglePlaybackDenoise,
                    contentDescription = if (controller.playbackDenoiseEnabled) {
                        "关闭神经网络降噪"
                    } else {
                        "开启神经网络降噪"
                    },
                    icon = Icons.Default.GraphicEq,
                    selected = controller.playbackDenoiseEnabled,
                )
                CommandIconButton(
                    onClick = controller::toggleMuted,
                    contentDescription = if (controller.muted) "开启接收音频" else "关闭接收音频",
                    icon = if (controller.muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    danger = controller.muted,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CommandButton(
                    label = "发送/日志",
                    supportingText = selectedGroup?.name ?: "群组 ${controller.selectedGroupId}",
                    leadingIcon = Icons.Default.Mic,
                    trailingIcon = Icons.Default.KeyboardArrowDown,
                    onClick = { groupMenu = true },
                    modifier = Modifier.weight(1f),
                    enabled = controller.groups.isNotEmpty() && !controller.radioRoutingUpdating,
                )
                CommandButton(
                    label = "收听频道",
                    supportingText = "${controller.receiveGroupIds.size} 个频道",
                    leadingIcon = Icons.Default.Headphones,
                    trailingIcon = Icons.Default.KeyboardArrowDown,
                    onClick = { routingMenu = true },
                    modifier = Modifier.weight(1f),
                    enabled = controller.groups.isNotEmpty() && status.connected && !controller.radioRoutingUpdating,
                )
            }
            if (status.speaker.isNotBlank()) {
                InlineNotice(
                    text = "${status.speaker} 正在发言",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    tone = StatusTone.RECEIVE,
                )
            }
            if (status.error.isNotBlank()) {
                InlineNotice(
                    text = status.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    tone = StatusTone.ERROR,
                )
            }
            HorizontalDivider(color = MaterialTheme.appColors.divider)
        }
    }
}

@Composable
private fun AudioLevelMeter(
    receiveLevel: Float,
    transmitLevel: Float,
    receiving: Boolean,
    transmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = transmitting || receiving
    val level = if (transmitting) transmitLevel else receiveLevel
    val animatedLevel by animateFloatAsState(
        targetValue = if (active) level.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = MaterialTheme.appMotion.quick),
        label = "audioLevel",
    )
    val primary = if (transmitting) MaterialTheme.appColors.transmit else MaterialTheme.appColors.receive
    val warning = MaterialTheme.appColors.warning
    val error = MaterialTheme.appColors.transmit
    val inactive = MaterialTheme.appColors.divider.copy(alpha = 0.72f)
    Canvas(
        modifier = modifier.semantics {
            contentDescription = when {
                transmitting -> "发送电平 ${(animatedLevel * 100).toInt()}%"
                receiving -> "接收电平 ${(animatedLevel * 100).toInt()}%"
                else -> "当前没有收发音频"
            }
        },
    ) {
        val gap = 2.dp.toPx()
        val segments = (size.width / 6.dp.toPx()).toInt().coerceIn(8, 18)
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        repeat(segments) { index ->
            val progress = (index + 1f) / segments
            val envelope = 0.4f + 0.6f * sin(progress * PI).toFloat()
            val barHeight = size.height * envelope
            val selected = active && animatedLevel >= progress
            val color = if (!selected) {
                inactive
            } else when {
                progress > 0.84f -> error
                progress > 0.66f -> warning
                else -> primary
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * (segmentWidth + gap),
                    y = size.height - barHeight,
                ),
                size = androidx.compose.ui.geometry.Size(segmentWidth, barHeight),
                cornerRadius = CornerRadius(segmentWidth / 2f, segmentWidth / 2f),
            )
        }
    }
}

@Composable
private fun connectionTone(phase: RadioConnectionPhase): StatusTone = when (phase) {
    RadioConnectionPhase.CONNECTED -> StatusTone.CONNECTED
    RadioConnectionPhase.CONNECTING,
    RadioConnectionPhase.AUTHENTICATING,
    RadioConnectionPhase.RECONNECTING,
    RadioConnectionPhase.DISCOVERING -> StatusTone.CONNECTING
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
