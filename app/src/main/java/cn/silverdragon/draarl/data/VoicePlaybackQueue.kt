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

    /**
     * Finds the next incoming voice after [currentId]. A played voice is a hard
     * boundary so a manual play chain never jumps across an already-heard item.
     */
    fun nextUnplayedAfter(
        messages: List<RadioMessage>,
        currentId: String,
        skippedIds: Set<String> = emptySet(),
    ): RadioMessage? {
        val currentIndex = messages.indexOfFirst { it.id == currentId }
        return currentIndex.takeIf { it >= 0 }?.let { index ->
            messages
                .asSequence()
                .drop(index + 1)
                .takeWhile { message ->
                    message.type != RadioMessageType.VOICE || message.mine || !message.played
                }
                .firstOrNull { message -> message.id !in skippedIds && isUnplayed(message) }
        }
    }
}
