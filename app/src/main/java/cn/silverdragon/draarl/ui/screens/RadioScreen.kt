package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioConnectionPhase

@Composable
fun RadioScreen(controller: AppController) {
    val context = LocalContext.current
    val messages = controller.radioMessages
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0),
    )
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var followLatest by rememberSaveable(controller.selectedGroupId) { mutableStateOf(true) }
    var userScrollPending by rememberSaveable(controller.selectedGroupId) { mutableStateOf(false) }
    var initialListPositioned by rememberSaveable(controller.selectedGroupId) { mutableStateOf(messages.isNotEmpty()) }
    var text by rememberSaveable { mutableStateOf("") }
    var textMode by rememberSaveable { mutableStateOf(false) }
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val textFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.connectRadio()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val canSendText = controller.canSendText()

    LaunchedEffect(textMode) {
        if (textMode) {
            textFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

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
        if (showDevices) OnlineDeviceStrip(controller.onlineDevices)
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
                        showTimeDivider = index == 0 ||
                            message.timestamp - messages[index - 1].timestamp >= RADIO_TIME_DIVIDER_MS,
                    )
                }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth().imePadding(), shadowElevation = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { textMode = !textMode }) {
                    Icon(
                        if (textMode) Icons.Default.Mic else Icons.Default.Keyboard,
                        contentDescription = if (textMode) "切换到语音" else "切换到文本",
                    )
                }
                if (textMode) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).focusRequester(textFocusRequester),
                        placeholder = { Text("发送文本消息") },
                        singleLine = true,
                        enabled = controller.radioStatus.connected,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (canSendText && controller.sendText(text)) text = ""
                        }),
                    )
                } else {
                    PttButton(
                        modifier = Modifier.weight(1f),
                        transmitting = controller.radioStatus.transmitting,
                        enabled = controller.radioStatus.connected && controller.radioStatus.speaker.isBlank(),
                        onStart = startPtt,
                        onStop = controller::stopPtt,
                    )
                }
                if (textMode) {
                    IconButton(
                        onClick = { if (controller.sendText(text)) text = "" },
                        enabled = canSendText && text.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                } else {
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
            }
        }
    }
}
