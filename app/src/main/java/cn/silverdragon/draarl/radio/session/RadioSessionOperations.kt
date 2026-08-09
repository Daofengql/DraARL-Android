package cn.silverdragon.draarl.radio.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioRouting
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.radio.AccessPointSelection
import cn.silverdragon.draarl.radio.AccessPointSelector
import cn.silverdragon.draarl.radio.RadioConnectionConfig
import cn.silverdragon.draarl.radio.RadioServiceListener
import cn.silverdragon.draarl.settings.RadioAudioSettings
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RadioSessionDependencies(
    val remote: RadioSessionRemoteDataSource,
    val storage: RadioSessionStorage,
    val service: RadioServiceGateway,
    val effects: RadioSessionEffects
)

internal data class RadioSessionExecution(
    val scope: CoroutineScope,
    val ioDispatcher: CoroutineDispatcher,
    val selectAccessPoint: suspend (List<AccessPoint>) -> AccessPointSelection = AccessPointSelector::select,
    val periodicDiscoveryIntervalMs: Long = ACCESS_POINT_PROBE_INTERVAL_MS
)

internal class RadioSessionStateStore {
    var account: RadioSessionAccount? = null
        private set
    private var contextGeneration = 0
    private var closed = false

    var uiState by mutableStateOf(RadioSessionUiState())
        private set

    fun setAccount(updatedAccount: RadioSessionAccount?): Boolean {
        val changed = account?.key != updatedAccount?.key
        account = updatedAccount
        if (changed) contextGeneration++
        return changed
    }

    fun update(transform: (RadioSessionUiState) -> RadioSessionUiState) {
        if (!closed) uiState = transform(uiState)
    }

    fun context(): RadioSessionContext? = account
        ?.takeIf { !closed }
        ?.let { RadioSessionContext(it, contextGeneration) }

    fun matches(context: RadioSessionContext): Boolean =
        !closed && context.generation == contextGeneration && context.account.key == account?.key

    fun close() {
        closed = true
        contextGeneration++
    }
}

internal data class RadioSessionContext(val account: RadioSessionAccount, val generation: Int)

internal class RadioServiceConfiguration(
    private val connection: RadioServiceConnectionGateway,
    initialAudioSettings: RadioAudioSettings
) {
    private var audioSettings = initialAudioSettings
    private var overlayConfig = RadioPttOverlayConfig()

    fun applyAudioSettings(settings: RadioAudioSettings) {
        audioSettings = settings
        connection.applyAudioSettings(settings)
    }

    fun configurePttOverlay(config: RadioPttOverlayConfig): Boolean {
        val groupName = config.groupName.ifBlank { overlayConfig.groupName }
        overlayConfig = config.copy(groupName = groupName)
        return connection.configurePttOverlay(overlayConfig)
    }

    fun updateGroupName(groupName: String) {
        overlayConfig = overlayConfig.copy(groupName = groupName)
        connection.configurePttOverlay(overlayConfig)
    }

    fun disableOverlay() {
        overlayConfig = RadioPttOverlayConfig()
        connection.configurePttOverlay(overlayConfig)
    }

    fun onServiceConnected() {
        connection.applyAudioSettings(audioSettings)
        connection.configurePttOverlay(overlayConfig)
    }
}

