package cn.silverdragon.draarl.radio

import kotlin.math.PI
import kotlin.math.sin

enum class TransmitTailTone {
    OFF,
    SHORT_BEEP,
    MOTOROLA_STYLE,
    DOUBLE_BEEP,
    RISING_TRIPLE,
}

internal object TransmitTailToneGenerator {
    fun generate(preset: TransmitTailTone): ShortArray {
        val segments = when (preset) {
            TransmitTailTone.OFF -> return ShortArray(0)
            TransmitTailTone.SHORT_BEEP -> listOf(
                Segment(1_000, 130),
                Segment(null, 50),
            )
            TransmitTailTone.MOTOROLA_STYLE -> listOf(
                Segment(1_800, 65),
                Segment(null, 35),
                Segment(1_200, 115),
                Segment(null, 25),
            )
            TransmitTailTone.DOUBLE_BEEP -> listOf(
                Segment(900, 80),
                Segment(null, 55),
                Segment(1_200, 95),
                Segment(null, 10),
            )
            TransmitTailTone.RISING_TRIPLE -> listOf(
                Segment(700, 55),
                Segment(null, 25),
                Segment(950, 55),
                Segment(null, 25),
                Segment(1_250, 70),
                Segment(null, 10),
            )
        }
        val rawSamples = segments.sumOf { millisecondsToSamples(it.durationMs) }
        val packetSamples = OpusAudioFormat.FRAME_SAMPLES * OpusAudioFormat.FRAMES_PER_PACKET
        val paddedSamples = ((rawSamples + packetSamples - 1) / packetSamples) * packetSamples
        val output = ShortArray(paddedSamples)
        var offset = 0
        segments.forEach { segment ->
            val sampleCount = millisecondsToSamples(segment.durationMs)
            val frequency = segment.frequencyHz
            if (frequency != null) writeTone(output, offset, sampleCount, frequency)
            offset += sampleCount
        }
        return output
    }

    private fun writeTone(target: ShortArray, offset: Int, sampleCount: Int, frequencyHz: Int) {
        val fadeSamples = minOf(millisecondsToSamples(FADE_MS), sampleCount / 3).coerceAtLeast(1)
        repeat(sampleCount) { index ->
            val attack = (index.toDouble() / fadeSamples).coerceIn(0.0, 1.0)
            val release = ((sampleCount - 1 - index).toDouble() / fadeSamples).coerceIn(0.0, 1.0)
            val envelope = minOf(attack, release)
            val phase = 2.0 * PI * frequencyHz * index / OpusAudioFormat.SAMPLE_RATE
            target[offset + index] = (sin(phase) * envelope * Short.MAX_VALUE * AMPLITUDE).toInt().toShort()
        }
    }

    private fun millisecondsToSamples(milliseconds: Int): Int =
        OpusAudioFormat.SAMPLE_RATE * milliseconds / 1_000

    private data class Segment(val frequencyHz: Int?, val durationMs: Int)

    private const val FADE_MS = 5
    private const val AMPLITUDE = 0.58
}
