package cn.silverdragon.draarl.data

import java.util.concurrent.CopyOnWriteArrayList
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardCacheControllerTest {
    @Test
    fun cacheLoadUsesIoDispatcherAndAppliesToOwnerScope() = runBlocking {
        val ownerThread = Thread.currentThread()
        var loadThread: Thread? = null
        var applyThread: Thread? = null
        val cached = DashboardData(devices = 3)
        val fixture = fixture(
            scope = this,
            cache = FakeDashboardCache(
                loadAction = {
                    loadThread = Thread.currentThread()
                    cached
                }
            ),
            onApply = { applyThread = Thread.currentThread() }
        )
        try {
            fixture.controller.onUserChanged(1)
            awaitCondition { fixture.applied.lastOrNull() == cached }

            assertNotEquals(ownerThread, loadThread)
            assertEquals(ownerThread, applyThread)
            assertEquals(listOf(DashboardData(), cached), fixture.applied)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun freshSnapshotInvalidatesBlockingCacheLoadAndIsSaved() = runBlocking {
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val loadFinished = CountDownLatch(1)
        val stale = DashboardData(devices = 1)
        val fresh = DashboardData(devices = 5)
        val cache = FakeDashboardCache(
            loadAction = {
                loadStarted.countDown()
                awaitIgnoringInterruption(releaseLoad)
                loadFinished.countDown()
                stale
            }
        )
        val fixture = fixture(this, cache)
        try {
            fixture.controller.onUserChanged(1)
            awaitCondition { loadStarted.count == 0L }

            fixture.controller.store(1, fresh)
            awaitCondition { cache.saved == listOf(1 to fresh) }
            releaseLoad.countDown()
            assertTrue(loadFinished.await(1, TimeUnit.SECONDS))
            yield()

            assertEquals(listOf(DashboardData()), fixture.applied)
        } finally {
            releaseLoad.countDown()
            fixture.close()
        }
    }

    @Test
    fun userChangeDropsPreviousUsersLateCache() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)
        val first = DashboardData(devices = 1)
        val second = DashboardData(devices = 2)
        val cache = FakeDashboardCache(
            loadAction = { userId ->
                if (userId == 1) {
                    firstStarted.countDown()
                    awaitIgnoringInterruption(releaseFirst)
                    firstFinished.countDown()
                    first
                } else {
                    second
                }
            }
        )
        val fixture = fixture(this, cache)
        try {
            fixture.controller.onUserChanged(1)
            awaitCondition { firstStarted.count == 0L }

            fixture.controller.onUserChanged(2)
            awaitCondition { fixture.applied.lastOrNull() == second }
            releaseFirst.countDown()
            assertTrue(firstFinished.await(1, TimeUnit.SECONDS))
            yield()

            assertEquals(listOf(DashboardData(), DashboardData(), second), fixture.applied)
        } finally {
            releaseFirst.countDown()
            fixture.close()
        }
    }

    private fun fixture(
        scope: CoroutineScope,
        cache: FakeDashboardCache,
        onApply: (DashboardData) -> Unit = {}
    ): Fixture {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val applied = mutableListOf<DashboardData>()
        return Fixture(
            controller = DashboardCacheController(cache, scope, dispatcher) {
                applied += it
                onApply(it)
            },
            dispatcher = dispatcher,
            applied = applied
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }
}

private data class Fixture(
    val controller: DashboardCacheController,
    val dispatcher: ExecutorCoroutineDispatcher,
    val applied: List<DashboardData>
) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeDashboardCache(
    private val loadAction: (Int) -> DashboardData? = { null },
    private val saveAction: (Int, DashboardData) -> Unit = { _, _ -> }
) : DashboardCache {
    val saved = CopyOnWriteArrayList<Pair<Int, DashboardData>>()

    override fun load(userId: Int): DashboardData? = loadAction(userId)

    override fun save(userId: Int, data: DashboardData) {
        saved += userId to data
        saveAction(userId, data)
    }
}

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking cache backend that cannot be cancelled cooperatively.
        }
    }
}
