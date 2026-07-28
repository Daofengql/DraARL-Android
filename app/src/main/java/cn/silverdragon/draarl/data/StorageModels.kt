package cn.silverdragon.draarl.data

data class StorageUsage(
    val audioBytes: Long = 0L,
    val avatarBytes: Long = 0L,
    val messageBytes: Long = 0L,
) {
    val totalBytes: Long get() = audioBytes + avatarBytes + messageBytes
}

enum class StorageCategory {
    AUDIO,
    AVATARS,
    MESSAGES,
    ALL,
}
