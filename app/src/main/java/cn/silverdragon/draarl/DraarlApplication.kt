package cn.silverdragon.draarl

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path.Companion.toOkioPath

class DraarlApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(AVATAR_CACHE_DIRECTORY).toOkioPath())
                .maxSizeBytes(AVATAR_CACHE_MAX_BYTES)
                .build()
        }
        .build()

    private companion object {
        const val AVATAR_CACHE_DIRECTORY = "avatar_images"
        const val AVATAR_CACHE_MAX_BYTES = 64L * 1024L * 1024L
    }
}
