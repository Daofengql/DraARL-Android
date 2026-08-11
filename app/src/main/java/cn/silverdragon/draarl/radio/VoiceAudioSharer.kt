package cn.silverdragon.draarl.radio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cn.silverdragon.draarl.data.RadioMessage
import java.io.File

/** Loads a cached/raw Opus recording, converts it to PCM WAV, and exposes it to another app. */
class VoiceAudioSharer(context: Context) {
    private val appContext = context.applicationContext
    private val audioCache = RadioAudioCache(appContext.filesDir.resolve("radio_audio"))
    private val audioLoader = HistoricalAudioLoader(audioCache)
    private val shareDirectory = appContext.cacheDir.resolve(SHARE_DIRECTORY)

    fun hasCachedAudio(message: RadioMessage): Boolean =
        message.audioCacheKey.isNotBlank() && audioCache.contains(message.audioCacheKey)

    fun createWav(message: RadioMessage): File {
        require(message.type == cn.silverdragon.draarl.data.RadioMessageType.VOICE) {
            "只有语音消息可以分享"
        }
        val wavBytes = OpusRecordingWavEncoder.encode(
            audioLoader.load(message.audioCacheKey, message.audioUrl)
        )
        check(shareDirectory.exists() || shareDirectory.mkdirs()) { "无法创建临时分享目录" }
        deleteExpiredShares()
        val file = File.createTempFile("draarl-voice-", ".wav", shareDirectory)
        file.outputStream().buffered().use { it.write(wavBytes) }
        return file
    }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            clipData = ClipData.newRawUri("DraARL WAV 音频", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(
            Intent.createChooser(sendIntent, "分享 WAV 音频").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun deleteExpiredShares() {
        val expiration = System.currentTimeMillis() - SHARE_RETENTION_MS
        shareDirectory.listFiles { file ->
            file.isFile && file.extension == "wav" && file.lastModified() < expiration
        }?.forEach(File::delete)
    }

    companion object {
        private const val SHARE_DIRECTORY = "shared_audio"
        private const val SHARE_RETENTION_MS = 60 * 60 * 1_000L
    }
}