internal class RadioAccessPointCoordinator(
    private val dependencies: RadioSessionDependencies,
    private val execution: RadioSessionExecution,
    private val state: RadioSessionStateStore,
    private val reconnect: () -> Unit
) {
    private var manualSelection = false
    private var discoveryJob: Job? = null
    private val periodicJob = execution.scope.launch {
        if (execution.periodicDiscoveryIntervalMs <= 0) return@launch
        while (isActive) {
            delay(execution.periodicDiscoveryIntervalMs)
            if (state.account != null) discover()
        }
    }

    fun discover() {
        val context = state.context() ?: return
        if (discoveryJob?.isActive == true || state.uiState.selectingAccessPoint) return
        state.update { it.copy(selectingAccessPoint = true) }
        discoveryJob = execution.scope.launch {
            val discovered = withContext(execution.ioDispatcher) {
                runCatching(dependencies.remote::loadAccessPoints).getOrElse {
                    derivedAccessPoint(context.account.baseUrl)?.let(::listOf).orEmpty()
                }
            }
            if (!state.matches(context)) return@launch
            if (discovered.isEmpty()) {
                state.update { it.copy(selectingAccessPoint = false) }
                dependencies.effects.showNotice("服务端没有发布可用的 UDP 入口")
            } else {
                applyDiscoveryResult(context, discovered)
            }
            discoveryJob = null
        }
    }

    fun select(accessPoint: AccessPoint) {
        if (state.uiState.selectedAccessPoint?.id == accessPoint.id) return
        manualSelection = true
        state.update { it.copy(selectedAccessPoint = accessPoint, autoConnectAllowed = true) }
        dependencies.storage.saveSelectedAccessPoint(accessPoint.id)
        if (state.uiState.status.phase != RadioConnectionPhase.DISCONNECTED) reconnect()
    }

    fun cancel() {
        discoveryJob?.cancel()
        discoveryJob = null
        state.update { it.copy(selectingAccessPoint = false) }
    }

    fun close() {
        cancel()
        periodicJob.cancel()
    }

    private suspend fun applyDiscoveryResult(context: RadioSessionContext, discovered: List<AccessPoint>) {
        val selection = withContext(execution.ioDispatcher) { execution.selectAccessPoint(discovered) }
        if (!state.matches(context)) return
        val selected = if (manualSelection) {
            state.uiState.selectedAccessPoint?.let { current ->
                discovered.firstOrNull { it.id == current.id }
            } ?: selection.selected
        } else {
            selection.selected
        }
        state.update {
            it.copy(
                accessPoints = discovered,
                accessPointProbes = selection.probes,
                selectedAccessPoint = selected,
                selectingAccessPoint = false
            )
        }
        dependencies.storage.saveSelectedAccessPoint(selected.id)
    }
}

