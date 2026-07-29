package cn.silverdragon.draarl.aprs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AprsService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reportJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getIntExtra(EXTRA_USER_ID, 0) ?: 0
        if (userId <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        reportJob?.cancel()
        reportJob = serviceScope.launch {
            val store = AprsConfigStore(applicationContext)
            val client = AprsIsClient()
            val locationProvider = CurrentLocationProvider(applicationContext)
            var previousPosition: AprsPosition? = null
            while (isActive) {
                val config = store.load(userId)
                if (!config.enabled || !config.autoReport || config.callsign.isBlank()) {
                    delay(IDLE_DELAY_MS)
                    continue
                }
                val currentPosition = runCatching {
                    val location = locationProvider.locate()
                    AprsPosition(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    )
                }.getOrNull()
                if (currentPosition != null && (currentPosition.accuracyMeters ?: 0f) <= MAX_ACCEPTED_ACCURACY_METERS) {
                    val movedMeters = previousPosition?.let { distanceMeters(it, currentPosition) } ?: Float.POSITIVE_INFINITY
                    runCatching { client.sendPosition(config, currentPosition) }
                    previousPosition = currentPosition
                    val interval = if (movedMeters >= MOVING_DISTANCE_METERS) {
                        config.movingIntervalSeconds
                    } else {
                        config.stationaryIntervalSeconds
                    }
                    delay(interval.coerceIn(60, 3_600) * 1_000L)
                } else {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        reportJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.draarl_splash_logo)
            .setContentTitle("APRS 自动上报")
            .setContentText("正在按设置上报当前位置")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "APRS", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun distanceMeters(first: AprsPosition, second: AprsPosition): Float {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
        return (2 * radius * kotlin.math.atan2(sqrt(a), sqrt(1 - a))).toFloat()
    }

    companion object {
        private const val CHANNEL_ID = "aprs_reporting"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_USER_ID = "user_id"
        private const val IDLE_DELAY_MS = 60_000L
        private const val RETRY_DELAY_MS = 60_000L
        private const val MOVING_DISTANCE_METERS = 150f
        private const val MAX_ACCEPTED_ACCURACY_METERS = 100f

        fun startIntent(context: Context, userId: Int): Intent =
            Intent(context, AprsService::class.java).putExtra(EXTRA_USER_ID, userId)

        fun stopIntent(context: Context): Intent = Intent(context, AprsService::class.java)
    }
}
