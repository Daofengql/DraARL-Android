package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RadioScreen(controller: AppController) {
    val context = LocalContext.current
    val messages = controller.radioMessages
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var showDevices by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.connectRadio()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(controller.selectedGroupId, controller.radioStatus.connected) {
        if (controller.radioStatus.connected) controller.refreshRadioData()
    }

    val connect = {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            controller.connectRadio()
        }
    }
    val startPtt = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            controller.startPtt()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ConnectionPanel(
            controller = controller,
            status = controller.radioStatus,
            onConnect = connect,
            onToggleDevices = { showDevices = !showDevices },
        )
        if (showDevices) {
            OnlineDeviceStrip(controller.onlineDevices)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = if (controller.radioStatus.connected) "当前群组暂无消息" else "电台未连接",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(messages, key = RadioMessage::id) { message -> MessageItem(message) }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发送文本消息") },
                    singleLine = true,
                    enabled = controller.radioStatus.connected,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (controller.sendText(text)) text = ""
                    }),
                )
                IconButton(
                    onClick = { if (controller.sendText(text)) text = "" },
                    enabled = controller.radioStatus.connected && text.isNotBlank(),
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
            PttButton(
                transmitting = controller.radioStatus.transmitting,
                enabled = controller.radioStatus.connected,
                onStart = startPtt,
                onStop = controller::stopPtt,
            )
        }
    }
}

@Composable
private fun ConnectionPanel(
    controller: AppController,
    status: RadioStatus,
    onConnect: () -> Unit,
    onToggleDevices: () -> Unit,
) {
    var accessMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    val selectedGroup = controller.groups.firstOrNull { it.id == controller.selectedGroupId }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (status.connected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = connectionColor(status.phase),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(connectionText(status.phase), fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(status.callsign, status.endpoint).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = controller::toggleMuted) {
                    Icon(
                        if (controller.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (controller.muted) "取消静音" else "静音",
                    )
                }
                if (status.connected || status.phase == RadioConnectionPhase.RECONNECTING) {
                    OutlinedButton(onClick = controller::disconnectRadio) { Text("断开") }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = status.phase !in setOf(RadioConnectionPhase.CONNECTING, RadioConnectionPhase.AUTHENTICATING),
                    ) { Text("连接") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { accessMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = controller.accessPoints.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null)
                        Text(
                            controller.selectedAccessPoint?.displayName ?: if (controller.selectingAccessPoint) "优选入口中" else "UDP 入口",
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            maxLines = 1,
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = accessMenu, onDismissRequest = { accessMenu = false }) {
                        controller.accessPoints.forEach { point ->
                            val probe = controller.accessPointProbes.firstOrNull { it.accessPoint.id == point.id }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(point.displayName)
                                        Text(
                                            listOf(point.region, probe?.latencyMs?.let { "${it} ms" } ?: "按优先级").filter(String::isNotBlank).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                },
                                onClick = {
                                    controller.selectAccessPoint(point)
                                    accessMenu = false
                                },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
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
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        controller.groups.filter { !it.isPrivate || it.joined || it.owner }.forEach { group ->
                            DropdownMenuItem(
                                text = { Text("${group.name} · ${group.onlineCount} 在线") },
                                onClick = {
                                    controller.switchGroup(group)
                                    groupMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = when {
                        status.transmitting -> "正在发射"
                        status.speaker.isNotBlank() -> "接收 ${status.speaker}"
                        status.connected -> "守听中"
                        else -> "离线"
                    },
                    color = when {
                        status.transmitting -> MaterialTheme.colorScheme.error
                        status.speaker.isNotBlank() -> Color(0xFF9A6700)
                        status.connected -> Color(0xFF087F5B)
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onToggleDevices) {
                    Text("${controller.onlineDevices.size} 台在线")
                }
                IconButton(onClick = controller::refreshRadioData) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新群组状态")
                }
            }
            if (status.error.isNotBlank()) {
                Text(status.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OnlineDeviceStrip(devices: List<OnlineDevice>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(devices, key = { "${it.id}-${it.ssid}-${it.username}" }) { device ->
            Card(shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(device.callsign.ifBlank { device.nickname.ifBlank { device.username } }, fontWeight = FontWeight.SemiBold)
                    Text("SSID ${device.ssid}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: RadioMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(8.dp),
            color = if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (message.mine) "我 · ${message.senderCallsign}" else "${message.senderCallsign}-${message.senderSsid}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.type == RadioMessageType.VOICE) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PttButton(
    transmitting: Boolean,
    enabled: Boolean,
    onStart: () -> Boolean,
    onStop: () -> Unit,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        transmitting -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = "按住发射"
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        val started = onStart()
                        if (started) {
                            try {
                                tryAwaitRelease()
                            } finally {
                                onStop()
                            }
                        }
                    },
                )
            },
        shape = CircleShape,
        color = color,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (enabled) 3.dp else 0.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(3.dp))
            Text(if (transmitting) "发射中" else "按住发射", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun connectionColor(phase: RadioConnectionPhase): Color = when (phase) {
    RadioConnectionPhase.CONNECTED -> Color(0xFF087F5B)
    RadioConnectionPhase.CONNECTING,
    RadioConnectionPhase.AUTHENTICATING,
    RadioConnectionPhase.RECONNECTING,
    RadioConnectionPhase.DISCOVERING -> Color(0xFF9A6700)
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

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
