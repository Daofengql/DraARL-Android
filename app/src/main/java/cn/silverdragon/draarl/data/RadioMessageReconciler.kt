package cn.silverdragon.draarl.data

import kotlin.math.abs

internal object RadioMessageReconciler {
    const val REMOTE_SETTLE_DELAY_MS = 3 * 60 * 1_000L
    const val TEXT_MATCH_WINDOW_MS = 30 * 1_000L
    const val VOICE_MATCH_WINDOW_MS = 30 * 1_000L
    private const val LIVE_DELIVERY_WINDOW_MS = 1_500L

    fun matchWindowMs(type: RadioMessageType): Long = when (type) {
        RadioMessageType.VOICE -> VOICE_MATCH_WINDOW_MS
        else -> TEXT_MATCH_WINDOW_MS
    }

    fun settledRemoteMessages(
        messages: List<RadioMessage>,
        now: Long = System.currentTimeMillis(),
    ): List<RadioMessage> = messages.filter { it.timestamp <= now - REMOTE_SETTLE_DELAY_MS }

    fun isLikelySameEvent(first: RadioMessage, second: RadioMessage): Boolean {
        if (first.type != second.type || first.mine != second.mine) return false
        if (first.senderSsid != second.senderSsid) return false
        val sameSender = if (first.senderUsername.isNotBlank() && second.senderUsername.isNotBlank()) {
            first.senderUsername.equals(second.senderUsername, ignoreCase = true)
        } else {
            first.senderCallsign.equals(second.senderCallsign, ignoreCase = true)
        }
        if (!sameSender) return false
        if (abs(first.timestamp - second.timestamp) > matchWindowMs(first.type)) return false
        return when (first.type) {
            RadioMessageType.TEXT -> first.content == second.content
            RadioMessageType.VOICE -> voiceDurationsMatch(first.durationMs, second.durationMs)
            RadioMessageType.SYSTEM -> first.content == second.content
        }
    }

    fun isDuplicateLiveDelivery(first: RadioMessage, second: RadioMessage): Boolean {
        if (abs(first.timestamp - second.timestamp) > LIVE_DELIVERY_WINDOW_MS) return false
        return isLikelySameEvent(first, second)
    }

    fun isStillSettling(timestamp: Long, durationMs: Long, settleCutoff: Long): Boolean =
        timestamp + durationMs.coerceAtLeast(0L) > settleCutoff

    fun shouldRemoveFromAuthoritativeWindow(
        serverRecordId: Int?,
        timestamp: Long,
        durationMs: Long,
        authoritativeRecordIds: Set<Int>,
        window: LongRange,
    ): Boolean {
        if (timestamp !in window) return false
        if (serverRecordId == null && isStillSettling(timestamp, durationMs, window.last)) return false
        return serverRecordId == null || serverRecordId !in authoritativeRecordIds
    }

    fun deduplicate(messages: List<RadioMessage>): List<RadioMessage> {
        val result = messages
            .filter { it.syncState == RadioMessageSyncState.LOCAL }
            .distinctBy(RadioMessage::id)
            .toMutableList()
        messages.filter { it.syncState == RadioMessageSyncState.CONFIRMED }
            .sortedBy(RadioMessage::timestamp)
            .forEach { message ->
            val exactMatch = result.indexOfFirst { existing ->
                existing.id == message.id || existing.serverRecordId != null && existing.serverRecordId == message.serverRecordId
            }
            val localMatch = result.indices
                .filter { index ->
                    result[index].syncState == RadioMessageSyncState.LOCAL &&
                        isLikelySameEvent(result[index], message)
                }
                .minByOrNull { index -> abs(result[index].timestamp - message.timestamp) }
            val matchIndex = exactMatch.takeIf { it >= 0 } ?: localMatch ?: -1
            if (matchIndex < 0) {
                result += message
            } else {
                val existing = result[matchIndex]
                val cached = if (existing.audioCacheKey.isNotBlank()) existing else message
                result[matchIndex] = message.copy(audioCacheKey = cached.audioCacheKey)
            }
        }
        return result.sortedBy(RadioMessage::timestamp)
    }

    private fun voiceDurationsMatch(first: Long, second: Long): Boolean {
        if (first <= 0L || second <= 0L) return true
        val tolerance = maxOf(1_500L, maxOf(first, second) / 3)
        return abs(first - second) <= tolerance
    }
}
