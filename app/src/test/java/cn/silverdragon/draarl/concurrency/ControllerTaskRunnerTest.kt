package cn.silverdragon.draarl.concurrency

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerTaskRunnerTest {
    @Test
    fun operationUsesIoDispatcherAndResultReturnsToOwnerScope() = runBlocking {
        val ownerThread = Thread.currentThread()
        val fixture = fixture(this) { }
        var operationThread: Thread? = null
        var callbackThread: Thread? = null
        var value: String? = null
        try {
            assertTrue(
                fixture.runner.launch(
                    operation = {
                        operationThread = Thread.currentThread()
                        "complete"
                    },
                    onSuccess = {
                        callbackThread = Thread.currentThread()
                        value = it
                    },
                    onFailure = { error("Unexpected failure: $it") }
                )
            )

            awaitCondition { value != null }

            assertEquals("complete", value)
            assertNotEquals(ownerThread, operationThread)
            assertEquals(ownerThread, callbackThread)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancelDropsLateResultAndRestoresIdleState() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val runningStates = mutableListOf<Boolean>()
        val fixture = fixture(this, runningStates::add)
        var callbackCalled = false
        try {
            fixture.runner.launch(
                operation = {
                    started.countDown()
                    awaitIgnoringInterruption(release)
                    finished.countDown()
                },
                onSuccess = { callbackCalled = true },
                onFailure = { callbackCalled = true }
            )
            awaitCondition { started.count == 0L }

            fixture.runner.cancel()
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertFalse(callbackCalled)
            assertEquals(listOf(true, false), runningStates)
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun closeRejectsNewTasksAndDoesNotPublishCancellationAsFailure() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = fixture(this) { }
        var failureCalled = false
        try {
            fixture.runner.launch(
                operation = {
                    started.countDown()
                    awaitIgnoringInterruption(release)
                },
                onSuccess = {},
                onFailure = { failureCalled = true }
            )
            awaitCondition { started.count == 0L }

            fixture.runner.close()
            release.countDown()
            yield()

            assertFalse(failureCalled)
            assertFalse(fixture.runner.launch({}, {}, { failureCalled = true }))
        } finally {
            release.countDown()
            fixture.dispatcher.close()
        }
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope, onRunningChanged: (Boolean) -> Unit): Fixture {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        return Fixture(ControllerTaskRunner(scope, dispatcher, onRunningChanged), dispatcher)
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
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
}

private data class Fixture(val runner: ControllerTaskRunner, val dispatcher: ExecutorCoroutineDispatcher) {
    fun close() {
        runner.close()
        dispatcher.close()
    }
}
