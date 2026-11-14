package cn.silverdragon.draarl.tools.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import java.util.UUID
import org.json.JSONObject

@SuppressLint("MissingPermission")
class BleProvisioningClient(
    context: Context,
    private val onStatus: (BleProvisionStatus) -> Unit,
    private val onDevices: (List<BleDeviceInfo>) -> Unit
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var closed = false
    private var generation = 0
    private var nextRpcId = 1
    private var scanning = false
    private var scanGeneration = 0
    private val scanResults = linkedMapOf<String, BleDeviceInfo>()
    private val rpcPending = linkedMapOf<Int, RpcPending>()
    private val assembler = BleProvisionProtocol.FrameAssembler()
    private var status = BleProvisionStatus()
    private var gatt: BluetoothGatt? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var authCharacteristic: BluetoothGattCharacteristic? = null
    private var rpcTxCharacteristic: BluetoothGattCharacteristic? = null
    private var rpcRxCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingAuth: ((Result<Unit>) -> Unit)? = null
    private var statusReadQueued = false
    private var connectionTimeout: Runnable? = null
    private val operations = BleGattOperationQueue(
        scheduleTimeout = { block ->
            val task = Runnable { block() }
            mainHandler.postDelayed(task, GATT_TIMEOUT_MS)
            val cancel: () -> Unit = { mainHandler.removeCallbacks(task) }
            cancel
        },
        onFailure = ::fail
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            mainHandler.post {
                if (!scanning || closed) return@post
                val device = result.device
                val info =
                    BleDeviceInfo(
                        device.address,
                        result.scanRecord?.deviceName ?: device.name ?: "DraARL BLE",
                        result.rssi
                    )
                scanResults[info.address] = info
                onDevices(scanResults.values.sortedByDescending(BleDeviceInfo::rssi))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.post {
                if (!scanning || closed) return@post
                scanning = false
                updateStatus(status.copy(phase = BleConnectionPhase.ERROR, error = "蓝牙扫描失败 ($errorCode)"))
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(candidate: BluetoothGatt, code: Int, newState: Int) = dispatch(candidate) {
            if (code != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail(if (code == BluetoothGatt.GATT_SUCCESS) "设备已断开" else "蓝牙连接失败 ($code)")
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateStatus(status.copy(phase = BleConnectionPhase.DISCOVERING))
                operations.enqueue("services", { candidate.discoverServices() }) { bindService(candidate) }
            }
        }

        override fun onServicesDiscovered(candidate: BluetoothGatt, code: Int) = dispatch(candidate) {
            operations.complete("services", code == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(candidate: BluetoothGatt, descriptor: BluetoothGattDescriptor, code: Int) =
            dispatch(candidate) {
                operations.complete(descriptorKey(descriptor), code == BluetoothGatt.GATT_SUCCESS)
            }

        @Deprecated("Deprecated in Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(candidate: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            changed(candidate, characteristic.uuid, characteristic.value?.copyOf() ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            candidate: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            changed(candidate, characteristic.uuid, value.copyOf())
        }

        @Deprecated("Deprecated in Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            candidate: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            code: Int
        ) {
            read(candidate, characteristic.uuid, characteristic.value?.copyOf() ?: byteArrayOf(), code)
        }

        override fun onCharacteristicRead(
            candidate: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            code: Int
        ) {
            read(candidate, characteristic.uuid, value.copyOf(), code)
        }

        override fun onCharacteristicWrite(
            candidate: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            code: Int
        ) = dispatch(candidate) {
            operations.complete("write:${characteristic.uuid}", code == BluetoothGatt.GATT_SUCCESS)
        }
    }

    // Binder callbacks are always posted. connectGatt returns and assigns gatt
    // before the first callback runs; stale callbacks cannot close a new session.
    private fun dispatch(candidate: BluetoothGatt, block: () -> Unit) {
        mainHandler.post { if (!closed && gatt === candidate) block() }
    }

    private fun changed(candidate: BluetoothGatt, uuid: UUID, value: ByteArray) = dispatch(candidate) {
        handleValue(uuid, value)
    }

    private fun read(candidate: BluetoothGatt, uuid: UUID, value: ByteArray, code: Int) = dispatch(candidate) {
        if (code == BluetoothGatt.GATT_SUCCESS) handleValue(uuid, value)
        operations.complete("read:$uuid", code == BluetoothGatt.GATT_SUCCESS)
    }

    fun currentStatus(): BleProvisionStatus = status
    fun isBluetoothEnabled(): Boolean = bluetoothManager.adapter?.isEnabled == true

    fun startScan() {
        check(!closed) { "蓝牙工具已关闭" }
        val adapter = bluetoothManager.adapter ?: error("该设备不支持蓝牙")
        check(adapter.isEnabled) { "请先开启蓝牙" }
        check(gatt == null) { "请先断开当前设备再扫描" }
        stopScan()
        val expected = ++scanGeneration
        scanResults.clear()
        onDevices(emptyList())
        scanning = true
        updateStatus(BleProvisionStatus(phase = BleConnectionPhase.SCANNING))
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleProvisionProtocol.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching {
            adapter.bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
        }.onFailure {
            scanning = false
        }.getOrThrow()
        mainHandler.postDelayed({ if (expected == scanGeneration) stopScan() }, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        scanGeneration++
        if (!scanning) return
        scanning = false
        runCatching { bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (status.phase == BleConnectionPhase.SCANNING) updateStatus(status.copy(phase = BleConnectionPhase.IDLE))
    }

    fun connect(address: String, name: String) {
        check(!closed) { "蓝牙工具已关闭" }
        disconnect(notify = false)
        val device = bluetoothManager.adapter?.getRemoteDevice(address) ?: error("找不到蓝牙设备")
        updateStatus(BleProvisionStatus(phase = BleConnectionPhase.CONNECTING, deviceName = name))
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            fail("无法创建蓝牙连接")
            return
        }
        val expected = generation
        connectionTimeout = Runnable {
            if (expected == generation && status.phase != BleConnectionPhase.READY) fail("蓝牙连接超时，请靠近设备后重试")
        }.also { mainHandler.postDelayed(it, CONNECTION_TIMEOUT_MS) }
    }

    fun disconnect(notify: Boolean = true) {
        stopScan()
        cleanup("设备已断开")
        if (notify) updateStatus(BleProvisionStatus(phase = BleConnectionPhase.DISCONNECTED))
    }

    private fun bindService(current: BluetoothGatt) {
        val service = current.getService(BleProvisionProtocol.SERVICE_UUID)
        if (service == null || !bindCharacteristics(service)) {
            fail("设备不支持 DraARL 配置协议")
            return
        }
        val descriptors = runCatching {
            listOfNotNull(statusCharacteristic, rpcRxCharacteristic).map { characteristic ->
                check(current.setCharacteristicNotification(characteristic, true)) { "设备通知通道不可用" }
                checkNotNull(characteristic.getDescriptor(BleProvisionProtocol.CCCD_UUID)) { "设备缺少通知描述符" }
            }
        }.getOrElse {
            fail(it.message ?: "设备通知通道不可用")
            return
        }
        descriptors.forEach { descriptor ->
            operations.enqueue(descriptorKey(descriptor), { writeDescriptor(current, descriptor) })
        }
        readStatus {
            connectionTimeout?.let(mainHandler::removeCallbacks)
            connectionTimeout = null
            updateStatus(status.copy(phase = BleConnectionPhase.READY, bleState = "已连接", error = ""))
        }
    }

    private fun bindCharacteristics(service: BluetoothGattService): Boolean {
        statusCharacteristic = service.getCharacteristic(BleProvisionProtocol.STATUS_UUID)
        authCharacteristic = service.getCharacteristic(BleProvisionProtocol.AUTH_UUID)
        rpcTxCharacteristic = service.getCharacteristic(BleProvisionProtocol.RPC_TX_UUID)
        rpcRxCharacteristic = service.getCharacteristic(BleProvisionProtocol.RPC_RX_UUID)
        return listOf(statusCharacteristic, authCharacteristic, rpcTxCharacteristic, rpcRxCharacteristic).all {
            it !=
                null
        }
    }

    private fun descriptorKey(descriptor: BluetoothGattDescriptor) = "descriptor:${descriptor.characteristic.uuid}"

    @Suppress("DEPRECATION")
    private fun writeDescriptor(current: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            current.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            current.writeDescriptor(descriptor)
        }

    @Suppress("DEPRECATION")
    private fun write(characteristic: BluetoothGattCharacteristic, bytes: ByteArray, done: () -> Unit = {}) {
        operations.enqueue("write:${characteristic.uuid}", {
            val current = gatt ?: return@enqueue false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                current.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = bytes
                current.writeCharacteristic(characteristic)
            }
        }, done)
    }

    private fun readStatus(done: () -> Unit = {}) {
        val characteristic = statusCharacteristic ?: return
        statusReadQueued = true
        operations.enqueue("read:${characteristic.uuid}", { gatt?.readCharacteristic(characteristic) == true }) {
            statusReadQueued = false
            done()
        }
    }

    fun authenticate(code: String, callback: (Result<Unit>) -> Unit) {
        require(code.matches(Regex("^\\d{6}$"))) { "请输入设备屏幕上的 6 位蓝牙验证码" }
        check(status.phase == BleConnectionPhase.READY && pendingAuth == null) { "设备尚未就绪或正在认证" }
        val characteristic = authCharacteristic ?: error("设备尚未连接")
        if (status.authenticated) {
            callback(Result.success(Unit))
            return
        }
        pendingAuth = callback
        val expected = generation
        write(characteristic, code.toByteArray()) {
            waitForAuthentication(
                expected,
                SystemClock.elapsedRealtime() + AUTH_TIMEOUT_MS
            )
        }
    }

    private fun waitForAuthentication(expected: Int, deadline: Long) {
        if (expected != generation || pendingAuth == null) return
        when {
            status.authenticated -> completeAuth(Result.success(Unit))

            SystemClock.elapsedRealtime() >= deadline -> completeAuth(
                Result.failure(IllegalStateException("蓝牙验证码错误或已过期"))
            )

            else -> {
                if (!statusReadQueued) readStatus()
                mainHandler.postDelayed({ waitForAuthentication(expected, deadline) }, AUTH_POLL_MS)
            }
        }
    }

    private fun completeAuth(result: Result<Unit>) {
        val callback = pendingAuth
        pendingAuth = null
        callback?.invoke(result)
    }

    fun loadConfig(callback: (Result<BleProvisionConfig>) -> Unit) = sendRpc("get_config", JSONObject()) {
        callback(it.mapCatching(BleConfigCodec::parse))
    }

    fun scanWifi(callback: (Result<List<BleWifiNetwork>>) -> Unit) = sendRpc("scan_wifi", JSONObject()) { result ->
        callback(
            result.mapCatching { data ->
                val networks = data.optJSONArray("networks")
                (0 until (networks?.length() ?: 0)).map { index ->
                    val network = networks!!.getJSONObject(index)
                    BleWifiNetwork(network.optString("ssid"), network.optInt("rssi"), network.optInt("auth") != 0)
                }.filter { it.ssid.isNotBlank() }.distinctBy { it.ssid }.sortedByDescending { it.rssi }
            }
        )
    }

    fun saveWifi(config: BleWifiConfig, callback: (Result<Unit>) -> Unit) {
        val data = JSONObject().put("ssid", config.ssid).put("password", config.password).put("dhcp", config.dhcp)
            .put("ip", config.ip).put("gateway", config.gateway).put("subnet", config.subnet)
            .put("dns1", config.dns1).put("dns2", config.dns2)
        sendRpc("set_wifi", data) { callback(it.map { Unit }) }
    }

    fun saveServer(config: BleServerConfig, callback: (Result<Unit>) -> Unit) =
        sendRpc("set_server", BleConfigCodec.serverPayload(config)) {
            callback(it.map { Unit })
        }

    fun saveRadio(model: Int, config: BleRadioConfig, callback: (Result<Unit>) -> Unit) =
        sendRpc("set_radio", BleConfigCodec.radioPayload(model, config)) {
            callback(it.map { Unit })
        }

    private fun sendRpc(command: String, data: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        check(status.phase == BleConnectionPhase.READY && status.authenticated) { "请先完成蓝牙认证" }
        check(rpcPending.isEmpty()) { "设备正在处理上一条配置，请稍候" }
        val characteristic = rpcTxCharacteristic ?: error("RPC 通道尚未就绪")
        val id = nextRpcId++
        if (nextRpcId <= 0) nextRpcId = 1
        val timeout = Runnable { if (rpcPending.containsKey(id)) fail("设备响应超时：$command，请重新连接后读取配置") }
        rpcPending[id] = RpcPending(callback, timeout)
        mainHandler.postDelayed(timeout, RPC_TIMEOUT_MS)
        BleProvisionProtocol.rpcChunks(id, command, data).forEach { write(characteristic, it) }
    }

    private fun handleValue(uuid: UUID, value: ByteArray) {
        if (uuid == BleProvisionProtocol.STATUS_UUID) {
            BleProvisionProtocol.parseStatus(value.toString(Charsets.UTF_8), status)?.let(::updateStatus)
        } else if (uuid == BleProvisionProtocol.RPC_RX_UUID) {
            assembler.append(value)?.let(::handleRpcResponse)
        }
    }

    private fun handleRpcResponse(text: String) {
        val response = runCatching { JSONObject(text) }.getOrElse {
            fail("设备返回了无效数据")
            return
        }
        val pending = rpcPending.remove(response.optInt("id")) ?: return
        mainHandler.removeCallbacks(pending.timeout)
        if (response.optBoolean("ok")) {
            pending.callback(Result.success(response.optJSONObject("data") ?: JSONObject()))
        } else {
            pending.callback(Result.failure(IllegalStateException(response.optString("error", "设备操作失败"))))
        }
    }

    private fun cleanup(message: String) {
        generation++
        connectionTimeout?.let(mainHandler::removeCallbacks)
        connectionTimeout = null
        operations.clear()
        assembler.clear()
        statusReadQueued = false
        val current = gatt
        gatt = null
        runCatching { current?.disconnect() }
        runCatching { current?.close() }
        statusCharacteristic = null
        authCharacteristic = null
        rpcTxCharacteristic = null
        rpcRxCharacteristic = null
        val pending = rpcPending.values.toList()
        rpcPending.clear()
        pending.forEach {
            mainHandler.removeCallbacks(it.timeout)
            it.callback(Result.failure(IllegalStateException(message)))
        }
        completeAuth(Result.failure(IllegalStateException(message)))
    }

    private fun fail(message: String) {
        cleanup(message)
        updateStatus(status.copy(phase = BleConnectionPhase.ERROR, authenticated = false, error = message))
    }

    private fun updateStatus(value: BleProvisionStatus) {
        status = value
        onStatus(value)
    }

    fun close() {
        if (closed) return
        closed = true
        disconnect(notify = false)
        mainHandler.removeCallbacksAndMessages(null)
    }

    private data class RpcPending(val callback: (Result<JSONObject>) -> Unit, val timeout: Runnable)

    private companion object {
        const val SCAN_TIMEOUT_MS = 10000L
        const val CONNECTION_TIMEOUT_MS = 20000L
        const val GATT_TIMEOUT_MS = 8000L
        const val AUTH_TIMEOUT_MS = 5000L
        const val AUTH_POLL_MS = 250L
        const val RPC_TIMEOUT_MS = 25000L
    }
}
