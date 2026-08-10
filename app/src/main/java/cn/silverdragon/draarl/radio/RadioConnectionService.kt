package cn.silverdragon.draarl.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import cn.silverdragon.draarl.MainActivity
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioStatus

interface RadioServiceListener {
    fun onRadioStatus(status: RadioStatus)
    fun onRadioMessage(message: RadioMessage)
    fun onPlaybackState(messageId: String?)
    fun onPlaybackLevel(level: Float)
    fun onTransmitLevel(level: Float)
    fun onCwPreviewState(active: Boolean)
}

class RadioConnectionService :
    Service(),
    UdpRadioListener {
    private val binder = LocalBinder()
    private lateinit var radioClient: UdpRadioClient
    private lateinit var pttOverlay: PttOverlayWindow

    @Volatile private var foreground = false

    @Volatile private var overlayFeatureEnabled = false
    private val messageDispatcher = RadioMessageDispatcher(MAX_PENDING_MESSAGES)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        radioClient = UdpRadioClient(applicationContext, this)
        pttOverlay = PttOverlayWindow(
            context = applicationContext,
            onStartPtt = ::startPtt,
            onStopPtt = radioClient::stopPtt
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnect()
        } else {
            ensureForeground(radioClient.snapshot().copy(phase = RadioConnectionPhase.CONNECTING))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        messageDispatcher.clear()
        pttOverlay.hide()
        radioClient.release()
        super.onDestroy()
    }

    override fun onStatus(status: RadioStatus) {
        messageDispatcher.listener?.onRadioStatus(status)
        pttOverlay.updateStatus(status)
        when (RadioServiceStatePolicy.foregroundAction(status.phase, overlayFeatureEnabled)) {
            RadioServiceForegroundAction.ENSURE -> ensureForeground(status)
            RadioServiceForegroundAction.UPDATE -> updateNotification(status)
            RadioServiceForegroundAction.STOP -> stopForegroundService()
        }
    }

    override fun onMessage(message: RadioMessage) {
        val target = messageDispatcher.dispatch(message)
        target?.onRadioMessage(message)
    }

    override fun onPlaybackState(messageId: String?) {
        messageDispatcher.listener?.onPlaybackState(messageId)
    }

    override fun onPlaybackLevel(level: Float) {
        messageDispatcher.listener?.onPlaybackLevel(level)
    }

    override fun onTransmitLevel(level: Float) {
        messageDispatcher.listener?.onTransmitLevel(level)
    }

    override fun onCwPreviewState(active: Boolean) {
        messageDispatcher.listener?.onCwPreviewState(active)
    }

    private fun connect(config: RadioConnectionConfig) {
        ensureForeground(
            RadioStatus(
                phase = RadioConnectionPhase.CONNECTING,
                endpoint = config.accessPoint.address,
                groupId = config.groupId
            )
        )
        radioClient.connect(config)
    }

    private fun disconnect() {
        radioClient.disconnect()
    }

    private fun ensureForeground(status: RadioStatus) {
        val notification = buildNotification(status)
        if (!foreground) {
            startRadioForeground(notification, microphone = status.transmitting || overlayFeatureEnabled)
            foreground = true
        } else {
            startRadioForeground(notification, microphone = status.transmitting || overlayFeatureEnabled)
        }
    }

    private fun updateNotification(status: RadioStatus) {
        if (foreground) notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun stopForegroundService() {
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun buildNotification(status: RadioStatus): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RadioConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = RadioServiceStatePolicy.notificationTitle(status, overlayFeatureEnabled)
        val detail = listOf(status.callsign, status.endpoint).filter(String::isNotBlank).joinToString(" · ")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "断开", disconnectIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createNotificationChannelApi26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelApi26() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "在线通信",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 DraARL UDP 通信连接"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startRadioForeground(notification: Notification, microphone: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForegroundApi34(notification, microphone)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForegroundApi30(notification, microphone)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startForegroundApi34(notification: Notification, microphone: Boolean) {
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or
            if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        startForeground(NOTIFICATION_ID, notification, type)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startForegroundApi30(notification: Notification, microphone: Boolean) {
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
            if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        startForeground(NOTIFICATION_ID, notification, type)
    }

    private fun startPtt(): Boolean {
        val current = radioClient.snapshot()
        return runCatching {
            startRadioForeground(buildNotification(current.copy(transmitting = true)), microphone = true)
            radioClient.startPtt().also { started ->
                if (!started) {
                    startRadioForeground(buildNotification(current), microphone = overlayFeatureEnabled)
                }
            }
        }.getOrDefault(false)
    }

    private fun configurePttOverlay(enabled: Boolean, visible: Boolean, groupName: String): Boolean {
        overlayFeatureEnabled = enabled
        pttOverlay.updateGroupName(groupName)
        val visibleResult = if (enabled && visible) {
            pttOverlay.show(groupName)
        } else {
            pttOverlay.hide()
            true
        }
        val status = radioClient.snapshot()
        if (enabled || status.connected) {
            ensureForeground(status)
        } else {
            stopForegroundService()
        }
        return visibleResult
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    inner class LocalBinder : Binder() {
        fun setListener(value: RadioServiceListener?) {
            val pending = messageDispatcher.setListener(value)
            value?.onRadioStatus(radioClient.snapshot())
            pending.forEach { value?.onRadioMessage(it) }
        }

        fun connect(config: RadioConnectionConfig) = this@RadioConnectionService.connect(config)
        fun disconnect() = this@RadioConnectionService.disconnect()
        fun sendText(text: String): Boolean = radioClient.sendText(text)
        fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean =
            radioClient.sendCw(text, wordsPerMinute, toneHz)
        fun stopCw(): Boolean = radioClient.stopCw()
        fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean =
            radioClient.previewCw(text, wordsPerMinute, toneHz)
        fun stopCwPreview(): Boolean = radioClient.stopCwPreview()
        fun startPtt(): Boolean = this@RadioConnectionService.startPtt()
        fun stopPtt() = radioClient.stopPtt()
        fun togglePlayback(message: RadioMessage): Boolean = radioClient.togglePlayback(message)
        fun stopPlayback() = radioClient.stopPlayback()
        fun setMuted(muted: Boolean) = radioClient.setMuted(muted)
        fun setPlaybackDenoiseEnabled(enabled: Boolean) = radioClient.setPlaybackDenoiseEnabled(enabled)
        fun setPlaybackDenoiseWetMix(value: Float) = radioClient.setPlaybackDenoiseWetMix(value)
        fun setTransmitTimeoutSeconds(seconds: Int) = radioClient.setTransmitTimeoutSeconds(seconds)
        fun setTransmitTailTone(tone: TransmitTailTone) = radioClient.setTransmitTailTone(tone)
        fun setTransmitTailToneToRemoteEnabled(enabled: Boolean) =
            radioClient.setTransmitTailToneToRemoteEnabled(enabled)
        fun setReceiveTailToneEnabled(enabled: Boolean) = radioClient.setReceiveTailToneEnabled(enabled)
        fun transmitTimeoutSeconds(): Int = radioClient.transmitTimeoutSeconds()
        fun audioCacheSizeBytes(): Long = radioClient.audioCacheSizeBytes()
        fun hasAudioCacheKey(key: String): Boolean = radioClient.hasAudioCacheKey(key)
        fun clearAudioCache() = radioClient.clearAudioCache()
        fun setRouting(groupId: Int, receiveGroupIds: Collection<Int>) =
            radioClient.setRouting(groupId, receiveGroupIds)
        fun configurePttOverlay(enabled: Boolean, visible: Boolean, groupName: String): Boolean =
            this@RadioConnectionService.configurePttOverlay(enabled, visible, groupName)
        fun updateAccessToken(token: String) = radioClient.updateAccessToken(token)
        fun snapshot(): RadioStatus = radioClient.snapshot()
    }

    companion object {
        private const val CHANNEL_ID = "draarl_radio_connection"
        private const val NOTIFICATION_ID = 101
        private const val MAX_PENDING_MESSAGES = 100
        private const val ACTION_DISCONNECT = "cn.silverdragon.draarl.action.DISCONNECT"

        fun startIntent(context: Context): Intent = Intent(context, RadioConnectionService::class.java)

        fun disconnectIntent(context: Context): Intent = Intent(context, RadioConnectionService::class.java)
            .setAction(ACTION_DISCONNECT)
    }
}
