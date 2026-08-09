package cn.silverdragon.draarl.session

import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ApiException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerTest {
    @Test
    fun noStoredSessionCompletesInitializationWithoutEffects() = runBlocking {
        val fixture = fixture(this)
        try {
            fixture.controller.start()

            assertEquals(SessionUiState(initializing = false), fixture.controller.uiState)
            assertTrue(fixture.effects.prepared.isEmpty())
            assertTrue(fixture.effects.activated.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun storedSessionIsPreparedThenValidatedAndActivated() = runBlocking {
        val stored = session(user = user(nickname = "缓存用户"))
        val restored = stored.copy(user = user(nickname = "服务端用户"))
        val remote = FakeRemote(currentSession = stored, restoreAction = { restored })
        val fixture = fixture(this, remote)
        try {
            fixture.controller.start()
            assertEquals(stored.user, fixture.controller.uiState.user)
            assertEquals(listOf(stored), fixture.effects.prepared)

            awaitCondition { fixture.controller.uiState.authenticated }

            assertEquals(restored.user, fixture.controller.uiState.user)
            assertEquals(listOf(Activation(restored, SessionEntryPoint.RESTORE)), fixture.effects.activated)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rejectedStoredSessionPublishesServerReasonAndClearsPreparedResources() = runBlocking {
        val stored = session()
        val remote = FakeRemote(
            currentSession = stored,
            restoreAction = { throw ApiException(403, "账号已被停用") }
        )
        val fixture = fixture(this, remote)
        try {
            fixture.controller.start()
            awaitCondition { !fixture.controller.uiState.initializing }

            assertFalse(fixture.controller.uiState.authenticated)
            assertNull(fixture.controller.uiState.user)
            assertEquals("账号已被停用", fixture.controller.uiState.loginError)
            assertEquals(1, fixture.effects.cleared)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun missingCaptchaIsRejectedBeforeLoginAndRequestsAChallenge() = runBlocking {
        val fixture = fixture(this)
        try {
            fixture.controller.start()
            fixture.controller.login("operator", "secret", "", "")

            assertEquals("请输入图片验证码", fixture.controller.uiState.loginError)
            assertFalse(fixture.controller.uiState.loginBusy)
            assertEquals(1, fixture.effects.captchaRequests)
            assertTrue(fixture.remote.loginRequests.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun successfulLoginActivatesSessionAndAcceptsSameAccountUpdates() = runBlocking {
        val loggedIn = session()
        val remote = FakeRemote(loginAction = { loggedIn })
        val fixture = fixture(this, remote)
        try {
            fixture.controller.start()
            fixture.controller.login("operator", "secret", "captcha-id", "1234")
            awaitCondition { fixture.controller.uiState.authenticated }

            assertEquals(
                LoginRequest("https://example.test", "operator", "secret", "captcha-id", "1234"),
                remote.loginRequests.single()
            )
            assertEquals(listOf(Activation(loggedIn, SessionEntryPoint.LOGIN)), fixture.effects.activated)

            val updated = loggedIn.copy(user = loggedIn.user.copy(nickname = "新昵称"))
            fixture.controller.onRemoteSessionChanged(updated)

            assertEquals("新昵称", fixture.controller.uiState.user?.nickname)
            assertEquals(listOf(updated), fixture.effects.updated)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedLoginClearsBusyStateAndReloadsCaptcha() = runBlocking {
        val remote = FakeRemote(loginAction = { throw ApiException(401, "用户名或密码错误") })
        val fixture = fixture(this, remote)
        try {
            fixture.controller.start()
            fixture.controller.login("operator", "bad", "captcha-id", "1234")
            awaitCondition { !fixture.controller.uiState.loginBusy }

            assertEquals("用户名或密码错误", fixture.controller.uiState.loginError)
            assertEquals(1, fixture.effects.captchaRequests)
            assertFalse(fixture.controller.uiState.authenticated)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun logoutClearsStateImmediatelyAndRevokesDetachedSession() = runBlocking {
        val loggedIn = session()
        val remote = FakeRemote(loginAction = { loggedIn })
        val fixture = fixture(this, remote)
        try {
            fixture.controller.start()
            fixture.controller.login("operator", "secret", "captcha-id", "1234")
            awaitCondition { fixture.controller.uiState.authenticated }

            fixture.controller.logout()

            assertEquals(SessionUiState(initializing = false), fixture.controller.uiState)
            assertEquals(1, fixture.effects.cleared)
            assertNull(remote.currentSession)
            awaitCondition { remote.revokedSessions == listOf(loggedIn) }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun staleLoginResultAfterLogoutCannotReactivateOrRemainStored() = runBlocking {
        val loginStarted = CountDownLatch(1)
        val releaseLogin = CountDownLatch(1)
        val staleSession = session()
        val remote = FakeRemote(
            loginAction = {
                loginStarted.countDown()
                try {
                    check(releaseLogin.await(1, TimeUnit.SECONDS))
                } catch (_: InterruptedException) {
                    check(releaseLogin.await(1, TimeUnit.SECONDS))
                }
                staleSession
            }
        )
        val fixture = fixture(this, remote)
        try {
            fixture.controller.start()
            fixture.controller.login("operator", "secret", "captcha-id", "1234")
            awaitCondition { loginStarted.count == 0L }

            fixture.controller.logout()
            releaseLogin.countDown()
            awaitCondition { remote.currentSession == staleSession }
            fixture.controller.onRemoteSessionChanged(staleSession)
            awaitCondition { remote.revokedSessions.contains(staleSession) }

            assertFalse(fixture.controller.uiState.authenticated)
            assertNull(fixture.controller.uiState.user)
            assertNull(remote.currentSession)
            assertTrue(fixture.effects.activated.isEmpty())
        } finally {
            releaseLogin.countDown()
            fixture.close()
        }
    }

    @Test
    fun remoteInvalidationClearsAnActiveSessionOnce() = runBlocking {
        val loggedIn = session()
        val fixture = fixture(this, FakeRemote(loginAction = { loggedIn }))
        try {
            fixture.controller.start()
            fixture.controller.login("operator", "secret", "captcha-id", "1234")
            awaitCondition { fixture.controller.uiState.authenticated }

            fixture.controller.onRemoteSessionChanged(null)
            fixture.controller.onRemoteSessionChanged(null)

            assertFalse(fixture.controller.uiState.authenticated)
            assertNull(fixture.controller.uiState.user)
            assertEquals(1, fixture.effects.cleared)
        } finally {
            fixture.close()
        }
    }

    private fun fixture(
        scope: CoroutineScope,
        remote: FakeRemote = FakeRemote(),
        effects: FakeEffects = FakeEffects()
    ): Fixture {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        return Fixture(
            controller = SessionController(
                remote = remote,
                effects = effects,
                scope = scope,
                ioDispatcher = dispatcher,
                serverUrl = "https://example.test"
            ),
            remote = remote,
            effects = effects,
            dispatcher = dispatcher
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }
}

private data class Fixture(
    val controller: SessionController,
    val remote: FakeRemote,
    val effects: FakeEffects,
    val dispatcher: ExecutorCoroutineDispatcher
) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeRemote(
    var currentSession: Session? = null,
    private val restoreAction: () -> Session = { currentSession ?: error("No stored session") },
    private val loginAction: (LoginRequest) -> Session = { error("Unexpected login: $it") }
) : SessionRemoteDataSource {
    val loginRequests = mutableListOf<LoginRequest>()
    val revokedSessions = mutableListOf<Session>()

    override fun prepareStoredSession(baseUrl: String): Session? = currentSession?.copy(baseUrl = baseUrl).also {
        currentSession = it
    }

    override fun login(
        baseUrl: String,
        username: String,
        password: String,
        captchaId: String,
        captchaCode: String
    ): Session {
        val request = LoginRequest(baseUrl, username, password, captchaId, captchaCode)
        loginRequests += request
        return loginAction(request).also { currentSession = it }
    }

    override fun restoreAndValidate(): Session = restoreAction().also { currentSession = it }

    override fun detachSessionForLogout(expected: Session?): Session? {
        val current = currentSession ?: return null
        if (expected != null && current != expected) return null
        currentSession = null
        return current
    }

    override fun revokeSession(session: Session): Result<Unit> {
        revokedSessions += session
        return Result.success(Unit)
    }
}

private class FakeEffects : SessionEffects {
    val prepared = mutableListOf<Session>()
    val activated = mutableListOf<Activation>()
    val updated = mutableListOf<Session>()
    var cleared = 0
    var captchaRequests = 0

    override fun onStoredSessionPrepared(session: Session) {
        prepared += session
    }

    override fun onSessionActivated(session: Session, entryPoint: SessionEntryPoint) {
        activated += Activation(session, entryPoint)
    }

    override fun onSessionUpdated(session: Session) {
        updated += session
    }

    override fun onSessionCleared() {
        cleared++
    }

    override fun requestLoginCaptcha() {
        captchaRequests++
    }

    override fun friendlyError(error: Throwable): String = error.message ?: "操作失败"
}

private data class LoginRequest(
    val baseUrl: String,
    val username: String,
    val password: String,
    val captchaId: String,
    val captchaCode: String
)

private data class Activation(val session: Session, val entryPoint: SessionEntryPoint)

private fun session(user: User = user()) = Session(
    baseUrl = "https://example.test",
    accessToken = "access-token",
    refreshToken = "refresh-token",
    accessExpiresAt = Long.MAX_VALUE,
    refreshExpiresAt = Long.MAX_VALUE,
    user = user
)

private fun user(nickname: String = "测试用户") = User(
    id = 7,
    username = "operator",
    nickname = nickname,
    callsign = "BI1ABC",
    approvalStatus = 1
)
