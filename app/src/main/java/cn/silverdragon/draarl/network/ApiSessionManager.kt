package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal interface ApiSessionStorage {
    fun load(): Session?

    fun save(session: Session)

    fun clear()
}

internal class SecureApiSessionStorage(private val store: SecureSessionStore) : ApiSessionStorage {
    override fun load(): Session? = store.load()

    override fun save(session: Session) = store.save(session)

    override fun clear() = store.clearSession()
}

internal class ApiRequestExecutor(private val transport: HttpTransport) {
    fun executeJson(
        baseUrl: String,
        method: String,
        path: String,
        body: JSONObject?,
        accessToken: String?
    ): JSONObject {
        val headers = buildMap {
            put("Accept", "application/json")
            put("Cache-Control", "no-cache")
            if (!accessToken.isNullOrBlank()) put("Authorization", "Bearer $accessToken")
        }
        val requestBody = body?.toString()?.toByteArray(Charsets.UTF_8)?.let {
            HttpRequestBody.Bytes(it, "application/json; charset=utf-8")
        }
        return execute(
            HttpRequest(
                url = ApiClient.normalizeBaseUrl(baseUrl) + path,
                method = method,
                headers = headers,
                body = requestBody
            )
        ).toApiJson()
    }

    fun execute(request: HttpRequest): HttpResponse = try {
        transport.execute(request)
    } catch (error: HttpTransportException) {
        throw ApiException(0, error.message ?: "无法连接服务器", cause = error)
    }
}

internal class ApiSessionManager(
    storage: ApiSessionStorage,
    private val requests: ApiRequestExecutor,
    onSessionChanged: (Session?) -> Unit = {},
    clockMillis: () -> Long = System::currentTimeMillis
) {
    private val state = ApiSessionState(storage, onSessionChanged)
    private val tokenRefresher = ApiTokenRefresher(state, requests, clockMillis)
    private val requester = AuthenticatedApiRequester(state, requests, tokenRefresher)

    fun currentSession(): Session? = state.current()

    fun prepareCurrentSession(baseUrl: String): Session? = state.prepare(baseUrl)

    fun beginAuthOperation(): Int = state.beginOperation()

    fun completeAuthOperation(operation: Int, session: Session, cancelledMessage: String): Session =
        state.completeOperation(operation, session, cancelledMessage)

    fun accessToken(forceRefresh: Boolean): String = tokenRefresher.accessToken(forceRefresh)

    fun execute(method: String, path: String, body: JSONObject? = null, requiresAuth: Boolean = true): JSONObject =
        requester.execute(method, path, body, requiresAuth)

    fun detachSessionForLogout(expected: Session? = null): Session? = state.detach(expected)

    fun revokeSession(session: Session): Result<Unit> = runCatching {
        requests.executeJson(
            baseUrl = session.baseUrl,
            method = "POST",
            path = "/api/auth/logout",
            body = JSONObject(),
            accessToken = session.accessToken
        ).requireSuccess()
        Unit
    }

    fun acceptUser(user: User) {
        val current = currentSession() ?: return
        if (current.user.id == user.id) state.replace(current.copy(user = user))
    }

    fun clearSession() = state.replace(null)
}

private class ApiSessionState(
    private val storage: ApiSessionStorage,
    private val onSessionChanged: (Session?) -> Unit
) {
    private val lock = Any()
    private val operationGeneration = AtomicInteger(0)
    private val sessionRef = AtomicReference(storage.load())

    fun current(): Session? = sessionRef.get()

    fun prepare(baseUrl: String): Session? = locked { current ->
        val normalizedUrl = ApiClient.normalizeBaseUrl(baseUrl)
        when {
            current == null -> null
            current.baseUrl == normalizedUrl -> current
            else -> current.copy(baseUrl = normalizedUrl).also(::replace)
        }
    }

    fun beginOperation(): Int = operationGeneration.incrementAndGet()

    fun completeOperation(operation: Int, session: Session, cancelledMessage: String): Session = locked {
        if (operation != operationGeneration.get()) {
            throw ApiException(AUTH_OPERATION_CANCELLED, cancelledMessage)
        }
        session.also(::replace)
    }

    fun replace(session: Session?) = locked {
        sessionRef.set(session)
        if (session == null) storage.clear() else storage.save(session)
        onSessionChanged(session)
    }

    fun detach(expected: Session?): Session? = locked { current ->
        if (expected != null && current != expected) return@locked null
        operationGeneration.incrementAndGet()
        current?.also { replace(null) }
    }

    fun <T> locked(block: (Session?) -> T): T = synchronized(lock) { block(current()) }
}

