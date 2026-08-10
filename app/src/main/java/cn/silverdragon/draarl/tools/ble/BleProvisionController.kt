package cn.silverdragon.draarl.tools.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BleProvisionController internal constructor(
    clientFactory: BleProvisionClientFactory,
    private val mainDispatcher: BleProvisionMainDispatcher
) {
    constructor(context: Context) : this(
        clientFactory = BleProvisionClientFactory { onStatus, onDevices ->
            AndroidBleProvisionClient(context, onStatus, onDevices)
        },
        mainDispatcher = AndroidBleProvisionMainDispatcher()
    )

    private val closed = AtomicBoolean(false)
    private val operationGeneration = AtomicInteger(0)

    var status by mutableStateOf(BleProvisionStatus())
        private set
    var devices by mutableStateOf<List<BleDeviceInfo>>(emptyList())
        private set
    var config by mutableStateOf(BleProvisionConfig())
        private set
    var selectedProfileKey by mutableStateOf(BleDeviceProfiles.all.first().key)
        private set
    val selectedProfile: BleDeviceProfile
        get() = BleDeviceProfiles.find(selectedProfileKey)
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set
    private val client = clientFactory.create(
        onStatus = { value -> post { status = value } },
        onDevices = { value -> post { devices = value } }
    )

    fun startScan() = runAction {
        client.startScan()
        busy = false
    }

    fun stopScan() {
        client.stopScan()
        busy = false
    }

    fun connect(device: BleDeviceInfo) {
        if (closed.get()) return
        invalidatePendingOperation()
        runAction {
            client.connect(device.address, device.name)
            busy = false
        }
    }

    fun disconnect() {
        if (closed.get()) return
        invalidatePendingOperation()
        client.disconnect()
        message = ""
    }

    fun authenticate(code: String) = runAction(keepBusy = true) {
        val generation = operationGeneration.get()
        client.authenticate(code) { result ->
            postResult(generation, result, "认证成功") {
                status = client.currentStatus()
                loadConfig()
            }
        }
    }

    fun loadConfig() = runAction(keepBusy = true) {
        val generation = operationGeneration.get()
        client.loadConfig { result ->
            postOperation(generation) {
                busy = false
                result.onSuccess {
                    config = it
                    error = ""
                    message = "已读取设备配置"
                }.onFailure(::setError)
            }
        }
    }

    fun updateWifi(value: BleWifiConfig) {
        config = config.copy(wifi = value)
    }

    fun selectProfile(key: String) {
        selectedProfileKey = BleDeviceProfiles.find(key).key
    }

    fun updateServer(value: BleServerConfig) {
        config = config.copy(server = value)
    }

    fun saveWifi() {
        val wifi = config.wifi
        if (wifi.ssid.isBlank()) {
            error = "请输入 Wi-Fi SSID"
            return
        }
        if (!wifi.dhcp && listOf(wifi.ip, wifi.gateway, wifi.subnet).any(String::isBlank)) {
            error = "静态地址模式需要填写 IP、网关和子网掩码"
            return
        }
        runAction(keepBusy = true) {
            val generation = operationGeneration.get()
            client.saveWifi(wifi) { result -> postResult(generation, result, "Wi-Fi 配置已写入") }
        }
    }

    fun saveServer() {
        val server = config.server
        if (server.account.isBlank() || server.deviceAuthPassword.isBlank()) {
            error = "请输入 DraARL 账号和设备认证密码"
            return
        }
        if (!server.httpApiBaseUrl.startsWith("https://")) {
            error = "HTTP API 地址必须使用 HTTPS"
            return
        }
        runAction(keepBusy = true) {
            val generation = operationGeneration.get()
            client.saveServer(server) { result -> postResult(generation, result, "DraARL 配置已写入") }
        }
    }

    fun clearFeedback() {
        error = ""
        message = ""
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        operationGeneration.incrementAndGet()
        busy = false
        client.close()
        mainDispatcher.clear()
    }

    private fun runAction(keepBusy: Boolean = false, block: () -> Unit) {
        if (busy || closed.get()) return
        busy = true
        error = ""
        message = ""
        runCatching(block).onFailure {
            busy = false
            setError(it)
        }
        if (!keepBusy) busy = false
    }

    private fun postResult(generation: Int, result: Result<Unit>, successMessage: String, onSuccess: () -> Unit = {}) {
        postOperation(generation) {
            busy = false
            result.onSuccess {
                error = ""
                message = successMessage
                onSuccess()
            }.onFailure(::setError)
        }
    }

    private fun setError(throwable: Throwable) {
        error = throwable.message ?: "蓝牙操作失败"
    }

    private fun invalidatePendingOperation() {
        operationGeneration.incrementAndGet()
        busy = false
    }

    private fun postOperation(generation: Int, block: () -> Unit) {
        if (closed.get() || generation != operationGeneration.get()) return
        mainDispatcher.post {
            if (!closed.get() && generation == operationGeneration.get()) block()
        }
    }

    private fun post(block: () -> Unit) {
        if (closed.get()) return
        mainDispatcher.post { if (!closed.get()) block() }
    }
}

internal fun interface BleProvisionClientFactory {
    fun create(onStatus: (BleProvisionStatus) -> Unit, onDevices: (List<BleDeviceInfo>) -> Unit): BleProvisionClient
}

internal interface BleProvisionClient {
    fun currentStatus(): BleProvisionStatus
    fun startScan()
    fun stopScan()
    fun connect(address: String, name: String)
    fun disconnect()
    fun authenticate(code: String, callback: (Result<Unit>) -> Unit)
    fun loadConfig(callback: (Result<BleProvisionConfig>) -> Unit)
    fun saveWifi(config: BleWifiConfig, callback: (Result<Unit>) -> Unit)
    fun saveServer(config: BleServerConfig, callback: (Result<Unit>) -> Unit)
    fun close()
}

internal interface BleProvisionMainDispatcher {
    fun post(block: () -> Unit)
    fun clear()
}

private class AndroidBleProvisionMainDispatcher : BleProvisionMainDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(block: () -> Unit) {
        handler.post(block)
    }

    override fun clear() {
        handler.removeCallbacksAndMessages(null)
    }
}

private class AndroidBleProvisionClient(
    context: Context,
    onStatus: (BleProvisionStatus) -> Unit,
    onDevices: (List<BleDeviceInfo>) -> Unit
) : BleProvisionClient {
    private val delegate = BleProvisioningClient(context, onStatus, onDevices)

    override fun currentStatus(): BleProvisionStatus = delegate.currentStatus()
    override fun startScan() = delegate.startScan()
    override fun stopScan() = delegate.stopScan()
    override fun connect(address: String, name: String) = delegate.connect(address, name)
    override fun disconnect() = delegate.disconnect()
    override fun authenticate(code: String, callback: (Result<Unit>) -> Unit) = delegate.authenticate(code, callback)
    override fun loadConfig(callback: (Result<BleProvisionConfig>) -> Unit) = delegate.loadConfig(callback)
    override fun saveWifi(config: BleWifiConfig, callback: (Result<Unit>) -> Unit) = delegate.saveWifi(config, callback)
    override fun saveServer(config: BleServerConfig, callback: (Result<Unit>) -> Unit) =
        delegate.saveServer(config, callback)
    override fun close() = delegate.close()
}
