package cn.silverdragon.draarl.radio

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutorDispatchTest {
    @Test
    fun `does not submit to a closed executor`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdownNow()

        assertFalse(executeIfActive(executor, { true }) { error("must not run") })
    }

    @Test
    fun `skips queued work after lifecycle becomes inactive`() {
        val executor = Executors.newSingleThreadExecutor()
        val active = AtomicBoolean(true)
        val ran = AtomicBoolean(false)
        val blocker = java.util.concurrent.CountDownLatch(1)
        executor.execute { blocker.await() }

        assertTrue(executeIfActive(executor, active::get) { ran.set(true) })
        active.set(false)
        blocker.countDown()
        executor.shutdown()
        executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)

        assertFalse(ran.get())
    }
}
