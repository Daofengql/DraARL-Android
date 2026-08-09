package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiDomainClientsTest {
    @Test
    fun authRegistrationUsesPublicEndpointAndRequestModel() {
        val transport = RecordingHttpTransport(
            jsonResponse("""{"code":200,"data":{"id":8,"username":"bg0abc","approval_status":0}}""")
        )
        val requests = ApiRequestExecutor(transport)
        val sessions = sessionManager(session = null, requests = requests)
        val api: AuthApi = AuthApiClient(requests, sessions, UserJsonMapper { "" })

        val result = api.register(
            RegistrationRequest(
                baseUrl = "https://api.example.test/",
                username = " bg0abc ",
                password = "secret",
                callsign = " bg0abc ",
                phone = " 13800000000 ",
                nickname = " Operator ",
                email = " test@example.test ",
                sessionId = "mail-session",
                emailCode = " 123456 "
            )
        )

        assertEquals(8, result.id)
        val request = transport.singleRequest()
        assertEquals("POST", request.method)
        assertEquals("https://api.example.test/api/auth/register", request.url)
        assertNull(request.headers["Authorization"])
        val body = request.jsonBody()
        assertEquals("bg0abc", body.getString("username"))
        assertEquals("BG0ABC", body.getString("callsign"))
        assertEquals("123456", body.getString("email_code"))
    }

    @Test
    fun devicesApiMapsDeviceAndKeepsManagementPath() {
        val requester = RecordingApiJsonRequester { call ->
            assertEquals("GET", call.method)
            JSONObject(
                """{"code":200,"data":{"total":1,"items":[{"id":3,"name":"Handheld","callsign":"BG0ABC","ssid":2,"is_online":true,"status":1}]}}"""
            )
        }
        val api: DevicesApi = DevicesApiClient(requester)

        val devices = api.getDevices()

        assertEquals("/api/devices?page=1&limit=100&owner_only=true", requester.singleCall().path)
        assertEquals(3, devices.single().id)
        assertEquals("Handheld", devices.single().name)
        assertTrue(devices.single().online)
    }

    @Test
    fun deviceProvisioningApiSendsConfigAsJsonObject() {
        val requester = RecordingApiJsonRequester { call ->
            JSONObject().put("code", 200).put("data", call.body)
        }
        val api: DevicesApi = DevicesApiClient(requester)

        val result = api.updateDeviceConfig(4, mapOf("host" to "node.example.test", "port" to "9000"))

        val call = requester.singleCall()
        assertEquals("PUT", call.method)
        assertEquals("/api/devices/4/config", call.path)
        assertEquals("node.example.test", result["host"])
        assertEquals("9000", result["port"])
    }

    @Test
    fun groupsApiUsesUpdateRequestWithoutSendingMissingFields() {
        val requester = RecordingApiJsonRequester { call ->
            JSONObject(
                """{"code":200,"data":{"id":6,"name":"Local","type":1,"status":0,"is_joined":true}}"""
            )
        }
        val api: GroupsApi = GroupsApiClient(requester)

        val group = api.updateGroup(GroupUpdateRequest(groupId = 6, status = 0))

        val call = requester.singleCall()
        assertEquals("PUT", call.method)
        assertEquals("/api/groups/6", call.path)
        assertEquals(0, call.body?.getInt("status"))
        assertFalse(call.body?.has("name") ?: true)
        assertEquals(6, group.id)
        assertEquals(0, group.status)
    }

    @Test
    fun radioApiBuildsCursorPathAndResolvesMessageAudioUrl() {
        val requester = RecordingApiJsonRequester {
            JSONObject(
                """{"code":200,"data":{"messages":[{"id":9,"message_type":"voice","source_group_id":7,"sender":{"username":"bg0abc"},"sent_at":"2026-08-09T10:00:00Z","audio_url":"/uploads/voice.raw"}],"next_cursor":"next","has_more":true}}"""
            )
        }
        val sessions = sessionManager(session())
        val api: RadioApi = RadioApiClient(requester, sessions, UserJsonMapper { API_BASE_URL })

        val page = api.getGroupMessages(groupId = 7, cursor = "older cursor")

        assertEquals("/api/groups/7/messages?message_type=all&cursor=older+cursor", requester.singleCall().path)
        assertEquals("https://api.example.test/uploads/voice.raw", page.messages.single().audioUrl)
        assertEquals("next", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun profileApiUsesUpdateModelAndPersistsReturnedUser() {
        val requester = RecordingApiJsonRequester { call ->
            if (call.method == "PUT") {
                JSONObject().put("code", 200)
            } else {
                JSONObject(
                    """{"code":200,"data":{"id":7,"username":"bg0abc","nickname":"New name","approval_status":1,"status":1}}"""
                )
            }
        }
        val storage = DomainSessionStorage(session())
        val requests = ApiRequestExecutor(FailingHttpTransport)
        val sessions = ApiSessionManager(storage, requests)
        val api: ProfileApi = ProfileApiClient(requester, requests, sessions, UserJsonMapper { API_BASE_URL })

        val user = api.updateProfile(
            ProfileUpdateRequest(
                nickname = "New name",
                phone = "13800000000",
                address = "QTH",
                introduction = "portable"
            )
        )

        assertEquals(listOf("PUT", "GET"), requester.calls.map(DomainApiCall::method))
        assertEquals("New name", requester.calls.first().body?.getString("nickname"))
        assertEquals("New name", user.nickname)
        assertEquals("New name", storage.current()?.user?.nickname)
    }

    @Test
    fun profileResponseDoesNotOverwriteSessionReplacedDuringRequest() {
        val original = session()
        val replacement = original.copy(
            accessToken = "replacement-token",
            user = original.user.copy(nickname = "Current")
        )
        val storage = DomainSessionStorage(original)
        val requests = ApiRequestExecutor(FailingHttpTransport)
        val sessions = ApiSessionManager(storage, requests)
        val requester = RecordingApiJsonRequester {
            val operation = sessions.beginAuthOperation()
            sessions.completeAuthOperation(operation, replacement, "cancelled")
            JSONObject(
                """{"code":200,"data":{"id":7,"username":"bg0abc","nickname":"Stale","approval_status":1,"status":1}}"""
            )
        }
        val api: ProfileApi = ProfileApiClient(requester, requests, sessions, UserJsonMapper { API_BASE_URL })

        val responseUser = api.getMe()

        assertEquals("Stale", responseUser.nickname)
        assertEquals(replacement, sessions.currentSession())
        assertEquals(replacement, storage.current())
    }

    @Test
    fun toolsApiSendsDistinctIdsForBatchDeletion() {
        val requester = RecordingApiJsonRequester { JSONObject().put("code", 200) }
        val api: ToolsApi = ToolsApiClient(requester)

        api.deleteLogbooks(listOf(3, 3, 5))

        val call = requester.singleCall()
        assertEquals("DELETE", call.method)
        assertEquals("/api/logbooks/batch", call.path)
        assertEquals(listOf(3, 5), call.body?.getJSONArray("ids")?.let { listOf(it.getInt(0), it.getInt(1)) })
    }

    @Test
    fun updatesApiEncodesQueryAndMapsArtifactUrl() {
        val requester = RecordingApiJsonRequester {
            JSONObject(
                """{"code":200,"data":{"schema_version":1,"server_version":"2.0.0","protocol_version":1,"resources":[{"resource":{"id":1,"resource_key":"app.draarl","name":"DraARL","category":"application"},"release":{"id":2,"version":"2.1.0","channel":"stable"},"artifacts":[{"id":3,"release_id":2,"format":"apk","external_url":"/downloads/app.apk","targets":[]}]}]}}"""
            )
        }
        val api: UpdatesApi = UpdatesApiClient(requester) { session() }

        val manifest = api.getClientResourceManifest(
            ClientResourceManifestQuery(
                platform = "android",
                arch = "arm64 v8a",
                clientVersion = "2.0.0-alpha1",
                androidApi = 36
            )
        )

        assertEquals(
            "/api/public/client-resources/manifest?platform=android&arch=arm64+v8a&channel=stable&client_version=2.0.0-alpha1&android_api=36",
            requester.singleCall().path
        )
        assertFalse(requester.singleCall().requiresAuth)
        assertEquals(
            "https://api.example.test/downloads/app.apk",
            manifest.resources.single().artifacts.single().externalUrl
        )
    }

    private fun sessionManager(
        session: Session?,
        requests: ApiRequestExecutor = ApiRequestExecutor(FailingHttpTransport)
    ): ApiSessionManager = ApiSessionManager(DomainSessionStorage(session), requests)

    private fun session() = Session(
        baseUrl = API_BASE_URL,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        accessExpiresAt = TEST_NOW + TimeUnit.HOURS.toMillis(1),
        refreshExpiresAt = TEST_NOW + TimeUnit.DAYS.toMillis(1),
        user = User(id = 7, username = "bg0abc", approvalStatus = 1)
    )

    private companion object {
        const val API_BASE_URL = "https://api.example.test"
        const val TEST_NOW = 1_000_000L
    }
}

private data class DomainApiCall(val method: String, val path: String, val body: JSONObject?, val requiresAuth: Boolean)

private class RecordingApiJsonRequester(private val response: (DomainApiCall) -> JSONObject) : ApiJsonRequester {
    val calls = mutableListOf<DomainApiCall>()

    override fun execute(method: String, path: String, body: JSONObject?, requiresAuth: Boolean): JSONObject {
        val call = DomainApiCall(method, path, body, requiresAuth)
        calls += call
        return response(call)
    }

    fun singleCall(): DomainApiCall = calls.single()
}

private class DomainSessionStorage(initial: Session?) : ApiSessionStorage {
    private var session = initial

    override fun load(): Session? = current()

    override fun save(session: Session) {
        this.session = session
    }

    override fun clear() {
        session = null
    }

    fun current(): Session? = session
}

private class RecordingHttpTransport(private val response: HttpResponse) : HttpTransport {
    private val requests = mutableListOf<HttpRequest>()

    override fun newCall(request: HttpRequest): HttpCall {
        requests += request
        return object : HttpCall {
            override fun execute(): HttpResponse = response

            override fun cancel() = Unit
        }
    }

    fun singleRequest(): HttpRequest = requests.single()
}

private object FailingHttpTransport : HttpTransport {
    override fun newCall(request: HttpRequest): HttpCall = error("Unexpected HTTP request: ${request.url}")
}

private fun jsonResponse(body: String) = HttpResponse(
    status = 200,
    headers = emptyMap(),
    body = body.toByteArray()
)

private fun HttpRequest.jsonBody(): JSONObject {
    val bytes = (body as HttpRequestBody.Bytes).content
    return JSONObject(bytes.toString(Charsets.UTF_8))
}