internal class RadioConnectionCoordinator(
    private val dependencies: RadioSessionDependencies,
    private val execution: RadioSessionExecution,
    private val state: RadioSessionStateStore,
    private val configuration: RadioServiceConfiguration
) {
    private var connectionGeneration = 0
    private var preparingConnection = false
    private var pendingConnection: RadioConnectionConfig? = null
    private var connectionJob: Job? = null
    private var tokenRefreshJob: Job? = null

    fun onAccountChanged(accountChanged: Boolean) {
        val account = state.account
        dependencies.service.connection.updateAccessToken(account?.accessToken.orEmpty())
        if (accountChanged) {
            cancel()
            if (account == null) {
                configuration.disableOverlay()
                dependencies.service.connection.disconnect()
                state.update { it.copy(autoConnectAllowed = false) }
            } else {
                state.update { it.copy(autoConnectAllowed = true) }
            }
        }
    }

    fun connect() {
        val account = state.account
        val accessPoint = state.uiState.selectedAccessPoint
        state.update { it.copy(autoConnectAllowed = true) }
        when {
            account == null || preparingConnection -> Unit
            !account.approved -> dependencies.effects.showNotice("账号审核通过后才能连接在线电台")
            accessPoint == null -> dependencies.effects.showNotice("正在发现并优选 UDP 入口，请稍候")
            else -> prepareConnection(accessPoint)
        }
    }

    fun disconnect() {
        state.update { it.copy(autoConnectAllowed = false) }
        cancel()
        dependencies.service.connection.disconnect()
    }

    fun onServiceConnected() {
        pendingConnection?.let { config ->
            dependencies.service.connection.connect(config)
            pendingConnection = null
            preparingConnection = false
        }
    }

    fun onServiceDisconnected() {
        val previous = state.uiState.status
        val current = RadioStatus(phase = RadioConnectionPhase.DISCONNECTED)
        state.update { it.copy(status = current) }
        dependencies.effects.onStatusChanged(previous, current)
    }

    fun onStatus(previous: RadioStatus, current: RadioStatus) {
        if (current.connected) preparingConnection = false
        dependencies.effects.onStatusChanged(previous, current)
        val tokenRejected = current.phase == RadioConnectionPhase.ERROR && current.error.contains("凭证无效")
        if (tokenRejected && tokenRefreshJob?.isActive != true) refreshRejectedToken()
    }

    fun cancel() {
        connectionGeneration++
        preparingConnection = false
        pendingConnection = null
        connectionJob?.cancel()
        tokenRefreshJob?.cancel()
        connectionJob = null
        tokenRefreshJob = null
    }

    fun close() {
        val stopPendingService = preparingConnection
        cancel()
        if (stopPendingService) dependencies.service.connection.stopStartedService()
    }

    private fun prepareConnection(accessPoint: AccessPoint) {
        val context = state.context() ?: return
        val groupId = state.uiState.selectedGroupId
        preparingConnection = true
        val requestGeneration = ++connectionGeneration
        runCatching(dependencies.service.connection::startForeground).onFailure { error ->
            preparingConnection = false
            dependencies.effects.showNotice(radioSessionFriendlyError(error))
            return
        }
        connectionJob?.cancel()
        connectionJob = execution.scope.launch {
            val result = radioSessionAttempt {
                withContext(execution.ioDispatcher) {
                    RadioConnectionConfig(
                        accessPoint = accessPoint,
                        accessToken = dependencies.remote.freshAccessToken(),
                        clientInstanceId = dependencies.storage.clientInstanceId(),
                        groupId = groupId
                    )
                }
            }
            if (!state.matches(context) || requestGeneration != connectionGeneration) return@launch
            result.onSuccess(::connectPrepared).onFailure { error ->
                preparingConnection = false
                dependencies.service.connection.stopStartedService()
                dependencies.effects.showNotice(radioSessionFriendlyError(error))
            }
            connectionJob = null
        }
    }

    private fun connectPrepared(config: RadioConnectionConfig) {
        if (dependencies.service.connection.connect(config)) {
            preparingConnection = false
        } else {
            pendingConnection = config
            val binding = dependencies.service.connection.bind()
            if (!binding) {
                pendingConnection = null
                preparingConnection = false
                dependencies.service.connection.stopStartedService()
                dependencies.effects.showNotice("无法绑定电台通信服务")
            }
        }
    }

    private fun refreshRejectedToken() {
        val context = state.context() ?: return
        val failedGeneration = connectionGeneration
        tokenRefreshJob = execution.scope.launch {
            val result = radioSessionAttempt {
                withContext(execution.ioDispatcher) { dependencies.remote.renewAccessToken() }
            }
            if (!state.matches(context) || failedGeneration != connectionGeneration) return@launch
            result.onSuccess { token ->
                dependencies.service.connection.updateAccessToken(token)
                if (state.uiState.status.phase == RadioConnectionPhase.ERROR) connect()
            }.onFailure { error ->
                dependencies.service.connection.disconnect()
                dependencies.effects.showNotice(radioSessionFriendlyError(error))
            }
            tokenRefreshJob = null
        }
    }
}

