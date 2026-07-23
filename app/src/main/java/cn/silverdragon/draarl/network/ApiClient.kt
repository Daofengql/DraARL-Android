package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.CaptchaChallenge
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.CommunicationStats
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.PlatformInfo
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class ApiException(val code: Int, override val message: String) : Exception(message)

class ApiClient(
    private val sessionStore: SecureSessionStore,
    private val onSessionChanged: (Session?) -> Unit = {},
) {
    private val refreshLock = Any()
    private val sessionRef = AtomicReference(sessionStore.load())

    fun currentSession(): Session? = sessionRef.get()

    fun freshAccessToken(): String {
        val current = currentSession() ?: throw ApiException(401, "请先登录")
        if (current.accessExpiresAt <= System.currentTimeMillis() + 60_000L) {
            if (!refreshSession(current.accessToken)) throw ApiException(401, "登录状态已失效，请重新登录")
        }
        return currentSession()?.accessToken ?: throw ApiException(401, "登录状态已失效，请重新登录")
    }

    fun renewAccessToken(): String {
        val current = currentSession() ?: throw ApiException(401, "请先登录")
        if (!refreshSession(current.accessToken)) throw ApiException(401, "登录状态已失效，请重新登录")
        return currentSession()?.accessToken ?: throw ApiException(401, "登录状态已失效，请重新登录")
    }

    fun getCaptcha(baseUrl: String): CaptchaChallenge {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val data = rawRequest(
            baseUrl = normalizedUrl,
            method = "GET",
            path = "/api/captcha",
            body = null,
            accessToken = null,
        ).requireSuccess().requireObject("data")
        return CaptchaChallenge(
            id = data.requireString("captcha_id"),
            imageBase64 = data.optStringClean("captcha_image")
                .ifBlank { data.optStringClean("image_base64") }
                .ifBlank { throw ApiException(500, "服务器响应缺少验证码图片") },
            expiresInSeconds = data.optInt("expire", 300),
        )
    }

    fun login(
        baseUrl: String,
        username: String,
        password: String,
        captchaId: String,
        captchaCode: String,
    ): Session {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val response = rawRequest(
            baseUrl = normalizedUrl,
            method = "POST",
            path = "/api/auth/login",
            body = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("captcha_id", captchaId)
                .put("captcha_code", captchaCode.trim()),
            accessToken = null,
        ).requireSuccess()
        val data = response.requireObject("data")
        val now = System.currentTimeMillis()
        val session = Session(
            baseUrl = normalizedUrl,
            accessToken = data.requireString("token"),
            refreshToken = data.optStringClean("refresh_token"),
            accessExpiresAt = now + data.optLong("expires_in", 10_800L) * 1_000L,
            refreshExpiresAt = now + data.optLong("refresh_expires_in", 1_209_600L) * 1_000L,
            user = parseUser(data.requireObject("user")),
        )
        updateSession(session)
        return session
    }

    fun restoreAndValidate(): Session {
        val session = currentSession() ?: throw ApiException(401, "登录状态不存在")
        val user = getMe()
        return currentSession()?.copy(user = user)?.also(::updateSession)
            ?: throw ApiException(401, "登录状态已失效")
    }

    fun logout() {
        runCatching { request("POST", "/api/auth/logout", JSONObject()) }
        updateSession(null)
    }

    fun getMe(): User {
        val user = parseUser(request("GET", "/api/me").requireObject("data"))
        currentSession()?.copy(user = user)?.let(::updateSession)
        return user
    }

    fun getPlatformInfo(): PlatformInfo {
        val data = request("GET", "/api/platform/info", requiresAuth = false).requireObject("data")
        return PlatformInfo(
            name = data.optStringClean("name").ifBlank { "DraARL 麟链" },
            version = data.optStringClean("version"),
            protocolVersion = data.optStringClean("protocol_version").ifBlank { "DraARLv1" },
        )
    }

    fun getAccessPoints(): List<AccessPoint> {
        val items = request("GET", "/api/access-points").requireObject("data").optJSONArray("items")
            ?: JSONArray()
        return items.objects().mapNotNull { item ->
            val host = item.optStringClean("udp_host")
            val port = item.optInt("udp_port")
            if (host.isBlank() || port !in 1..65535) return@mapNotNull null
            AccessPoint(
                id = item.optStringClean("id").ifBlank { "$host:$port" },
                displayName = item.optStringClean("display_name").ifBlank { host },
                host = host,
                port = port,
                region = item.optStringClean("region"),
                network = item.optStringClean("network"),
                priority = item.optInt("priority", 100),
            )
        }.sortedWith(compareBy(AccessPoint::priority, AccessPoint::displayName))
    }

    fun getDevices(): List<Device> {
        val items = request("GET", "/api/devices?page=1&limit=100").requireObject("data")
            .optJSONArray("items") ?: JSONArray()
        return items.objects().map { item ->
            Device(
                id = item.optInt("id"),
                name = item.optStringClean("name"),
                callsign = item.optStringClean("callsign"),
                ssid = item.optInt("ssid"),
                model = item.optInt("dev_model"),
                groupId = item.optInt("group_id"),
                online = item.optBoolean("is_online"),
                enabled = item.optInt("status", 1) == 1,
                disableSend = item.optBoolean("disable_send"),
                disableReceive = item.optBoolean("disable_recv"),
                qth = item.optStringClean("qth"),
                note = item.optStringClean("note"),
                onlineTime = item.optStringClean("online_time"),
                entryName = item.optStringClean("entry_node_name"),
            )
        }
    }

    fun getGroups(): List<Group> {
        val response = request("GET", "/api/groups?page=1&page_size=100").requireObject("data")
        val items = response.optJSONArray("items") ?: JSONArray()
        val groups = items.objects().map(::parseGroup)
        val realtime = runCatching { getGroupStats() }.getOrDefault(emptyMap())
        return groups.map { group ->
            realtime[group.id]?.let { (online, total) ->
                group.copy(onlineCount = online, totalCount = total)
            } ?: group
        }
    }

    fun getGroupStats(): Map<Int, Pair<Int, Int>> {
        val data = request("GET", "/api/radio/groups/stats").optJSONArray("data") ?: JSONArray()
        return data.objects().associate { item ->
            item.optInt("id") to Pair(
                item.optInt("online_dev_number", item.optInt("online_count")),
                item.optInt("total_dev_number", item.optInt("total_count")),
            )
        }
    }

    fun getOnlineDevices(groupId: Int): List<OnlineDevice> {
        val data = request("GET", "/api/radio/groups/$groupId/devices").optJSONArray("data")
            ?: JSONArray()
        return data.objects().map { item ->
            OnlineDevice(
                id = item.optInt("id"),
                username = item.optStringClean("username"),
                callsign = item.optStringClean("callsign"),
                ssid = item.optInt("ssid"),
                nickname = item.optStringClean("nickname"),
                model = item.optInt("dev_model"),
                ghost = item.optBoolean("is_ghost"),
                disableSend = item.optBoolean("disable_send"),
                disableReceive = item.optBoolean("disable_recv"),
                lastActivity = item.optStringClean("last_activity"),
            )
        }
    }

    fun switchRadioGroup(groupId: Int) {
        request(
            "PUT",
            "/api/radio/group",
            JSONObject().put("group_id", groupId).put("dev_model", ANDROID_DEVICE_MODEL),
        )
    }

    fun joinGroup(groupId: Int, password: String) {
        request("POST", "/api/groups/$groupId/join", JSONObject().put("password", password))
    }

    fun leaveGroup(groupId: Int) {
        request("POST", "/api/groups/$groupId/leave", JSONObject())
    }

    fun getCommunicationStats(): CommunicationStats {
        val data = request("GET", "/api/comm-records/user-stats").requireObject("data")
        return CommunicationStats(
            totalCount = data.optInt("total_count"),
            totalSize = data.optLong("total_size"),
            totalDurationMs = data.optLong("total_duration"),
        )
    }

    fun getCommunicationRecords(page: Int = 1, groupId: Int? = null): List<CommunicationRecord> {
        val query = buildString {
            append("/api/comm-records?page=").append(page).append("&page_size=30")
            if (groupId != null) append("&group_id=").append(groupId)
        }
        val data = request("GET", query).requireObject("data")
        val records = data.optJSONArray("list") ?: JSONArray()
        return records.objects().map { item ->
            CommunicationRecord(
                id = item.optInt("id"),
                deviceName = item.optStringClean("device_name"),
                model = item.optInt("dev_model"),
                groupId = item.optNullableInt("group_id"),
                groupName = item.optStringClean("group_name"),
                username = item.optStringClean("username"),
                nickname = item.optStringClean("nickname"),
                startedAt = item.optStringClean("start_time"),
                durationMs = item.optLong("duration_ms"),
                messageType = item.optInt("msg_type"),
                text = item.optStringClean("text_content"),
                audioUrl = item.optStringClean("audio_url"),
            )
        }
    }

    fun updateProfile(nickname: String, phone: String, address: String, introduction: String): User {
        val body = JSONObject()
            .put("nickname", nickname)
            .put("phone", phone)
            .put("address", address)
            .put("introduction", introduction)
        request("PUT", "/api/me", body)
        return getMe()
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        requiresAuth: Boolean = true,
        allowRefresh: Boolean = true,
    ): JSONObject {
        val session = currentSession()
        if (requiresAuth && session == null) throw ApiException(401, "请先登录")
        val baseUrl = session?.baseUrl ?: throw ApiException(400, "服务器地址未配置")
        val token = if (requiresAuth) session.accessToken else null
        val response = rawRequest(baseUrl, method, path, body, token)
        val code = response.optInt("code", 200)
        if (code == 401 && requiresAuth && allowRefresh && refreshSession(token.orEmpty())) {
            return request(method, path, body, requiresAuth, allowRefresh = false)
        }
        return response.requireSuccess()
    }

    private fun refreshSession(tokenUsed: String): Boolean = synchronized(refreshLock) {
        val current = currentSession() ?: return@synchronized false
        if (current.accessToken != tokenUsed) return@synchronized true
        if (current.refreshToken.isBlank() || current.refreshExpiresAt <= System.currentTimeMillis()) {
            updateSession(null)
            return@synchronized false
        }
        val response = runCatching {
            rawRequest(
                current.baseUrl,
                "POST",
                "/api/auth/refresh",
                JSONObject().put("refresh_token", current.refreshToken),
                accessToken = null,
            ).requireSuccess()
        }.getOrElse {
            updateSession(null)
            return@synchronized false
        }
        val data = response.requireObject("data")
        val now = System.currentTimeMillis()
        updateSession(
            current.copy(
                accessToken = data.requireString("token"),
                refreshToken = data.optStringClean("refresh_token").ifBlank { current.refreshToken },
                accessExpiresAt = now + data.optLong("expires_in", 10_800L) * 1_000L,
                refreshExpiresAt = now + data.optLong("refresh_expires_in", 1_209_600L) * 1_000L,
            ),
        )
        true
    }

    private fun rawRequest(
        baseUrl: String,
        method: String,
        path: String,
        body: JSONObject?,
        accessToken: String?,
    ): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (!accessToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse {
                throw ApiException(status, "服务器返回了无法识别的数据")
            }
            if (!json.has("code")) json.put("code", status)
            if (status !in 200..299 && json.optInt("code") < 400) json.put("code", status)
            return json
        } catch (error: ApiException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(0, error.message ?: "无法连接服务器")
        } finally {
            connection.disconnect()
        }
    }

    private fun updateSession(session: Session?) {
        sessionRef.set(session)
        if (session == null) sessionStore.clearSession() else sessionStore.save(session)
        onSessionChanged(session)
    }

    private fun parseUser(json: JSONObject): User {
        val roles = json.opt("roles")
        val role = when (roles) {
            is JSONArray -> roles.optString(0, "user")
            is String -> roles.substringBefore(',').trim().ifBlank { "user" }
            else -> json.optStringClean("role").ifBlank {
                if (json.optBoolean("isAdmin")) "admin" else "user"
            }
        }
        return User(
            id = json.optInt("id"),
            username = json.optStringClean("username"),
            nickname = json.optStringClean("nickname"),
            callsign = json.optStringClean("callsign"),
            email = json.optStringClean("email"),
            role = role,
            approvalStatus = json.optInt("approval_status"),
            reviewNote = json.optStringClean("review_note"),
            avatarUrl = json.optStringClean("avatar_thumb").ifBlank { json.optStringClean("avatar") },
            address = json.optStringClean("address"),
            phone = json.optStringClean("phone"),
            introduction = json.optStringClean("introduction"),
            dmrId = json.optInt("dmrid"),
            lastGroupId = json.optInt("last_group_id", 999).takeIf { it > 0 } ?: 999,
        )
    }

    private fun parseGroup(json: JSONObject) = Group(
        id = json.optInt("id"),
        name = json.optStringClean("name"),
        type = json.optInt("type"),
        status = json.optInt("status", 1),
        note = json.optStringClean("note"),
        ownerCallsign = json.optStringClean("ower_callsign"),
        joined = json.optBoolean("is_joined"),
        owner = json.optBoolean("is_owner"),
        requiresPassword = json.optBoolean("require_password"),
        onlineCount = json.optInt("online_count"),
        totalCount = json.optInt("total_count"),
    )

    companion object {
        const val ANDROID_DEVICE_MODEL = 101
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) throw ApiException(400, "请输入服务器地址")
            val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = runCatching { URI(normalized) }.getOrNull()
                ?: throw ApiException(400, "服务器地址格式不正确")
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                throw ApiException(400, "服务器地址必须是 HTTP 或 HTTPS 地址")
            }
            return normalized
        }
    }
}

private fun JSONObject.requireSuccess(): JSONObject {
    val code = optInt("code", 200)
    if (code !in 200..299) {
        throw ApiException(code, optStringClean("message").ifBlank { "请求失败 ($code)" })
    }
    return this
}

private fun JSONObject.requireObject(key: String): JSONObject = optJSONObject(key)
    ?: throw ApiException(optInt("code", 500), "服务器响应缺少 $key")

private fun JSONObject.requireString(key: String): String = optStringClean(key)
    .ifBlank { throw ApiException(optInt("code", 500), "服务器响应缺少 $key") }

private fun JSONObject.optStringClean(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key).takeUnless { it == "null" } ?: ""
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}
