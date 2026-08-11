package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.LocationMessageKind
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.VoicePlaybackQueue
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.data.encodeLocationMessage
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.radio.messages.RadioMessageEvent
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.state.groupNamesById
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class RadioContentMode {
    MAP,
    MESSAGES
}

@Immutable
private data class RadioTransmissionState(val connected: Boolean, val transmitting: Boolean, val receiving: Boolean) {
    val canSend: Boolean get() = connected && !transmitting && !receiving
}

@Immutable
private data class RadioConnectionEffectState(
    val selectedPointId: String,
    val selectedGroupId: Int,
    val phase: RadioConnectionPhase,
    val connected: Boolean,
    val autoConnectAllowed: Boolean
)

@Composable
private fun RadioTransmissionStateScope(
    controller: AppController,
    content: @Composable (RadioTransmissionState) -> Unit
) {
    val state by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) {
            val status = controller.radioSession.uiState.status
            RadioTransmissionState(
                connected = status.connected,
                transmitting = status.transmitting,
                receiving = status.speaker.isNotBlank()
            )
        }
    }
    content(state)
}

@Composable
private fun RadioConnectionEffects(controller: AppController, onConnect: () -> Unit) {
    val state by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) {
            val session = controller.radioSession.uiState
            RadioConnectionEffectState(
                selectedPointId = session.selectedAccessPoint?.id.orEmpty(),
                selectedGroupId = session.selectedGroupId,
                phase = session.status.phase,
                connected = session.status.connected,
                autoConnectAllowed = session.autoConnectAllowed
            )
        }
    }
    val currentOnConnect by rememberUpdatedState(onConnect)

    LaunchedEffect(state.selectedGroupId, state.connected) {
        if (state.connected) controller.refreshRadioData()
    }
    LaunchedEffect(state.selectedPointId, state.phase) {
        if (
            state.selectedPointId.isNotBlank() &&
            state.phase == RadioConnectionPhase.DISCONNECTED &&
            state.autoConnectAllowed
        ) {
            currentOnConnect()
        }
    }
}