internal class RadioRoutingCoordinator(
    private val dependencies: RadioSessionDependencies,
    private val execution: RadioSessionExecution,
    private val state: RadioSessionStateStore,
    private val configuration: RadioServiceConfiguration
) {
    private var groupNames: Map<Int, String> = emptyMap()
    private var routingJob: Job? = null

    fun onAccountChanged(accountChanged: Boolean) {
        if (accountChanged) groupNames = emptyMap()
        val account = state.account
        if (account == null) {
            dependencies.effects.onContextChanged(0, selectionChanged = accountChanged)
        } else if (accountChanged) {
            val fallback = account.defaultGroupId.takeIf { it > 0 } ?: DEFAULT_RADIO_GROUP_ID
            val routing = dependencies.storage.loadRouting(account.userId, fallback)
            state.update {
                it.copy(
                    selectedGroupId = routing.txGroupId,
                    receiveGroupIds = routing.rxGroupIds + routing.txGroupId,
                    routingUpdating = false
                )
            }
            dependencies.effects.onContextChanged(activeGroupId(account, state.uiState), selectionChanged = true)
            configuration.updateGroupName(groupName(routing.txGroupId))
        } else {
            dependencies.effects.onContextChanged(activeGroupId(account, state.uiState), selectionChanged = false)
        }
    }

    fun onGroupsChanged(groups: List<Group>, preferredGroupId: Int) {
        groupNames = groups.associate { it.id to it.name }
        val account = state.account
        if (account == null) {
            configuration.updateGroupName(groupName(state.uiState.selectedGroupId))
            return
        }
        val availableIds = groups.mapTo(HashSet(), Group::id)
        val txGroupId = state.uiState.selectedGroupId
            .takeIf(availableIds::contains)
            ?: preferredGroupId.takeIf(availableIds::contains)
            ?: DEFAULT_RADIO_GROUP_ID.takeIf(availableIds::contains)
            ?: groups.firstOrNull()?.id
            ?: DEFAULT_RADIO_GROUP_ID
        applyRouting(account, txGroupId, state.uiState.receiveGroupIds + txGroupId, notice = null)
    }

    fun switchGroup(group: Group) {
        val account = state.account
        if (account == null || group.id == state.uiState.selectedGroupId) return
        runCatching {
            RadioRouting.forTransmitGroupSwitch(
                state.uiState.selectedGroupId,
                state.uiState.receiveGroupIds,
                group.id
            )
        }.onSuccess { routing ->
            if (state.uiState.status.connected && state.uiState.status.sessionId.isNotBlank()) {
                updateRouting(routing.txGroupId, routing.rxGroupIds)
            } else {
                applyRouting(
                    account,
                    routing.txGroupId,
                    routing.rxGroupIds,
                    "已切换发送/日志频道：${group.name}"
                )
                dependencies.service.connection.setRouting(routing.txGroupId, routing.rxGroupIds)
            }
        }.onFailure { error ->
            dependencies.effects.showNotice(error.message ?: "发送与收听频道无效")
        }
    }

    fun updateRouting(txGroupId: Int, rxGroupIds: Collection<Int>) {
        val account = state.account
        val status = state.uiState.status
        when {
            account == null -> Unit

            !status.connected || status.sessionId.isBlank() -> {
                dependencies.effects.showNotice("请先连接电台，再修改发送与收听频道")
            }

            state.uiState.routingUpdating || routingJob?.isActive == true -> Unit

            else -> runCatching { RadioRouting.normalize(txGroupId, rxGroupIds) }
                .onSuccess { routing -> launchRoutingUpdate(account, status.sessionId, routing) }
                .onFailure { error ->
                    dependencies.effects.showNotice(error.message ?: "发送与收听频道无效")
                }
        }
    }

    fun onStatus(status: RadioStatus) {
        val account = state.account
        if (account != null && status.connected && status.sessionId.isNotBlank()) {
            applyRouting(
                account = account,
                txGroupId = status.groupId,
                rxGroupIds = status.receiveGroupIds.toSet() + status.groupId,
                notice = null
            )
        }
    }

    fun cancel() {
        routingJob?.cancel()
        routingJob = null
        state.update { it.copy(routingUpdating = false) }
    }

    private fun launchRoutingUpdate(account: RadioSessionAccount, sessionId: String, routing: RadioRouting) {
        val context = state.context() ?: return
        state.update { it.copy(routingUpdating = true) }
        routingJob = execution.scope.launch {
            val result = radioSessionAttempt {
                withContext(execution.ioDispatcher) {
                    dependencies.remote.updateRouting(sessionId, routing.txGroupId, routing.rxGroupIds)
                }
            }
            val currentSessionId = state.uiState.status.sessionId
            if (!state.matches(context) || currentSessionId != sessionId) {
                if (state.matches(context)) state.update { it.copy(routingUpdating = false) }
                routingJob = null
                return@launch
            }
            state.update { it.copy(routingUpdating = false) }
            result.onSuccess { updated ->
                val primaryChanged = state.uiState.selectedGroupId != updated.txGroupId
                val notice = if (primaryChanged) {
                    "已切换发送/日志频道：${groupName(updated.txGroupId)}"
                } else {
                    "收听频道已更新"
                }
                applyRouting(account, updated.txGroupId, updated.rxGroupIds, notice)
                dependencies.service.connection.setRouting(updated.txGroupId, updated.rxGroupIds)
            }.onFailure { error ->
                dependencies.effects.showNotice(radioSessionFriendlyError(error))
            }
            routingJob = null
        }
    }

    private fun applyRouting(
        account: RadioSessionAccount,
        txGroupId: Int,
        rxGroupIds: Collection<Int>,
        notice: String?
    ) {
        if (txGroupId <= 0) return
        val normalizedRxGroupIds = rxGroupIds.filterTo(mutableSetOf()) { it > 0 }.apply { add(txGroupId) }
        val selectionChanged = state.uiState.selectedGroupId != txGroupId
        val routingChanged = selectionChanged || state.uiState.receiveGroupIds != normalizedRxGroupIds
        if (routingChanged) {
            state.update {
                it.copy(selectedGroupId = txGroupId, receiveGroupIds = normalizedRxGroupIds)
            }
            dependencies.storage.saveRouting(account.userId, txGroupId, normalizedRxGroupIds)
        }
        dependencies.effects.onContextChanged(activeGroupId(account, state.uiState), selectionChanged)
        notice?.let(dependencies.effects::showNotice)
        configuration.updateGroupName(groupName(txGroupId))
    }

    private fun groupName(groupId: Int): String = groupNames[groupId] ?: "群组 $groupId"
}

