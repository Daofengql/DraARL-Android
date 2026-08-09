package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSessionManagerTest {
    @Test
    fun concurrentUnauthorizedResponsesShareOneRefreshAndRetryWithNewToken() = withServer { server ->
        val oldRequestsArrived = CountDownLatch(2)
        val oldRequestCount = AtomicInteger()
        val refreshedRequestCount = AtomicInteger()
        val refreshCount = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == RESOURCE_PATH && request.authorization == "Bearer old-token" -> {
                    oldRequestCount.incrementAndGet()
                    oldRequestsArrived.countDown()
                    if (!oldRequestsArrived.await(2, TimeUnit.SECONDS)) {
                        jsonResponse(500, """{"code":500,"message":"requests did not overlap"}""")
                    } else {
                        jsonResponse(401, """{"code":401,"message":"expired"}""")
                    }
                }

                request.path == REFRESH_PATH -> {
                    refreshCount.incrementAndGet()
                    jsonResponse(200, refreshResponse("new-token"))
                }

                request.path == RESOURCE_PATH && request.authorization == "Bearer new-token" -> {
                    refreshedRequestCount.incrementAndGet()
                    jsonResponse(200, """{"code":200,"data":{"value":"ready"}}""")
                }

                else -> jsonResponse(404, """{"code":404,"message":"unexpected request"}""")
            }
        }
        val fixture = fixture(server, session = session(server, accessToken = "old-token"))
        val results = ConcurrentLinkedQueue<String>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val start = CountDownLatch(1)
        val workers = List(2) { index ->
            thread(start = true, name = "api-session-test-$index") {
                start.await()
                runCatching { fixture.manager.execute("GET", RESOURCE_PATH) }
                    .onSuccess { results.add(it.getJSONObject("data").getString("value")) }
                    .onFailure(failures::add)
            }
        }

        start.countDown()
        workers.forEach { it.join(5_000) }

        assertTrue(workers.none { it.isAlive })
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(listOf("ready", "ready"), results.sorted())
        assertEquals(2, oldRequestCount.get())
        assertEquals(1, refreshCount.get())
        assertEquals(2, refreshedRequestCount.get())
        assertEquals("new-token", fixture.manager.currentSession()?.accessToken)
        assertEquals("new-token", fixture.storage.current()?.accessToken)
        assertEquals(1, fixture.storage.saveCount.get())
        assertEquals(listOf("new-token"), fixture.sessionChanges.mapNotNull { it?.accessToken })
    }

    @Test
    fun failedRefreshClearsSessionStorageAndNotifiesObserver() = withServer { server ->
        server.enqueue(jsonResponse(401, """{"code":401,"message":"expired"}"""))
        server.enqueue(jsonResponse(401, """{"code":401,"message":"refresh rejected"}"""))
        val fixture = fixture(server, session = session(server))

        val error = assertThrows(ApiException::class.java) {
            fixture.manager.execute("GET", RESOURCE_PATH)
        }

        assertEquals(401, error.code)
        assertNull(fixture.manager.currentSession())
        assertNull(fixture.storage.current())
        assertEquals(1, fixture.storage.clearCount.get())
        assertEquals(listOf<Session?>(null), fixture.sessionChanges)
        assertEquals(2, server.requestCount)
        val resourceRequest = server.takeRequest()
        val refreshRequest = server.takeRequest()
        assertEquals(RESOURCE_PATH, resourceRequest.path)
        assertEquals("Bearer access-token", resourceRequest.authorization)
        assertEquals(REFRESH_PATH, refreshRequest.path)
        assertEquals("refresh-token", JSONObject(refreshRequest.body.readUtf8()).getString("refresh_token"))
    }

    @Test
    fun forceRefreshRenewsTokenEvenWhenCurrentTokenIsFresh() = withServer { server ->
        server.enqueue(jsonResponse(200, refreshResponse("forced-token")))
        val now = 10_000L
        val fixture = fixture(
            server,
            session = session(server, accessExpiresAt = now + TimeUnit.HOURS.toMillis(1)),
            clockMillis = { now }
        )

        assertEquals("access-token", fixture.manager.accessToken(forceRefresh = false))
        assertEquals(0, server.requestCount)
        assertEquals("forced-token", fixture.manager.accessToken(forceRefresh = true))
        assertEquals(1, server.requestCount)
        assertEquals(REFRESH_PATH, server.takeRequest().path)
    }

    @Test
    fun tokenExpiringWithinMarginIsRefreshed() = withServer { server ->
        server.enqueue(jsonResponse(200, refreshResponse("fresh-token")))
        val now = 20_000L
        val fixture = fixture(
            server,
            session = session(server, accessExpiresAt = now + TimeUnit.SECONDS.toMillis(30)),
            clockMillis = { now }
        )

        assertEquals("fresh-token", fixture.manager.accessToken(forceRefresh = false))
        assertEquals(1, server.requestCount)
        assertEquals("fresh-token", fixture.storage.current()?.accessToken)
    }

    @Test
    fun newerAuthOperationPreventsOldResultFromReplacingSession() = withServer { server ->
        val fixture = fixture(server, session = null)
        val oldOperation = fixture.manager.beginAuthOperation()
        val currentOperation = fixture.manager.beginAuthOperation()
        val current = session(server, accessToken = "current-token")
        fixture.manager.completeAuthOperation(currentOperation, current, "cancelled")

        val error = assertThrows(ApiException::class.java) {
            fixture.manager.completeAuthOperation(
                oldOperation,
                session(server, accessToken = "stale-token"),
                "cancelled"
            )
        }

        assertEquals(409, error.code)
        assertEquals("current-token", fixture.manager.currentSession()?.accessToken)
        assertEquals("current-token", fixture.storage.current()?.accessToken)
        assertEquals(1, fixture.storage.saveCount.get())
    }

    @Test
    fun preparingSameNormalizedBaseUrlDoesNotPersistAgain() = withServer { server ->
        val stored = session(server)
        val fixture = fixture(server, session = stored)

        val prepared = fixture.manager.prepareCurrentSession("${stored.baseUrl}/")

        assertEquals(stored, prepared)
        assertEquals(0, fixture.storage.saveCount.get())
        assertTrue(fixture.sessionChanges.isEmpty())
    }

    @Test
    fun staleProfileResultCannotMutateReplacementSession() = withServer { server ->
        val original = session(server, accessToken = "original-token")
        val fixture = fixture(server, session = original)
        val expectation = fixture.manager.sessionExpectation()
        val operation = fixture.manager.beginAuthOperation()
        val replacement = session(server, accessToken = "replacement-token")
        fixture.manager.completeAuthOperation(operation, replacement, "cancelled")

        fixture.manager.acceptUser(original.user.copy(nickname = "stale"), expected = expectation)
        fixture.manager.clearSession(expected = expectation)

        assertEquals(replacement, fixture.manager.currentSession())
        assertEquals(replacement, fixture.storage.current())
        assertEquals(0, fixture.storage.clearCount.get())
        assertEquals(1, fixture.storage.saveCount.get())
    }

    @Test
    fun profileExpectationRemainsValidAcrossTokenRefresh() = withServer { server ->
        server.enqueue(jsonResponse(200, refreshResponse("refreshed-token")))
        val fixture = fixture(server, session = session(server))
        val expectation = fixture.manager.sessionExpectation()

        assertEquals("refreshed-token", fixture.manager.accessToken(forceRefresh = true))
        fixture.manager.acceptUser(user = session(server).user.copy(nickname = "Updated"), expected = expectation)

        assertEquals("refreshed-token", fixture.manager.currentSession()?.accessToken)
        assertEquals("Updated", fixture.manager.currentSession()?.user?.nickname)
        assertEquals("Updated", fixture.storage.current()?.user?.nickname)
    }

    private fun fixture(
        server: MockWebServer,
        session: Session?,
        clockMillis: () -> Long = { TEST_NOW }
    ): SessionFixture {
        val storage = FakeApiSessionStorage(session)
        val changes = CopyOnWriteArrayList<Session?>()
        val transport = LoopbackHttpTransport(server, OkHttpTransport(allowCleartext = true))
        val manager = ApiSessionManager(storage, ApiRequestExecutor(transport), changes::add, clockMillis)
        return SessionFixture(manager, storage, changes)
    }

    private fun session(
        server: MockWebServer,
        accessToken: String = "access-token",
        accessExpiresAt: Long = TEST_NOW + TimeUnit.HOURS.toMillis(1)
    ) = Session(
        baseUrl = "https://localhost:${server.port}",
        accessToken = accessToken,
        refreshToken = "refresh-token",
        accessExpiresAt = accessExpiresAt,
        refreshExpiresAt = TEST_NOW + TimeUnit.DAYS.toMillis(1),
        user = User(id = 7, username = "BG0TEST")
    )

    private fun withServer(block: (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            block(server)
        } finally {
            server.shutdown()
        }
    }

    private companion object {
        const val REFRESH_PATH = "/api/auth/refresh"
        const val RESOURCE_PATH = "/api/resource"
        const val TEST_NOW = 1_000_000L
    }
}

