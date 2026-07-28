package cn.silverdragon.draarl.data

enum class AppDisplayScale(val shortestWidthDp: Int) {
    COMPACT(480),
    STANDARD(432),
    COMFORTABLE(384),
}

internal fun appDensityFor(shortestWindowPixels: Float, scale: AppDisplayScale): Float =
    (shortestWindowPixels / scale.shortestWidthDp).coerceAtLeast(0.5f)
