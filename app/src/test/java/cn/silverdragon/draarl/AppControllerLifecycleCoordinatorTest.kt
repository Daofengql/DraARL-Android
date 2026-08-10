package cn.silverdragon.draarl

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControllerLifecycleCoordinatorTest {
    @Test
    fun closeRunsLifecyclePhasesOnceWithoutInitializingUnusedResources() {
        val disposed = AtomicBoolean(false)
        val events = mutableListOf<String>()
        var lazyInitialized = false
        val coordinator = AppControllerLifecycleCoordinator(
            disposed = disposed,
            removeScheduledCallbacks = {
                assertTrue(disposed.get())
                events += "callbacks"
            },
            cancelOwnedRequests = { events += "requests" },
            closeActions = listOf(
                AppControllerCloseAction(close = { events += "eager" }),
                AppControllerCloseAction(
                    isInitialized = { lazyInitialized },
                    close = { events += "lazy" }
                )
            )
        )

        coordinator.close()
        coordinator.close()

        assertEquals(listOf("callbacks", "requests", "eager"), events)
        assertTrue(disposed.get())
    }

    @Test
    fun closeContinuesAfterFailuresAndRethrowsTheFirstError() {
        val first = IllegalStateException("callback cleanup failed")
        val second = IllegalArgumentException("resource cleanup failed")
        val events = mutableListOf<String>()
        val coordinator = AppControllerLifecycleCoordinator(
            disposed = AtomicBoolean(false),
            removeScheduledCallbacks = {
                events += "callbacks"
                throw first
            },
            cancelOwnedRequests = { events += "requests" },
            closeActions = listOf(
                AppControllerCloseAction {
                    events += "first resource"
                    throw second
                },
                AppControllerCloseAction { events += "last resource" }
            )
        )

        val thrown = runCatching(coordinator::close).exceptionOrNull()

        assertSame(first, thrown)
        assertEquals(listOf(second), first.suppressed.toList())
        assertEquals(listOf("callbacks", "requests", "first resource", "last resource"), events)
        coordinator.close()
        assertEquals(4, events.size)
    }
}