@Composable
private fun RadioMessagesPane(
    controller: AppController,
    state: RadioMessagesPaneState,
    listState: LazyListState,
    actions: RadioMessagesPaneActions,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.messages.isEmpty()) {
                RadioMessageEmptyFeedback(
                    hasSyncError = state.history.syncError.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                RadioMessageList(
                    controller = controller,
                    state = state,
                    listState = listState,
                    actions = actions,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Immutable
private data class RadioMessagesHistoryState(
    val loading: Boolean,
    val syncError: String
)

@Immutable
private data class RadioMessagesPaneState(
    val messages: List<RadioMessage>,
    val history: RadioMessagesHistoryState,
    val publicProfiles: Map<String, User>,
    val groupNames: Map<Int, String>,
    val showUnreadJump: Boolean
)

private data class RadioMessagesPaneActions(
    val onJumpToUnread: () -> Unit,
    val onMarkAllUnplayed: () -> Unit,
    val onScrollToBottom: () -> Unit,
    val onOpenLocation: (Wgs84LocationMessage) -> Unit
)

@Composable
private fun RadioMessageList(
    controller: AppController,
    state: RadioMessagesPaneState,
    listState: LazyListState,
    actions: RadioMessagesPaneActions,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        RadioMessageLazyColumn(
            controller = controller,
            state = state,
            listState = listState,
            onOpenLocation = actions.onOpenLocation,
            modifier = Modifier.fillMaxSize()
        )
        RadioMessageListActions(
            controller = controller,
            state = state,
            listState = listState,
            actions = actions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RadioMessageLazyColumn(
    controller: AppController,
    state: RadioMessagesPaneState,
    listState: LazyListState,
    onOpenLocation: (Wgs84LocationMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = if (listState.canScrollForward) 76.dp else 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.history.loading || state.history.syncError.isNotBlank()) {
            item(key = "radio-history-status") {
                RadioHistoryFeedback(
                    loading = state.history.loading,
                    hasSyncError = state.history.syncError.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
        }
        itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
            val previousTimestamp = state.messages.getOrNull(index - 1)?.timestamp
            ControllerMessageItem(
                controller = controller,
                state = MessageItemState(
                    message = message,
                    profile = messageProfile(controller, message, state.publicProfiles),
                    sourceGroupName = state.groupNames[message.groupId].orEmpty(),
                    showTimeDivider = previousTimestamp == null ||
                        message.timestamp - previousTimestamp >= RADIO_TIME_DIVIDER_MS
                ),
                onOpenLocation = onOpenLocation
            )
        }
    }
}

private fun messageProfile(
    controller: AppController,
    message: RadioMessage,
    publicProfiles: Map<String, User>
): User? = if (message.mine) {
    controller.session.uiState.user
} else {
    publicProfiles[message.senderUsername.lowercase()]
}

@Composable
private fun RadioMessageListActions(
    controller: AppController,
    state: RadioMessagesPaneState,
    listState: LazyListState,
    actions: RadioMessagesPaneActions,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (controller.unplayedVoiceCount > 0) {
            UnreadVoiceJumpAction(
                unplayedCount = controller.unplayedVoiceCount,
                showJump = state.showUnreadJump,
                onClick = actions.onJumpToUnread,
                onMarkAllPlayed = actions.onMarkAllUnplayed,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp)
            )
        }
        MessageListFloatingActions(
            canScrollToBottom = listState.canScrollForward,
            onScrollToBottom = actions.onScrollToBottom,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun AutoPlayMessageScrollEffect(
    controller: AppController,
    selectedGroupId: Int,
    messages: List<RadioMessage>,
    listState: LazyListState
) {
    val autoPlaying = controller.voiceAutoPlayEnabled
    val playingMessageId = controller.playingMessageId
    LaunchedEffect(playingMessageId, autoPlaying, selectedGroupId) {
        if (!autoPlaying) return@LaunchedEffect
        val playingId = playingMessageId ?: return@LaunchedEffect
        val playingIndex = messages.indexOfFirst { it.id == playingId }
        if (playingIndex >= 0) listState.animateScrollToItem(playingIndex)
    }
}

@Composable
private fun ControllerMessageItem(
    controller: AppController,
    state: MessageItemState,
    onOpenLocation: (Wgs84LocationMessage) -> Unit
) {
    val playing by remember(controller, state.message.id) {
        derivedStateOf(structuralEqualityPolicy()) {
            controller.playingMessageId == state.message.id
        }
    }
    MessageItem(
        state = state,
        playing = playing,
        onToggleVoicePlayback = controller::toggleVoicePlayback,
        onShareVoiceAudio = controller::shareVoiceMessage,
        onOpenLocation = onOpenLocation
    )
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
    val messages by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { controller.messageController.uiState.messages }
    }
    val historyLoading by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { controller.messageController.uiState.historyLoading }
    }
    val syncError by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { controller.messageController.uiState.syncError }
    }
    val publicProfiles by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { controller.messageController.uiState.publicProfiles }
    }
    val unplayedVoiceCount by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { controller.messageController.uiState.unplayedVoiceCount }
    }
    val selectedGroupId by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { controller.radioSession.uiState.selectedGroupId }
    }
    val groupNames by remember(controller) {
        derivedStateOf(structuralEqualityPolicy()) { groupNamesById(controller.groups) }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0)
    )
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var followLatest by rememberSaveable(selectedGroupId) { mutableStateOf(true) }
    var userScrollPending by rememberSaveable(selectedGroupId) { mutableStateOf(false) }
    var initialListPositioned by rememberSaveable(selectedGroupId) { mutableStateOf(messages.isNotEmpty()) }
    var text by rememberSaveable { mutableStateOf("") }
    var textMode by rememberSaveable { mutableStateOf(false) }
    var contentMode by rememberSaveable { mutableStateOf(RadioContentMode.MAP) }
    var locating by remember { mutableStateOf(false) }
    var showLocationChoices by rememberSaveable { mutableStateOf(false) }
    var showCwSheet by rememberSaveable { mutableStateOf(false) }
    var cwText by rememberSaveable { mutableStateOf("") }
    var cwWordsPerMinute by rememberSaveable { mutableIntStateOf(18) }
    var cwToneHz by rememberSaveable { mutableIntStateOf(700) }
    var historyAnchorId by rememberSaveable(selectedGroupId) { mutableStateOf("") }
    var historyAnchorOffset by rememberSaveable(selectedGroupId) { mutableIntStateOf(0) }
    var unreadJumpDismissed by rememberSaveable(selectedGroupId) { mutableStateOf(false) }
    var previousUnplayedVoiceCount by rememberSaveable(selectedGroupId) { mutableIntStateOf(0) }
    val dismissExtrasInteraction = remember { MutableInteractionSource() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.radioSession.connect()
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
    BackHandler(enabled = extrasExpanded && !showLocationChoices && !showCwSheet) { onExtrasExpandedChange(false) }

    LaunchedEffect(extrasExpanded) {
        if (!extrasExpanded) {
            showLocationChoices = false
            showCwSheet = false
        }
    }

    LaunchedEffect(unplayedVoiceCount) {
        when {
            unplayedVoiceCount == 0 -> unreadJumpDismissed = false
            unplayedVoiceCount > previousUnplayedVoiceCount -> unreadJumpDismissed = false
        }
        previousUnplayedVoiceCount = unplayedVoiceCount
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
    LaunchedEffect(messages.lastOrNull()?.id, messages.size, selectedGroupId) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!initialListPositioned) {
            listState.scrollToItem(messages.lastIndex)
            initialListPositioned = true
        } else if (followLatest && !controller.voiceAutoPlayEnabled) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(contentMode, selectedGroupId) {
        if (contentMode != RadioContentMode.MESSAGES || !followLatest || messages.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        listState.scrollToItem(messages.lastIndex)
    }
    AutoPlayMessageScrollEffect(controller, selectedGroupId, messages, listState)
    LaunchedEffect(messages.firstOrNull()?.id, messages.size, historyLoading) {
        if (historyLoading || historyAnchorId.isBlank()) return@LaunchedEffect
        val anchorIndex = messages.indexOfFirst { it.id == historyAnchorId }
        if (anchorIndex >= 0) {
            listState.scrollToItem(anchorIndex, historyAnchorOffset)
        }
        historyAnchorId = ""
        historyAnchorOffset = 0
    }
    LaunchedEffect(selectedGroupId) {
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
    val connect = {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            controller.radioSession.connect()
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
    RadioConnectionEffects(controller = controller, onConnect = connect)

    Column(Modifier.fillMaxSize()) {
        ConnectionPanel(controller = controller)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            RadioMessagesPane(
                controller = controller,
                state = RadioMessagesPaneState(
                    messages = messages,
                    history = RadioMessagesHistoryState(historyLoading, syncError),
                    publicProfiles = publicProfiles,
                    groupNames = groupNames,
                    showUnreadJump = unplayedVoiceCount > 0 && !unreadJumpDismissed
                ),
                listState = listState,
                actions = RadioMessagesPaneActions(
                    onJumpToUnread = {
                        val unreadIndex = messages.indexOfFirst(VoicePlaybackQueue::isUnplayed)
                        unreadJumpDismissed = true
                        if (unreadIndex >= 0) {
                            followLatest = false
                            val statusItemCount = if (historyLoading || syncError.isNotBlank()) 1 else 0
                            scope.launch { listState.animateScrollToItem(unreadIndex + statusItemCount) }
                        }
                    },
                    onMarkAllUnplayed = controller::clearUnplayedVoiceMessages,
                    onScrollToBottom = {
                        followLatest = true
                        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                    },
                    onOpenLocation = onOpenLocation
                ),
                modifier = Modifier.fillMaxSize()
            )
            val mapVisible = contentMode == RadioContentMode.MAP
            val mapAlpha by animateFloatAsState(
                targetValue = if (mapVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "radio-map-alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(mapAlpha)
                    .zIndex(if (mapVisible) 0f else -1f)
            ) {
                AprsMapPanel(
                    controller = controller,
                    onStartPtt = startPtt,
                    onStopPtt = controller::stopPtt,
                    display = AprsMapPanelDisplay(
                        visible = mapVisible,
                        active = mapVisible || mapAlpha > 0.01f
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 16.dp,
                        bottom = 8.dp + if (contentMode == RadioContentMode.MAP) {
                            RADIO_COMPOSER_HEIGHT
                        } else {
                            0.dp
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                CommandIconButton(
                    onClick = {
                        controller.stopVoiceAutoPlay()
                        contentMode = if (contentMode == RadioContentMode.MAP) {
                            RadioContentMode.MESSAGES
                        } else {
                            RadioContentMode.MAP
                        }
                        onExtrasExpandedChange(false)
                    },
                    contentDescription = if (contentMode == RadioContentMode.MAP) "打开通联日志" else "返回地图",
                    icon = if (contentMode == RadioContentMode.MAP) {
                        Icons.AutoMirrored.Filled.MenuBook
                    } else {
                        Icons.Default.Map
                    }
                )
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
        if (contentMode == RadioContentMode.MESSAGES) {
            RadioTransmissionStateScope(controller) { transmission ->
                RadioComposer(
                    textMode = textMode,
                    text = text,
                    connected = transmission.connected,
                    transmitting = transmission.transmitting,
                    receiving = transmission.receiving,
                    canSendText = transmission.canSend,
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
            }
            AnimatedVisibility(
                visible = extrasExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                RadioTransmissionStateScope(controller) { transmission ->
                    RadioExtraPanel(
                        locating = locating,
                        cwEnabled = transmission.canSend,
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
        RadioTransmissionStateScope(controller) { transmission ->
            CwSendSheet(
                text = cwText,
                wordsPerMinute = cwWordsPerMinute,
                toneHz = cwToneHz,
                enabled = transmission.canSend,
                previewEnabled = !transmission.transmitting && !transmission.receiving,
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
}

// Keep the map/log toggle aligned with the log page, where the composer occupies this slot.
private val RADIO_COMPOSER_HEIGHT = 73.dp
