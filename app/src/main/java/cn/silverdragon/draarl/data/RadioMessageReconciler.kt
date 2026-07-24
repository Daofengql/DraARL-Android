package cn.silverdragon.draarl.data

import kotlin.math.abs

internal object RadioMessageReconciler {
    const val REMOTE_SETTLE_DELAY_MS = 3 * 60 * 1_000L
    const val TEXT_MATCH_WINDOW_MS = 2 * 60 * 1_000L
    const val VOICE_MATCH_WINDOW_MS = 3 * 60 * 1_000L

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
        if (!first.senderCallsign.equals(second.senderCallsign, ignoreCase = true)) return false
        if (abs(first.timestamp - second.timestamp) > matchWindowMs(first.type)) return false
        return first.type == RadioMessageType.VOICE || first.content == second.content
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
}
