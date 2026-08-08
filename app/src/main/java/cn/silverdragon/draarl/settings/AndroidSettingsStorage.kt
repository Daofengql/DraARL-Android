package cn.silverdragon.draarl.settings

import android.content.Context
import cn.silverdragon.draarl.data.RadioMessageStore
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.data.StorageUsage
import coil3.SingletonImageLoader
import java.io.File

internal class AndroidSettingsStorage(
    context: Context,
    private val messageStore: RadioMessageStore,
    private val clearBoundAudioCache: () -> Boolean
) : SettingsStorage {
    private val appContext = context.applicationContext
    private val audioCacheDirectory = File(appContext.filesDir, AUDIO_CACHE_DIRECTORY)
    private val avatarCacheDirectory = File(appContext.cacheDir, AVATAR_CACHE_DIRECTORY)

    override fun calculateUsage(): StorageUsage = StorageUsage(
        audioBytes = audioCacheDirectory.directorySizeBytes(),
        avatarBytes = avatarCacheDirectory.directorySizeBytes(),
        messageBytes = messageStore.sizeBytes()
    )

    override fun clear(category: StorageCategory) {
        if (category == StorageCategory.AUDIO || category == StorageCategory.ALL) {
            if (!clearBoundAudioCache()) audioCacheDirectory.clearContents()
        }
        if (category == StorageCategory.AVATARS || category == StorageCategory.ALL) {
            val imageLoader = SingletonImageLoader.get(appContext)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }
        if (category == StorageCategory.MESSAGES || category == StorageCategory.ALL) {
            messageStore.clearAll()
        }
    }

    private fun File.directorySizeBytes(): Long = if (!exists()) {
        0L
    } else {
        walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    private fun File.clearContents() {
        listFiles()?.forEach(File::deleteRecursively)
    }

    private companion object {
        const val AUDIO_CACHE_DIRECTORY = "radio_audio"
        const val AVATAR_CACHE_DIRECTORY = "avatar_images"
    }
}
