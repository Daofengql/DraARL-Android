package cn.silverdragon.draarl.radio.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioMessageController internal constructor(
    private val remote: RadioMessageRemoteDataSource,
    private val cache: RadioMessageCache,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val friendlyError: (Throwable) -> String
) {
    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(scope.coroutineContext + controllerJob)
    private val syncEngine = RadioMessageSyncEngine(remote, cache, currentTimeMillis)
    private val writer = RadioMessageWriter(cache, controllerScope, ioDispatcher)
    private val historyStates = mutableMapOf<String, RadioHistoryState>()
    private val visibleLimits = mutableMapOf<String, Int>()
    private val profileLoader = RadioMessageProfileLoader(
        remote = remote,
        scope = controllerScope,
        ioDispatcher = ioDispatcher,
        currentAccountKey = { account?.key },
        currentProfiles = { uiState.publicProfiles },
        updateProfile = { key, profile ->
            uiState = uiState.copy(publicProfiles = uiState.publicProfiles + (key to profile))
        }
    )
    private var account: RadioMessageAccount? = null
    private var selectedGroupId = 0
    private var contextGeneration = 0
    private var cacheLoadJob: Job? = null
    private var historyJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshPending = false
    private var closed = false

    private val currentContext: MessageContext?
        get() = account
            ?.takeIf { !closed && selectedGroupId > 0 }
            ?.let { MessageContext(it, selectedGroupId, contextGeneration) }

    var uiState by mutableStateOf(RadioMessageUiState())
        private set

    fun onEvent(event: RadioMessageEvent) {
        when (event) {
            RadioMessageEvent.Refresh -> refresh()

            RadioMessageEvent.LoadOlder -> loadOlder()

            RadioMessageEvent.MarkAllPlayed -> currentContext?.let { context ->
                if (uiState.unplayedVoiceCount > 0) {
                    uiState = uiState.copy(messages = RadioMessageReducer.markAllPlayed(uiState.messages))
                    writer.markAllPlayed(context)
                }
            }

            is RadioMessageEvent.OnlineDevicesChanged -> currentContext?.let { context ->
                profileLoader.preload(event.usernames, context.account.key)
            }

            RadioMessageEvent.BeforeCacheClear -> invalidateContext()

            RadioMessageEvent.AfterCacheClear -> {
                historyStates.clear()
                visibleLimits.clear()
                uiState = uiState.copy(
                    messages = emptyList(),
                    historyLoading = false,
                    historyHasMore = true,
                    syncError = ""
                )
            }
        }
    }

    fun onContextChanged(updatedAccount: RadioMessageAccount?, groupId: Int) {
        val normalizedGroupId = groupId.takeIf { updatedAccount != null && it > 0 } ?: 0
        val accountChanged = account?.key != updatedAccount?.key
        val groupChanged = selectedGroupId != normalizedGroupId
        if (!accountChanged && !groupChanged) {
            account = updatedAccount
            return
        }
        invalidateContext()
        account = updatedAccount
        selectedGroupId = normalizedGroupId
        if (accountChanged) {
            historyStates.clear()
            visibleLimits.clear()
            uiState = RadioMessageUiState()
        } else {
            currentContext?.let { historyStates.remove(it.stateKey) }
            uiState = uiState.copy(
                messages = emptyList(),
                historyLoading = false,
                historyHasMore = true,
                syncError = ""
            )
        }
        if (currentContext != null) loadCached()
    }

    fun onLiveMessage(message: RadioMessage, identity: RadioMessageIdentityContext) {
        val context = currentContext ?: return
        val enriched = RadioMessageReducer.enrich(message, context.account.user, identity)
        val messageGroupId = enriched.groupId.takeIf { it > 0 } ?: context.groupId
        val messageToStore = if (messageGroupId == context.groupId) {
            val (messages, merged) = RadioMessageReducer.mergeLive(uiState.messages, enriched)
            uiState = uiState.copy(messages = messages)
            merged
        } else {
            enriched
        }
        writer.save(context, messageGroupId, messageToStore)
        profileLoader.preload(listOf(enriched.senderUsername), context.account.key)
    }

    fun refreshServerMessage(message: RadioMessage, onResult: (Result<RadioMessage>) -> Unit): Boolean {
        val context = currentContext
        val recordId = message.serverRecordId
        if (context == null || recordId == null) return false
        val groupId = message.groupId.takeIf { it > 0 } ?: context.groupId
        controllerScope.launch {
            val result = radioMessageAttempt {
                withContext(ioDispatcher) {
                    remote.loadMessage(groupId, recordId, context.account.user)
                }
            }
            if (context.matches(account?.key, selectedGroupId, contextGeneration, closed)) onResult(result)
        }
        return true
    }

    fun updateMessage(message: RadioMessage) {
        val context = currentContext ?: return
        uiState = uiState.copy(messages = RadioMessageReducer.update(uiState.messages, message))
        val groupId = message.groupId.takeIf { it > 0 } ?: context.groupId
        writer.save(context, groupId, message)
    }

    fun markPlayed(message: RadioMessage) {
        if (message.type != RadioMessageType.VOICE || message.played) return
        val context = currentContext ?: return
        uiState = uiState.copy(messages = RadioMessageReducer.markPlayed(uiState.messages, message))
        val groupId = message.groupId.takeIf { it > 0 } ?: context.groupId
        writer.markPlayed(context, groupId, message)
    }

    fun close() {
        if (closed) return
        closed = true
        controllerScope.cancel()
    }

    private fun loadCached() {
        val context = currentContext ?: return
        cacheLoadJob?.cancel()
        val visibleLimit = visibleLimits.getOrDefault(context.stateKey, INITIAL_VISIBLE_MESSAGES)
        cacheLoadJob = controllerScope.launch {
            val result = radioMessageAttempt {
                withContext(ioDispatcher) {
                    cache.load(context.account.key, context.groupId, visibleLimit)
                }
            }
            if (context.matches(account?.key, selectedGroupId, contextGeneration, closed)) {
                result.onSuccess { cachedMessages ->
                    uiState = uiState.copy(
                        messages = RadioMessageReducer.replaceCached(
                            uiState.messages,
                            cachedMessages,
                            currentTimeMillis()
                        ),
                        historyHasMore = cachedMessages.size >= visibleLimit ||
                            historyStates[context.stateKey]?.hasMore != false
                    )
                    profileLoader.preload(cachedMessages.map(RadioMessage::senderUsername), context.account.key)
                }
            }
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) {
            refreshPending = true
            return
        }
        val context = currentContext ?: return
        refreshPending = false
        val visibleLimit = visibleLimits.getOrDefault(context.stateKey, INITIAL_VISIBLE_MESSAGES)
        refreshJob = controllerScope.launch {
            val result = radioMessageAttempt {
                withContext(ioDispatcher) { syncEngine.synchronizeLatest(context, visibleLimit) }
            }
            if (context.matches(account?.key, selectedGroupId, contextGeneration, closed)) {
                result
                    .onSuccess { snapshot ->
                        historyStates[context.stateKey] = snapshot.historyState
                        uiState = uiState.copy(
                            messages = RadioMessageReducer.replaceCached(
                                uiState.messages,
                                snapshot.messages,
                                currentTimeMillis()
                            ),
                            historyHasMore = snapshot.hasMoreHistory,
                            syncError = ""
                        )
                        profileLoader.preload(
                            snapshot.messages.map(RadioMessage::senderUsername),
                            context.account.key
                        )
                    }
                    .onFailure { error -> uiState = uiState.copy(syncError = friendlyError(error)) }
                refreshJob = null
                if (refreshPending) refresh()
            }
        }
    }

    private fun loadOlder() {
        val context = currentContext
        val operationUnavailable = context == null || historyJob?.isActive == true
        val stateUnavailable = uiState.historyLoading || !uiState.historyHasMore
        if (operationUnavailable || stateUnavailable) return
        val previousLimit = visibleLimits.getOrDefault(context.stateKey, INITIAL_VISIBLE_MESSAGES)
        if (previousLimit >= MAX_MESSAGES) {
            uiState = uiState.copy(historyHasMore = false)
            return
        }
        val requestedLimit = (previousLimit + HISTORY_LOAD_BATCH).coerceAtMost(MAX_MESSAGES)
        val initialHistoryState = historyStates[context.stateKey] ?: RadioHistoryState()
        uiState = uiState.copy(historyLoading = true)
        historyJob = controllerScope.launch {
            val result = radioMessageAttempt {
                withContext(ioDispatcher) {
                    syncEngine.loadOlder(context, previousLimit, requestedLimit, initialHistoryState)
                }
            }
            if (context.matches(account?.key, selectedGroupId, contextGeneration, closed)) {
                uiState = uiState.copy(historyLoading = false)
                result
                    .onSuccess { history ->
                        historyStates[context.stateKey] = history.historyState
                        visibleLimits[context.stateKey] = requestedLimit
                        uiState = uiState.copy(
                            messages = RadioMessageReducer.replaceCached(
                                uiState.messages,
                                history.messages,
                                currentTimeMillis()
                            ),
                            historyHasMore = history.hasMore,
                            syncError = ""
                        )
                        profileLoader.preload(
                            history.messages.map(RadioMessage::senderUsername),
                            context.account.key
                        )
                    }
                    .onFailure { error -> uiState = uiState.copy(syncError = friendlyError(error)) }
                historyJob = null
            }
        }
    }

    private fun invalidateContext() {
        contextGeneration++
        refreshPending = false
        cacheLoadJob?.cancel()
        historyJob?.cancel()
        refreshJob?.cancel()
        cacheLoadJob = null
        historyJob = null
        refreshJob = null
        uiState = uiState.copy(historyLoading = false)
    }

    private companion object {
        const val INITIAL_VISIBLE_MESSAGES = 200
        const val HISTORY_LOAD_BATCH = 100
        const val MAX_MESSAGES = 1_000
    }
}
