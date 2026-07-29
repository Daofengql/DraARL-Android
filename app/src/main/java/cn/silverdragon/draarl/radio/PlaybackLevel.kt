package cn.silverdragon.draarl.radio

import kotlin.math.log10
import kotlin.math.sqrt

internal fun normalizedPcmLevel(samples: ShortArray): Float {
    if (samples.isEmpty()) return 0f
    var sumSquares = 0.0
    for (sample in samples) {
        val normalized = sample / Short.MAX_VALUE.toDouble()
        sumSquares += normalized * normalized
    }
    val rms = sqrt(sumSquares / samples.size).coerceAtLeast(MIN_RMS)
    val decibels = 20.0 * log10(rms)
    return ((decibels - MIN_DECIBELS) / -MIN_DECIBELS).toFloat().coerceIn(0f, 1f)
}

private const val MIN_RMS = 0.001
private const val MIN_DECIBELS = -60.0
