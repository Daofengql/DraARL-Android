package cn.silverdragon.draarl.radio.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.radio.RadioConnectionConfig
import cn.silverdragon.draarl.radio.RadioConnectionService
import cn.silverdragon.draarl.radio.RadioServiceListener
import cn.silverdragon.draarl.radio.denoiseStrengthPercentToWetMix
import cn.silverdragon.draarl.settings.RadioAudioSettings

internal interface RadioServiceConnectionObserver {
    fun onServiceConnected()

    fun onServiceDisconnected()
}

internal data class RadioServiceGateway(
    val connection: RadioServiceConnectionGateway,
    val controls: RadioServiceControls
)

internal interface RadioServiceConnectionGateway {
    fun setCallbacks(listener: RadioServiceListener, observer: RadioServiceConnectionObserver)

    fun bind(): Boolean

    fun startForeground()

    fun stopStartedService()

    fun connect(config: RadioConnectionConfig): Boolean

    fun disconnect()

    fun setRouting(groupId: Int, receiveGroupIds: Collection<Int>)

    fun updateAccessToken(token: String)

    fun applyAudioSettings(settings: RadioAudioSettings)

    fun configurePttOverlay(config: RadioPttOverlayConfig): Boolean

    fun close()
}

interface RadioServiceControls {
    fun sendText(text: String): Boolean

    fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean

    fun stopCw(): Boolean

    fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean

    fun stopCwPreview(): Boolean

    fun startPtt(): Boolean

    fun stopPtt()

    fun togglePlayback(message: RadioMessage): Boolean

    fun stopPlayback()

    fun hasAudioCacheKey(key: String): Boolean

    fun clearAudioCache(): Boolean
}

internal fun createAndroidRadioServiceGateway(context: Context): RadioServiceGateway {
    val connection = AndroidRadioServiceConnectionGateway(context)
    return RadioServiceGateway(
        connection = connection,
        controls = AndroidRadioServiceControls(connection)
    )
}

private class AndroidRadioServiceConnectionGateway(context: Context) : RadioServiceConnectionGateway {
    private val appContext = context.applicationContext
    private var binder: RadioConnectionService.LocalBinder? = null
    private var bindingRequested = false
    private var listener: RadioServiceListener? = null
    private var observer: RadioServiceConnectionObserver? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? RadioConnectionService.LocalBinder
            binder?.setListener(listener)
            if (binder != null) observer?.onServiceConnected() else observer?.onServiceDisconnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            observer?.onServiceDisconnected()
        }
    }

    override fun setCallbacks(listener: RadioServiceListener, observer: RadioServiceConnectionObserver) {
        this.listener = listener
        this.observer = observer
        binder?.setListener(listener)
    }

    override fun bind(): Boolean = when {
        binder != null -> {
            binder?.setListener(listener)
            observer?.onServiceConnected()
            true
        }

        bindingRequested -> true

        else -> {
            bindingRequested = appContext.bindService(
                Intent(appContext, RadioConnectionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            bindingRequested
        }
    }

    override fun startForeground() {
        ContextCompat.startForegroundService(appContext, RadioConnectionService.startIntent(appContext))
    }

    override fun stopStartedService() {
        appContext.startService(RadioConnectionService.disconnectIntent(appContext))
    }

    override fun connect(config: RadioConnectionConfig): Boolean = binder?.let {
        it.connect(config)
        true
    } ?: false

    override fun disconnect() {
        binder?.disconnect() ?: run {
            if (bindingRequested) stopStartedService()
        }
    }

    override fun setRouting(groupId: Int, receiveGroupIds: Collection<Int>) {
        binder?.setRouting(groupId, receiveGroupIds)
    }

    override fun updateAccessToken(token: String) {
        binder?.updateAccessToken(token)
    }

    override fun applyAudioSettings(settings: RadioAudioSettings) {
        binder?.setMuted(settings.muted)
        binder?.setPlaybackDenoiseWetMix(
            denoiseStrengthPercentToWetMix(settings.playbackDenoiseStrengthPercent)
        )
        binder?.setPlaybackDenoiseEnabled(settings.playbackDenoiseEnabled)
        binder?.setTransmitTimeoutSeconds(settings.transmitTimeoutSeconds)
        binder?.setTransmitTailTone(settings.transmitTailTone)
        binder?.setTransmitTailToneToRemoteEnabled(settings.transmitTailToneToRemoteEnabled)
        binder?.setReceiveTailToneEnabled(settings.receiveTailToneEnabled)
    }

    override fun configurePttOverlay(config: RadioPttOverlayConfig): Boolean =
        binder?.configurePttOverlay(config.enabled, config.visible, config.groupName) == true

    override fun close() {
        binder?.setListener(null)
        if (bindingRequested) runCatching { appContext.unbindService(connection) }
        binder = null
        bindingRequested = false
        listener = null
        observer = null
    }

    val currentBinder: RadioConnectionService.LocalBinder?
        get() = binder
}

private class AndroidRadioServiceControls(private val connection: AndroidRadioServiceConnectionGateway) :
    RadioServiceControls {
    override fun sendText(text: String): Boolean = connection.currentBinder?.sendText(text) == true

    override fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean =
        connection.currentBinder?.sendCw(text, wordsPerMinute, toneHz) == true

    override fun stopCw(): Boolean = connection.currentBinder?.stopCw() == true

    override fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean =
        connection.currentBinder?.previewCw(text, wordsPerMinute, toneHz) == true

    override fun stopCwPreview(): Boolean = connection.currentBinder?.stopCwPreview() == true

    override fun startPtt(): Boolean = connection.currentBinder?.startPtt() == true

    override fun stopPtt() {
        connection.currentBinder?.stopPtt()
    }

    override fun togglePlayback(message: RadioMessage): Boolean =
        connection.currentBinder?.togglePlayback(message) == true

    override fun stopPlayback() {
        connection.currentBinder?.stopPlayback()
    }

    override fun hasAudioCacheKey(key: String): Boolean = connection.currentBinder?.hasAudioCacheKey(key) == true

    override fun clearAudioCache(): Boolean = connection.currentBinder?.let {
        it.clearAudioCache()
        true
    } ?: false
}
