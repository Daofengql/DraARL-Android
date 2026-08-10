package cn.silverdragon.draarl.auth

import cn.silverdragon.draarl.data.CaptchaChallenge
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.network.AuthApi
import cn.silverdragon.draarl.network.RegistrationRequest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicAuthControllerTest {
    @Test
    fun closeDropsLateRegistrationResultAndCallback() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val api = FakeAuthApi(
            registrationAction = {
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
                RegistrationResult(1, it.username, it.nickname, 0, "device-password")
            }
        )
        val fixture = fixture(this, api)
        var callbackCalled = false
        try {
            fixture.controller.register(
                username = "operator",
                callsign = "BH1ABC",
                nickname = "Operator",
                email = "operator@example.test",
                phone = "",
                password = "secret1",
                confirmPassword = "secret1",
                sessionId = "email-session",
                emailCode = "123456",
                onSuccess = { callbackCalled = true }
            )
            awaitCondition { started.count == 0L }
            assertTrue(fixture.controller.busy)

            fixture.controller.close()
            assertFalse(fixture.controller.busy)
            val closedError = fixture.controller.error
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertFalse(callbackCalled)
            assertEquals(closedError, fixture.controller.error)
            assertTrue(fixture.loginErrors.isEmpty())
            assertFalse(fixture.controller.busy)
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun closeDropsLateCaptchaFailureAndNotice() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val api = FakeAuthApi(
            captchaAction = {
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
                throw IllegalStateException("late captcha failure")
            }
        )
        val fixture = fixture(this, api)
        try {
            fixture.controller.loadCaptcha()
            awaitCondition { started.count == 0L }
            assertTrue(fixture.controller.captchaLoading)

            fixture.controller.close()
            assertFalse(fixture.controller.captchaLoading)
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertEquals("", fixture.controller.captchaId)
            assertEquals("", fixture.controller.captchaImageBase64)
            assertTrue(fixture.loginErrors.isEmpty())
            assertFalse(fixture.controller.captchaLoading)
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun reloadingCaptchaDropsTheFirstLateResponse() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)
        val api = FakeAuthApi(
            captchaAction = { call ->
                if (call == 1) {
                    firstStarted.countDown()
                    awaitIgnoringInterruption(releaseFirst)
                    firstFinished.countDown()
                    CaptchaChallenge("old", "old-image", 60)
                } else {
                    CaptchaChallenge("new", "new-image", 60)
                }
            }
        )
        val fixture = fixture(this, api)
        try {
            fixture.controller.loadCaptcha()
            awaitCondition { firstStarted.count == 0L }

            fixture.controller.loadCaptcha()
            awaitCondition { fixture.controller.captchaId == "new" }
            releaseFirst.countDown()
            awaitCondition { firstFinished.count == 0L }
            yield()

            assertEquals("new", fixture.controller.captchaId)
            assertEquals("new-image", fixture.controller.captchaImageBase64)
            assertFalse(fixture.controller.captchaLoading)
            assertTrue(fixture.loginErrors.isEmpty())
        } finally {
            releaseFirst.countDown()
            fixture.close()
        }
    }

    @Test
    fun clearingFlowCancelsRegistrationWithoutPublishingItsLateResult() = runBlocking {
        val registrationStarted = CountDownLatch(1)
        val releaseRegistration = CountDownLatch(1)
        val registrationFinished = CountDownLatch(1)
        val api = FakeAuthApi(
            registrationAction = {
                registrationStarted.countDown()
                awaitIgnoringInterruption(releaseRegistration)
                registrationFinished.countDown()
                RegistrationResult(1, it.username, it.nickname, 0, "device-password")
            }
        )
        val fixture = fixture(this, api)
        var callbackCalled = false
        try {
            fixture.controller.register(
                username = "operator",
                callsign = "BH1ABC",
                nickname = "Operator",
                email = "operator@example.test",
                phone = "",
                password = "secret1",
                confirmPassword = "secret1",
                sessionId = "email-session",
                emailCode = "123456",
                onSuccess = { callbackCalled = true }
            )
            awaitCondition { registrationStarted.count == 0L }
            assertTrue(fixture.controller.busy)

            fixture.controller.clearFlowState()
            releaseRegistration.countDown()
            awaitCondition { registrationFinished.count == 0L }
            yield()

            assertFalse(fixture.controller.busy)
            assertFalse(callbackCalled)
            assertEquals("", fixture.controller.error)
        } finally {
            releaseRegistration.countDown()
            fixture.close()
        }
    }

    @Test
    fun registrationConfigAndCaptchaUseIndependentTaskSlots() = runBlocking {
        val configStarted = CountDownLatch(1)
        val releaseConfig = CountDownLatch(1)
        val api = FakeAuthApi(
            captchaAction = { CaptchaChallenge("captcha", "image", 60) },
            registrationConfigAction = {
                configStarted.countDown()
                awaitIgnoringInterruption(releaseConfig)
                false
            }
        )
        val fixture = fixture(this, api)
        try {
            fixture.controller.loadRegistrationConfig()
            awaitCondition { configStarted.count == 0L }

            fixture.controller.loadCaptcha()
            awaitCondition { fixture.controller.captchaId == "captcha" }

            assertTrue(fixture.controller.registrationConfigLoading)
            assertFalse(fixture.controller.captchaLoading)

            releaseConfig.countDown()
            awaitCondition { !fixture.controller.registrationConfigLoading }
            assertFalse(fixture.controller.registrationRequiresEmailVerification)
        } finally {
            releaseConfig.countDown()
            fixture.close()
        }
    }

    private fun fixture(scope: CoroutineScope, api: FakeAuthApi): Fixture {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val loginErrors = mutableListOf<String>()
        return Fixture(
            controller = PublicAuthController(
                api = api,
                scope = scope,
                ioDispatcher = dispatcher,
                showLoginError = loginErrors::add,
                friendlyError = { it.message ?: "request failed" }
            ),
            dispatcher = dispatcher,
            loginErrors = loginErrors
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }
}

private data class Fixture(
    val controller: PublicAuthController,
    val dispatcher: ExecutorCoroutineDispatcher,
    val loginErrors: List<String>
) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeAuthApi(
    private val captchaAction: (Int) -> CaptchaChallenge = { error("Unexpected captcha request $it") },
    private val registrationConfigAction: () -> Boolean = { error("Unexpected registration config request") },
    private val registrationAction: (
        RegistrationRequest
    ) -> RegistrationResult = { error("Unexpected registration: $it") }
) : AuthApi {
    private val captchaCalls = AtomicInteger(0)

    override fun getCaptcha(baseUrl: String): CaptchaChallenge = captchaAction(captchaCalls.incrementAndGet())

    override fun login(
        baseUrl: String,
        username: String,
        password: String,
        captchaId: String,
        captchaCode: String
    ): Session = error("Unexpected login")

    override fun getRegistrationRequiresEmailVerification(baseUrl: String): Boolean = registrationConfigAction()

    override fun sendEmailCode(
        baseUrl: String,
        email: String,
        purpose: String,
        captchaId: String,
        captchaCode: String
    ): EmailCodeSession = error("Unexpected email code request")

    override fun register(request: RegistrationRequest): RegistrationResult = registrationAction(request)

    override fun resetPassword(baseUrl: String, sessionId: String, code: String, newPassword: String) =
        error("Unexpected password reset")
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
