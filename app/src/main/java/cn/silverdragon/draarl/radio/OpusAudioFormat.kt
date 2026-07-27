package cn.silverdragon.draarl.radio

internal object OpusAudioFormat {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val FRAME_SAMPLES = 960
    const val MAX_DECODE_SAMPLES = 5_760
    const val FRAMES_PER_PACKET = 2
    const val BIT_RATE = 16_000
    const val MAX_ENCODED_FRAME = 400
}
