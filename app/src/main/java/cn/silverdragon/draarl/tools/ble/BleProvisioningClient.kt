package cn.silverdragon.draarl.tools.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import org.json.JSONObject
import java.util.ArrayDeque
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("MissingPermission")
class BleProvisioningClient(
    context: Context,
    private val onStatus: (BleProvisionStatus) -> Unit,
    private val onDevices: (List<BleDeviceInfo>) -> Unit,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "draarl-ble-timeouts")
    }
    private val closed = AtomicBoolean(false)
    private val nextRpcId = AtomicInteger(1)
    private val scanGeneration = AtomicInteger(0)
    private val connectionGeneration = AtomicInteger(0)
    private val scanResults = LinkedHashMap<String, BleDeviceInfo>()
    private val writeQueue = ArrayDeque<GattWrite>()
    private val descriptorQueue = ArrayDeque<BluetoothGattDescriptor>()
    private val rpcPending = ConcurrentHashMap<Int, RpcPending>()
    private val rpcAssembler = BleProvisionProtocol.FrameAssembler()
    @Volatile private var status = BleProvisionStatus()
    @Volatile private var gatt: BluetoothGatt? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var authCharacteristic: BluetoothGattCharacteristic? = null
    private var rpcTxCharacteristic: BluetoothGattCharacteristic? = null
    private var rpcRxCharacteristic: BluetoothGattCharacteristic? = null
    private var writing = false
    private var scanning = false
    @Volatile private var authReadInFlight = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val info = BleDeviceInfo(
                address = device.address,
                name = result.scanRecord?.deviceName ?: device.name ?: "DraARL BLE",
                rssi = result.rssi,
            )
            synchronized(scanResults) {
                scanResults[info.address] = info
                onDevices(scanResults.values.sortedByDescending(BleDeviceInfo::rssi))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            updateStatus(status.copy(phase = BleConnectionPhase.ERROR, error = "蓝牙扫描失败 ($errorCode)"))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (!isCurrentGatt(gatt)) {
                runCatching { gatt.close() }
                return
            }
            if (statusCode != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                handleDisconnected(if (statusCode == BluetoothGatt.GATT_SUCCESS) "设备已断开" else "蓝牙连接失败 ($statusCode)")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateStatus(status.copy(phase = BleConnectionPhase.DISCOVERING, error = ""))
                if (!gatt.discoverServices()) fail("无法发现设备服务")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            if (!isCurrentGatt(gatt)) return
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                fail("读取蓝牙服务失败 ($statusCode)")
                return
            }
            val service = gatt.getService(BleProvisionProtocol.SERVICE_UUID)
            if (service == null || !bindCharacteristics(service)) {
                fail("设备不支持 DraARL 配置协议")
                return
            }
            configureNotifications(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, statusCode: Int) {
            if (!isCurrentGatt(gatt)) return
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                fail("启用蓝牙通知失败 ($statusCode)")
                return
            }
            writeNextDescriptor(gatt)
        }

        @Deprecated("Deprecated in Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!isCurrentGatt(gatt)) return
            handleCharacteristicValue(characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (!isCurrentGatt(gatt)) return
            handleCharacteristicValue(characteristic, value)
        }

        @Deprecated("Deprecated in Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            statusCode: Int,
        ) {
            if (!isCurrentGatt(gatt)) return
            if (characteristic.uuid == BleProvisionProtocol.STATUS_UUID) authReadInFlight = false
            if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic, characteristic.value ?: ByteArray(0))
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            statusCode: Int,
        ) {
            if (!isCurrentGatt(gatt)) return
            if (characteristic.uuid == BleProvisionProtocol.STATUS_UUID) authReadInFlight = false
            if (statusCode == BluetoothGatt.GATT_SUCCESS) handleCharacteristicValue(characteristic, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            statusCode: Int,
        ) {
            if (!isCurrentGatt(gatt)) return
            val completed: GattWrite? = synchronized(writeQueue) {
                writing = false
                writeQueue.pollFirst()
            }
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                completed?.onFailure?.invoke("蓝牙写入失败 ($statusCode)")
                fail("蓝牙写入失败 ($statusCode)")
                return
            }
            completed?.onSuccess?.invoke()
            writeNext()
        }
    }

    fun currentStatus(): BleProvisionStatus = status

    fun isBluetoothEnabled(): Boolean = bluetoothManager.adapter?.isEnabled == true

    fun startScan() {
        check(!closed.get()) { "蓝牙工具已关闭" }
        val adapter = bluetoothManager.adapter ?: throw IllegalStateException("该设备不支持蓝牙")
        check(adapter.isEnabled) { "请先开启蓝牙" }
        stopScan()
        val generation = scanGeneration.incrementAndGet()
        synchronized(scanResults) { scanResults.clear() }
        onDevices(emptyList())
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleProvisionProtocol.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = true
        updateStatus(status.copy(phase = BleConnectionPhase.SCANNING, error = ""))
        adapter.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
            ?: throw IllegalStateException("无法启动蓝牙扫描")
        mainHandler.postDelayed({
            if (generation == scanGeneration.get() && scanning) stopScan()
        }, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        scanGeneration.incrementAndGet()
        if (!scanning) return
        scanning = false
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (status.phase == BleConnectionPhase.SCANNING) updateStatus(status.copy(phase = BleConnectionPhase.IDLE))
    }

    fun connect(address: String, name: String) {
        stopScan()
        disconnect(notify = false)
        connectionGeneration.incrementAndGet()
        val device = bluetoothManager.adapter?.getRemoteDevice(address)
            ?: throw IllegalArgumentException("找不到蓝牙设备")
        updateStatus(BleProvisionStatus(phase = BleConnectionPhase.CONNECTING, deviceName = name))
        gatt = device.connectGatt(
            appContext,
            false,
            gattCallback,
            android.bluetooth.BluetoothDevice.TRANSPORT_LE,
        )
    }

    fun disconnect(notify: Boolean = true) {
        stopScan()
        connectionGeneration.incrementAndGet()
        rejectPending("设备已断开")
        synchronized(writeQueue) {
            writeQueue.clear()
            writing = false
        }
        synchronized(descriptorQueue) { descriptorQueue.clear() }
        synchronized(rpcAssembler) { rpcAssembler.clear() }
        authReadInFlight = false
        val current = gatt
        gatt = null
        runCatching { current?.disconnect() }
        runCatching { current?.close() }
        clearCharacteristics()
        if (notify) updateStatus(BleProvisionStatus(phase = BleConnectionPhase.DISCONNECTED))
    }

    fun authenticate(code: String, callback: (Result<Unit>) -> Unit) {
        require(code.matches(Regex("^\\d{6}$"))) { "请输入 6 位动态码" }
        val characteristic = authCharacteristic ?: throw IllegalStateException("设备尚未连接")
        val expectedGeneration = connectionGeneration.get()
        val completed = AtomicBoolean(false)
        val completeOnce: (Result<Unit>) -> Unit = { result ->
            if (completed.compareAndSet(false, true)) callback(result)
        }
        enqueue(
            GattWrite(
                characteristic = characteristic,
                value = code.toByteArray(),
                onSuccess = {
                    waitForAuthentication(
                        deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS,
                        expectedGeneration = expectedGeneration,
                        callback = completeOnce,
                    )
                },
                onFailure = { completeOnce(Result.failure(IllegalStateException(it))) },
            ),
        )
    }

    fun loadConfig(callback: (Result<BleProvisionConfig>) -> Unit) {
        sendRpc("get_config", JSONObject()) { result ->
            callback(result.mapCatching(::parseConfig))
        }
    }

    fun saveWifi(config: BleWifiConfig, callback: (Result<Unit>) -> Unit) {
        val data = JSONObject()
            .put("ssid", config.ssid)
            .put("password", config.password)
            .put("dhcp", config.dhcp)
            .put("ip", config.ip)
            .put("gateway", config.gateway)
            .put("subnet", config.subnet)
            .put("dns1", config.dns1)
            .put("dns2", config.dns2)
        sendRpc("set_wifi", data) { callback(it.map { Unit }) }
    }

    fun saveServer(config: BleServerConfig, callback: (Result<Unit>) -> Unit) {
        val apiUri = runCatching { URI(config.httpApiBaseUrl.trim()) }.getOrNull()
        require(apiUri?.scheme == "https" && !apiUri.host.isNullOrBlank()) {
            "API 地址必须使用 HTTPS"
        }
        val data = JSONObject()
            .put("callsign", config.callsign)
            .put("node_ssid", config.nodeSsid)
            .put("udp_host", config.udpHost)
            .put("udp_port", config.udpPort)
            .put("http_api_base_url", config.httpApiBaseUrl)
            .put("account", config.account)
            .put("device_auth_password", config.deviceAuthPassword)
        sendRpc("set_server", data) { callback(it.map { Unit }) }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        disconnect(notify = false)
        mainHandler.removeCallbacksAndMessages(null)
        timeoutExecutor.shutdownNow()
    }

    private fun bindCharacteristics(service: BluetoothGattService): Boolean {
        statusCharacteristic = service.getCharacteristic(BleProvisionProtocol.STATUS_UUID)
        authCharacteristic = service.getCharacteristic(BleProvisionProtocol.AUTH_UUID)
        rpcTxCharacteristic = service.getCharacteristic(BleProvisionProtocol.RPC_TX_UUID)
        rpcRxCharacteristic = service.getCharacteristic(BleProvisionProtocol.RPC_RX_UUID)
        return statusCharacteristic != null && authCharacteristic != null && rpcTxCharacteristic != null && rpcRxCharacteristic != null
    }

    private fun configureNotifications(gatt: BluetoothGatt) {
        synchronized(descriptorQueue) { descriptorQueue.clear() }
        listOfNotNull(statusCharacteristic, rpcRxCharacteristic).forEach { characteristic ->
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                fail("设备通知通道不可用")
                return
            }
            characteristic.getDescriptor(BleProvisionProtocol.CCCD_UUID)?.let { descriptor ->
                synchronized(descriptorQueue) { descriptorQueue.addLast(descriptor) }
            }
        }
        if (synchronized(descriptorQueue) { descriptorQueue.size } < 2) {
            fail("设备缺少通知描述符")
            return
        }
        writeNextDescriptor(gatt)
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt) {
        val descriptor: BluetoothGattDescriptor? = synchronized(descriptorQueue) {
            descriptorQueue.pollFirst()
        }
        if (descriptor == null) {
            updateStatus(status.copy(phase = BleConnectionPhase.READY, bleState = "已连接", error = ""))
            statusCharacteristic?.let { characteristic -> runCatching { gatt.readCharacteristic(characteristic) } }
            return
        }
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!started) fail("无法启用设备通知")
    }

    private fun enqueue(write: GattWrite) {
        synchronized(writeQueue) { writeQueue.addLast(write) }
        writeNext()
    }

    private fun writeNext() {
        val currentGatt = gatt ?: return
        val next = synchronized(writeQueue) {
            if (writing) return
            writeQueue.peekFirst()?.also { writing = true }
        } ?: return
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(
                next.characteristic,
                next.value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            next.characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            next.characteristic.value = next.value
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(next.characteristic)
        }
        if (!started) {
            synchronized(writeQueue) {
                writing = false
                writeQueue.pollFirst()
            }
            next.onFailure?.invoke("无法提交蓝牙写入")
            fail("无法提交蓝牙写入")
        }
    }

    private fun sendRpc(command: String, data: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        val characteristic = rpcTxCharacteristic ?: throw IllegalStateException("RPC 通道尚未就绪")
        check(status.phase == BleConnectionPhase.READY) { "设备尚未就绪" }
        val id = nextRpcId.getAndUpdate { if (it == Int.MAX_VALUE) 1 else it + 1 }
        val timeout = timeoutExecutor.schedule({
            rpcPending.remove(id)?.callback?.invoke(Result.failure(IllegalStateException("RPC 超时: $command")))
        }, RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        rpcPending[id] = RpcPending(callback, timeout)
        val chunks = BleProvisionProtocol.rpcChunks(id, command, data)
        chunks.forEachIndexed { index, frame ->
            enqueue(
                GattWrite(
                    characteristic = characteristic,
                    value = frame,
                    onFailure = if (index == chunks.lastIndex) ({ message ->
                        rpcPending.remove(id)?.let { pending ->
                            pending.timeout.cancel(false)
                            pending.callback(Result.failure(IllegalStateException(message)))
                        }
                    }) else null,
                ),
            )
        }
    }

    private fun handleCharacteristicValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        when (characteristic.uuid) {
            BleProvisionProtocol.STATUS_UUID -> {
                BleProvisionProtocol.parseStatus(value.toString(Charsets.UTF_8), status)?.let(::updateStatus)
            }
            BleProvisionProtocol.RPC_RX_UUID -> {
                val complete = synchronized(rpcAssembler) { rpcAssembler.append(value) } ?: return
                val payload = runCatching { JSONObject(complete) }.getOrElse {
                    rejectPending("设备返回了无效数据")
                    return
                }
                val pending = rpcPending.remove(payload.optInt("id")) ?: return
                pending.timeout.cancel(false)
                if (payload.optBoolean("ok")) {
                    pending.callback(Result.success(payload.optJSONObject("data") ?: JSONObject()))
                } else {
                    pending.callback(Result.failure(IllegalStateException(payload.optString("error", "设备操作失败"))))
                }
            }
        }
    }

    private fun waitForAuthentication(
        deadline: Long,
        expectedGeneration: Int,
        callback: (Result<Unit>) -> Unit,
    ) {
        if (
            closed.get() || expectedGeneration != connectionGeneration.get() ||
            gatt == null || status.phase == BleConnectionPhase.DISCONNECTED
        ) {
            callback(Result.failure(IllegalStateException("蓝牙连接已断开")))
            return
        }
        if (status.authenticated) {
            callback(Result.success(Unit))
            return
        }
        if (System.currentTimeMillis() >= deadline) {
            callback(Result.failure(IllegalStateException("动态码认证失败或已超时")))
            return
        }
        val currentGatt = gatt
        val characteristic = statusCharacteristic
        if (!authReadInFlight && currentGatt != null && characteristic != null) {
            authReadInFlight = runCatching { currentGatt.readCharacteristic(characteristic) }.getOrDefault(false)
        }
        mainHandler.postDelayed({
            waitForAuthentication(deadline, expectedGeneration, callback)
        }, AUTH_POLL_MS)
    }

    private fun parseConfig(data: JSONObject): BleProvisionConfig {
        val wifi = data.optJSONObject("wifi") ?: JSONObject()
        val server = data.optJSONObject("server") ?: JSONObject()
        return BleProvisionConfig(
            wifi = BleWifiConfig(
                ssid = wifi.optString("ssid"),
                password = wifi.optString("password"),
                dhcp = wifi.optBoolean("dhcp", true),
                ip = wifi.optString("ip"),
                gateway = wifi.optString("gateway"),
                subnet = wifi.optString("subnet"),
                dns1 = wifi.optString("dns1"),
                dns2 = wifi.optString("dns2"),
            ),
            server = BleServerConfig(
                callsign = server.optString("callsign"),
                nodeSsid = server.optInt("node_ssid"),
                udpHost = server.optString("udp_host", "ptt.4l2.cn"),
                udpPort = server.optInt("udp_port", 60_050),
                httpApiBaseUrl = server.optString("http_api_base_url", "https://ptt.4l2.cn"),
                account = server.optString("account"),
                deviceAuthPassword = server.optString("device_auth_password"),
            ),
        )
    }

    private fun updateStatus(value: BleProvisionStatus) {
        status = value
        onStatus(value)
    }

    private fun isCurrentGatt(candidate: BluetoothGatt): Boolean = gatt === candidate && !closed.get()

    private fun fail(message: String) {
        connectionGeneration.incrementAndGet()
        rejectPending(message)
        synchronized(writeQueue) {
            writeQueue.clear()
            writing = false
        }
        synchronized(descriptorQueue) { descriptorQueue.clear() }
        synchronized(rpcAssembler) { rpcAssembler.clear() }
        authReadInFlight = false
        val current = gatt
        gatt = null
        runCatching { current?.disconnect() }
        runCatching { current?.close() }
        clearCharacteristics()
        updateStatus(
            status.copy(
                phase = BleConnectionPhase.ERROR,
                authenticated = false,
                error = message,
            ),
        )
    }

    private fun handleDisconnected(message: String) {
        connectionGeneration.incrementAndGet()
        rejectPending(message)
        synchronized(writeQueue) {
            writeQueue.clear()
            writing = false
        }
        synchronized(descriptorQueue) { descriptorQueue.clear() }
        synchronized(rpcAssembler) { rpcAssembler.clear() }
        authReadInFlight = false
        val current = gatt
        gatt = null
        runCatching { current?.close() }
        clearCharacteristics()
        updateStatus(BleProvisionStatus(phase = BleConnectionPhase.DISCONNECTED, error = message))
    }

    private fun rejectPending(message: String) {
        rpcPending.entries.toList().forEach { (id, pending) ->
            if (rpcPending.remove(id, pending)) {
                pending.timeout.cancel(false)
                pending.callback(Result.failure(IllegalStateException(message)))
            }
        }
    }

    private fun clearCharacteristics() {
        statusCharacteristic = null
        authCharacteristic = null
        rpcTxCharacteristic = null
        rpcRxCharacteristic = null
    }

    private data class GattWrite(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val onSuccess: (() -> Unit)? = null,
        val onFailure: ((String) -> Unit)? = null,
    )

    private data class RpcPending(
        val callback: (Result<JSONObject>) -> Unit,
        val timeout: ScheduledFuture<*>,
    )

    private companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
        const val AUTH_TIMEOUT_MS = 4_000L
        const val AUTH_POLL_MS = 200L
        const val RPC_TIMEOUT_MS = 12_000L
    }
}
