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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.formatRadioIdentifiers
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.ui.components.UserAvatar
import cn.silverdragon.draarl.ui.theme.appColors
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
    val selectedGroup = controller.groups.firstOrNull { it.id == controller.selectedGroupId }
    val selectedProbe = controller.accessPointProbes.firstOrNull {
        it.accessPoint.id == controller.selectedAccessPoint?.id
    }
    val user = controller.user
    val callsign = user?.let { it.callsign.ifBlank { it.displayName } }.orEmpty().ifBlank { "DraARL" }
    val stationIdentity = formatRadioIdentity(callsign, status.ssid)
    val radioIdentifiers = formatRadioIdentifiers(user?.mdcId.orEmpty(), user?.dmrId ?: 0)
    if (accessMenu) AccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    if (groupMenu) GroupDialog(controller = controller, onDismiss = { groupMenu = false })
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (status.connected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = connectionColor(status.phase),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            connectionText(status.phase),
                            style = MaterialTheme.typography.bodySmall,
                            color = connectionColor(status.phase),
                            maxLines = 1,
                        )
                        Text(
                            "${controller.onlineDevices.size} 在线",
                            modifier = Modifier.clickable(onClick = onToggleDevices).padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                        color = if (status.transmitting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AudioLevelMeter(
                        receiveLevel = controller.playbackLevel,
                        transmitLevel = controller.transmitLevel,
                        receiving = receivingAudio,
                        transmitting = status.transmitting,
                        modifier = Modifier.fillMaxWidth().height(18.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = controller::togglePlaybackDenoise,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (controller.playbackDenoiseEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (controller.playbackDenoiseEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = if (controller.playbackDenoiseEnabled) {
                            "关闭神经网络降噪"
                        } else {
                            "开启神经网络降噪"
                        },
                    )
                }
                IconButton(onClick = controller::toggleMuted) {
                    Icon(
                        if (controller.muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (controller.muted) "开启接收音频" else "关闭接收音频",
                        tint = if (controller.muted) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).padding(start = 12.dp)) {
                    OutlinedButton(
                        onClick = { accessMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = controller.accessPoints.isNotEmpty() && status.phase !in setOf(
                            RadioConnectionPhase.CONNECTING,
                            RadioConnectionPhase.AUTHENTICATING,
                            RadioConnectionPhase.RECONNECTING,
                        ),
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(
                                controller.selectedAccessPoint?.displayName
                                    ?: if (controller.selectingAccessPoint) "优选边缘中" else "边缘节点",
                                maxLines = 1,
                            )
                            if (controller.selectedAccessPoint != null || selectedProbe != null) {
                                LatencyText(selectedProbe?.latencyMs)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }
                Box(Modifier.weight(1f).padding(end = 12.dp)) {
                    OutlinedButton(
                        onClick = { groupMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = controller.groups.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null)
                        Text(
                            selectedGroup?.name ?: "群组 ${controller.selectedGroupId}",
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            maxLines = 1,
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }
            }
            if (status.speaker.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("${status.speaker} 正在发言", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (status.error.isNotBlank()) {
                Text(
                    status.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
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
        animationSpec = tween(durationMillis = 80),
        label = "audioLevel",
    )
    val primary = if (transmitting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val warning = MaterialTheme.appColors.statusWarning
    val error = MaterialTheme.colorScheme.error
    val inactive = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
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
private fun connectionColor(phase: RadioConnectionPhase): Color = when (phase) {
    RadioConnectionPhase.CONNECTED -> MaterialTheme.appColors.statusConnected
    RadioConnectionPhase.CONNECTING,
    RadioConnectionPhase.AUTHENTICATING,
    RadioConnectionPhase.RECONNECTING,
    RadioConnectionPhase.DISCOVERING -> MaterialTheme.appColors.statusWarning
    RadioConnectionPhase.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
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
