package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.AccessPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPointSelectorTest {
    @Test
    fun selectsLowestMeasuredLatencyBeforeConfiguredPriority() = runBlocking {
        val primary = accessPoint("primary", priority = 1)
        val edge = accessPoint("edge", priority = 20)

        val selection = AccessPointSelector.select(listOf(primary, edge)) { point ->
            AccessPointProbe(point, latencyMs = if (point == primary) 80 else 25)
        }

        assertEquals(edge, selection.selected)
        assertTrue(selection.measured)
        assertEquals(listOf(primary, edge), selection.probes.map(AccessPointProbe::accessPoint))
    }

    @Test
    fun fallsBackToConfiguredPriorityWhenAllPointsAreUnreachable() = runBlocking {
        val fallback = accessPoint("fallback", priority = 5)
        val secondary = accessPoint("secondary", priority = 10)

        val selection = AccessPointSelector.select(listOf(secondary, fallback)) { point ->
            AccessPointProbe(point, latencyMs = null)
        }

        assertEquals(fallback, selection.selected)
        assertFalse(selection.measured)
    }

    @Test
    fun oneProbeFailureDoesNotCancelOtherMeasurements() = runBlocking {
        val failing = accessPoint("failing", priority = 1)
        val reachable = accessPoint("reachable", priority = 20)

        val selection = AccessPointSelector.select(listOf(failing, reachable)) { point ->
            if (point == failing) error("probe failed")
            AccessPointProbe(point, latencyMs = 30)
        }

        assertEquals(reachable, selection.selected)
        assertEquals(listOf(null, 30), selection.probes.map(AccessPointProbe::latencyMs))
        assertTrue(selection.measured)
    }

    private fun accessPoint(id: String, priority: Int) = AccessPoint(
        id = id,
        displayName = id,
        host = "$id.example.test",
        port = 5_000,
        priority = priority
    )
}
