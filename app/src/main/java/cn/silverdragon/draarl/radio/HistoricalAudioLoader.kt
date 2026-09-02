package cn.silverdragon.draarl.radio

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class HistoricalAudioLoader(
    private val audioCache: RadioAudioStore,
    private val downloader: (String) -> ByteArray = ::downloadHttps
) {
    fun load(audioCacheKey: String, audioUrl: String): ByteArray {
        val cacheKey = audioCacheKey.ifBlank { audioUrl }
        audioCache.get(cacheKey)?.let { return it }
        require(audioUrl.isNotBlank()) { "本地语音缓存已失效" }
        val bytes = downloader(audioUrl)
        require(bytes.size <= MAX_RAW_OPUS_RECORDING_BYTES) { "语音记录超过 16 MiB 限制" }
        RawOpusRecording.decode(bytes)
        audioCache.put(cacheKey, bytes)
        return bytes
    }

    companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 15_000

        private fun downloadHttps(audioUrl: String): ByteArray {
            val uri = runCatching { URI(audioUrl) }.getOrNull()
            require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) { "语音记录地址必须使用 HTTPS" }
            val connection = URL(audioUrl).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = DOWNLOAD_TIMEOUT_MS
                connection.readTimeout = DOWNLOAD_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/octet-stream")
                val status = connection.responseCode
                require(connection.url.protocol.equals("https", ignoreCase = true)) {
                    "语音记录重定向到了不安全的地址"
                }
                require(status in 200..299) { "语音记录下载失败 ($status)" }
                val declaredSize = connection.contentLengthLong
                require(declaredSize <= MAX_RAW_OPUS_RECORDING_BYTES) { "语音记录超过 16 MiB 限制" }
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_RAW_OPUS_RECORDING_BYTES) { "语音记录超过 16 MiB 限制" }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
