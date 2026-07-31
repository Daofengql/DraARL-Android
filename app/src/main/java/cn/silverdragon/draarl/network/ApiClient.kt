package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.auth.accountLoginRejection
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.CaptchaChallenge
import cn.silverdragon.draarl.data.ClientResourceArtifact
import cn.silverdragon.draarl.data.ClientResourceArtifactTarget
import cn.silverdragon.draarl.data.ClientResourceDownload
import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.ClientResourceManifestItem
import cn.silverdragon.draarl.data.ClientResourceRelease
import cn.silverdragon.draarl.data.ClientResourceSummary
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.CommunicationRecordPage
import cn.silverdragon.draarl.data.CommunicationStats
import cn.silverdragon.draarl.data.DailyCommunicationStats
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.PlatformInfo
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.ReplaceableDevice
import cn.silverdragon.draarl.tools.LogbookEntry
import cn.silverdragon.draarl.tools.LogbookPage
import cn.silverdragon.draarl.tools.RadioPreset
import cn.silverdragon.draarl.tools.RelayStation
import cn.silverdragon.draarl.tools.ToolApiJson
import cn.silverdragon.draarl.profile.emailChangeRequestJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
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
            user = parseUser(data.requireObject("user"), normalizedUrl),
        )
        accountLoginRejection(session.user)?.let { message ->
            throw ApiException(403, message)
        }
        updateSession(session)
        return session
    }

    fun getRegistrationRequiresEmailVerification(baseUrl: String): Boolean {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val data = rawRequest(
            baseUrl = normalizedUrl,
            method = "GET",
            path = "/api/config/public",
            body = null,
            accessToken = null,
        ).requireSuccess().requireObject("data")
        return data.optJSONObject("registration")
            ?.optBoolean("require_email_verification", true)
            ?: true
    }

    fun sendEmailCode(
        baseUrl: String,
        email: String,
        purpose: String,
        captchaId: String,
        captchaCode: String,
    ): EmailCodeSession {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val data = rawRequest(
            baseUrl = normalizedUrl,
            method = "POST",
            path = "/api/auth/send-code",
            body = JSONObject()
                .put("email", email.trim())
                .put("purpose", purpose)
                .put("captcha_id", captchaId)
                .put("captcha_code", captchaCode.trim()),
            accessToken = null,
        ).requireSuccess().requireObject("data")
        return EmailCodeSession(
            sessionId = data.requireString("session_id"),
            expiresInSeconds = data.optInt("expires_in", 600),
        )
    }

    fun register(
        baseUrl: String,
        username: String,
        password: String,
        callsign: String,
        phone: String,
        nickname: String,
        email: String,
        sessionId: String,
        emailCode: String,
    ): RegistrationResult {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val body = JSONObject()
            .put("username", username.trim())
            .put("password", password)
            .put("callsign", callsign.trim().uppercase())
            .put("phone", phone.trim())
            .put("nickname", nickname.trim())
            .put("email", email.trim())
        if (sessionId.isNotBlank()) body.put("session_id", sessionId)
        if (emailCode.isNotBlank()) body.put("email_code", emailCode.trim())
        val data = rawRequest(
            baseUrl = normalizedUrl,
            method = "POST",
            path = "/api/auth/register",
            body = body,
            accessToken = null,
        ).requireSuccess().requireObject("data")
        return RegistrationResult(
            id = data.optInt("id"),
            username = data.optStringClean("username"),
            nickname = data.optStringClean("nickname"),
            approvalStatus = data.optInt("approval_status"),
            devicePassword = data.optStringClean("device_password"),
        )
    }

    fun resetPassword(baseUrl: String, sessionId: String, code: String, newPassword: String) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        rawRequest(
            baseUrl = normalizedUrl,
            method = "POST",
            path = "/api/auth/reset-password",
            body = JSONObject()
                .put("session_id", sessionId)
                .put("code", code.trim())
                .put("new_password", newPassword),
            accessToken = null,
        ).requireSuccess()
    }

    fun restoreAndValidate(): Session {
        currentSession() ?: throw ApiException(401, "登录状态不存在")
        val user = getMe(updateSession = false)
        accountLoginRejection(user)?.let { message ->
            updateSession(null)
            throw ApiException(403, message)
        }
        return currentSession()?.copy(user = user)?.also(::updateSession)
            ?: throw ApiException(401, "登录状态已失效")
    }

    fun logout() {
        runCatching { request("POST", "/api/auth/logout", JSONObject()) }
        updateSession(null)
    }

    fun getMe(updateSession: Boolean = true): User {
        val user = parseUser(request("GET", "/api/me").requireObject("data"))
        accountLoginRejection(user)?.let { message ->
            // A successful HTTP response can still represent an account that is
            // no longer allowed to use the client. Clear the persisted session
            // immediately so every caller follows the same rejection path.
            if (currentSession() != null) this.updateSession(null)
            throw ApiException(403, message)
        }
        if (updateSession) currentSession()?.copy(user = user)?.let(::updateSession)
        return user
    }

    fun acceptCurrentUser(user: User) {
        val current = currentSession() ?: return
        if (current.user.id != user.id) return
        if (accountLoginRejection(user) != null) {
            this.updateSession(null)
            return
        }
        updateSession(current.copy(user = user))
    }

    fun getPublicUserByName(username: String): User {
        val encoded = java.net.URLEncoder.encode(username, Charsets.UTF_8.name())
        return parseUser(request("GET", "/api/users/name/$encoded/public").requireObject("data"))
    }

    fun getPlatformInfo(): PlatformInfo {
        val data = request("GET", "/api/platform/info", requiresAuth = false).requireObject("data")
        return PlatformInfo(
            name = data.optStringClean("name").ifBlank { "DraARL 麟链" },
            version = data.optStringClean("version"),
            protocolVersion = data.optStringClean("protocol_version").ifBlank { "DraARLv1" },
        )
    }

    fun getClientResourceManifest(
        platform: String,
        arch: String,
        clientVersion: String = "",
        channel: String = "stable",
        osVersion: String = "",
        androidApi: Int = 0,
    ): ClientResourceManifest {
        val query = buildString {
            append("/api/public/client-resources/manifest?platform=").append(urlEncode(platform))
            append("&arch=").append(urlEncode(arch))
            append("&channel=").append(urlEncode(channel))
            if (clientVersion.isNotBlank()) append("&client_version=").append(urlEncode(clientVersion))
            if (osVersion.isNotBlank()) append("&os_version=").append(urlEncode(osVersion))
            if (androidApi > 0) append("&android_api=").append(androidApi)
        }
        val data = request("GET", query, requiresAuth = false).requireObject("data")
        return parseClientResourceManifest(data)
    }

    fun getClientResourceArtifactDownload(artifactId: Int): ClientResourceDownload {
        val data = request(
            "GET",
            "/api/public/client-resources/artifacts/${artifactId.coerceAtLeast(1)}/download",
            requiresAuth = false,
        ).requireObject("data")
        return ClientResourceDownload(
            artifactId = data.optInt("artifact_id", artifactId),
            downloadUrl = optionalHttpsUrl(data.optStringClean("download_url")),
            urlExpiresAt = data.optStringClean("url_expires_at"),
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
        val result = ArrayList<Device>()
        var page = 1
        var total = Int.MAX_VALUE
        while (result.size < total) {
            val data = request("GET", "/api/devices?page=$page&limit=100&owner_only=true").requireObject("data")
            total = data.optInt("total", total)
            val items = data.optJSONArray("items") ?: JSONArray()
            val pageItems = items.objects().map(::parseDevice)
            if (pageItems.isEmpty()) break
            result += pageItems
            if (pageItems.size < 100) break
            page += 1
        }
        return result
    }

    fun getDefaultDeviceGroup(): Int? = request("GET", "/api/user/device-default-group")
        .requireObject("data")
        .optNullableInt("group_id")

    fun setDefaultDeviceGroup(groupId: Int?): Int? {
        val body = JSONObject().put("group_id", groupId ?: JSONObject.NULL)
        return request("PUT", "/api/user/device-default-group", body)
            .requireObject("data")
            .optNullableInt("group_id")
    }

    fun updateDevice(
        deviceId: Int,
        name: String? = null,
        disableSend: Boolean? = null,
        disableReceive: Boolean? = null,
    ): Device {
        val body = JSONObject().apply {
            name?.let { put("name", it) }
            disableSend?.let { put("disable_send", it) }
            disableReceive?.let { put("disable_recv", it) }
        }
        return parseDevice(request("PUT", "/api/devices/$deviceId", body).requireObject("data"))
    }

    fun deleteDevice(deviceId: Int) {
        request("DELETE", "/api/devices/$deviceId")
    }

    fun switchDeviceGroup(deviceId: Int, groupId: Int, password: String = "") {
        request(
            "POST",
            "/api/device/changegroup",
            JSONObject()
                .put("device_id", deviceId)
                .put("group_id", groupId)
                .put("password", password),
        )
    }

    fun getDeviceConfig(deviceId: Int): Map<String, String> =
        jsonStringMap(request("GET", "/api/devices/$deviceId/config").requireObject("data"))

    fun updateDeviceConfig(deviceId: Int, config: Map<String, String>): Map<String, String> {
        val body = JSONObject().apply { config.forEach(::put) }
        return jsonStringMap(request("PUT", "/api/devices/$deviceId/config", body).requireObject("data"))
    }

    fun syncDeviceConfig(deviceId: Int): String = request("POST", "/api/devices/$deviceId/config/sync")
        .optJSONObject("data")
        ?.optStringClean("message")
        .orEmpty()
        .ifBlank { "同步请求已发送" }

    fun getDevicePassword(): DevicePasswordInfo {
        val data = request("GET", "/api/user/device-password").requireObject("data")
        return DevicePasswordInfo(
            password = data.optStringClean("device_password"),
            hasPassword = data.optBoolean("has_password"),
            isNew = data.optBoolean("is_new"),
            createdAt = data.optStringClean("created_at"),
        )
    }

    fun regenerateDevicePassword(): DevicePasswordInfo {
        val data = request("POST", "/api/user/device-password/regenerate").requireObject("data")
        return DevicePasswordInfo(
            password = data.optStringClean("device_password"),
            hasPassword = true,
            isNew = true,
            createdAt = data.optStringClean("created_at"),
        )
    }

    fun bindDevice(dynamicCode: String): DeviceBindPreview {
        val data = request(
            "POST",
            "/api/device/bind",
            JSONObject().put("dynamic_code", dynamicCode),
        ).requireObject("data")
        val availableSsids = data.optJSONArray("available_ssids") ?: JSONArray()
        val replacements = data.optJSONArray("replaceable_devices") ?: JSONArray()
        return DeviceBindPreview(
            deviceMac = data.optStringClean("device_mac"),
            callsign = data.optStringClean("call_sign"),
            message = data.optStringClean("message"),
            availableSsids = buildList {
                for (index in 0 until availableSsids.length()) add(availableSsids.optInt(index))
            },
            recommendedSsid = data.optInt("recommended_ssid"),
            replaceableDevices = replacements.objects().map { item ->
                ReplaceableDevice(
                    deviceId = item.optInt("device_id"),
                    name = item.optStringClean("name"),
                    callsign = item.optStringClean("callsign"),
                    ssid = item.optInt("ssid"),
                    lastOnlineIp = item.optStringClean("last_online_ip"),
                    onlineTime = item.optStringClean("online_time"),
                )
            },
        )
    }

    fun submitDeviceConfig(deviceMac: String, ssid: Int?, replaceDeviceId: Int?): DeviceBindResult {
        val body = JSONObject().put("device_mac", deviceMac).apply {
            ssid?.let { put("ssid", it) }
            replaceDeviceId?.let { put("replace_device_id", it) }
        }
        val data = request("POST", "/api/device/submit-config", body).requireObject("data")
        val auth = data.optJSONObject("udp_auth_info") ?: JSONObject()
        return DeviceBindResult(
            message = data.optStringClean("message"),
            ssid = data.optNullableInt("ssid"),
            username = auth.optStringClean("username"),
            devicePassword = auth.optStringClean("device_password"),
            dmrId = data.optInt("dmr_id"),
        )
    }

    fun getGroups(): List<Group> {
        val groups = mutableListOf<Group>()
        var page = 1
        while (page <= MAX_GROUP_PAGES) {
            val response = request("GET", "/api/groups?page=$page&page_size=$GROUP_PAGE_SIZE")
                .requireObject("data")
            val items = response.optJSONArray("items") ?: JSONArray()
            val pageGroups = items.objects().map(::parseGroup)
            groups += pageGroups
            val pagination = response.optJSONObject("pagination")
            val total = response.optInt("total", pagination?.optInt("total", -1) ?: -1)
            val hasMore = response.optBoolean(
                "has_more",
                response.optBoolean("hasMore", pagination?.optBoolean("has_more", false) ?: false),
            )
            val shouldContinue = pageGroups.isNotEmpty() && (
                hasMore ||
                    (total >= 0 && groups.size < total) ||
                    (total < 0 && pageGroups.size >= GROUP_PAGE_SIZE)
                )
            if (!shouldContinue) break
            page++
        }
        val uniqueGroups = groups.distinctBy(Group::id)
        val realtime = runCatching { getGroupStats() }.getOrDefault(emptyMap())
        return uniqueGroups.map { group ->
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

    fun searchGroups(keyword: String): List<Group> {
        val data = request(
            "POST",
            "/api/groups/search",
            JSONObject().put("keyword", keyword).put("page", 1).put("page_size", 50),
        ).requireObject("data")
        return (data.optJSONArray("items") ?: JSONArray()).objects().map(::parseGroup)
    }

    fun createGroup(name: String, type: Int, password: String, note: String): Group {
        val body = JSONObject().put("name", name).put("type", type).put("note", note)
        if (password.isNotBlank()) body.put("password", password)
        return parseGroup(request("POST", "/api/groups", body).requireObject("data"))
    }

    fun updateGroup(
        groupId: Int,
        name: String? = null,
        type: Int? = null,
        password: String? = null,
        note: String? = null,
        status: Int? = null,
    ): Group {
        val body = JSONObject().apply {
            name?.let { put("name", it) }
            type?.let { put("type", it) }
            password?.takeIf(String::isNotBlank)?.let { put("password", it) }
            note?.let { put("note", it) }
            status?.let { put("status", it) }
        }
        return parseGroup(request("PUT", "/api/groups/$groupId", body).requireObject("data"))
    }

    fun deleteGroup(groupId: Int) {
        request("DELETE", "/api/groups/$groupId")
    }

    fun getGroupDevices(groupId: Int): List<Device> {
        val data = request("GET", "/api/groups/$groupId/devices").requireObject("data")
        return (data.optJSONArray("items") ?: JSONArray()).objects().map(::parseDevice)
    }

    fun updateGroupDeviceCommControl(
        groupId: Int,
        deviceId: Int,
        disableSend: Boolean,
        disableReceive: Boolean,
    ): Pair<Boolean, Boolean> {
        val data = request(
            "PUT",
            "/api/groups/$groupId/devices/$deviceId/comm-control",
            JSONObject().put("disable_send", disableSend).put("disable_recv", disableReceive),
        ).requireObject("data")
        return data.optBoolean("disable_send") to data.optBoolean("disable_recv")
    }

    fun kickGroupDevice(groupId: Int, deviceId: Int) {
        request("DELETE", "/api/groups/$groupId/devices/$deviceId")
    }

    fun getCommunicationStats(): CommunicationStats {
        val data = request("GET", "/api/comm-records/user-stats").requireObject("data")
        return CommunicationStats(
            totalCount = data.optInt("total_count"),
            totalSize = data.optLong("total_size"),
            totalDurationMs = data.optLong("total_duration"),
        )
    }

    fun getCommunicationTrend(): List<DailyCommunicationStats> {
        val data = request("GET", "/api/comm-records/user-trend").optJSONArray("data") ?: JSONArray()
        return data.objects().map { item ->
            DailyCommunicationStats(
                date = item.optStringClean("date"),
                count = item.optInt("count"),
                durationMs = item.optLong("duration"),
            )
        }
    }

    fun getCommunicationRecords(
        page: Int = 1,
        pageSize: Int = 100,
        groupId: Int? = null,
    ): CommunicationRecordPage {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 100)
        val query = buildString {
            append("/api/comm-records?page=").append(safePage).append("&page_size=").append(safePageSize)
            if (groupId != null) append("&group_id=").append(groupId)
        }
        val data = request("GET", query).requireObject("data")
        val records = data.optJSONArray("list") ?: JSONArray()
        return CommunicationRecordPage(
            records = records.objects().map(::parseCommunicationRecord),
            total = data.optInt("total"),
            page = data.optInt("page", safePage),
            pageSize = data.optInt("page_size", safePageSize).coerceAtLeast(1),
        )
    }

    fun getCommunicationRecord(id: Int): CommunicationRecord {
        val data = request("GET", "/api/comm-records/${id.coerceAtLeast(1)}").requireObject("data")
        return parseCommunicationRecord(data)
    }

    fun searchPublicRelays(location: String): List<RelayStation> {
        val encoded = urlEncode(location.trim())
        val items = request(
            "GET",
            "/api/public/relays?location=$encoded",
            requiresAuth = false,
        ).requireObject("data").optJSONArray("items") ?: JSONArray()
        return List(items.length()) { index ->
            ToolApiJson.relay(items.getJSONObject(index))
        }
    }

    fun getLogbooks(page: Int = 1, pageSize: Int = 20, callsign: String = ""): LogbookPage {
        val query = buildString {
            append("?page=$page&page_size=$pageSize")
            if (callsign.isNotBlank()) {
                append("&callsign=")
                append(urlEncode(callsign.trim()))
            }
        }
        val data = request("GET", "/api/logbooks$query").requireObject("data")
        val items = data.optJSONArray("items") ?: JSONArray()
        return LogbookPage(
            items = List(items.length()) { ToolApiJson.logbook(items.getJSONObject(it)) },
            total = data.optInt("total"),
            page = data.optInt("page", page),
            pageSize = data.optInt("page_size", pageSize),
        )
    }

    fun saveLogbook(entry: LogbookEntry): LogbookEntry {
        val body = JSONObject()
            .put("my_callsign", entry.myCallsign.trim().uppercase())
            .put("time_utc", entry.timeUtc)
            .put("tx_frequency", entry.txFrequency)
            .put("rx_frequency", entry.rxFrequency)
            .put("cq_zone", entry.cqZone)
            .put("itu_zone", entry.ituZone)
            .put("mode", entry.mode.trim().uppercase())
            .put("callsign", entry.callsign.trim().uppercase())
            .put("their_rst", entry.theirRst)
            .put("their_power", entry.theirPower ?: JSONObject.NULL)
            .put("their_qth", entry.theirQth)
            .put("their_radio", entry.theirRadio)
            .put("their_antenna", entry.theirAntenna)
            .put("my_rst", entry.myRst)
            .put("my_power", entry.myPower ?: JSONObject.NULL)
            .put("my_qth", entry.myQth)
            .put("my_radio", entry.myRadio)
            .put("my_antenna", entry.myAntenna)
            .put("notes", entry.notes)
        val path = if (entry.id > 0) "/api/logbooks/${entry.id}" else "/api/logbooks"
        val method = if (entry.id > 0) "PUT" else "POST"
        return ToolApiJson.logbook(request(method, path, body).requireObject("data"))
    }

    fun deleteLogbook(id: Int) {
        request("DELETE", "/api/logbooks/$id")
    }

    fun deleteLogbooks(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        request(
            "DELETE",
            "/api/logbooks/batch",
            JSONObject().put("ids", JSONArray().apply { ids.distinct().forEach(::put) }),
        )
    }

    fun getRadioPresets(): List<RadioPreset> {
        val data = request("GET", "/api/user/radio-presets").optJSONArray("data") ?: JSONArray()
        return List(data.length()) { ToolApiJson.preset(data.getJSONObject(it)) }
    }

    fun saveRadioPreset(preset: RadioPreset): RadioPreset {
        val body = JSONObject()
            .put("name", preset.name.trim())
            .put("radio", preset.radio.trim())
            .put("antenna", preset.antenna.trim())
            .put("power", preset.power ?: JSONObject.NULL)
            .put("qth", preset.qth.trim())
            .put("sort_order", preset.sortOrder)
        val path = if (preset.id > 0) "/api/user/radio-presets/${preset.id}" else "/api/user/radio-presets"
        return ToolApiJson.preset(request(if (preset.id > 0) "PUT" else "POST", path, body).requireObject("data"))
    }

    fun deleteRadioPreset(id: Int) {
        request("DELETE", "/api/user/radio-presets/$id")
    }

    fun reorderRadioPresets(orders: List<Pair<Int, Int>>) {
        val items = JSONArray()
        orders.forEach { (id, order) ->
            items.put(JSONObject().put("id", id).put("order", order))
        }
        request("PUT", "/api/user/radio-presets/reorder", JSONObject().put("orders", items))
    }

    fun updateProfile(
        nickname: String,
        phone: String,
        address: String,
        introduction: String,
        birthday: String = "",
        sex: Int = 0,
        dmrid: Int = 0,
        mdcid: String = "",
        alarmMsg: Boolean = false,
    ): User {
        val body = JSONObject()
            .put("nickname", nickname)
            .put("phone", phone)
            .put("address", address)
            .put("introduction", introduction)
            .put("birthday", birthday)
            .put("sex", sex)
            .put("dmrid", dmrid)
            .put("mdcid", mdcid)
            .put("alarm_msg", alarmMsg)
        request("PUT", "/api/me", body)
        return getMe()
    }

    fun uploadFile(fileBytes: ByteArray, fileName: String, fileType: String): String {
        val session = currentSession() ?: throw ApiException(401, "请先登录")
        val baseUrl = normalizeBaseUrl(session.baseUrl)
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"

        val body = buildString {
            append("--$boundary$lineEnd")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd")
            append("Content-Type: application/octet-stream$lineEnd")
            append(lineEnd)
        }.toByteArray() + fileBytes + "$lineEnd--$boundary$lineEnd".toByteArray() +
            "Content-Disposition: form-data; name=\"file_type\"$lineEnd$lineEnd$fileType$lineEnd--$boundary--$lineEnd".toByteArray()

        val url = URL("${baseUrl.trimEnd('/')}/api/upload/file")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.doOutput = true

        try {
            connection.outputStream.use { it.write(body) }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (responseCode != 200) {
                throw ApiException(responseCode, "上传失败: $responseText")
            }

            val response = JSONObject(responseText)
            val data = response.optJSONObject("data") ?: response
            return resolveHttpsUrl(
                baseUrl,
                data.optStringClean("url").ifBlank { data.optStringClean("file_url") },
            )
        } finally {
            connection.disconnect()
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        val body = JSONObject()
            .put("old_password", oldPassword)
            .put("new_password", newPassword)
        request("PUT", "/api/me/password", body)
    }

    fun changeEmail(
        oldSessionId: String,
        oldCode: String,
        newSessionId: String,
        newCode: String,
    ): User {
        request(
            "PUT",
            "/api/me/email",
            emailChangeRequestJson(oldSessionId, oldCode, newSessionId, newCode),
        )
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
        val baseUrl = session?.baseUrl?.let(::normalizeBaseUrl)
            ?: throw ApiException(400, "服务器地址未配置")
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
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val connection = URL(normalizedBaseUrl + path).openConnection() as HttpURLConnection
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

    private fun parseUser(
        json: JSONObject,
        baseUrl: String = currentSession()?.baseUrl.orEmpty(),
    ): User {
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
            emailVerified = json.optBoolean("email_verified"),
            role = role,
            approvalStatus = json.optInt("approval_status"),
            reviewNote = json.optStringClean("review_note"),
            avatarUrl = optionalHttpsUrl(
                json.optStringClean("avatar_thumb").ifBlank { json.optStringClean("avatar") },
                baseUrl,
            ),
            address = json.optStringClean("address"),
            phone = json.optStringClean("phone"),
            introduction = json.optStringClean("introduction"),
            dmrId = json.optInt("dmrid"),
            mdcId = json.optStringClean("mdcid"),
            birthday = json.optStringClean("birthday"),
            sex = json.optInt("sex"),
            alarmMsg = json.optBoolean("alarm_msg"),
            lastGroupId = json.optInt("last_group_id", 999).takeIf { it > 0 } ?: 999,
            status = json.optInt("status", 1),
            lastLoginTime = json.optStringClean("last_login_time"),
            lastLoginIp = json.optStringClean("last_login_ip"),
            lastLoginIpLocation = json.optStringClean("last_login_ip_location"),
        )
    }

    private fun parseDevice(item: JSONObject) = Device(
        id = item.optInt("id"),
        name = item.optStringClean("name"),
        callsign = item.optStringClean("callsign").ifBlank { item.optStringClean("owner_callsign") },
        ssid = item.optInt("ssid"),
        model = item.optInt("dev_model", item.optInt("model")),
        groupId = item.optInt("group_id"),
        online = item.optBoolean("is_online", item.optBoolean("online")),
        enabled = item.optInt("status", 1) == 1,
        disableSend = item.optBoolean("disable_send"),
        disableReceive = item.optBoolean("disable_recv"),
        qth = item.optStringClean("qth"),
        note = item.optStringClean("note"),
        onlineTime = item.optStringClean("online_time"),
        entryName = item.optStringClean("entry_node_name"),
        priority = item.optInt("priority"),
        lastOnlineIp = item.optStringClean("last_online_ip"),
        lastOnlineIpLocation = item.optStringClean("last_online_ip_location"),
        entryId = item.optStringClean("entry_node_id"),
        entryMode = item.optStringClean("entry_mode"),
        entrySeenAt = item.optStringClean("entry_seen_at"),
        ownerId = item.optInt("owner_id"),
        ownerName = item.optStringClean("owner_name"),
        ownerCallsign = item.optStringClean("owner_callsign"),
        createdAt = item.optStringClean("create_time"),
        updatedAt = item.optStringClean("update_time"),
    )

    private fun parseGroup(json: JSONObject) = Group(
        id = json.optInt("id"),
        name = json.optStringClean("name"),
        type = json.optInt("type"),
        status = json.optInt("status", 1),
        note = json.optStringClean("note"),
        ownerId = json.optInt("ower_id"),
        ownerCallsign = json.optStringClean("ower_callsign"),
        joined = json.optBoolean("is_joined"),
        owner = json.optBoolean("is_owner"),
        requiresPassword = json.optBoolean("require_password"),
        onlineCount = json.optInt("online_count"),
        totalCount = json.optInt("total_count"),
        createdAt = json.optStringClean("create_time"),
        updatedAt = json.optStringClean("update_time"),
    )

    private fun parseCommunicationRecord(item: JSONObject) = CommunicationRecord(
        id = item.optInt("id"),
        deviceId = item.optInt("device_id"),
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
        audioUrl = optionalHttpsUrl(item.optStringClean("audio_url")),
    )

    private fun parseClientResourceManifest(json: JSONObject) = ClientResourceManifest(
        schemaVersion = json.optInt("schema_version"),
        resources = (json.optJSONArray("resources") ?: JSONArray()).objects().map { item ->
            ClientResourceManifestItem(
                resource = parseClientResourceSummary(item.requireObject("resource")),
                release = parseClientResourceRelease(item.requireObject("release")),
                artifacts = (item.optJSONArray("artifacts") ?: JSONArray()).objects().map(::parseClientResourceArtifact),
            )
        },
    )

    private fun parseClientResourceSummary(json: JSONObject) = ClientResourceSummary(
        id = json.optInt("id"),
        resourceKey = json.optStringClean("resource_key"),
        name = json.optStringClean("name"),
        category = json.optStringClean("category"),
        required = json.optBoolean("required"),
    )

    private fun parseClientResourceRelease(json: JSONObject) = ClientResourceRelease(
        id = json.optInt("id"),
        version = json.optStringClean("version"),
        channel = json.optStringClean("channel"),
        title = json.optStringClean("title"),
        changelog = json.optStringClean("changelog"),
        forceUpdate = json.optBoolean("force_update"),
        minClientVersion = json.optStringClean("min_client_version"),
        publishedAt = json.optStringClean("published_at"),
    )

    private fun parseClientResourceArtifact(json: JSONObject) = ClientResourceArtifact(
        id = json.optInt("id"),
        releaseId = json.optInt("release_id"),
        format = json.optStringClean("format"),
        runtime = json.optStringClean("runtime"),
        variant = json.optStringClean("variant"),
        buildNumber = json.optStringClean("build_number"),
        fileName = json.optStringClean("file_name"),
        fileSize = json.optLong("file_size"),
        sha256 = json.optStringClean("sha256"),
        contentSignature = json.optStringClean("content_signature"),
        signatureAlgorithm = json.optStringClean("signature_algorithm"),
        externalUrl = optionalHttpsUrl(json.optStringClean("external_url")),
        targets = (json.optJSONArray("targets") ?: JSONArray()).objects().map(::parseClientResourceArtifactTarget),
    )

    private fun parseClientResourceArtifactTarget(json: JSONObject) = ClientResourceArtifactTarget(
        platform = json.optStringClean("platform"),
        arch = json.optStringClean("arch"),
        minOsVersion = json.optStringClean("min_os_version"),
        minAndroidApi = json.optInt("min_android_api"),
    )

    companion object {
        const val ANDROID_DEVICE_MODEL = 101
        private const val GROUP_PAGE_SIZE = 100
        private const val MAX_GROUP_PAGES = 100
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) throw ApiException(400, "请输入服务器地址")
            val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = runCatching { URI(normalized) }.getOrNull()
                ?: throw ApiException(400, "服务器地址格式不正确")
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
                throw ApiException(400, "服务器地址必须是 HTTPS 地址")
            }
            return normalized
        }

        fun resolveHttpsUrl(baseUrl: String, value: String): String {
            val candidate = value.trim()
            if (candidate.isBlank()) return ""
            val base = normalizeBaseUrl(baseUrl)
            val resolved = runCatching { URI("$base/").resolve(candidate) }.getOrNull()
                ?: throw ApiException(400, "资源地址格式不正确")
            if (resolved.scheme != "https" || resolved.host.isNullOrBlank()) {
                throw ApiException(400, "资源地址必须使用 HTTPS")
            }
            return resolved.toASCIIString()
        }
    }

    private fun optionalHttpsUrl(
        value: String,
        baseUrl: String = currentSession()?.baseUrl.orEmpty(),
    ): String {
        if (value.isBlank() || baseUrl.isBlank()) return ""
        return runCatching { resolveHttpsUrl(baseUrl, value) }.getOrDefault("")
    }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

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

private fun jsonStringMap(json: JSONObject): Map<String, String> = buildMap {
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (!json.isNull(key)) put(key, json.optString(key))
    }
}

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}
