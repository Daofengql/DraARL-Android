package cn.silverdragon.draarl

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.PlatformInfo
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ApiClient
import cn.silverdragon.draarl.network.ApiException
import cn.silverdragon.draarl.radio.AccessPointProbe
import cn.silverdragon.draarl.radio.AccessPointSelector
import cn.silverdragon.draarl.radio.RadioConnectionConfig
import cn.silverdragon.draarl.radio.RadioConnectionService
import cn.silverdragon.draarl.radio.RadioServiceListener
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class AppPage { RADIO, DASHBOARD, DEVICES, GROUPS, PROFILE, RECORDS }

class AppController(application: Application) : AndroidViewModel(application), RadioServiceListener {
    private val appContext = application.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "draarl-app-worker")
    }
    private val sessionStore = SecureSessionStore(appContext)
    private var serviceBinder: RadioConnectionService.LocalBinder? = null
    private var serviceBound = false
    private var pendingConnection: RadioConnectionConfig? = null
    private var manualAccessPointSelection = false
    private val refreshingRadioToken = AtomicBoolean(false)
    private val radioConnectionGeneration = AtomicInteger(0)
    private val captchaGeneration = AtomicInteger(0)
    private val preparingRadioConnection = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val api = ApiClient(sessionStore) { session ->
        mainHandler.post {
            if (disposed.get()) return@post
            user = session?.user
            serviceBinder?.updateAccessToken(session?.accessToken.orEmpty())
            if (session == null) {
                radioConnectionGeneration.incrementAndGet()
                preparingRadioConnection.set(false)
                pendingConnection = null
                serviceBinder?.disconnect()
                authenticated = false
            }
        }
    }

    var initializing by mutableStateOf(true)
        private set
    var authenticated by mutableStateOf(false)
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf("")
        private set
    var captchaId by mutableStateOf("")
        private set
    var captchaImageBase64 by mutableStateOf("")
        private set
    var captchaLoading by mutableStateOf(false)
        private set
    var serverUrl by mutableStateOf(sessionStore.lastServerUrl())
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var page by mutableStateOf(AppPage.RADIO)
        private set
    var contentLoading by mutableStateOf(false)
        private set
    var notice by mutableStateOf("")
        private set

    var devices by mutableStateOf<List<Device>>(emptyList())
        private set
    var groups by mutableStateOf<List<Group>>(emptyList())
        private set
    var records by mutableStateOf<List<CommunicationRecord>>(emptyList())
        private set
    var onlineDevices by mutableStateOf<List<OnlineDevice>>(emptyList())
        private set
    var dashboard by mutableStateOf(DashboardData())
        private set

    var accessPoints by mutableStateOf<List<AccessPoint>>(emptyList())
        private set
    var accessPointProbes by mutableStateOf<List<AccessPointProbe>>(emptyList())
        private set
    var selectedAccessPoint by mutableStateOf<AccessPoint?>(null)
        private set
    var selectingAccessPoint by mutableStateOf(false)
        private set
    var selectedGroupId by mutableStateOf(999)
        private set

    var radioStatus by mutableStateOf(RadioStatus())
        private set
    val radioMessages = mutableStateListOf<RadioMessage>()
    var muted by mutableStateOf(sessionStore.isMuted())
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? RadioConnectionService.LocalBinder
            serviceBound = serviceBinder != null
            serviceBinder?.setListener(this@AppController)
            serviceBinder?.setMuted(muted)
            pendingConnection?.let {
                serviceBinder?.connect(it)
                preparingRadioConnection.set(false)
                pendingConnection = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            serviceBound = false
            onRadioStatus(RadioStatus(phase = RadioConnectionPhase.DISCONNECTED))
        }
    }

    init {
        bindRadioService()
        restoreSession()
    }

    fun updateServerUrl(value: String) {
        serverUrl = value
        loginError = ""
        captchaGeneration.incrementAndGet()
        captchaId = ""
        captchaImageBase64 = ""
        captchaLoading = false
    }

    fun loadCaptcha() {
        if (serverUrl.isBlank()) return
        val requestedServerUrl = serverUrl
        val requestGeneration = captchaGeneration.incrementAndGet()
        captchaId = ""
        captchaImageBase64 = ""
        captchaLoading = true
        executor.execute {
            runCatching { api.getCaptcha(requestedServerUrl) }
                .onSuccess { challenge ->
                    mainHandler.post {
                        if (
                            disposed.get() ||
                            requestGeneration != captchaGeneration.get() ||
                            requestedServerUrl != serverUrl
                        ) return@post
                        captchaId = challenge.id
                        captchaImageBase64 = challenge.imageBase64
                        captchaLoading = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        if (
                            disposed.get() ||
                            requestGeneration != captchaGeneration.get() ||
                            requestedServerUrl != serverUrl
                        ) return@post
                        captchaId = ""
                        captchaImageBase64 = ""
                        captchaLoading = false
                        loginError = friendlyError(error)
                    }
                }
        }
    }

    fun login(username: String, password: String, captchaCode: String) {
        if (loginBusy) return
        if (captchaId.isBlank() || captchaCode.isBlank()) {
            loginError = "请输入图片验证码"
            if (captchaId.isBlank()) loadCaptcha()
            return
        }
        val submittedCaptchaId = captchaId
        loginBusy = true
        loginError = ""
        executor.execute {
            runCatching { api.login(serverUrl, username, password, submittedCaptchaId, captchaCode) }
                .onSuccess { session ->
                    mainHandler.post {
                        serverUrl = session.baseUrl
                        user = session.user
                        authenticated = true
                        loginBusy = false
                        selectedGroupId = sessionStore.selectedGroupId(
                            session.user.id,
                            session.user.lastGroupId.takeIf { it > 0 } ?: 999,
                        )
                        page = if (session.user.isApproved) AppPage.RADIO else AppPage.DASHBOARD
                    }
                    mainHandler.post {
                        refreshAll()
                        discoverAccessPoints()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        loginBusy = false
                        loginError = friendlyError(error)
                        loadCaptcha()
                    }
                }
        }
    }

    fun logout() {
        radioConnectionGeneration.incrementAndGet()
        preparingRadioConnection.set(false)
        pendingConnection = null
        serviceBinder?.disconnect()
        executor.execute { api.logout() }
        authenticated = false
        user = null
        devices = emptyList()
        groups = emptyList()
        records = emptyList()
        onlineDevices = emptyList()
        radioMessages.clear()
        page = AppPage.RADIO
    }

    fun navigate(target: AppPage) {
        if (target in APPROVED_PAGES && user?.isApproved != true) {
            notice = "账号审核通过后才能使用该功能"
            return
        }
        page = target
        when (target) {
            AppPage.RECORDS -> refreshRecords()
            AppPage.DEVICES, AppPage.GROUPS, AppPage.DASHBOARD -> refreshAll()
            else -> Unit
        }
    }

    fun goBack() {
        page = AppPage.DASHBOARD
    }

    fun clearNotice() {
        notice = ""
    }

    fun refreshAll() {
        if (api.currentSession() == null) return
        mainHandler.post { contentLoading = true }
        executor.execute {
            val loadedDevices = runCatching(api::getDevices).getOrDefault(devices)
            val loadedGroups = runCatching(api::getGroups).getOrDefault(groups)
            val stats = runCatching(api::getCommunicationStats).getOrNull()
            val platform = runCatching(api::getPlatformInfo).getOrDefault(dashboard.platform)
            val refreshedUser = runCatching(api::getMe).getOrNull()
            mainHandler.post {
                devices = loadedDevices
                groups = loadedGroups
                refreshedUser?.let {
                    user = it
                    selectedGroupId = selectedGroupId.takeIf { id -> loadedGroups.any { group -> group.id == id } }
                        ?: it.lastGroupId.takeIf { id -> loadedGroups.any { group -> group.id == id } }
                        ?: loadedGroups.firstOrNull { group -> group.id == 999 }?.id
                        ?: loadedGroups.firstOrNull()?.id
                        ?: 999
                }
                dashboard = DashboardData(
                    devices = loadedDevices.size,
                    onlineDevices = loadedDevices.count(Device::online),
                    groups = loadedGroups.size,
                    communications = stats?.totalCount ?: dashboard.communications,
                    communicationDurationMs = stats?.totalDurationMs ?: dashboard.communicationDurationMs,
                    platform = platform,
                )
                contentLoading = false
            }
        }
    }

    fun discoverAccessPoints() {
        if (api.currentSession() == null || selectingAccessPoint) return
        selectingAccessPoint = true
        executor.execute {
            val discovered = runCatching(api::getAccessPoints).getOrElse {
                derivedAccessPoint()?.let(::listOf).orEmpty()
            }
            if (discovered.isEmpty()) {
                mainHandler.post {
                    selectingAccessPoint = false
                    notice = "服务端没有发布可用的 UDP 入口"
                }
                return@execute
            }
            val selection = AccessPointSelector.select(discovered)
            val selected = if (manualAccessPointSelection) {
                selectedAccessPoint?.let { current -> discovered.firstOrNull { it.id == current.id } }
                    ?: selection.selected
            } else {
                selection.selected
            }
            mainHandler.post {
                accessPoints = discovered
                accessPointProbes = selection.probes
                selectedAccessPoint = selected
                selectingAccessPoint = false
                sessionStore.setSelectedAccessPointId(selected.id)
            }
        }
    }

    fun selectAccessPoint(accessPoint: AccessPoint) {
        manualAccessPointSelection = true
        selectedAccessPoint = accessPoint
        sessionStore.setSelectedAccessPointId(accessPoint.id)
        if (radioStatus.connected) {
            serviceBinder?.disconnect()
            connectRadio()
        }
    }

    fun connectRadio() {
        if (user?.isApproved != true) {
            notice = "账号审核通过后才能连接在线电台"
            return
        }
        val point = selectedAccessPoint
        if (point == null) {
            discoverAccessPoints()
            notice = "正在发现并优选 UDP 入口，请稍候"
            return
        }
        val requestGeneration = radioConnectionGeneration.incrementAndGet()
        preparingRadioConnection.set(true)
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                RadioConnectionService.startIntent(appContext),
            )
        }.onFailure { error ->
            preparingRadioConnection.set(false)
            notice = friendlyError(error)
            return
        }
        executor.execute {
            runCatching {
                api.switchRadioGroup(selectedGroupId)
                RadioConnectionConfig(point, api.freshAccessToken(), selectedGroupId)
            }.onSuccess { config ->
                mainHandler.post {
                    if (
                        disposed.get() ||
                        requestGeneration != radioConnectionGeneration.get() ||
                        !authenticated
                    ) return@post
                    if (serviceBinder == null) {
                        pendingConnection = config
                        bindRadioService()
                    } else {
                        serviceBinder?.connect(config)
                        preparingRadioConnection.set(false)
                    }
                }
            }.onFailure { error ->
                if (requestGeneration == radioConnectionGeneration.get()) {
                    preparingRadioConnection.set(false)
                    appContext.startService(RadioConnectionService.disconnectIntent(appContext))
                    mainHandler.post {
                        if (!disposed.get() && requestGeneration == radioConnectionGeneration.get()) {
                            notice = friendlyError(error)
                        }
                    }
                }
            }
        }
    }

    fun disconnectRadio() {
        radioConnectionGeneration.incrementAndGet()
        preparingRadioConnection.set(false)
        pendingConnection = null
        serviceBinder?.disconnect()
    }

    fun sendText(text: String): Boolean {
        val sent = serviceBinder?.sendText(text) == true
        if (!sent) notice = "电台尚未连接"
        return sent
    }

    fun startPtt(): Boolean {
        val started = serviceBinder?.startPtt() == true
        if (!started && !radioStatus.connected) notice = "请先连接电台"
        return started
    }

    fun stopPtt() {
        serviceBinder?.stopPtt()
    }

    fun toggleMuted() {
        muted = !muted
        sessionStore.setMuted(muted)
        serviceBinder?.setMuted(muted)
    }

    fun switchGroup(group: Group) {
        if (group.id == selectedGroupId) return
        contentLoading = true
        executor.execute {
            runCatching { api.switchRadioGroup(group.id) }
                .onSuccess {
                    val history = runCatching { api.getCommunicationRecords(groupId = group.id) }.getOrDefault(emptyList())
                    val online = runCatching { api.getOnlineDevices(group.id) }.getOrDefault(emptyList())
                    mainHandler.post {
                        selectedGroupId = group.id
                        user?.let { sessionStore.setSelectedGroupId(it.id, group.id) }
                        serviceBinder?.setGroup(group.id)
                        radioMessages.clear()
                        radioMessages.addAll(history.asReversed().map(::recordToMessage))
                        onlineDevices = online
                        contentLoading = false
                        notice = "已切换到 ${group.name}"
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        contentLoading = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun refreshRadioData() {
        val groupId = selectedGroupId
        executor.execute {
            val history = runCatching { api.getCommunicationRecords(groupId = groupId) }.getOrDefault(emptyList())
            val online = runCatching { api.getOnlineDevices(groupId) }.getOrDefault(emptyList())
            mainHandler.post {
                if (groupId != selectedGroupId) return@post
                mergeHistory(history)
                onlineDevices = online
            }
        }
    }

    fun joinGroup(group: Group, password: String) {
        executor.execute {
            runCatching { api.joinGroup(group.id, password) }
                .onSuccess {
                    mainHandler.post { notice = "已加入 ${group.name}" }
                    refreshAll()
                }
                .onFailure { error -> mainHandler.post { notice = friendlyError(error) } }
        }
    }

    fun leaveGroup(group: Group) {
        executor.execute {
            runCatching { api.leaveGroup(group.id) }
                .onSuccess {
                    mainHandler.post { notice = "已退出 ${group.name}" }
                    refreshAll()
                }
                .onFailure { error -> mainHandler.post { notice = friendlyError(error) } }
        }
    }

    fun refreshRecords() {
        if (api.currentSession() == null) return
        contentLoading = true
        executor.execute {
            runCatching { api.getCommunicationRecords() }
                .onSuccess { loaded ->
                    mainHandler.post {
                        records = loaded
                        contentLoading = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        contentLoading = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun updateProfile(nickname: String, phone: String, address: String, introduction: String) {
        contentLoading = true
        executor.execute {
            runCatching { api.updateProfile(nickname, phone, address, introduction) }
                .onSuccess { updated ->
                    mainHandler.post {
                        user = updated
                        contentLoading = false
                        notice = "个人资料已保存"
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        contentLoading = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    override fun onRadioStatus(status: RadioStatus) {
        mainHandler.post {
            val wasConnected = radioStatus.connected
            radioStatus = status.copy(groupId = selectedGroupId)
            if (status.connected) preparingRadioConnection.set(false)
            if (!wasConnected && status.connected) refreshRadioData()
            if (
                status.phase == RadioConnectionPhase.ERROR &&
                status.error.contains("凭证无效") &&
                refreshingRadioToken.compareAndSet(false, true)
            ) {
                val failedGeneration = radioConnectionGeneration.get()
                executor.execute {
                    runCatching(api::renewAccessToken)
                        .onSuccess {
                            refreshingRadioToken.set(false)
                            mainHandler.post {
                                if (
                                    !disposed.get() &&
                                    failedGeneration == radioConnectionGeneration.get() &&
                                    radioStatus.phase == RadioConnectionPhase.ERROR
                                ) connectRadio()
                            }
                        }
                        .onFailure {
                            refreshingRadioToken.set(false)
                            mainHandler.post {
                                if (!disposed.get()) {
                                    serviceBinder?.disconnect()
                                    notice = friendlyError(it)
                                }
                            }
                        }
                }
            }
        }
    }

    override fun onRadioMessage(message: RadioMessage) {
        mainHandler.post {
            radioMessages += message
            while (radioMessages.size > MAX_MESSAGES) radioMessages.removeAt(0)
        }
    }

    override fun onCleared() {
        if (!disposed.compareAndSet(false, true)) return
        radioConnectionGeneration.incrementAndGet()
        pendingConnection = null
        if (preparingRadioConnection.getAndSet(false)) {
            appContext.startService(RadioConnectionService.disconnectIntent(appContext))
        }
        serviceBinder?.setListener(null)
        if (serviceBound) runCatching { appContext.unbindService(serviceConnection) }
        serviceBound = false
        serviceBinder = null
        executor.shutdownNow()
    }

    private fun restoreSession() {
        val stored = api.currentSession()
        if (stored == null) {
            initializing = false
            return
        }
        serverUrl = stored.baseUrl
        user = stored.user
        executor.execute {
            runCatching(api::restoreAndValidate)
                .onSuccess { session ->
                    mainHandler.post {
                        user = session.user
                        authenticated = true
                        initializing = false
                        selectedGroupId = sessionStore.selectedGroupId(
                            session.user.id,
                            session.user.lastGroupId.takeIf { it > 0 } ?: 999,
                        )
                        page = if (session.user.isApproved) AppPage.RADIO else AppPage.DASHBOARD
                    }
                    mainHandler.post {
                        refreshAll()
                        discoverAccessPoints()
                    }
                }
                .onFailure {
                    mainHandler.post {
                        initializing = false
                        authenticated = false
                        user = null
                        loginError = "登录状态已过期，请重新登录"
                    }
                }
        }
    }

    private fun bindRadioService() {
        if (serviceBound) return
        serviceBound = appContext.bindService(
            Intent(appContext, RadioConnectionService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun derivedAccessPoint(): AccessPoint? {
        val session = api.currentSession() ?: return null
        val host = runCatching { URI(session.baseUrl).host }.getOrNull().orEmpty()
        if (host.isBlank()) return null
        return AccessPoint(
            id = "derived-center",
            displayName = "默认中心入口",
            host = host,
            port = DEFAULT_UDP_PORT,
            priority = Int.MAX_VALUE,
        )
    }

    private fun mergeHistory(history: List<CommunicationRecord>) {
        val liveIds = radioMessages.mapTo(HashSet()) { it.id }
        val historical = history.asReversed().map(::recordToMessage).filterNot { it.id in liveIds }
        if (historical.isNotEmpty()) radioMessages.addAll(0, historical)
        while (radioMessages.size > MAX_MESSAGES) radioMessages.removeAt(0)
    }

    private fun recordToMessage(record: CommunicationRecord): RadioMessage {
        val callsign = record.deviceName.substringBefore('-').ifBlank { record.nickname.ifBlank { "未知台站" } }
        val ssid = if (record.model in 100..105) record.model else record.deviceName.substringAfterLast('-').toIntOrNull() ?: 0
        val mine = record.username.equals(user?.username, ignoreCase = true) && ssid == 101
        return RadioMessage(
            id = "record-${record.id}",
            type = if (record.messageType == 1) RadioMessageType.TEXT else RadioMessageType.VOICE,
            senderCallsign = callsign,
            senderSsid = ssid,
            content = if (record.messageType == 1) record.text else "历史语音 ${formatDuration(record.durationMs)}",
            timestamp = parseServerTime(record.startedAt),
            mine = mine,
            durationMs = record.durationMs,
        )
    }

    private fun parseServerTime(value: String): Long = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).parse(value)?.time
    }.getOrNull() ?: System.currentTimeMillis()

    private fun friendlyError(error: Throwable): String = when (error) {
        is ApiException -> error.message
        else -> error.message ?: "操作失败，请稍后重试"
    }

    companion object {
        private const val DEFAULT_UDP_PORT = 60_050
        private const val MAX_MESSAGES = 200
        private val APPROVED_PAGES = setOf(AppPage.RADIO, AppPage.DEVICES, AppPage.GROUPS, AppPage.RECORDS)

        fun formatDuration(milliseconds: Long): String {
            val totalSeconds = milliseconds / 1_000
            val hours = totalSeconds / 3_600
            val minutes = totalSeconds % 3_600 / 60
            val seconds = totalSeconds % 60
            return buildList {
                if (hours > 0) add("${hours}小时")
                if (minutes > 0) add("${minutes}分钟")
                if (seconds > 0 || isEmpty()) add("${seconds}秒")
            }.joinToString(" ")
        }
    }
}
