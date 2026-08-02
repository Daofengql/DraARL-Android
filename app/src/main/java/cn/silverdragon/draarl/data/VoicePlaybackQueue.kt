package cn.silverdragon.draarl.data

internal object VoicePlaybackQueue {
    fun isUnplayed(message: RadioMessage): Boolean =
        message.type == RadioMessageType.VOICE &&
            !message.mine &&
            !message.played &&
            isPlayable(message)

    fun isPlayable(message: RadioMessage): Boolean =
        message.type == RadioMessageType.VOICE && (
            message.audioCacheKey.isNotBlank() ||
                message.audioUrl.isNotBlank() ||
                message.serverRecordId != null
            )

    fun nextUnplayed(
        messages: List<RadioMessage>,
        skippedIds: Set<String> = emptySet(),
    ): RadioMessage? = messages.firstOrNull { it.id !in skippedIds && isUnplayed(it) }
}
