package cn.silverdragon.draarl.data

import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataRefresherTest {
    @Test
    fun `keeps per-section fallback when one request fails`() {
        val fallbackDevice = device(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val source = object : AppDataSource {
                override fun devices(): List<Device> = error("offline")
                override fun groups(): List<Group> = emptyList()
                override fun defaultDeviceGroup(): Int? = 999
                override fun communicationStats() = CommunicationStats(totalCount = 3)
                override fun communicationTrend(): List<DailyCommunicationStats> = emptyList()
                override fun currentUser(): User = User(1, "tester")
            }
            val result = AppDataRefresher(source, executor).refresh(
                AppDataFallback(listOf(fallbackDevice), emptyList(), emptyList()),
            ).get()

            assertEquals(listOf(fallbackDevice), result.devices)
            assertEquals(3, result.stats?.totalCount)
            assertTrue(result.defaultDeviceGroup.isSuccess)
        } finally {
            executor.shutdownNow()
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
        enabled = true,
    )
}
