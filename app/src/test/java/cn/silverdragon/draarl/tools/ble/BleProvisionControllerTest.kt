package cn.silverdragon.draarl.tools.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleProvisionControllerTest {
    @Test
    fun disconnectDropsLateConfigResult() {
        val client = FakeBleProvisionClient()
        val dispatcher = QueuedMainDispatcher()
        val controller = controller(client, dispatcher)
        val initialConfig = controller.config

        controller.loadConfig()
        assertTrue(controller.busy)
        controller.disconnect()
        client.completeConfig(
            Result.success(BleProvisionConfig(wifi = BleWifiConfig(ssid = "late-network")))
        )
        dispatcher.runAll()

        assertEquals(initialConfig, controller.config)
        assertEquals("", controller.message)
        assertEquals("", controller.error)
        assertFalse(controller.busy)
        assertEquals(1, client.disconnectCalls)
    }

    @Test
    fun newConnectionDropsPreviousAuthenticationResult() {
        val client = FakeBleProvisionClient()
        val dispatcher = QueuedMainDispatcher()
        val controller = controller(client, dispatcher)

        controller.authenticate("123456")
        assertTrue(controller.busy)
        controller.connect(DEVICE)
        client.completeAuthentication(Result.success(Unit))
        dispatcher.runAll()

        assertEquals(listOf(DEVICE.address to DEVICE.name), client.connectCalls)
        assertEquals(0, client.loadConfigCalls)
        assertEquals("", controller.message)
        assertEquals("", controller.error)
        assertFalse(controller.busy)
    }

    @Test
    fun closeDropsLateSaveResult() {
        val client = FakeBleProvisionClient()
        val dispatcher = QueuedMainDispatcher()
        val controller = controller(client, dispatcher)
        controller.updateWifi(BleWifiConfig(ssid = "field-network"))

        controller.saveWifi()
        assertTrue(controller.busy)
        controller.close()
        controller.close()
        client.completeWifiSave(Result.success(Unit))
        dispatcher.runAll()

        assertEquals("", controller.message)
        assertEquals("", controller.error)
        assertFalse(controller.busy)
        assertEquals(1, client.closeCalls)
        assertEquals(1, dispatcher.clearCalls)
    }

    private fun controller(client: FakeBleProvisionClient, dispatcher: BleProvisionMainDispatcher) =
        BleProvisionController(
            clientFactory = BleProvisionClientFactory { _, _ -> client },
            mainDispatcher = dispatcher
        )

    private companion object {
        val DEVICE = BleDeviceInfo(address = "00:11:22:33:44:55", name = "DraARL", rssi = -40)
    }
}

private class QueuedMainDispatcher : BleProvisionMainDispatcher {
    private val pending = ArrayDeque<() -> Unit>()
    var clearCalls = 0

    override fun post(block: () -> Unit) {
        pending.addLast(block)
    }

    override fun clear() {
        clearCalls++
        pending.clear()
    }

    fun runAll() {
        while (pending.isNotEmpty()) pending.removeFirst().invoke()
    }
}

private class FakeBleProvisionClient : BleProvisionClient {
    private var authenticationCallback: ((Result<Unit>) -> Unit)? = null
    private var configCallback: ((Result<BleProvisionConfig>) -> Unit)? = null
    private var wifiSaveCallback: ((Result<Unit>) -> Unit)? = null
    val connectCalls = mutableListOf<Pair<String, String>>()
    var disconnectCalls = 0
    var loadConfigCalls = 0
    var closeCalls = 0

    override fun currentStatus(): BleProvisionStatus = BleProvisionStatus(authenticated = true)
    override fun startScan() = Unit
    override fun stopScan() = Unit

    override fun connect(address: String, name: String) {
        connectCalls += address to name
    }

    override fun disconnect() {
        disconnectCalls++
    }

    override fun authenticate(code: String, callback: (Result<Unit>) -> Unit) {
        authenticationCallback = callback
    }

    override fun loadConfig(callback: (Result<BleProvisionConfig>) -> Unit) {
        loadConfigCalls++
        configCallback = callback
    }

    override fun saveWifi(config: BleWifiConfig, callback: (Result<Unit>) -> Unit) {
        wifiSaveCallback = callback
    }

    override fun saveServer(config: BleServerConfig, callback: (Result<Unit>) -> Unit) = Unit

    override fun close() {
        closeCalls++
    }

    fun completeAuthentication(result: Result<Unit>) {
        authenticationCallback?.invoke(result)
    }

    fun completeConfig(result: Result<BleProvisionConfig>) {
        configCallback?.invoke(result)
    }

    fun completeWifiSave(result: Result<Unit>) {
        wifiSaveCallback?.invoke(result)
    }
}
