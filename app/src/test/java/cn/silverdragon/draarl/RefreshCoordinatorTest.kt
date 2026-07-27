package cn.silverdragon.draarl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshCoordinatorTest {
    @Test
    fun `coalesces repeated requests into one trailing refresh`() {
        val coordinator = RefreshCoordinator()
        val first = coordinator.request()!!

        assertNull(coordinator.request())
        assertNull(coordinator.request())

        val firstCompletion = coordinator.complete(first)
        assertFalse(firstCompletion.applyResults)
        val trailing = firstCompletion.nextGeneration
        assertNotNull(trailing)

        val trailingCompletion = coordinator.complete(trailing!!)
        assertTrue(trailingCompletion.applyResults)
        assertTrue(trailingCompletion.isIdle)
    }

    @Test
    fun `cancel rejects an old response and allows a new refresh`() {
        val coordinator = RefreshCoordinator()
        val old = coordinator.request()!!

        coordinator.cancel()
        val current = coordinator.request()!!

        assertFalse(coordinator.complete(old).applyResults)
        assertTrue(coordinator.complete(current).applyResults)
    }
}
