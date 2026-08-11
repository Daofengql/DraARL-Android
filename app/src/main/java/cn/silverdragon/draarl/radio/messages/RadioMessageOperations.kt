package cn.silverdragon.draarl.radio.messages

import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageReconciler
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RadioMessageSyncEngine(
    private val remote: RadioMessageRemoteDataSource,
    private val cache: RadioMessageCache,
    private val currentTimeMillis: () -> Long
) {
    suspend fun synchronizeLatest(context: MessageContext, visibleLimit: Int): RadioSyncSnapshot {
        currentCoroutineContext().ensureActive()
        val expectedGeneration = cache.generation()
        val initializingHistory = !cache.isHistoryInitialized(context.account.key, context.groupId)
        val page = remote.loadPage(context.groupId, cursor = "", context.account.user)
        val remoteMessages = page.messages
            .distinctBy { it.serverRecordId ?: it.id }
            .sortedBy(RadioMessage::timestamp)
            .map { message -> if (initializingHistory) message.asReadHistory() else message }
        currentCoroutineContext().ensureActive()
        val settleCutoff = currentTimeMillis() - RadioMessageReconciler.REMOTE_SETTLE_DELAY_MS
        val authoritativeWindow = when {
            remoteMessages.isNotEmpty() -> remoteMessages.first().timestamp..settleCutoff
            !page.hasMore -> 0L..settleCutoff
            else -> null
        }
        cache.reconcile(
            accountKey = context.account.key,
            groupId = context.groupId,
            remoteMessages = remoteMessages,
            authoritativeWindow = authoritativeWindow,
            expectedGeneration = expectedGeneration
        )
        if (initializingHistory) {
            cache.markHistoryInitialized(context.account.key, context.groupId, expectedGeneration)
        }
        currentCoroutineContext().ensureActive()
        val cachedMessages = cache.load(context.account.key, context.groupId, visibleLimit + 1)
        return RadioSyncSnapshot(
            messages = cachedMessages.takeLast(visibleLimit),
            hasMoreHistory = visibleLimit < MAX_MESSAGES &&
                (cachedMessages.size > visibleLimit || page.hasMore),
            historyState = RadioHistoryState(
                nextCursor = page.nextCursor,
                hasMore = page.hasMore && page.nextCursor.isNotBlank()
            )
        )
    }

    suspend fun loadOlder(
        context: MessageContext,
        previousLimit: Int,
        requestedLimit: Int,
        initialHistoryState: RadioHistoryState
    ): OlderRadioHistoryResult {
        var historyState = initialHistoryState
        var cachedMessages = cache.load(context.account.key, context.groupId, requestedLimit)
        var pagesLoaded = 0
        while (
            cachedMessages.size <= previousLimit && historyState.hasMore &&
            pagesLoaded < MAX_HISTORY_PAGES_PER_LOAD
        ) {
            currentCoroutineContext().ensureActive()
            val page = remote.loadPage(context.groupId, historyState.nextCursor, context.account.user)
            currentCoroutineContext().ensureActive()
            cache.reconcile(
                accountKey = context.account.key,
                groupId = context.groupId,
                remoteMessages = page.messages.map { it.asReadHistory() },
                expectedGeneration = cache.generation()
            )
            val nextCursor = page.nextCursor
            historyState = RadioHistoryState(
                nextCursor = nextCursor,
                hasMore = page.hasMore && nextCursor.isNotBlank() && nextCursor != historyState.nextCursor
            )
            pagesLoaded++
            cachedMessages = cache.load(context.account.key, context.groupId, requestedLimit)
            if (page.messages.isEmpty()) break
        }
        return OlderRadioHistoryResult(
            messages = cachedMessages,
            hasMore = requestedLimit < MAX_MESSAGES &&
                (cachedMessages.size >= requestedLimit || historyState.hasMore),
            historyState = historyState
        )
    }

    private fun RadioMessage.asReadHistory(): RadioMessage =
        if (type == RadioMessageType.VOICE && !played) copy(played = true) else this

    private companion object {
        const val MAX_HISTORY_PAGES_PER_LOAD = 5
        const val MAX_MESSAGES = 1_000
    }
}

internal class RadioMessageWriter(
    private val cache: RadioMessageCache,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    fun save(context: MessageContext, groupId: Int, message: RadioMessage) {
        val expectedGeneration = cache.generation()
        scope.launch(ioDispatcher) {
            runCatching {
                cache.save(context.account.key, groupId, message, expectedGeneration)
            }
        }
    }

    fun markPlayed(context: MessageContext, groupId: Int, message: RadioMessage) {
        val expectedGeneration = cache.generation()
        scope.launch(ioDispatcher) {
            runCatching {
                cache.markPlayed(
                    accountKey = context.account.key,
                    groupId = groupId,
                    localId = message.id,
                    serverRecordId = message.serverRecordId,
                    expectedGeneration = expectedGeneration
                )
            }
        }
    }

    fun markAllPlayed(context: MessageContext) {
        val expectedGeneration = cache.generation()
        scope.launch(ioDispatcher) {
            runCatching {
                cache.markAllPlayed(context.account.key, context.groupId, expectedGeneration)
            }
        }
    }
}

internal class RadioMessageProfileLoader(
    private val remote: RadioMessageRemoteDataSource,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val currentAccountKey: () -> String?,
    private val currentProfiles: () -> Map<String, User>,
    private val updateProfile: (String, User) -> Unit
) {
    private val loadingProfiles = mutableSetOf<String>()

    fun preload(usernames: Collection<String>, accountKey: String) {
        usernames.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .forEach { username ->
                val key = username.lowercase()
                val requestKey = "$accountKey#$key"
                if (key in currentProfiles() || !loadingProfiles.add(requestKey)) return@forEach
                scope.launch {
                    val result = radioMessageAttempt {
                        withContext(ioDispatcher) { remote.loadPublicProfile(username) }
                    }
                    loadingProfiles.remove(requestKey)
                    if (currentAccountKey() == accountKey) {
                        result.onSuccess { profile -> updateProfile(key, profile) }
                    }
                }
            }
    }
}

internal data class MessageContext(val account: RadioMessageAccount, val groupId: Int, val generation: Int) {
    val stateKey: String = "${account.key}#$groupId"

    fun matches(accountKey: String?, groupId: Int, generation: Int, closed: Boolean): Boolean =
        !closed && this.generation == generation && account.key == accountKey && this.groupId == groupId
}

internal data class RadioHistoryState(val nextCursor: String = "", val hasMore: Boolean = true)

internal data class RadioSyncSnapshot(
    val messages: List<RadioMessage>,
    val hasMoreHistory: Boolean,
    val historyState: RadioHistoryState
)

internal data class OlderRadioHistoryResult(
    val messages: List<RadioMessage>,
    val hasMore: Boolean,
    val historyState: RadioHistoryState
)

internal suspend fun <T> radioMessageAttempt(block: suspend () -> T): Result<T> = runCatching { block() }
    .onFailure { error -> if (error is CancellationException) throw error }
