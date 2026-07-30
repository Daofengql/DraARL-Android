package cn.silverdragon.draarl.radio

import android.util.Log
import kotlin.math.roundToInt

enum class PlaybackDenoiseState {
    DISABLED,
    READY,
    ERROR,
}

internal class RnnoisePlaybackDenoiser {
    private val lock = Any()
    @Volatile private var enabled = false
    @Volatile private var wetMix = denoiseStrengthPercentToWetMix(PLAYBACK_DENOISE_DEFAULT_STRENGTH_PERCENT)
    private var failed = false
    private var handle = 0L

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) closeHandle()
    }

    fun setWetMix(value: Float) {
        wetMix = value.coerceIn(0f, 1f)
    }

    fun process(pcm: ShortArray): ShortArray {
        val mix = wetMix
        if (!enabled || pcm.isEmpty() || mix <= 0f) return pcm
        synchronized(lock) {
            if (!ensureHandleLocked()) return pcm
            val processed = pcm.copyOf()
            return if (RnnoiseNative.process(handle, processed)) {
                blendDenoisedPcm(pcm, processed, mix)
            } else {
                pcm
            }
        }
    }

    fun reset() = synchronized(lock) {
        if (handle != 0L && !RnnoiseNative.reset(handle)) {
            closeHandleLocked()
            failed = true
        }
    }

    fun release() = closeHandle()

    private fun ensureHandleLocked(): Boolean {
        if (handle != 0L) return true
        if (failed) return false
        val created = RnnoiseNative.create()
        if (created == 0L) {
            failed = true
            return false
        }
        handle = created
        return true
    }

    private fun closeHandle() = synchronized(lock) {
        closeHandleLocked()
    }

    private fun closeHandleLocked() {
        val active = handle
        handle = 0L
        if (active != 0L) RnnoiseNative.destroy(active)
    }
}

internal const val PLAYBACK_DENOISE_MIN_STRENGTH_PERCENT = 0
internal const val PLAYBACK_DENOISE_DEFAULT_STRENGTH_PERCENT = 50
internal const val PLAYBACK_DENOISE_MAX_STRENGTH_PERCENT = 100

internal fun denoiseStrengthPercentToWetMix(percent: Int): Float =
    percent.coerceIn(
        PLAYBACK_DENOISE_MIN_STRENGTH_PERCENT,
        PLAYBACK_DENOISE_MAX_STRENGTH_PERCENT,
    ) / 100f

internal fun blendDenoisedPcm(
    original: ShortArray,
    denoised: ShortArray,
    wetMix: Float,
): ShortArray {
    require(original.size == denoised.size) { "PCM buffers must have the same length" }
    val mix = wetMix.coerceIn(0f, 1f)
    if (mix == 1f) return denoised
    if (mix == 0f) return original
    val dryMix = 1f - mix
    return ShortArray(original.size) { index ->
        (original[index] * dryMix + denoised[index] * mix)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}

internal object RnnoiseNative {
    private val loaded: Boolean by lazy {
        runCatching {
            System.loadLibrary("draarl_rnnoise")
            true
        }.getOrElse { error ->
            Log.w("DraarlRnnoise", "RNNoise native library unavailable: ${error.message}")
            false
        }
    }

    fun create(): Long = if (loaded) nativeCreate() else 0L
    fun destroy(handle: Long) {
        if (loaded && handle != 0L) nativeDestroy(handle)
    }
    fun reset(handle: Long): Boolean = loaded && handle != 0L && nativeReset(handle)
    fun process(handle: Long, pcm: ShortArray): Boolean =
        loaded && handle != 0L && nativeProcess(handle, pcm)

    @JvmStatic private external fun nativeCreate(): Long
    @JvmStatic private external fun nativeDestroy(handle: Long)
    @JvmStatic private external fun nativeReset(handle: Long): Boolean
    @JvmStatic private external fun nativeProcess(handle: Long, pcm: ShortArray): Boolean
}
