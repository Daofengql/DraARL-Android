package cn.silverdragon.draarl.radio

import java.io.File
import java.security.MessageDigest

class RadioAudioCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    init {
        require(maxBytes > 0) { "语音缓存容量必须大于 0" }
    }

    @Synchronized
    fun get(key: String): ByteArray? {
        if (key.isBlank()) return null
        val file = fileFor(key)
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null || runCatching { RawOpusRecording.decode(bytes) }.isFailure) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return bytes
    }

    @Synchronized
    fun put(key: String, bytes: ByteArray) {
        require(key.isNotBlank()) { "语音缓存键不能为空" }
        RawOpusRecording.decode(bytes)
        check(directory.exists() || directory.mkdirs()) { "无法创建语音缓存目录" }
        val target = fileFor(key)
        val temporary = File.createTempFile("audio-", ".tmp", directory)
        try {
            temporary.outputStream().buffered().use { it.write(bytes) }
            if (target.exists() && !target.delete()) error("无法更新语音缓存")
            if (!temporary.renameTo(target)) error("无法写入语音缓存")
            target.setLastModified(System.currentTimeMillis())
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        trimToSize()
    }

    @Synchronized
    fun contains(key: String): Boolean = get(key) != null

    @Synchronized
    fun clear() {
        directory.listFiles { file -> file.isFile && file.extension == CACHE_EXTENSION }
            ?.forEach(File::delete)
    }

    @Synchronized
    fun sizeBytes(): Long = directory.listFiles { file ->
        file.isFile && file.extension == CACHE_EXTENSION
    }?.sumOf(File::length) ?: 0L

    private fun trimToSize() {
        val files = directory.listFiles { file -> file.isFile && file.extension == CACHE_EXTENSION }
            ?.sortedBy(File::lastModified)
            .orEmpty()
        var totalBytes = files.sumOf(File::length)
        for (file in files) {
            if (totalBytes <= maxBytes) break
            val length = file.length()
            if (file.delete()) totalBytes -= length
        }
    }

    private fun fileFor(key: String): File = File(directory, "${sha256(key)}.$CACHE_EXTENSION")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private const val CACHE_EXTENSION = "raw"
        private const val DEFAULT_MAX_BYTES = 96L * 1024L * 1024L
    }
}
