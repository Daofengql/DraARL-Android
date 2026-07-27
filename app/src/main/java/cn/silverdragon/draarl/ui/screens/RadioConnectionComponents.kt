package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.ui.components.UserAvatar
import cn.silverdragon.draarl.ui.theme.appColors

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
    val callsign = controller.user?.callsign?.ifBlank { controller.user?.displayName }.orEmpty().ifBlank { "DraARL" }
    if (accessMenu) AccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    if (groupMenu) GroupDialog(controller = controller, onDismiss = { groupMenu = false })
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(controller.user?.avatarUrl.orEmpty(), Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("$callsign-101", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
                        TextButton(onClick = onToggleDevices) { Text("${controller.onlineDevices.size} 在线") }
                    }
                }
                IconButton(onClick = controller::toggleMuted) {
                    Icon(
                        if (controller.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (controller.muted) "取消静音" else "静音",
                    )
                }
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "连接由系统自动锁定和重试",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
