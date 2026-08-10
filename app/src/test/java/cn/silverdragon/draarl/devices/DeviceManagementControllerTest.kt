package cn.silverdragon.draarl.devices

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.network.DevicesApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceManagementControllerTest {
    @Test
    fun resetDropsLateConfigResultForPreviousDevice() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)
        val fixture = fixture(
            scope = this,
            api = FakeDevicesApi(
                configAction = { deviceId ->
                    if (deviceId == 1) {
                        firstStarted.countDown()
                        awaitIgnoringInterruption(firstRelease)
                        firstFinished.countDown()
                    }
                    mapOf("device" to deviceId.toString())
                }
            )
        )
        try {
            fixture.controller.loadConfig(1)
            awaitCondition { firstStarted.count == 0L }

            fixture.controller.reset()
            fixture.controller.loadConfig(2)
            awaitCondition { fixture.controller.config == mapOf("device" to "2") }

            firstRelease.countDown()
            assertTrue(firstFinished.await(1, TimeUnit.SECONDS))
            yield()

            assertEquals(mapOf("device" to "2"), fixture.controller.config)
            assertEquals(2, fixture.controller.configDeviceId)
        } finally {
            firstRelease.countDown()
            fixture.close()
        }
    }

    @Test
    fun closeCancelsPasswordRequestWithoutPublishingResult() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = fixture(
            scope = this,
            api = FakeDevicesApi(
                passwordAction = {
                    started.countDown()
                    awaitIgnoringInterruption(release)
                    DevicePasswordInfo("late", true, false, "now")
                }
            )
        )
        try {
            fixture.controller.loadPassword()
            awaitCondition { started.count == 0L }
            fixture.controller.close()
            release.countDown()
            yield()

            assertNull(fixture.controller.passwordInfo)
            assertFalse(fixture.controller.busy)
        } finally {
            release.countDown()
            fixture.dispatcher.close()
        }
    }

    private fun fixture(scope: CoroutineScope, api: FakeDevicesApi): Fixture {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        return Fixture(
            controller = DeviceManagementController(
                api = api,
                scope = scope,
                ioDispatcher = dispatcher,
                currentDevices = { emptyList() },
                updateDevices = {},
                refreshAll = {},
                showNotice = {},
                friendlyError = { it.message ?: "request failed" }
            ),
            dispatcher = dispatcher
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }
}

private data class Fixture(val controller: DeviceManagementController, val dispatcher: ExecutorCoroutineDispatcher) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeDevicesApi(
    private val configAction: (Int) -> Map<String, String> = { emptyMap() },
    private val passwordAction: () -> DevicePasswordInfo = { DevicePasswordInfo("", false, false, "") }
) : DevicesApi {
    override fun getDevices(): List<Device> = error("Unexpected devices request")
    override fun getDefaultDeviceGroup(): Int? = null
    override fun setDefaultDeviceGroup(groupId: Int?): Int? = groupId
    override fun updateDevice(deviceId: Int, name: String?, disableSend: Boolean?, disableReceive: Boolean?): Device =
        error("Unexpected device update")
    override fun deleteDevice(deviceId: Int) = error("Unexpected device deletion")
    override fun switchDeviceGroup(deviceId: Int, groupId: Int, password: String) = error("Unexpected group switch")
    override fun getDeviceConfig(deviceId: Int): Map<String, String> = configAction(deviceId)
    override fun updateDeviceConfig(deviceId: Int, config: Map<String, String>): Map<String, String> = config
    override fun syncDeviceConfig(deviceId: Int): String = ""
    override fun getDevicePassword(): DevicePasswordInfo = passwordAction()
    override fun regenerateDevicePassword(): DevicePasswordInfo = passwordAction()
    override fun bindDevice(dynamicCode: String): DeviceBindPreview = error("Unexpected bind")
    override fun submitDeviceConfig(deviceMac: String, ssid: Int?, replaceDeviceId: Int?): DeviceBindResult =
        error("Unexpected submit")
}

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking dependency that cannot be cancelled cooperatively.
        }
    }
}
