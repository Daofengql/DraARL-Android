package cn.silverdragon.draarl.devices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.network.DevicesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class DeviceManagementController(
    private val api: DevicesApi,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val currentDevices: () -> List<Device>,
    private val updateDevices: (List<Device>) -> Unit,
    private val refreshAll: () -> Unit,
    private val showNotice: (String) -> Unit,
    private val friendlyError: (Throwable) -> String
) {
    private var closed = false

    var busy by mutableStateOf(false)
        private set
    private val tasks = ControllerTaskRunner(scope, ioDispatcher) { busy = it }
    var defaultDeviceGroupId by mutableStateOf<Int?>(null)
        private set
    var passwordInfo by mutableStateOf<DevicePasswordInfo?>(null)
        private set
    var bindPreview by mutableStateOf<DeviceBindPreview?>(null)
        private set
    var bindResult by mutableStateOf<DeviceBindResult?>(null)
        private set
    var config by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var configDeviceId by mutableStateOf<Int?>(null)
        private set

    fun applyDefaultGroup(groupId: Int?) {
        defaultDeviceGroupId = groupId
    }

    fun setDefaultGroup(groupId: Int?) = launch(
        operation = { api.setDefaultDeviceGroup(groupId) },
        onSuccess = { saved ->
            defaultDeviceGroupId = saved
            showNotice(if (saved == null) "已清除新设备默认群组" else "新设备默认群组已保存")
        }
    )

    fun updateDevice(
        device: Device,
        name: String? = null,
        disableSend: Boolean? = null,
        disableReceive: Boolean? = null,
        onSuccess: () -> Unit = {}
    ) = launch(
        operation = { api.updateDevice(device.id, name, disableSend, disableReceive) },
        onSuccess = {
            showNotice("设备设置已保存")
            onSuccess()
            refreshAll()
        }
    )

    fun switchGroup(device: Device, group: Group, password: String = "", onSuccess: () -> Unit = {}) {
        if (device.groupId == group.id) {
            onSuccess()
            return
        }
        launch(
            operation = { api.switchDeviceGroup(device.id, group.id, password) },
            onSuccess = {
                showNotice("设备已切换到 ${group.name}")
                onSuccess()
                refreshAll()
            }
        )
    }

    fun deleteDevice(device: Device, onSuccess: () -> Unit = {}) = launch(
        operation = { api.deleteDevice(device.id) },
        onSuccess = {
            updateDevices(currentDevices().filterNot { it.id == device.id })
            showNotice("设备已删除")
            onSuccess()
        }
    )

    fun loadConfig(deviceId: Int) {
        configDeviceId = deviceId
        config = emptyMap()
        launch(
            operation = { api.getDeviceConfig(deviceId) },
            onSuccess = { loaded -> if (configDeviceId == deviceId) config = loaded }
        )
    }

    fun saveConfig(device: Device, value: Map<String, String>, onSuccess: () -> Unit = {}) = launch(
        operation = {
            api.updateDeviceConfig(device.id, value)
            if (device.online) api.syncDeviceConfig(device.id) else "配置已保存，设备上线后自动同步"
        },
        onSuccess = { message ->
            config = value
            showNotice(message)
            onSuccess()
        }
    )

    fun closeConfig() {
        configDeviceId = null
        config = emptyMap()
    }

    fun loadPassword() = launch(
        operation = api::getDevicePassword,
        onSuccess = { passwordInfo = it }
    )

    fun regeneratePassword() = launch(
        operation = api::regenerateDevicePassword,
        onSuccess = {
            passwordInfo = it
            showNotice("设备密码已刷新，旧密码已失效")
        }
    )

    fun resetBinding() {
        bindPreview = null
        bindResult = null
    }

    fun lookupBindCode(dynamicCode: String) {
        if (!dynamicCode.matches(Regex("\\d{6}"))) {
            showNotice("请输入 6 位动态码")
            return
        }
        launch(
            operation = { api.bindDevice(dynamicCode) },
            onSuccess = {
                bindPreview = it
                bindResult = null
            }
        )
    }

    fun submitBinding(ssid: Int?, replaceDeviceId: Int?) {
        val preview = bindPreview ?: return
        if (replaceDeviceId == null && (ssid == null || ssid !in 1..254 || ssid in 100..105)) {
            showNotice("请选择可用 SSID")
            return
        }
        launch(
            operation = { api.submitDeviceConfig(preview.deviceMac, ssid, replaceDeviceId) },
            onSuccess = {
                bindResult = it
                refreshAll()
            }
        )
    }

    fun reset() {
        tasks.cancel()
        clearState()
    }

    fun close() {
        if (closed) return
        closed = true
        tasks.close()
        clearState()
    }

    private fun <T> launch(operation: () -> T, onSuccess: (T) -> Unit) {
        if (closed) return
        tasks.launch(operation, onSuccess) { error -> showNotice(friendlyError(error)) }
    }

    private fun clearState() {
        defaultDeviceGroupId = null
        passwordInfo = null
        bindPreview = null
        bindResult = null
        config = emptyMap()
        configDeviceId = null
    }
}
