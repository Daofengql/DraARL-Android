package cn.silverdragon.draarl.tools.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean

class BleProvisionController(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val client = BleProvisioningClient(
        context = context,
        onStatus = { value -> post { status = value } },
        onDevices = { value -> post { devices = value } },
    )

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

    fun startScan() = runAction {
        client.startScan()
        busy = false
    }

    fun stopScan() {
        client.stopScan()
        busy = false
    }

    fun connect(device: BleDeviceInfo) = runAction {
        client.connect(device.address, device.name)
        busy = false
    }

    fun disconnect() {
        client.disconnect()
        busy = false
        message = ""
    }

    fun authenticate(code: String) = runAction(keepBusy = true) {
        client.authenticate(code) { result ->
            postResult(result, "认证成功") {
                status = client.currentStatus()
                loadConfig()
            }
        }
    }

    fun loadConfig() = runAction(keepBusy = true) {
        client.loadConfig { result ->
            post {
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
            client.saveWifi(wifi) { result -> postResult(result, "Wi-Fi 配置已写入") }
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
            client.saveServer(server) { result -> postResult(result, "DraARL 配置已写入") }
        }
    }

    fun clearFeedback() {
        error = ""
        message = ""
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        client.close()
        mainHandler.removeCallbacksAndMessages(null)
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

    private fun postResult(result: Result<Unit>, successMessage: String, onSuccess: () -> Unit = {}) {
        post {
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

    private fun post(block: () -> Unit) {
        mainHandler.post { if (!closed.get()) block() }
    }
}
