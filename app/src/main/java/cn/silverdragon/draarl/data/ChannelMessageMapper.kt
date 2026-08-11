package cn.silverdragon.draarl.data

import java.util.Locale

internal object ChannelMessageMapper {
    fun toRadioMessage(message: ChannelMessage, accountUser: User, timestamp: Long): RadioMessage {
        val senderUsername = if (message.isAutoBroadcast) SYSTEM_BROADCAST_USERNAME else message.sender.username
        val senderCallsign = if (message.isAutoBroadcast) SYSTEM_BROADCAST_CALLSIGN else message.sender.callsign
        val senderNickname = if (message.isAutoBroadcast) SYSTEM_BROADCAST_NICKNAME else message.sender.nickname
        val senderSsid = if (message.isAutoBroadcast) SYSTEM_BROADCAST_SSID else message.sender.ssid
        val mine = !message.isAutoBroadcast && message.sender.ghost && message.sender.ssid == 101 &&
            message.sender.username.equals(accountUser.username, ignoreCase = true)
        val voice = message.messageType.equals("voice", ignoreCase = true)
        return RadioMessage(
            id = "record-${message.id}",
            type = if (voice) RadioMessageType.VOICE else RadioMessageType.TEXT,
            senderCallsign = senderCallsign.ifBlank { senderUsername },
            senderSsid = senderSsid,
            senderUsername = senderUsername,
            senderNickname = senderNickname,
            content = if (voice) "历史语音 ${formatDuration(message.durationMs)}" else message.text,
            timestamp = timestamp,
            mine = mine,
            durationMs = message.durationMs,
            audioUrl = message.audioUrl,
            serverRecordId = message.id,
            syncState = RadioMessageSyncState.CONFIRMED,
            groupId = message.sourceGroupId,
            played = mine,
        )
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1_000.0).coerceAtLeast(0.1)
        return String.format(Locale.CHINA, "%.1f 秒", seconds)
    }
}