private class ApiTokenRefresher(
    private val state: ApiSessionState,
    private val requests: ApiRequestExecutor,
    private val clockMillis: () -> Long
) {
    fun accessToken(forceRefresh: Boolean): String {
        val current = requireSession("请先登录")
        val expiresSoon = current.accessExpiresAt <= clockMillis() + ACCESS_TOKEN_REFRESH_MARGIN_MILLIS
        if ((forceRefresh || expiresSoon) && !refreshIfCurrent(current.accessToken)) {
            throwSessionExpired()
        }
        return requireSession("登录状态已失效，请重新登录").accessToken
    }

    fun refreshIfCurrent(tokenUsed: String): Boolean = state.locked { current ->
        when {
            current == null -> false

            current.accessToken != tokenUsed -> true

            current.refreshToken.isBlank() || current.refreshExpiresAt <= clockMillis() -> {
                state.replace(null)
                false
            }

            else -> refresh(current)
        }
    }

    private fun refresh(current: Session): Boolean = runCatching {
        val data = requests.executeJson(
            baseUrl = current.baseUrl,
            method = "POST",
            path = "/api/auth/refresh",
            body = JSONObject().put("refresh_token", current.refreshToken),
            accessToken = null
        )
            .requireSuccess()
            .requireObject("data")
        val now = clockMillis()
        state.replace(
            current.copy(
                accessToken = data.requireString("token"),
                refreshToken = data.optStringClean("refresh_token").ifBlank { current.refreshToken },
                accessExpiresAt = now +
                    data.optLong("expires_in", DEFAULT_ACCESS_EXPIRES_SECONDS) * MILLIS_PER_SECOND,
                refreshExpiresAt = now +
                    data.optLong("refresh_expires_in", DEFAULT_REFRESH_EXPIRES_SECONDS) * MILLIS_PER_SECOND
            )
        )
        true
    }.getOrElse {
        state.replace(null)
        false
    }

    private fun requireSession(message: String): Session = state.current()
        ?: throw ApiException(HTTP_UNAUTHORIZED, message)

    private fun throwSessionExpired(): Nothing = throw ApiException(
        HTTP_UNAUTHORIZED,
        "登录状态已失效，请重新登录"
    )
}

private class AuthenticatedApiRequester(
    private val state: ApiSessionState,
    private val requests: ApiRequestExecutor,
    private val tokenRefresher: ApiTokenRefresher
) {
    fun execute(method: String, path: String, body: JSONObject?, requiresAuth: Boolean): JSONObject {
        val initial = requestContext(requiresAuth)
        val response = requests.executeJson(initial.baseUrl, method, path, body, initial.accessToken)
        if (response.optInt("code", HTTP_OK) == HTTP_UNAUTHORIZED && requiresAuth) {
            if (tokenRefresher.refreshIfCurrent(initial.accessToken.orEmpty())) {
                val refreshed = requestContext(requiresAuth = true)
                return requests.executeJson(refreshed.baseUrl, method, path, body, refreshed.accessToken)
                    .requireSuccess()
            }
        }
        return response.requireSuccess()
    }

    private fun requestContext(requiresAuth: Boolean): ApiRequestContext {
        val session = state.current()
        if (requiresAuth && session == null) throw ApiException(HTTP_UNAUTHORIZED, "请先登录")
        return ApiRequestContext(
            baseUrl = session?.baseUrl ?: throw ApiException(HTTP_BAD_REQUEST, "服务器地址未配置"),
            accessToken = session.accessToken.takeIf { requiresAuth }
        )
    }
}

private data class ApiRequestContext(val baseUrl: String, val accessToken: String?)

private const val ACCESS_TOKEN_REFRESH_MARGIN_MILLIS = 60_000L
private const val AUTH_OPERATION_CANCELLED = 409
private const val DEFAULT_ACCESS_EXPIRES_SECONDS = 10_800L
private const val DEFAULT_REFRESH_EXPIRES_SECONDS = 1_209_600L
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_OK = 200
private const val HTTP_UNAUTHORIZED = 401
private const val MILLIS_PER_SECOND = 1_000L
