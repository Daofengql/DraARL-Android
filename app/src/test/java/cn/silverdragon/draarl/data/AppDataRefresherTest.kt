package cn.silverdragon.draarl.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataRefresherTest {
    @Test
    fun `keeps per-section fallback when one request fails`() = runBlocking {
        val fallbackDevice = device(1)
        Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { dispatcher ->
            val source = object : AppDataSource {
                override fun devices(): List<Device> = error("offline")
                override fun groups(): List<Group> = emptyList()
                override fun defaultDeviceGroup(): Int? = 999
                override fun communicationStats() = CommunicationStats(totalCount = 3)
                override fun communicationTrend(): List<DailyCommunicationStats> = emptyList()
                override fun currentUser(): User = User(1, "tester")
            }
            val result = AppDataRefresher(source, dispatcher).refresh(
                AppDataFallback(listOf(fallbackDevice), emptyList(), emptyList())
            )

            assertEquals(listOf(fallbackDevice), result.devices)
            assertEquals(3, result.stats?.totalCount)
            assertTrue(result.defaultDeviceGroup.isSuccess)
        }
    }

    @Test
    fun `starts all dashboard requests concurrently`() = runBlocking {
        val started = CountDownLatch(6)
        val release = CountDownLatch(1)
        val source = object : AppDataSource {
            override fun devices(): List<Device> = awaitRequest(started, release, emptyList())
            override fun groups(): List<Group> = awaitRequest(started, release, emptyList())
            override fun defaultDeviceGroup(): Int? = awaitRequest(started, release, null)
            override fun communicationStats() = awaitRequest(started, release, CommunicationStats())
            override fun communicationTrend(): List<DailyCommunicationStats> =
                awaitRequest(started, release, emptyList())

            override fun currentUser(): User = awaitRequest(started, release, User(1, "tester"))
        }
        Executors.newFixedThreadPool(6).asCoroutineDispatcher().use { dispatcher ->
            val refresh = async {
                AppDataRefresher(source, dispatcher).refresh(AppDataFallback(emptyList(), emptyList(), emptyList()))
            }
            try {
                awaitCondition { started.count == 0L }
                assertTrue(!refresh.isCompleted)
                release.countDown()
                refresh.await()
            } finally {
                release.countDown()
            }
        }
        Unit
    }

    private fun <T> awaitRequest(started: CountDownLatch, release: CountDownLatch, value: T): T {
        started.countDown()
        while (release.count > 0L) {
            try {
                release.await(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                // Keeps the fake request blocking until the test releases the full batch.
            }
        }
        return value
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }

    private fun device(id: Int) = Device(
        id = id,
        name = "device",
        callsign = "BG7TEST",
        ssid = 1,
        model = 1,
        groupId = 999,
        online = false,
        enabled = true
    )
}
