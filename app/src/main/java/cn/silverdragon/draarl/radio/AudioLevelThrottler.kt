package cn.silverdragon.draarl.radio

import kotlin.math.abs
import kotlin.math.max

internal class AudioLevelThrottler(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val minimumChange: Float = DEFAULT_MINIMUM_CHANGE,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastEmissionAt: Long? = null
    private var lastEmittedLevel = 0f
    private var pendingPeak = 0f

    init {
        require(intervalMillis >= 0L)
        require(minimumChange >= 0f)
    }

    @Synchronized
    fun update(level: Float): Float? {
        val normalized = level.coerceIn(0f, 1f)
        val now = clockMillis()
        if (normalized == 0f) {
            pendingPeak = 0f
            lastEmissionAt = now
            if (lastEmittedLevel == 0f) return null
            lastEmittedLevel = 0f
            return 0f
        }

        pendingPeak = max(pendingPeak, normalized)
        val previousEmissionAt = lastEmissionAt
        if (previousEmissionAt != null && now - previousEmissionAt < intervalMillis) return null

        val candidate = pendingPeak
        pendingPeak = 0f
        lastEmissionAt = now
        if (previousEmissionAt != null && abs(candidate - lastEmittedLevel) < minimumChange) return null
        lastEmittedLevel = candidate
        return candidate
    }

    @Synchronized
    fun reset() {
        lastEmissionAt = null
        lastEmittedLevel = 0f
        pendingPeak = 0f
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 50L
        const val DEFAULT_MINIMUM_CHANGE = 0.01f
    }
}
