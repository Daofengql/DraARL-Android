package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.LocationMessageKind
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.data.encodeLocationMessage
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.radio.messages.RadioMessageEvent
import cn.silverdragon.draarl.ui.state.groupNamesById
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class RadioContentMode {
    MAP,
    MESSAGES
}

@Composable
fun RadioScreen(
    controller: AppController,
    extrasExpanded: Boolean,
    onExtrasExpandedChange: (Boolean) -> Unit,
    onPickLocation: () -> Unit,
    onOpenLocation: (Wgs84LocationMessage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    val messageState = controller.messageController.uiState
    val messages = messageState.messages
    val groupNames = remember(controller.groups) { groupNamesById(controller.groups) }
    val unplayedVoiceCount = messageState.unplayedVoiceCount
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0)
    )
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var followLatest by rememberSaveable(controller.selectedGroupId) { mutableStateOf(true) }
    var userScrollPending by rememberSaveable(controller.selectedGroupId) { mutableStateOf(false) }
    var initialListPositioned by rememberSaveable(controller.selectedGroupId) { mutableStateOf(messages.isNotEmpty()) }
    var text by rememberSaveable { mutableStateOf("") }
    var textMode by rememberSaveable { mutableStateOf(false) }
    var showDevices by rememberSaveable { mutableStateOf(false) }
    var contentMode by rememberSaveable { mutableStateOf(RadioContentMode.MAP) }
    var locating by remember { mutableStateOf(false) }
    var showLocationChoices by rememberSaveable { mutableStateOf(false) }
    var showCwSheet by rememberSaveable { mutableStateOf(false) }
    var cwText by rememberSaveable { mutableStateOf("") }
    var cwWordsPerMinute by rememberSaveable { mutableIntStateOf(18) }
    var cwToneHz by rememberSaveable { mutableIntStateOf(700) }
    var historyAnchorId by rememberSaveable(controller.selectedGroupId) { mutableStateOf("") }
    var historyAnchorOffset by rememberSaveable(controller.selectedGroupId) { mutableIntStateOf(0) }
    val dismissExtrasInteraction = remember { MutableInteractionSource() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.connectRadio()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val sendCurrentLocation = {
        if (!locating) {
            locating = true
            scope.launch {
                try {
                    val location = locationProvider.locate()
                    val message = Wgs84LocationMessage(
                        kind = LocationMessageKind.CURRENT,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitudeMeters = if (location.hasAltitude()) location.altitude else null
                    )
                    if (controller.sendText(encodeLocationMessage(message))) {
                        onExtrasExpandedChange(false)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    controller.showNotice(error.message ?: "当前位置获取失败")
                } finally {
                    locating = false
                }
            }
        }
    }
    val locationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) sendCurrentLocation() else controller.showNotice("需要定位权限才能发送当前位置")
        }
    val canSendText = controller.canSendText()

    BackHandler(enabled = extrasExpanded && !showLocationChoices && !showCwSheet) { onExtrasExpandedChange(false) }

    LaunchedEffect(extrasExpanded) {
        if (!extrasExpanded) {
            showLocationChoices = false
            showCwSheet = false
        }
    }

    LaunchedEffect(userDragging, listState.isScrollInProgress) {
        if (userDragging) {
            userScrollPending = true
            if (controller.voiceAutoPlayEnabled) controller.stopVoiceAutoPlay()
        }
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
        } else if (followLatest && !controller.voiceAutoPlayEnabled) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(controller.playingMessageId, controller.voiceAutoPlayEnabled, controller.selectedGroupId) {
        if (!controller.voiceAutoPlayEnabled) return@LaunchedEffect
        val playingId = controller.playingMessageId ?: return@LaunchedEffect
        val playingIndex = messages.indexOfFirst { it.id == playingId }
        if (playingIndex >= 0) listState.animateScrollToItem(playingIndex)
    }
    LaunchedEffect(messages.firstOrNull()?.id, messages.size, messageState.historyLoading) {
        if (messageState.historyLoading || historyAnchorId.isBlank()) return@LaunchedEffect
        val anchorIndex = messages.indexOfFirst { it.id == historyAnchorId }
        if (anchorIndex >= 0) {
            listState.scrollToItem(anchorIndex, historyAnchorOffset)
        }
        historyAnchorId = ""
        historyAnchorOffset = 0
    }
    LaunchedEffect(controller.selectedGroupId) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, followLatest, messages.firstOrNull()?.id)
        }
            .distinctUntilChanged()
            .collectLatest { (firstVisibleIndex, followsLatest, _) ->
                if (!followsLatest && firstVisibleIndex <= 2) {
                    val anchor = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key != "radio-history-status" }
                    val anchorId = anchor?.key as? String
                    if (!anchorId.isNullOrBlank()) {
                        historyAnchorId = anchorId
                        historyAnchorOffset = -(anchor.offset)
                    }
                    controller.messageController.onEvent(RadioMessageEvent.LoadOlder)
                }
            }
    }
    LaunchedEffect(controller.selectedGroupId, controller.radioStatus.connected) {
        if (controller.radioStatus.connected) controller.refreshRadioData()
    }

    val connect = {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            controller.connectRadio()
        }
    }
    val startPtt = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
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
            onToggleDevices = { showDevices = !showDevices }
        )
        if (showDevices) OnlineDeviceStrip(controller.onlineDevices)
        RadioModeSwitcher(
            mapSelected = contentMode == RadioContentMode.MAP,
            onMap = {
                controller.stopVoiceAutoPlay()
                contentMode = RadioContentMode.MAP
                onExtrasExpandedChange(false)
            },
            onMessages = { contentMode = RadioContentMode.MESSAGES },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AprsMapPanel(
                controller = controller,
                onStartPtt = startPtt,
                onStopPtt = controller::stopPtt,
                visible = contentMode == RadioContentMode.MAP,
                modifier = Modifier.fillMaxSize()
            )
            if (contentMode == RadioContentMode.MESSAGES) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (messages.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Text("暂无通联记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (messageState.syncError.isNotBlank()) {
                                        Text(
                                            "记录同步暂时中断，稍后自动重试",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        start = 12.dp,
                                        top = 12.dp,
                                        end = 12.dp,
                                        bottom = 12.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (messageState.historyLoading || messageState.syncError.isNotBlank()) {
                                        item(key = "radio-history-status") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (messageState.historyLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Text(
                                                        "正在加载更早记录",
                                                        modifier = Modifier.padding(start = 8.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    Text(
                                                        "记录同步暂时中断，稍后自动重试",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                                        val previousTimestamp = messages.getOrNull(index - 1)?.timestamp
                                        MessageItem(
                                            state = MessageItemState(
                                                message = message,
                                                profile = if (message.mine) {
                                                    controller.user
                                                } else {
                                                    messageState.publicProfiles[message.senderUsername.lowercase()]
                                                },
                                                playing = controller.playingMessageId == message.id,
                                                sourceGroupName = groupNames[message.groupId].orEmpty(),
                                                showTimeDivider = previousTimestamp == null ||
                                                    message.timestamp - previousTimestamp >= RADIO_TIME_DIVIDER_MS
                                            ),
                                            onToggleVoicePlayback = controller::toggleVoicePlayback,
                                            onOpenLocation = onOpenLocation
                                        )
                                    }
                                }
                                MessageListFloatingActions(
                                    unplayedCount = unplayedVoiceCount,
                                    autoPlaying = controller.voiceAutoPlayEnabled,
                                    canScrollToBottom = listState.canScrollForward,
                                    onToggleAutoPlay = {
                                        followLatest = false
                                        controller.toggleVoiceAutoPlay()
                                    },
                                    onClearUnplayed = controller::clearUnplayedVoiceMessages,
                                    onScrollToBottom = {
                                        followLatest = true
                                        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                                    },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
                                )
                            }
                        }
                    }
                }
                if (extrasExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = dismissExtrasInteraction,
                                indication = null
                            ) { onExtrasExpandedChange(false) }
                    )
                }
            }
        }
        if (contentMode == RadioContentMode.MESSAGES) {
            RadioComposer(
                textMode = textMode,
                text = text,
                connected = controller.radioStatus.connected,
                transmitting = controller.radioStatus.transmitting,
                receiving = controller.radioStatus.speaker.isNotBlank(),
                canSendText = canSendText,
                onTextModeChange = {
                    textMode = it
                    onExtrasExpandedChange(false)
                },
                onTextChange = { text = it },
                onTextInputFocused = { onExtrasExpandedChange(false) },
                onSendText = {
                    onExtrasExpandedChange(false)
                    if (controller.sendText(text)) {
                        text = ""
                    }
                },
                onMoreMessage = {
                    showLocationChoices = false
                    showCwSheet = false
                    onExtrasExpandedChange(!extrasExpanded)
                },
                onStartPtt = {
                    onExtrasExpandedChange(false)
                    startPtt()
                },
                onStopPtt = controller::stopPtt
            )
            AnimatedVisibility(
                visible = extrasExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                RadioExtraPanel(
                    locating = locating,
                    cwEnabled = controller.radioStatus.connected &&
                        !controller.radioStatus.transmitting && controller.radioStatus.speaker.isBlank(),
                    cwTransmitting = controller.cwTransmitting,
                    onLocationClick = {
                        showCwSheet = false
                        showLocationChoices = true
                    },
                    onCwClick = {
                        showLocationChoices = false
                        showCwSheet = true
                    },
                    onStopCw = controller::stopCw
                )
            }
        }
    }
    if (showLocationChoices) {
        LocationTypeSheet(
            locating = locating,
            onDismiss = {
                showLocationChoices = false
                onExtrasExpandedChange(false)
            },
            onCurrentLocation = {
                showLocationChoices = false
                onExtrasExpandedChange(false)
                val fineGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) {
                    sendCurrentLocation()
                } else {
                    locationPermission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onPickLocation = {
                showLocationChoices = false
                onExtrasExpandedChange(false)
                onPickLocation()
            }
        )
    }
    if (showCwSheet) {
        CwSendSheet(
            text = cwText,
            wordsPerMinute = cwWordsPerMinute,
            toneHz = cwToneHz,
            enabled = controller.radioStatus.connected &&
                !controller.radioStatus.transmitting && controller.radioStatus.speaker.isBlank(),
            previewEnabled = !controller.radioStatus.transmitting && controller.radioStatus.speaker.isBlank(),
            transmitting = controller.cwTransmitting,
            previewing = controller.cwPreviewing,
            onDismiss = {
                controller.stopCwPreview()
                showCwSheet = false
                onExtrasExpandedChange(false)
            },
            onTextChange = { value -> cwText = value.uppercase().take(80) },
            onWordsPerMinuteChange = { cwWordsPerMinute = it.coerceIn(8, 40) },
            onToneHzChange = { cwToneHz = it.coerceIn(400, 1_000) },
            onPreview = {
                controller.previewCw(cwText, cwWordsPerMinute, cwToneHz)
            },
            onStopPreview = controller::stopCwPreview,
            onSend = {
                controller.sendCw(cwText, cwWordsPerMinute, cwToneHz)
            },
            onStop = controller::stopCw
        )
    }
}
