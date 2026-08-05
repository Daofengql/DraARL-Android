package cn.silverdragon.draarl.data

import java.util.Locale

internal object ChannelMessageMapper {
    fun toRadioMessage(message: ChannelMessage, accountUser: User, timestamp: Long): RadioMessage {
        val mine = message.sender.ghost && message.sender.ssid == 101 &&
            message.sender.username.equals(accountUser.username, ignoreCase = true)
        val voice = message.messageType.equals("voice", ignoreCase = true)
        return RadioMessage(
            id = "record-${message.id}",
            type = if (voice) RadioMessageType.VOICE else RadioMessageType.TEXT,
            senderCallsign = message.sender.callsign.ifBlank { message.sender.username },
            senderSsid = message.sender.ssid,
            senderUsername = message.sender.username,
            senderNickname = message.sender.nickname,
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
