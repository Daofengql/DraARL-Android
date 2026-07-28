package cn.silverdragon.draarl.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class CurrentLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    suspend fun locate(): Location {
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFineLocation && !hasCoarseLocation) {
            error("需要定位权限才能发送当前位置")
        }

        val providers = buildList {
            if (hasFineLocation && providerEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (providerEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (providers.isEmpty()) error("请先开启系统定位服务")

        val cached = providers.mapNotNull(::lastKnownLocation)
        cached.filter { it.isRecent() }
            .maxByOrNull { if (it.provider == LocationManager.GPS_PROVIDER) 1 else 0 }
            ?.let { return it }

        providers.forEach { provider ->
            val timeoutMs = if (provider == LocationManager.GPS_PROVIDER) GPS_TIMEOUT_MS else NETWORK_TIMEOUT_MS
            withTimeoutOrNull(timeoutMs) { requestCurrentLocation(provider) }?.let { return it }
        }
        return cached.filter { it.isUsableFallback() }
            .maxByOrNull(Location::getTime)
            ?: error("暂时无法获取位置，请到开阔区域后重试")
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun providerEnabled(provider: String): Boolean = runCatching {
        locationManager.isProviderEnabled(provider)
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(provider: String): Location? = runCatching {
        locationManager.getLastKnownLocation(provider)
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellationSignal = CancellationSignal()
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
        } else {
            @Suppress("DEPRECATION")
            val listener = LocationListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }

    private fun Location.isRecent(): Boolean =
        System.currentTimeMillis() - time <= RECENT_LOCATION_MAX_AGE_MS

    private fun Location.isUsableFallback(): Boolean =
        System.currentTimeMillis() - time <= FALLBACK_LOCATION_MAX_AGE_MS

    private companion object {
        const val RECENT_LOCATION_MAX_AGE_MS = 2 * 60 * 1_000L
        const val FALLBACK_LOCATION_MAX_AGE_MS = 15 * 60 * 1_000L
        const val GPS_TIMEOUT_MS = 7_000L
        const val NETWORK_TIMEOUT_MS = 4_000L
    }
}
