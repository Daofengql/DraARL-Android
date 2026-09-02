package cn.silverdragon.draarl

import android.app.Application
import android.content.Context
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.network.ApiClient
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import okio.Path.Companion.toOkioPath

class DraarlApplication :
    Application(),
    SingletonImageLoader.Factory {
    val sessionStore: SecureSessionStore by lazy { SecureSessionStore(this) }
    val apiClient: ApiClient by lazy { ApiClient(sessionStore) }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
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
