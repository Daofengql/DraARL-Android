package cn.silverdragon.draarl.radio

import kotlin.math.PI
import kotlin.math.sin

internal data class CwTone(
    val normalizedText: String,
    val samples: ShortArray,
) {
    val durationMs: Long
        get() = samples.size * 1_000L / OpusAudioFormat.SAMPLE_RATE
}

internal object CwToneGenerator {
    fun generate(text: String, wordsPerMinute: Int, toneHz: Int): CwTone {
        val normalized = normalize(text)
        require(normalized.isNotBlank()) { "请输入字母、数字或常用 CW 符号" }
        require(normalized.length <= MAX_TEXT_LENGTH) { "CW 内容不能超过 $MAX_TEXT_LENGTH 个字符" }

        val wpm = wordsPerMinute.coerceIn(MIN_WPM, MAX_WPM)
        val frequency = toneHz.coerceIn(MIN_TONE_HZ, MAX_TONE_HZ)
        val unitSamples = (OpusAudioFormat.SAMPLE_RATE * 1.2 / wpm).toInt().coerceAtLeast(1)
        val segments = buildSegments(normalized)
        val rawSampleCount = segments.sumOf { it.units * unitSamples }
        val packetSamples = OpusAudioFormat.FRAME_SAMPLES * OpusAudioFormat.FRAMES_PER_PACKET
        val paddedSampleCount = ((rawSampleCount + packetSamples - 1) / packetSamples) * packetSamples
        val samples = ShortArray(paddedSampleCount)
        val fadeSamples = minOf((OpusAudioFormat.SAMPLE_RATE * FADE_MS / 1_000), unitSamples / 3).coerceAtLeast(1)
        var offset = 0

        segments.forEach { segment ->
            val segmentSamples = segment.units * unitSamples
            if (segment.tone) {
                repeat(segmentSamples) { index ->
                    val attack = (index.toDouble() / fadeSamples).coerceIn(0.0, 1.0)
                    val release = ((segmentSamples - 1 - index).toDouble() / fadeSamples).coerceIn(0.0, 1.0)
                    val envelope = minOf(attack, release)
                    val phase = 2.0 * PI * frequency * index / OpusAudioFormat.SAMPLE_RATE
                    samples[offset + index] = (sin(phase) * envelope * Short.MAX_VALUE * AMPLITUDE).toInt().toShort()
                }
            }
            offset += segmentSamples
        }
        return CwTone(normalized, samples)
    }

    fun normalize(text: String): String = text
        .trim()
        .uppercase()
        .map { character -> if (character == '\n' || character == '\t') ' ' else character }
        .filter { it == ' ' || MORSE.containsKey(it) }
        .joinToString("")
        .replace(Regex(" +"), " ")

    private fun buildSegments(text: String): List<Segment> = buildList {
        text.forEachIndexed { characterIndex, character ->
            if (character == ' ') return@forEachIndexed
            val code = MORSE.getValue(character)
            code.forEachIndexed { symbolIndex, symbol ->
                add(Segment(tone = true, units = if (symbol == '-') 3 else 1))
                if (symbolIndex != code.lastIndex) add(Segment(tone = false, units = 1))
            }
            if (characterIndex != text.lastIndex) {
                add(Segment(tone = false, units = if (text[characterIndex + 1] == ' ') 7 else 3))
            }
        }
    }

    private data class Segment(val tone: Boolean, val units: Int)

    private val MORSE = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
        '9' to "----.", '/' to "-..-.", '?' to "..--..", '.' to ".-.-.-", ',' to "--..--",
        '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '@' to ".--.-.",
    )

    const val MIN_WPM = 8
    const val MAX_WPM = 40
    const val MIN_TONE_HZ = 400
    const val MAX_TONE_HZ = 1_000
    const val MAX_TEXT_LENGTH = 80
    private const val FADE_MS = 5
    private const val AMPLITUDE = 0.62
}
