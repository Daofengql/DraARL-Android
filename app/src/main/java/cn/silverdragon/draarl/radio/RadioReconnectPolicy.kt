package cn.silverdragon.draarl.radio

import kotlin.math.max

object RadioReconnectPolicy {
    const val MIN_RETRY_DELAY_MS = 3_000L
    const val SERVER_SESSION_TIMEOUT_MS = 20_000L
    private const val EXPIRY_MARGIN_MS = 1_000L

    fun retryDelayMillis(lastPacketSentAt: Long, now: Long): Long {
        if (lastPacketSentAt <= 0L) return MIN_RETRY_DELAY_MS
        val serverSessionExpiresAt = lastPacketSentAt + SERVER_SESSION_TIMEOUT_MS + EXPIRY_MARGIN_MS
        return max(MIN_RETRY_DELAY_MS, serverSessionExpiresAt - now)
    }
}