private data class SessionFixture(
    val manager: ApiSessionManager,
    val storage: FakeApiSessionStorage,
    val sessionChanges: List<Session?>
)

private class FakeApiSessionStorage(initial: Session?) : ApiSessionStorage {
    private val session = AtomicReference(initial)
    val saveCount = AtomicInteger()
    val clearCount = AtomicInteger()

    override fun load(): Session? = current()

    override fun save(session: Session) {
        this.session.set(session)
        saveCount.incrementAndGet()
    }

    override fun clear() {
        session.set(null)
        clearCount.incrementAndGet()
    }

    fun current(): Session? = session.get()
}

private class LoopbackHttpTransport(private val server: MockWebServer, private val delegate: HttpTransport) :
    HttpTransport {
    override fun newCall(request: HttpRequest): HttpCall {
        val uri = URI(request.url)
        val path = buildString {
            append(uri.rawPath)
            uri.rawQuery?.let { append('?').append(it) }
        }
        return delegate.newCall(request.copy(url = server.url(path).toString()))
    }
}

private val RecordedRequest.authorization: String?
    get() = getHeader("Authorization")

private fun jsonResponse(status: Int, body: String): MockResponse = MockResponse()
    .setResponseCode(status)
    .addHeader("Content-Type", "application/json")
    .setBody(body)

private fun refreshResponse(token: String): String =
    """{"code":200,"data":{"token":"$token","expires_in":3600,"refresh_expires_in":7200}}"""
