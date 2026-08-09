package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.Session
import java.net.URI
import org.json.JSONObject

class ApiException(
    val code: Int,
    override val message: String,
    val errorCode: String = "",
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class ApiClient private constructor(dependencies: ApiClientDependencies) :
    AuthApi by dependencies.auth,
    DevicesApi by dependencies.devices,
    GroupsApi by dependencies.groups,
    ProfileApi by dependencies.profile,
    RadioApi by dependencies.radio,
    ToolsApi by dependencies.tools,
    UpdatesApi by dependencies.updates {
    private val sessions = dependencies.sessions

    constructor(
        sessionStore: SecureSessionStore,
        onSessionChanged: (Session?) -> Unit = {}
    ) : this(createApiClientDependencies(sessionStore, OkHttpTransport(), onSessionChanged))

    internal constructor(
        sessionStore: SecureSessionStore,
        transport: HttpTransport,
        onSessionChanged: (Session?) -> Unit = {}
    ) : this(createApiClientDependencies(sessionStore, transport, onSessionChanged))

    fun currentSession(): Session? = sessions.currentSession()

    internal fun prepareCurrentSession(baseUrl: String): Session? = sessions.prepareCurrentSession(baseUrl)

    fun restoreAndValidate(): Session {
        val operation = sessions.beginAuthOperation()
        currentSession() ?: throw ApiException(HTTP_UNAUTHORIZED, "登录状态不存在")
        val user = getMe(updateSession = false)
        val current = currentSession() ?: throw ApiException(HTTP_UNAUTHORIZED, "登录状态已失效")
        return sessions.completeAuthOperation(operation, current.copy(user = user), "登录恢复已取消")
    }

    internal fun detachSessionForLogout(expected: Session? = null): Session? = sessions.detachSessionForLogout(expected)

    internal fun revokeSession(session: Session): Result<Unit> = sessions.revokeSession(session)

    companion object {
        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) invalidAddress("请输入服务器地址")
            val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = runCatching { URI(normalized) }.getOrNull()
                ?: invalidAddress("服务器地址格式不正确")
            if (uri.scheme != HTTPS_SCHEME || uri.host.isNullOrBlank()) {
                invalidAddress("服务器地址必须是 HTTPS 地址")
            }
            return normalized
        }

        fun resolveHttpsUrl(baseUrl: String, value: String): String {
            val candidate = value.trim()
            if (candidate.isBlank()) return ""
            val base = normalizeBaseUrl(baseUrl)
            val resolved = runCatching { URI("$base/").resolve(candidate) }.getOrNull()
                ?: throw ApiException(HTTP_BAD_REQUEST, "资源地址格式不正确")
            if (resolved.scheme != HTTPS_SCHEME || resolved.host.isNullOrBlank()) {
                throw ApiException(HTTP_BAD_REQUEST, "资源地址必须使用 HTTPS")
            }
            return resolved.toASCIIString()
        }
    }
}

private class ApiClientDependencies(val requests: ApiRequestExecutor, val sessions: ApiSessionManager) {
    private val users = UserJsonMapper { sessions.currentSession()?.baseUrl.orEmpty() }

    val auth: AuthApi = AuthApiClient(requests, sessions, users)
    val devices: DevicesApi = DevicesApiClient(sessions)
    val groups: GroupsApi = GroupsApiClient(sessions)
    val profile: ProfileApi = ProfileApiClient(sessions, requests, sessions, users)
    val radio: RadioApi = RadioApiClient(sessions, sessions, users)
    val tools: ToolsApi = ToolsApiClient(sessions)
    val updates: UpdatesApi = UpdatesApiClient(sessions, sessions::currentSession)
}

private fun createApiClientDependencies(
    sessionStore: SecureSessionStore,
    transport: HttpTransport,
    onSessionChanged: (Session?) -> Unit
): ApiClientDependencies {
    val requests = ApiRequestExecutor(transport)
    val sessions = ApiSessionManager(SecureApiSessionStorage(sessionStore), requests, onSessionChanged)
    return ApiClientDependencies(requests, sessions)
}

internal fun JSONObject.requireSuccess(): JSONObject {
    val code = optInt("code", HTTP_OK)
    if (code !in HTTP_SUCCESS_RANGE) {
        val errorCode = optStringClean("error")
        val retryAfterSeconds = optInt(HTTP_RETRY_AFTER_SECONDS).takeIf { it > 0 }
        val serverMessage = optStringClean("message").ifBlank { "请求失败 ($code)" }
        throw ApiException(
            code = code,
            message = friendlyApiErrorMessage(errorCode, serverMessage, retryAfterSeconds),
            errorCode = errorCode,
            retryAfterSeconds = retryAfterSeconds
        )
    }
    return this
}

internal fun friendlyApiErrorMessage(errorCode: String, serverMessage: String, retryAfterSeconds: Int?): String {
    val retryText = retryAfterSeconds?.takeIf { it > 0 }?.let { "请在 $it 秒后重试" }
    return when (errorCode) {
        "ghost_multi_receive_disabled" -> "当前账号或 Android 客户端尚未开放多频道收听，请仅保留发送频道"
        "message_api_user_rate_limited" -> retryText ?: "当前账号查询消息过于频繁，请稍后重试"
        "message_api_ip_rate_limited" -> retryText ?: "当前网络查询消息过于频繁，请稍后重试"
        "message_api_busy" -> retryText ?: "消息查询繁忙，请稍后重试"
        "invalid_message_limit" -> "消息分页大小不符合服务端配置，请更新客户端后重试"
        else -> serverMessage
    }
}

internal fun JSONObject.requireObject(key: String): JSONObject = optJSONObject(key)
    ?: throw ApiException(optInt("code", HTTP_SERVER_ERROR), "服务器响应缺少 $key")

internal fun JSONObject.requireString(key: String): String = optStringClean(key)
    .ifBlank { throw ApiException(optInt("code", HTTP_SERVER_ERROR), "服务器响应缺少 $key") }

internal fun JSONObject.optStringClean(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key).takeUnless { it == "null" }.orEmpty()
}

private fun invalidAddress(message: String): Nothing = throw ApiException(HTTP_BAD_REQUEST, message)

private const val HTTPS_SCHEME = "https"
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_OK = 200
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_SUCCESS_MAX = 299
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_UNAUTHORIZED = 401
private val HTTP_SUCCESS_RANGE = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