internal interface RadioSessionServiceEvents {
    fun onConnected()
    fun onDisconnected()
    fun onStatus(status: RadioStatus)
    fun onMessage(message: RadioMessage)
    fun onPlaybackState(messageId: String?)
    fun onPlaybackLevel(level: Float)
    fun onTransmitLevel(level: Float)
    fun onCwPreviewState(active: Boolean)
}

internal class RadioSessionServiceCallbacks(
    private val scope: CoroutineScope,
    private val events: RadioSessionServiceEvents
) : RadioServiceListener,
    RadioServiceConnectionObserver {
    override fun onServiceConnected() = dispatch(events::onConnected)

    override fun onServiceDisconnected() = dispatch(events::onDisconnected)

    override fun onRadioStatus(status: RadioStatus) = dispatch { events.onStatus(status) }

    override fun onRadioMessage(message: RadioMessage) = dispatch { events.onMessage(message) }

    override fun onPlaybackState(messageId: String?) = dispatch { events.onPlaybackState(messageId) }

    override fun onPlaybackLevel(level: Float) = events.onPlaybackLevel(level)

    override fun onTransmitLevel(level: Float) = events.onTransmitLevel(level)

    override fun onCwPreviewState(active: Boolean) = dispatch { events.onCwPreviewState(active) }

    private fun dispatch(block: () -> Unit) {
        scope.launch { block() }
    }
}

internal class RadioSessionServiceEventsHandler(
    private val state: RadioSessionStateStore,
    private val routing: RadioRoutingCoordinator,
    private val connection: RadioConnectionCoordinator,
    private val configuration: RadioServiceConfiguration,
    private val effects: RadioSessionEffects
) : RadioSessionServiceEvents {
    override fun onConnected() {
        configuration.onServiceConnected()
        connection.onServiceConnected()
    }

    override fun onDisconnected() = connection.onServiceDisconnected()

    override fun onStatus(status: RadioStatus) {
        val previous = state.uiState.status
        state.update { it.copy(status = status) }
        routing.onStatus(status)
        connection.onStatus(previous, status)
    }

    override fun onMessage(message: RadioMessage) = effects.onRadioMessage(message)

    override fun onPlaybackState(messageId: String?) = effects.onPlaybackState(messageId)

    override fun onPlaybackLevel(level: Float) = effects.onPlaybackLevel(level)

    override fun onTransmitLevel(level: Float) = effects.onTransmitLevel(level)

    override fun onCwPreviewState(active: Boolean) = effects.onCwPreviewState(active)
}

private fun activeGroupId(account: RadioSessionAccount, state: RadioSessionUiState): Int =
    state.selectedGroupId.takeIf { account.approved } ?: 0

private fun derivedAccessPoint(baseUrl: String): AccessPoint? {
    val host = runCatching { URI(baseUrl).host }.getOrNull().orEmpty()
    if (host.isBlank()) return null
    return AccessPoint(
        id = "derived-center",
        displayName = "默认中心入口",
        host = host,
        port = DEFAULT_UDP_PORT,
        priority = Int.MAX_VALUE
    )
}

private fun radioSessionFriendlyError(error: Throwable): String = error.message ?: "操作失败，请稍后重试"

private suspend fun <T> radioSessionAttempt(block: suspend () -> T): Result<T> = runCatching { block() }
    .onFailure { error -> if (error is CancellationException) throw error }

private const val DEFAULT_UDP_PORT = 60_050
private const val ACCESS_POINT_PROBE_INTERVAL_MS = 10_000L
