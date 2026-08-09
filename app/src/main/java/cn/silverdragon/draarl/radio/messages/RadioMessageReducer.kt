package cn.silverdragon.draarl.radio.messages

import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageReconciler
import cn.silverdragon.draarl.data.RadioMessageSyncState
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.VoicePlaybackQueue

internal object RadioMessageReducer {
    fun replaceCached(
        current: List<RadioMessage>,
        cached: List<RadioMessage>,
        currentTimeMillis: Long
    ): List<RadioMessage> {
        if (current == cached) return current
        val cachedIds = cached.mapTo(HashSet()) { it.id }
        val localSettleCutoff = currentTimeMillis - RadioMessageReconciler.REMOTE_SETTLE_DELAY_MS
        val pending = current.filter { local ->
            local.syncState == RadioMessageSyncState.LOCAL &&
                RadioMessageReconciler.isStillSettling(
                    local.timestamp,
                    local.durationMs,
                    localSettleCutoff
                ) &&
                local.id !in cachedIds &&
                cached.none { remote ->
                    remote.syncState == RadioMessageSyncState.CONFIRMED &&
                        RadioMessageReconciler.isLikelySameEvent(local, remote)
                }
        }
        return RadioMessageReconciler.deduplicate(cached + pending).takeLast(MAX_MESSAGES)
    }

    fun mergeLive(current: List<RadioMessage>, message: RadioMessage): Pair<List<RadioMessage>, RadioMessage> {
        val messages = current.toMutableList()
        val duplicateIndex = messages.indexOfLast { existing ->
            RadioMessageReconciler.isDuplicateLiveDelivery(existing, message)
        }
        val messageToStore = if (duplicateIndex >= 0) {
            val existing = messages[duplicateIndex]
            existing.copy(
                senderUsername = existing.senderUsername.ifBlank { message.senderUsername },
                senderNickname = existing.senderNickname.ifBlank { message.senderNickname },
                senderCallsign = existing.senderCallsign.ifBlank { message.senderCallsign },
                audioCacheKey = existing.audioCacheKey.ifBlank { message.audioCacheKey },
                durationMs = maxOf(existing.durationMs, message.durationMs)
            ).also { messages[duplicateIndex] = it }
        } else {
            message.also(messages::add)
        }
        return messages.takeLast(MAX_MESSAGES) to messageToStore
    }

    fun enrich(message: RadioMessage, accountUser: User, identity: RadioMessageIdentityContext): RadioMessage {
        val online = identity.onlineDevices.firstOrNull { device ->
            val usernameMatches = message.senderUsername.isNotBlank() &&
                device.username.equals(message.senderUsername, true)
            val callsignMatches = message.senderCallsign.isNotBlank() &&
                device.callsign.equals(message.senderCallsign, true)
            device.ssid == message.senderSsid && (usernameMatches || callsignMatches)
        }
        val currentClientMessage = !message.mine &&
            message.senderUsername.equals(accountUser.username, ignoreCase = true) &&
            message.senderSsid == identity.currentSsid
        val mine = message.mine || currentClientMessage
        return message.copy(
            mine = mine,
            played = message.played || mine || (message.type == RadioMessageType.VOICE && !identity.muted),
            senderUsername = message.senderUsername.ifBlank {
                if (mine) accountUser.username else online?.username.orEmpty()
            },
            senderNickname = message.senderNickname.ifBlank {
                if (mine) accountUser.nickname else online?.nickname.orEmpty()
            },
            senderCallsign = message.senderCallsign.ifBlank {
                if (mine) accountUser.callsign else online?.callsign.orEmpty()
            }
        )
    }

    fun update(current: List<RadioMessage>, message: RadioMessage): List<RadioMessage> {
        val messages = current.toMutableList()
        val index = messages.indexOfFirst { existing ->
            existing.id == message.id ||
                (message.serverRecordId != null && existing.serverRecordId == message.serverRecordId)
        }
        if (index >= 0) messages[index] = message
        return messages
    }

    fun markPlayed(current: List<RadioMessage>, message: RadioMessage): List<RadioMessage> {
        if (message.type != RadioMessageType.VOICE || message.played) return current
        val messages = current.toMutableList()
        val index = messages.indexOfFirst { existing ->
            existing.id == message.id ||
                (message.serverRecordId != null && existing.serverRecordId == message.serverRecordId)
        }
        if (index >= 0 && !messages[index].played) messages[index] = messages[index].copy(played = true)
        return messages
    }

    fun markAllPlayed(current: List<RadioMessage>): List<RadioMessage> = current.map { message ->
        if (VoicePlaybackQueue.isUnplayed(message)) message.copy(played = true) else message
    }

    private const val MAX_MESSAGES = 1_000
}
