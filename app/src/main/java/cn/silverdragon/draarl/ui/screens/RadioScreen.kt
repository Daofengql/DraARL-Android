package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RadioScreen(controller: AppController) {
    val context = LocalContext.current
    val messages = controller.radioMessages
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0),
    )
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var followLatest by remember(controller.selectedGroupId) { mutableStateOf(true) }
    var userScrollPending by remember(controller.selectedGroupId) { mutableStateOf(false) }
    var initialListPositioned by remember(controller.selectedGroupId) { mutableStateOf(messages.isNotEmpty()) }
    var text by remember { mutableStateOf("") }
    var showDevices by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.connectRadio()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val canSendText = controller.canSendText()

    LaunchedEffect(userDragging, listState.isScrollInProgress) {
        if (userDragging) userScrollPending = true
        if (userScrollPending && !listState.isScrollInProgress) {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            followLatest = layout.totalItemsCount == 0 || lastVisible >= layout.totalItemsCount - 2
            userScrollPending = false
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id, messages.size, controller.selectedGroupId) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!initialListPositioned) {
            listState.scrollToItem(messages.lastIndex)
            initialListPositioned = true
        } else if (followLatest) {
            listState.animateScrollToItem(messages.lastIndex)
        }
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
    val selectedPointId = controller.selectedAccessPoint?.id.orEmpty()
    LaunchedEffect(selectedPointId, controller.radioStatus.phase) {
        if (
            selectedPointId.isNotBlank() &&
            controller.radioStatus.phase == RadioConnectionPhase.DISCONNECTED &&
            controller.shouldAutoConnectRadio()
        ) {
            connect()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ConnectionPanel(
            controller = controller,
            status = controller.radioStatus,
            onToggleDevices = { showDevices = !showDevices },
        )
        if (showDevices) {
            OnlineDeviceStrip(controller.onlineDevices)
        }
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text("暂无通联记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    MessageItem(
                        controller = controller,
                        message = message,
                        showTimeDivider = index == 0 || message.timestamp - messages[index - 1].timestamp >= TIME_DIVIDER_MS,
                    )
                }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth().imePadding(), shadowElevation = 8.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                            if (canSendText && controller.sendText(text)) text = ""
                        }),
                    )
                    IconButton(
                        onClick = { if (controller.sendText(text)) text = "" },
                        enabled = canSendText && text.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
                PttButton(
                    transmitting = controller.radioStatus.transmitting,
                    enabled = controller.radioStatus.connected && controller.radioStatus.speaker.isBlank(),
                    onStart = startPtt,
                    onStop = controller::stopPtt,
                )
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
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
    if (accessMenu) {
        AccessPointDialog(controller = controller, onDismiss = { accessMenu = false })
    }
    if (groupMenu) {
        GroupDialog(controller = controller, onDismiss = { groupMenu = false })
    }
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
                        TextButton(onClick = onToggleDevices) {
                            Text("${controller.onlineDevices.size} 在线")
                        }
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
private fun AccessPointDialog(controller: AppController, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "选择边缘节点",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(controller.accessPoints, key = AccessPoint::id) { point ->
                        val selected = point.id == controller.selectedAccessPoint?.id
                        val probe = controller.accessPointProbes.firstOrNull { it.accessPoint.id == point.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    if (!selected) controller.selectAccessPoint(point)
                                    onDismiss()
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(point.displayName, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                val meta = listOf(point.region, point.network).filter(String::isNotBlank).joinToString(" · ")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (meta.isNotBlank()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    LatencyText(probe?.latencyMs, prefix = "ICMP ")
                                }
                            }
                            if (selected) Icon(Icons.Default.Check, contentDescription = "当前节点")
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun GroupDialog(controller: AppController, onDismiss: () -> Unit) {
    val availableGroups = controller.groups.filter { !it.isPrivate || it.joined || it.owner }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "选择群组",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(availableGroups, key = Group::id) { group ->
                        val selected = group.id == controller.selectedGroupId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    if (!selected) controller.switchGroup(group)
                                    onDismiss()
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(group.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                Text(
                                    listOf(
                                        "${group.onlineCount} 在线",
                                        if (group.isPrivate) "私有群组" else "公开群组",
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) Icon(Icons.Default.Check, contentDescription = "当前群组")
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)) {
                    Text("关闭")
                }
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
private fun MessageItem(controller: AppController, message: RadioMessage, showTimeDivider: Boolean) {
    val profile = if (message.mine) controller.user else controller.publicProfile(message.senderUsername)
    val callsign = message.senderCallsign.ifBlank { profile?.callsign.orEmpty() }.ifBlank { message.senderUsername }
    val nickname = message.senderNickname.ifBlank { profile?.nickname.orEmpty() }.ifBlank { message.senderUsername }
    val time = remember(message.timestamp) { formatTime(message.timestamp) }
    val timeDivider = remember(message.timestamp) { formatTimeDivider(message.timestamp) }
    if (showTimeDivider) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    timeDivider,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (!message.mine) {
            UserAvatar(profile?.avatarUrl.orEmpty(), Modifier.size(38.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (nickname.isNotBlank()) {
                    Text(nickname, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatRadioIdentity(callsign, message.senderSsid),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (message.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (message.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                if (message.type == RadioMessageType.VOICE) {
                    VoiceMessageContent(controller, message)
                } else {
                    Text(message.content, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (message.mine) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(profile?.avatarUrl.orEmpty(), Modifier.size(38.dp))
        }
    }
}

@Composable
private fun VoiceMessageContent(controller: AppController, message: RadioMessage) {
    val playable = message.audioCacheKey.isNotBlank() || message.audioUrl.isNotBlank()
    val playing = controller.playingMessageId == message.id
    val contentColor = LocalContentColor.current
    Row(
        modifier = Modifier.widthIn(min = 170.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { controller.toggleVoicePlayback(message) }, enabled = playable) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "暂停语音" else "播放语音",
            )
        }
        Row(
            modifier = Modifier.width(82.dp).height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VOICE_BAR_HEIGHTS.forEach { height ->
                Box(
                    Modifier.width(3.dp).height(height.dp).background(
                        color = contentColor,
                        shape = RoundedCornerShape(2.dp),
                    ),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(AppController.formatDuration(message.durationMs), style = MaterialTheme.typography.labelMedium)
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
            .fillMaxWidth()
            .height(72.dp)
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
        shape = RoundedCornerShape(8.dp),
        color = color,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (enabled) 3.dp else 0.dp,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (transmitting) "正在发射" else "按住说话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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

@Composable
private fun LatencyText(latencyMs: Int?, modifier: Modifier = Modifier, prefix: String = "") {
    Text(
        text = latencyMs?.let { "$prefix${it} ms" } ?: "${prefix}不可达",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = latencyColor(latencyMs),
        maxLines = 1,
    )
}

@Composable
private fun latencyColor(latencyMs: Int?): Color = when {
    latencyMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
    latencyMs <= 80 -> Color(0xFF2E7D32)
    latencyMs <= 180 -> Color(0xFFF57C00)
    else -> MaterialTheme.colorScheme.error
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

private fun formatTimeDivider(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.CHINA).format(Date(timestamp))

private const val TIME_DIVIDER_MS = 10 * 60 * 1_000L
private val VOICE_BAR_HEIGHTS = listOf(7, 14, 20, 11, 18, 24, 13, 20, 9, 16, 22, 12)
