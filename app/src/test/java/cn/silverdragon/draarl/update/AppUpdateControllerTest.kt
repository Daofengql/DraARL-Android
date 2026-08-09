package cn.silverdragon.draarl.update

import cn.silverdragon.draarl.data.ClientResourceArtifact
import cn.silverdragon.draarl.network.ApiException
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateControllerTest {
    @Test
    fun resetDropsLateCheckResultAndNotice() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val gateway = FakeAppUpdateGateway(
            checkAction = {
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
                update()
            }
        )
        val fixture = fixture(this, gateway)
        try {
            fixture.controller.check()
            awaitCondition { started.count == 0L }

            fixture.controller.reset()
            release.countDown()
            awaitCondition { finished.count == 0L }
            yield()

            assertEquals(AppUpdateUiState(), fixture.controller.uiState)
            assertTrue(fixture.notices.isEmpty())
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun resetDropsLateDownloadProgressAndPreventsInstall() = runBlocking {
        val downloadStarted = CountDownLatch(1)
        val releaseDownload = CountDownLatch(1)
        val downloadFinished = CountDownLatch(1)
        val gateway = FakeAppUpdateGateway(
            checkAction = ::update,
            downloadAction = { progress ->
                progress(0.25f)
                downloadStarted.countDown()
                awaitIgnoringInterruption(releaseDownload)
                progress(0.9f)
                downloadFinished.countDown()
                File("update.apk")
            }
        )
        val fixture = fixture(this, gateway)
        try {
            fixture.controller.check()
            awaitCondition { fixture.controller.uiState.status == AppUpdateStatus.AVAILABLE }
            fixture.controller.downloadAndInstall()
            awaitCondition { downloadStarted.count == 0L && fixture.controller.uiState.progress == 0.25f }

            fixture.controller.reset()
            releaseDownload.countDown()
            awaitCondition { downloadFinished.count == 0L }
            yield()

            assertEquals(AppUpdateUiState(), fixture.controller.uiState)
            assertEquals(0, gateway.installCalls.get())
        } finally {
            releaseDownload.countDown()
            fixture.close()
        }
    }

    @Test
    fun permissionGrantResumesPendingDownloadAndInstall() = runBlocking {
        val gateway = FakeAppUpdateGateway(checkAction = ::update, installAllowed = false)
        val fixture = fixture(this, gateway)
        try {
            fixture.controller.check()
            awaitCondition { fixture.controller.uiState.status == AppUpdateStatus.AVAILABLE }

            fixture.controller.downloadAndInstall()

            assertEquals(AppUpdateStatus.INSTALL_PERMISSION_REQUIRED, fixture.controller.uiState.status)
            assertEquals(1, gateway.permissionSettingsCalls.get())

            gateway.installAllowed = true
            fixture.controller.resumePendingInstall()
            awaitCondition { fixture.controller.uiState.status == AppUpdateStatus.READY_TO_INSTALL }

            assertEquals(1, gateway.installCalls.get())
            assertEquals(1f, fixture.controller.uiState.progress)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unsupportedAutomaticCheckReturnsToIdleWithoutNotice() = runBlocking {
        val checkCalls = AtomicInteger(0)
        val gateway = FakeAppUpdateGateway(
            checkAction = {
                checkCalls.incrementAndGet()
                throw ApiException(404, "missing endpoint")
            }
        )
        val fixture = fixture(this, gateway)
        try {
            fixture.controller.check(manual = false)
            awaitCondition { checkCalls.get() == 1 && fixture.controller.uiState.status == AppUpdateStatus.IDLE }

            assertEquals(AppUpdateUiState(), fixture.controller.uiState)
            assertTrue(fixture.notices.isEmpty())
        } finally {
            fixture.close()
        }
    }

    private fun fixture(scope: CoroutineScope, gateway: FakeAppUpdateGateway): Fixture {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val notices = mutableListOf<String>()
        return Fixture(
            controller = AppUpdateController(
                gateway = gateway,
                parentScope = scope,
                ioDispatcher = dispatcher,
                effects = object : AppUpdateEffects {
                    override fun hasAuthenticatedSession(): Boolean = true

                    override fun automaticCheckEnabled(): Boolean = true

                    override fun showNotice(message: String) {
                        notices += message
                    }

                    override fun friendlyError(error: Throwable): String = error.message ?: "request failed"
                }
            ),
            dispatcher = dispatcher,
            notices = notices
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }
}

private data class Fixture(
    val controller: AppUpdateController,
    val dispatcher: ExecutorCoroutineDispatcher,
    val notices: List<String>
) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeAppUpdateGateway(
    private val checkAction: () -> AppUpdateInfo? = { null },
    private val downloadAction: ((Float) -> Unit) -> File = { File("update.apk") },
    var installAllowed: Boolean = true
) : AppUpdateGateway {
    override val currentVersionName: String = "1.0.0"
    val installCalls = AtomicInteger(0)
    val permissionSettingsCalls = AtomicInteger(0)

    override fun checkForUpdate(channel: String): AppUpdateInfo? = checkAction()

    override fun downloadUpdate(update: AppUpdateInfo, onProgress: (Float) -> Unit): File = downloadAction(onProgress)

    override fun installUpdate(apk: File) {
        installCalls.incrementAndGet()
    }

    override fun canRequestPackageInstalls(): Boolean = installAllowed

    override fun openInstallPermissionSettings() {
        permissionSettingsCalls.incrementAndGet()
    }
}

private fun update() = AppUpdateInfo(
    version = "2.0.0",
    title = "DraARL 2.0",
    changelog = "",
    forceUpdate = false,
    artifact = ClientResourceArtifact(id = 1, releaseId = 1, format = "apk"),
    currentVersionName = "1.0.0",
    currentVersion = "1.0.0"
)

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking download or request that ignores cancellation.
        }
    }
}
