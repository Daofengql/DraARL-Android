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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageReconciler
import cn.silverdragon.draarl.data.RadioMessageStore
import cn.silverdragon.draarl.data.RadioMessageSyncState
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.RegistrationResult
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class AppPage { RADIO, DASHBOARD, DEVICES, GROUPS, PROFILE, SETTINGS, ACCOUNT_SECURITY }

class AppController(application: Application) : AndroidViewModel(application), RadioServiceListener {
    private val appContext = application.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "draarl-app-worker")
    }
    private val sessionStore = SecureSessionStore(appContext)
    private val messageStore = RadioMessageStore(appContext)
    private var serviceBinder: RadioConnectionService.LocalBinder? = null
    private var serviceBound = false
    private var pendingConnection: RadioConnectionConfig? = null
    private var manualAccessPointSelection = false
    private var manualRadioDisconnect = false
    private val refreshingRadioToken = AtomicBoolean(false)
    private val syncingRadioData = AtomicBoolean(false)
    private val syncingGroupCounts = AtomicBoolean(false)
    private val radioConnectionGeneration = AtomicInteger(0)
    private val captchaGeneration = AtomicInteger(0)
    private val preparingRadioConnection = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val loadingPublicProfiles = ConcurrentHashMap.newKeySet<String>()
    private val loadingCachedRadioMessages = ConcurrentHashMap.newKeySet<String>()
    private var displayedRadioMessageGroupId: Int? = null
    private val periodicRadioSync = object : Runnable {
        override fun run() {
            if (disposed.get()) return
            if (authenticated && user?.isApproved == true) {
                refreshGroupOnlineCounts()
                if (page == AppPage.RADIO) refreshRadioData()
            }
            mainHandler.postDelayed(this, RADIO_SYNC_INTERVAL_MS)
        }
    }
    private val periodicAccessPointProbe = object : Runnable {
        override fun run() {
            if (disposed.get()) return
            if (authenticated) discoverAccessPoints()
            mainHandler.postDelayed(this, ACCESS_POINT_PROBE_INTERVAL_MS)
        }
    }
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
                publicProfiles = emptyMap()
                loadingPublicProfiles.clear()
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
    var publicAuthBusy by mutableStateOf(false)
        private set
    var publicAuthError by mutableStateOf("")
        private set
    var registrationRequiresEmailVerification by mutableStateOf(true)
        private set
    var registrationResult by mutableStateOf<RegistrationResult?>(null)
        private set
    var passwordResetComplete by mutableStateOf(false)
        private set
    var captchaId by mutableStateOf("")
        private set
    var captchaImageBase64 by mutableStateOf("")
        private set
    var captchaLoading by mutableStateOf(false)
        private set
    val serverUrl: String = AppConfig.BASE_URL
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
    var groupSearchResults by mutableStateOf<List<Group>>(emptyList())
        private set
    var managedGroupDevices by mutableStateOf<List<Device>>(emptyList())
        private set
    var managedGroupId by mutableStateOf<Int?>(null)
        private set
    var defaultDeviceGroupId by mutableStateOf<Int?>(null)
        private set
    var devicePasswordInfo by mutableStateOf<DevicePasswordInfo?>(null)
        private set
    var deviceBindPreview by mutableStateOf<DeviceBindPreview?>(null)
        private set
    var deviceBindResult by mutableStateOf<DeviceBindResult?>(null)
        private set
    var deviceConfig by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var deviceConfigDeviceId by mutableStateOf<Int?>(null)
        private set
    var managementBusy by mutableStateOf(false)
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
    var selectedGroupId by mutableIntStateOf(999)
        private set

    var radioStatus by mutableStateOf(RadioStatus())
        private set
    val radioMessages = mutableStateListOf<RadioMessage>()
    var muted by mutableStateOf(sessionStore.isMuted())
        private set
    var playingMessageId by mutableStateOf<String?>(null)
        private set
    var publicProfiles by mutableStateOf<Map<String, User>>(emptyMap())
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
        mainHandler.postDelayed(periodicRadioSync, RADIO_SYNC_INTERVAL_MS)
        mainHandler.postDelayed(periodicAccessPointProbe, ACCESS_POINT_PROBE_INTERVAL_MS)
    }

    fun loadCaptcha() {
        val requestGeneration = captchaGeneration.incrementAndGet()
        captchaId = ""
        captchaImageBase64 = ""
        captchaLoading = true
        executor.execute {
            runCatching { api.getCaptcha(AppConfig.BASE_URL) }
                .onSuccess { challenge ->
                    mainHandler.post {
                        if (
                            disposed.get() ||
                            requestGeneration != captchaGeneration.get()
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
                            requestGeneration != captchaGeneration.get()
                        ) return@post
                        captchaId = ""
                        captchaImageBase64 = ""
                        captchaLoading = false
                        loginError = friendlyError(error)
                    }
                }
        }
    }

    fun loadRegistrationConfig() {
        executor.execute {
            runCatching { api.getRegistrationRequiresEmailVerification(AppConfig.BASE_URL) }
                .onSuccess { required ->
                    mainHandler.post {
                        if (!disposed.get()) registrationRequiresEmailVerification = required
                    }
                }
        }
    }

    fun clearPublicAuthState() {
        publicAuthError = ""
        registrationResult = null
        passwordResetComplete = false
    }

    fun sendPublicEmailCode(
        email: String,
        purpose: String,
        captchaCode: String,
        onSuccess: (EmailCodeSession) -> Unit,
    ) {
        if (publicAuthBusy) return
        val trimmedEmail = email.trim()
        val submittedCaptchaId = captchaId
        val normalizedPurpose = purpose.trim()
        val validationError = when {
            !trimmedEmail.matches(EMAIL_PATTERN) -> "请输入正确的邮箱地址"
            normalizedPurpose !in setOf("register", "reset_password") -> "邮箱验证码用途不正确"
            submittedCaptchaId.isBlank() || captchaCode.isBlank() -> "请输入图片验证码"
            else -> null
        }
        if (validationError != null) {
            publicAuthError = validationError
            if (submittedCaptchaId.isBlank()) loadCaptcha()
            return
        }
        publicAuthBusy = true
        publicAuthError = ""
        executor.execute {
            runCatching {
                api.sendEmailCode(
                    baseUrl = AppConfig.BASE_URL,
                    email = trimmedEmail,
                    purpose = normalizedPurpose,
                    captchaId = submittedCaptchaId,
                    captchaCode = captchaCode,
                )
            }.onSuccess { session ->
                mainHandler.post {
                    publicAuthBusy = false
                    publicAuthError = ""
                    onSuccess(session)
                    loadCaptcha()
                }
            }.onFailure { error ->
                mainHandler.post {
                    publicAuthBusy = false
                    publicAuthError = friendlyError(error)
                    loadCaptcha()
                }
            }
        }
    }

    fun registerAccount(
        username: String,
        callsign: String,
        nickname: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        sessionId: String,
        emailCode: String,
        onSuccess: (RegistrationResult) -> Unit,
    ) {
        if (publicAuthBusy) return
        val trimmedUsername = username.trim()
        val normalizedCallsign = callsign.trim().uppercase()
        val trimmedEmail = email.trim()
        val trimmedPhone = phone.trim()
        val validationError = when {
            !trimmedUsername.matches(USERNAME_PATTERN) -> "用户名必须是 3-20 位字母、数字或下划线"
            !normalizedCallsign.matches(CALLSIGN_PATTERN) -> "呼号格式不正确，应以字母开头，3-10 个字符"
            !trimmedEmail.matches(EMAIL_PATTERN) -> "请输入正确的邮箱地址"
            trimmedPhone.isNotBlank() && !trimmedPhone.matches(PHONE_PATTERN) -> "手机号格式不正确"
            password.length < 6 -> "密码长度至少 6 位"
            password != confirmPassword -> "两次输入的密码不一致"
            registrationRequiresEmailVerification && (sessionId.isBlank() || emailCode.isBlank()) -> "请先获取并填写邮箱验证码"
            else -> null
        }
        if (validationError != null) {
            publicAuthError = validationError
            return
        }
        publicAuthBusy = true
        publicAuthError = ""
        registrationResult = null
        executor.execute {
            runCatching {
                api.register(
                    baseUrl = AppConfig.BASE_URL,
                    username = trimmedUsername,
                    password = password,
                    callsign = normalizedCallsign,
                    phone = trimmedPhone,
                    nickname = nickname.trim().ifBlank { trimmedUsername },
                    email = trimmedEmail,
                    sessionId = if (registrationRequiresEmailVerification) sessionId else "",
                    emailCode = if (registrationRequiresEmailVerification) emailCode else "",
                )
            }.onSuccess { result ->
                mainHandler.post {
                    publicAuthBusy = false
                    publicAuthError = ""
                    registrationResult = result
                    onSuccess(result)
                }
            }.onFailure { error ->
                mainHandler.post {
                    publicAuthBusy = false
                    publicAuthError = friendlyError(error)
                }
            }
        }
    }

    fun resetPassword(
        sessionId: String,
        emailCode: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit,
    ) {
        if (publicAuthBusy) return
        val validationError = when {
            sessionId.isBlank() -> "请先获取邮箱验证码"
            emailCode.isBlank() -> "请输入邮箱验证码"
            newPassword.length < 6 -> "新密码长度至少 6 位"
            newPassword != confirmPassword -> "两次输入的密码不一致"
            else -> null
        }
        if (validationError != null) {
            publicAuthError = validationError
            return
        }
        publicAuthBusy = true
        publicAuthError = ""
        passwordResetComplete = false
        executor.execute {
            runCatching { api.resetPassword(AppConfig.BASE_URL, sessionId, emailCode, newPassword) }
                .onSuccess {
                    mainHandler.post {
                        publicAuthBusy = false
                        publicAuthError = ""
                        passwordResetComplete = true
                        onSuccess()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        publicAuthBusy = false
                        publicAuthError = friendlyError(error)
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
            runCatching { api.login(AppConfig.BASE_URL, username, password, submittedCaptchaId, captchaCode) }
                .onSuccess { session ->
                    mainHandler.post {
                        user = session.user
                        authenticated = true
                        loginBusy = false
                        selectedGroupId = sessionStore.selectedGroupId(
                            session.user.id,
                            session.user.lastGroupId.takeIf { it > 0 } ?: 999,
                        )
                        page = if (session.user.isApproved) AppPage.RADIO else AppPage.DASHBOARD
                        manualRadioDisconnect = false
                        if (session.user.isApproved) loadCachedRadioMessages()
                    }
                    mainHandler.post {
                        refreshAll()
                        discoverAccessPoints()
                        if (session.user.isApproved) refreshRadioData()
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
        manualRadioDisconnect = true
        serviceBinder?.disconnect()
        executor.execute { api.logout() }
        authenticated = false
        user = null
        devices = emptyList()
        groups = emptyList()
        groupSearchResults = emptyList()
        managedGroupDevices = emptyList()
        managedGroupId = null
        defaultDeviceGroupId = null
        devicePasswordInfo = null
        resetDeviceBinding()
        deviceConfig = emptyMap()
        deviceConfigDeviceId = null
        records = emptyList()
        onlineDevices = emptyList()
        radioMessages.clear()
        displayedRadioMessageGroupId = null
        loadingCachedRadioMessages.clear()
        publicProfiles = emptyMap()
        loadingPublicProfiles.clear()
        playingMessageId = null
        clearPublicAuthState()
        page = AppPage.RADIO
    }

    fun navigate(target: AppPage) {
        if (target in APPROVED_PAGES && user?.isApproved != true) {
            notice = "账号审核通过后才能使用该功能"
            return
        }
        page = target
        when (target) {
            AppPage.DEVICES, AppPage.GROUPS, AppPage.DASHBOARD -> refreshAll()
            AppPage.RADIO -> {
                loadCachedRadioMessages()
                refreshRadioData()
            }
            else -> Unit
        }
    }

    fun goBack() {
        page = AppPage.DASHBOARD
    }

    fun clearNotice() {
        notice = ""
    }

    fun showNotice(message: String) {
        notice = message
    }

    fun refreshAll() {
        if (api.currentSession() == null) return
        mainHandler.post { contentLoading = true }
        // Capture fallback values on the calling (main) thread before submitting
        val fallbackDevices = devices
        val fallbackGroups  = groups
        val fallbackTrend   = dashboard.communicationTrend
        // Submit all six requests concurrently; collect when every future resolves
        val fDevices   = CompletableFuture.supplyAsync({ runCatching(api::getDevices).getOrDefault(fallbackDevices) }, executor)
        val fGroups    = CompletableFuture.supplyAsync({ runCatching(api::getGroups).getOrDefault(fallbackGroups) }, executor)
        val fDefGroup  = CompletableFuture.supplyAsync({ runCatching(api::getDefaultDeviceGroup) }, executor)
        val fStats     = CompletableFuture.supplyAsync({ runCatching(api::getCommunicationStats).getOrNull() }, executor)
        val fTrend     = CompletableFuture.supplyAsync({ runCatching(api::getCommunicationTrend).getOrDefault(fallbackTrend) }, executor)
        val fMe        = CompletableFuture.supplyAsync({ runCatching(api::getMe).getOrNull() }, executor)
        CompletableFuture.allOf(fDevices, fGroups, fDefGroup, fStats, fTrend, fMe)
            .thenRunAsync({
                val loadedDevices            = fDevices.join()
                val loadedGroups             = fGroups.join()
                val loadedDefaultDeviceGroup = fDefGroup.join()
                val stats                    = fStats.join()
                val communicationTrend       = fTrend.join()
                val refreshedUser            = fMe.join()
                mainHandler.post {
                    val previousGroupId = selectedGroupId
                    devices = loadedDevices
                    groups  = loadedGroups
                    if (loadedDefaultDeviceGroup.isSuccess) {
                        defaultDeviceGroupId = loadedDefaultDeviceGroup.getOrNull()
                    }
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
                        communicationTrend = communicationTrend,
                    )
                    contentLoading = false
                    if (selectedGroupId != previousGroupId && page == AppPage.RADIO) {
                        loadCachedRadioMessages()
                        refreshRadioData()
                    }
                }
            }, executor)
    }

    fun refreshGroupOnlineCounts() {
        if (api.currentSession() == null || !syncingGroupCounts.compareAndSet(false, true)) return
        executor.execute {
            try {
                val stats = runCatching(api::getGroupStats).getOrDefault(emptyMap())
                if (stats.isEmpty()) return@execute
                mainHandler.post {
                    groups = groups.map { group ->
                        stats[group.id]?.let { (online, total) ->
                            group.copy(onlineCount = online, totalCount = total)
                        } ?: group
                    }
                }
            } finally {
                syncingGroupCounts.set(false)
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
        if (selectedAccessPoint?.id == accessPoint.id) return
        manualAccessPointSelection = true
        manualRadioDisconnect = false
        selectedAccessPoint = accessPoint
        sessionStore.setSelectedAccessPointId(accessPoint.id)
        if (radioStatus.phase != RadioConnectionPhase.DISCONNECTED) {
            serviceBinder?.disconnect()
            connectRadio()
        }
    }

    fun connectRadio() {
        manualRadioDisconnect = false
        if (!preparingRadioConnection.compareAndSet(false, true)) return
        if (user?.isApproved != true) {
            preparingRadioConnection.set(false)
            notice = "账号审核通过后才能连接在线电台"
            return
        }
        val point = selectedAccessPoint
        if (point == null) {
            preparingRadioConnection.set(false)
            discoverAccessPoints()
            notice = "正在发现并优选 UDP 入口，请稍候"
            return
        }
        val requestGeneration = radioConnectionGeneration.incrementAndGet()
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
                    ) {
                        preparingRadioConnection.set(false)
                        return@post
                    }
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
        manualRadioDisconnect = true
        radioConnectionGeneration.incrementAndGet()
        preparingRadioConnection.set(false)
        pendingConnection = null
        serviceBinder?.disconnect()
    }

    fun shouldAutoConnectRadio(): Boolean = !manualRadioDisconnect

    fun sendText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (!radioStatus.connected) {
            notice = "电台尚未连接"
            return false
        }
        if (radioStatus.transmitting) {
            notice = "正在发射中，稍后再发送文本"
            return false
        }
        if (radioStatus.speaker.isNotBlank()) {
            notice = "正在接收语音，发言结束后再发送文本"
            return false
        }
        val sent = serviceBinder?.sendText(text) == true
        if (!sent) notice = "文本发送失败，请稍后重试"
        return sent
    }

    fun canSendText(): Boolean = radioStatus.connected && !radioStatus.transmitting && radioStatus.speaker.isBlank()

    fun startPtt(): Boolean {
        val started = serviceBinder?.startPtt() == true
        if (!started && !radioStatus.connected) notice = "请先连接电台"
        return started
    }

    fun stopPtt() {
        serviceBinder?.stopPtt()
    }

    fun toggleVoicePlayback(message: RadioMessage) {
        if (message.audioCacheKey.isBlank() && message.audioUrl.isBlank()) {
            notice = "这条语音暂时还没有可回放的数据"
            return
        }
        if (serviceBinder?.togglePlayback(message) != true) notice = "无法播放这条语音"
    }

    fun toggleRecordPlayback(record: CommunicationRecord) {
        toggleVoicePlayback(recordToMessage(record, user?.username.orEmpty()))
    }

    fun publicProfile(username: String): User? = publicProfiles[username.lowercase()]

    fun toggleMuted() {
        muted = !muted
        sessionStore.setMuted(muted)
        serviceBinder?.setMuted(muted)
    }

    fun switchGroup(group: Group) {
        if (group.id == selectedGroupId) return
        val previousGroupId = selectedGroupId
        selectedGroupId = group.id
        user?.let { sessionStore.setSelectedGroupId(it.id, group.id) }
        serviceBinder?.setGroup(group.id)
        radioMessages.clear()
        displayedRadioMessageGroupId = null
        loadCachedRadioMessages(group.id)
        contentLoading = true
        val accountKey = messageAccountKey()
        val accountUsername = user?.username.orEmpty()
        executor.execute {
            runCatching { api.switchRadioGroup(group.id) }
                .onSuccess {
                    val history = runCatching { api.getCommunicationRecords(groupId = group.id) }.getOrDefault(emptyList())
                    val online = runCatching { api.getOnlineDevices(group.id) }.getOrDefault(emptyList())
                    val remoteMessages = RadioMessageReconciler.settledRemoteMessages(
                        history.asReversed().map { recordToMessage(it, accountUsername) },
                    )
                    val cachedMessages = if (accountKey != null) {
                        messageStore.reconcile(accountKey, group.id, remoteMessages)
                        messageStore.load(accountKey, group.id)
                    } else {
                        remoteMessages
                    }
                    mainHandler.post {
                        if (selectedGroupId != group.id) return@post
                        replaceRadioMessages(cachedMessages, preserveUnsynced = true)
                        onlineDevices = online
                        contentLoading = false
                        notice = "已切换到 ${group.name}"
                    }
                    preloadPublicProfiles(history.map(CommunicationRecord::username))
                }
                .onFailure { error ->
                    mainHandler.post {
                        if (selectedGroupId != group.id) return@post
                        selectedGroupId = previousGroupId
                        user?.let { sessionStore.setSelectedGroupId(it.id, previousGroupId) }
                        serviceBinder?.setGroup(previousGroupId)
                        radioMessages.clear()
                        loadCachedRadioMessages(previousGroupId)
                        contentLoading = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun refreshRadioData() {
        if (api.currentSession() == null || !syncingRadioData.compareAndSet(false, true)) return
        val groupId = selectedGroupId
        val accountKey = messageAccountKey()
        val accountUsername = user?.username.orEmpty()
        executor.execute {
            try {
                val history = runCatching { api.getCommunicationRecords(groupId = groupId) }.getOrDefault(emptyList())
                val online = runCatching { api.getOnlineDevices(groupId) }.getOrDefault(emptyList())
                val remoteMessages = RadioMessageReconciler.settledRemoteMessages(
                    history.asReversed().map { recordToMessage(it, accountUsername) },
                )
                val cachedMessages = if (accountKey != null) {
                    messageStore.reconcile(accountKey, groupId, remoteMessages)
                    messageStore.load(accountKey, groupId)
                } else {
                    remoteMessages
                }
                mainHandler.post {
                    if (groupId != selectedGroupId) return@post
                    replaceRadioMessages(cachedMessages, preserveUnsynced = true)
                    onlineDevices = online
                }
                preloadPublicProfiles(history.map(CommunicationRecord::username) + online.map(OnlineDevice::username))
            } finally {
                syncingRadioData.set(false)
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
                    preloadPublicProfiles(loaded.map(CommunicationRecord::username))
                }
                .onFailure { error ->
                    mainHandler.post {
                        contentLoading = false
                        notice = friendlyError(error)
                    }
                }
        }
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
    ) {
        contentLoading = true
        executor.execute {
            runCatching {
                api.updateProfile(nickname, phone, address, introduction, birthday, sex, dmrid, mdcid, alarmMsg)
            }
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

    fun uploadAvatar(fileBytes: ByteArray, fileName: String, onSuccess: () -> Unit = {}) {
        contentLoading = true
        executor.execute {
            runCatching {
                val url = api.uploadFile(fileBytes, fileName, "avatar")
                api.updateProfile(
                    nickname = user?.nickname ?: "",
                    phone = user?.phone ?: "",
                    address = user?.address ?: "",
                    introduction = user?.introduction ?: "",
                    birthday = user?.birthday ?: "",
                    sex = user?.sex ?: 0,
                    dmrid = user?.dmrId ?: 0,
                    mdcid = user?.mdcId ?: "",
                    alarmMsg = user?.alarmMsg ?: false,
                )
            }
                .onSuccess { updated ->
                    mainHandler.post {
                        user = updated
                        contentLoading = false
                        notice = "头像已更新"
                        onSuccess()
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

    fun changePassword(oldPassword: String, newPassword: String) {
        contentLoading = true
        executor.execute {
            runCatching { api.changePassword(oldPassword, newPassword) }
                .onSuccess {
                    mainHandler.post {
                        contentLoading = false
                        notice = "密码已修改"
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

    fun searchGroups(keyword: String) {
        val query = keyword.trim()
        if (query.isBlank()) {
            notice = "请输入群组 ID 或名称"
            return
        }
        managementBusy = true
        executor.execute {
            runCatching { api.searchGroups(query) }
                .onSuccess { results ->
                    mainHandler.post {
                        groupSearchResults = results
                        managementBusy = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun clearGroupSearch() {
        groupSearchResults = emptyList()
    }

    fun saveGroup(
        editing: Group?,
        name: String,
        type: Int,
        password: String,
        note: String,
        onSuccess: () -> Unit = {},
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            notice = "请输入群组名称"
            return
        }
        if (editing == null && type == 2 && password.isBlank()) {
            notice = "私有群组必须设置加入密码"
            return
        }
        managementBusy = true
        executor.execute {
            val operation = if (editing == null) {
                runCatching { api.createGroup(trimmedName, type, password, note.trim()) }
            } else {
                runCatching {
                    api.updateGroup(
                        groupId = editing.id,
                        name = trimmedName,
                        type = type,
                        password = password,
                        note = note.trim(),
                    )
                }
            }
            operation
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        notice = if (editing == null) "群组已创建" else "群组设置已保存"
                        onSuccess()
                        refreshAll()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun setGroupEnabled(group: Group, enabled: Boolean) {
        managementBusy = true
        executor.execute {
            runCatching { api.updateGroup(group.id, status = if (enabled) 1 else 0) }
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        groups = groups.map { current ->
                            if (current.id == group.id) current.copy(status = if (enabled) 1 else 0) else current
                        }
                        notice = if (enabled) "群组已启用" else "群组已停用"
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun deleteGroup(group: Group, onSuccess: () -> Unit = {}) {
        managementBusy = true
        executor.execute {
            runCatching { api.deleteGroup(group.id) }
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        groups = groups.filterNot { it.id == group.id }
                        notice = "群组已删除"
                        onSuccess()
                        refreshAll()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun loadGroupDevices(groupId: Int) {
        managedGroupId = groupId
        managedGroupDevices = emptyList()
        managementBusy = true
        executor.execute {
            runCatching { api.getGroupDevices(groupId) }
                .onSuccess { loaded ->
                    mainHandler.post {
                        if (managedGroupId == groupId) managedGroupDevices = loaded
                        managementBusy = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun closeGroupDevices() {
        managedGroupId = null
        managedGroupDevices = emptyList()
    }

    fun updateGroupDeviceCommControl(
        groupId: Int,
        device: Device,
        disableSend: Boolean = device.disableSend,
        disableReceive: Boolean = device.disableReceive,
    ) {
        managementBusy = true
        executor.execute {
            runCatching {
                api.updateGroupDeviceCommControl(groupId, device.id, disableSend, disableReceive)
            }.onSuccess { (sendDisabled, receiveDisabled) ->
                mainHandler.post {
                    managementBusy = false
                    managedGroupDevices = managedGroupDevices.map { current ->
                        if (current.id == device.id) {
                            current.copy(disableSend = sendDisabled, disableReceive = receiveDisabled)
                        } else {
                            current
                        }
                    }
                }
            }.onFailure { error ->
                mainHandler.post {
                    managementBusy = false
                    notice = friendlyError(error)
                }
            }
        }
    }

    fun kickGroupDevice(groupId: Int, device: Device) {
        managementBusy = true
        executor.execute {
            runCatching { api.kickGroupDevice(groupId, device.id) }
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        managedGroupDevices = managedGroupDevices.filterNot { it.id == device.id }
                        notice = "设备已移出群组"
                        refreshAll()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun setDefaultDeviceGroup(groupId: Int?) {
        managementBusy = true
        executor.execute {
            runCatching { api.setDefaultDeviceGroup(groupId) }
                .onSuccess { saved ->
                    mainHandler.post {
                        defaultDeviceGroupId = saved
                        managementBusy = false
                        notice = if (saved == null) "已清除新设备默认群组" else "新设备默认群组已保存"
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun updateDevice(
        device: Device,
        name: String? = null,
        disableSend: Boolean? = null,
        disableReceive: Boolean? = null,
        onSuccess: () -> Unit = {},
    ) {
        managementBusy = true
        executor.execute {
            runCatching { api.updateDevice(device.id, name, disableSend, disableReceive) }
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        notice = "设备设置已保存"
                        onSuccess()
                        refreshAll()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun switchDeviceGroup(device: Device, group: Group, password: String = "", onSuccess: () -> Unit = {}) {
        if (device.groupId == group.id) {
            onSuccess()
            return
        }
        managementBusy = true
        executor.execute {
            runCatching { api.switchDeviceGroup(device.id, group.id, password) }
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        notice = "设备已切换到 ${group.name}"
                        onSuccess()
                        refreshAll()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun deleteDevice(device: Device, onSuccess: () -> Unit = {}) {
        managementBusy = true
        executor.execute {
            runCatching { api.deleteDevice(device.id) }
                .onSuccess {
                    mainHandler.post {
                        managementBusy = false
                        devices = devices.filterNot { it.id == device.id }
                        notice = "设备已删除"
                        onSuccess()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun loadDeviceConfig(deviceId: Int) {
        deviceConfigDeviceId = deviceId
        deviceConfig = emptyMap()
        managementBusy = true
        executor.execute {
            runCatching { api.getDeviceConfig(deviceId) }
                .onSuccess { loaded ->
                    mainHandler.post {
                        if (deviceConfigDeviceId == deviceId) deviceConfig = loaded
                        managementBusy = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun saveDeviceConfig(device: Device, config: Map<String, String>, onSuccess: () -> Unit = {}) {
        managementBusy = true
        executor.execute {
            runCatching {
                api.updateDeviceConfig(device.id, config)
                if (device.online) api.syncDeviceConfig(device.id) else "配置已保存，设备上线后自动同步"
            }.onSuccess { message ->
                mainHandler.post {
                    deviceConfig = config
                    managementBusy = false
                    notice = message
                    onSuccess()
                }
            }.onFailure { error ->
                mainHandler.post {
                    managementBusy = false
                    notice = friendlyError(error)
                }
            }
        }
    }

    fun closeDeviceConfig() {
        deviceConfigDeviceId = null
        deviceConfig = emptyMap()
    }

    fun loadDevicePassword() {
        managementBusy = true
        executor.execute {
            runCatching(api::getDevicePassword)
                .onSuccess { loaded ->
                    mainHandler.post {
                        devicePasswordInfo = loaded
                        managementBusy = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun regenerateDevicePassword() {
        managementBusy = true
        executor.execute {
            runCatching(api::regenerateDevicePassword)
                .onSuccess { loaded ->
                    mainHandler.post {
                        devicePasswordInfo = loaded
                        managementBusy = false
                        notice = "设备密码已刷新，旧密码已失效"
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun resetDeviceBinding() {
        deviceBindPreview = null
        deviceBindResult = null
    }

    fun lookupDeviceBindCode(dynamicCode: String) {
        if (!dynamicCode.matches(Regex("\\d{6}"))) {
            notice = "请输入 6 位动态码"
            return
        }
        managementBusy = true
        executor.execute {
            runCatching { api.bindDevice(dynamicCode) }
                .onSuccess { preview ->
                    mainHandler.post {
                        deviceBindPreview = preview
                        deviceBindResult = null
                        managementBusy = false
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun submitDeviceBinding(ssid: Int?, replaceDeviceId: Int?) {
        val preview = deviceBindPreview ?: return
        if (replaceDeviceId == null && (ssid == null || ssid !in 1..254 || ssid in 100..105)) {
            notice = "请选择可用 SSID"
            return
        }
        managementBusy = true
        executor.execute {
            runCatching { api.submitDeviceConfig(preview.deviceMac, ssid, replaceDeviceId) }
                .onSuccess { result ->
                    mainHandler.post {
                        deviceBindResult = result
                        managementBusy = false
                        refreshAll()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        managementBusy = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    override fun onRadioMessage(message: RadioMessage) {
        mainHandler.post {
            val currentUser = user
            val online = onlineDevices.firstOrNull { device ->
                device.ssid == message.senderSsid && (
                    message.senderUsername.isNotBlank() && device.username.equals(message.senderUsername, true) ||
                        message.senderCallsign.isNotBlank() && device.callsign.equals(message.senderCallsign, true)
                    )
            }
            val enriched = message.copy(
                senderUsername = message.senderUsername.ifBlank {
                    if (message.mine) currentUser?.username.orEmpty() else online?.username.orEmpty()
                },
                senderNickname = message.senderNickname.ifBlank {
                    if (message.mine) currentUser?.nickname.orEmpty() else online?.nickname.orEmpty()
                },
                senderCallsign = message.senderCallsign.ifBlank {
                    if (message.mine) currentUser?.callsign.orEmpty() else online?.callsign.orEmpty()
                },
            )
            // 只有当消息属于当前群组时才添加到列表
            val messageGroupId = if (enriched.groupId > 0) enriched.groupId else selectedGroupId
            if (messageGroupId == selectedGroupId) {
                displayedRadioMessageGroupId = selectedGroupId
                radioMessages += enriched
                while (radioMessages.size > MAX_MESSAGES) radioMessages.removeAt(0)
            }
            val accountKey = messageAccountKey()
            if (accountKey != null) {
                executor.execute {
                    runCatching { messageStore.save(accountKey, messageGroupId, enriched) }
                }
            }
            preloadPublicProfiles(listOf(enriched.senderUsername))
        }
    }

    override fun onPlaybackState(messageId: String?) {
        mainHandler.post { playingMessageId = messageId }
    }

    override fun onCleared() {
        if (!disposed.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(periodicRadioSync)
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
        val savedSession = api.currentSession()
        if (savedSession == null) {
            initializing = false
            return
        }
        val stored = if (savedSession.baseUrl != AppConfig.BASE_URL) {
            savedSession.copy(baseUrl = AppConfig.BASE_URL).also(sessionStore::save)
        } else {
            savedSession
        }
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
                        manualRadioDisconnect = false
                        if (session.user.isApproved) loadCachedRadioMessages()
                    }
                    mainHandler.post {
                        refreshAll()
                        discoverAccessPoints()
                        if (session.user.isApproved) refreshRadioData()
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

    private fun loadCachedRadioMessages(groupId: Int = selectedGroupId) {
        val accountKey = messageAccountKey() ?: return
        if (displayedRadioMessageGroupId == groupId) return
        val loadKey = "$accountKey#$groupId"
        if (!loadingCachedRadioMessages.add(loadKey)) return
        executor.execute {
            val cachedMessages = runCatching { messageStore.load(accountKey, groupId) }.getOrDefault(emptyList())
            mainHandler.post {
                loadingCachedRadioMessages.remove(loadKey)
                if (groupId == selectedGroupId && accountKey == messageAccountKey()) {
                    displayedRadioMessageGroupId = groupId
                    replaceRadioMessages(cachedMessages, preserveUnsynced = true)
                }
            }
        }
    }

    private fun replaceRadioMessages(messages: List<RadioMessage>, preserveUnsynced: Boolean) {
        displayedRadioMessageGroupId = selectedGroupId
        if (radioMessages.size == messages.size && radioMessages.indices.all { radioMessages[it] == messages[it] }) {
            return
        }
        val merged = if (preserveUnsynced) {
            val cachedIds = messages.mapTo(HashSet()) { it.id }
            val pending = radioMessages.filter { local ->
                local.syncState == RadioMessageSyncState.LOCAL &&
                    local.id !in cachedIds &&
                    messages.none { remote ->
                        remote.syncState == RadioMessageSyncState.CONFIRMED &&
                            RadioMessageReconciler.isLikelySameEvent(local, remote)
                    }
            }
            messages + pending
        } else {
            messages
        }
        val normalized = RadioMessageReconciler.deduplicate(merged).takeLast(MAX_MESSAGES)
        if (radioMessages.size == normalized.size && radioMessages.indices.all { radioMessages[it] == normalized[it] }) {
            return
        }
        radioMessages.clear()
        radioMessages.addAll(normalized)
    }

    private fun messageAccountKey(): String? {
        val session = api.currentSession() ?: return null
        return "${session.baseUrl.trimEnd('/')}#${session.user.id}"
    }

    private fun recordToMessage(record: CommunicationRecord, accountUsername: String): RadioMessage {
        val ssid = if (record.model in 100..105) record.model else record.deviceName.substringAfterLast('-').toIntOrNull() ?: 0
        val callsign = record.deviceName.removeSuffix(if (ssid > 0) "-$ssid" else "").ifBlank { record.username }
        val mine = record.username.equals(accountUsername, ignoreCase = true) && ssid == 101
        return RadioMessage(
            id = "record-${record.id}",
            type = if (record.messageType == 1) RadioMessageType.TEXT else RadioMessageType.VOICE,
            senderCallsign = callsign,
            senderSsid = ssid,
            senderUsername = record.username,
            senderNickname = record.nickname,
            content = if (record.messageType == 1) record.text else "历史语音 ${formatDuration(record.durationMs)}",
            timestamp = parseServerTime(record.startedAt),
            mine = mine,
            durationMs = record.durationMs,
            audioUrl = record.audioUrl,
            serverRecordId = record.id,
            syncState = RadioMessageSyncState.CONFIRMED,
        )
    }

    private fun preloadPublicProfiles(usernames: Collection<String>) {
        val pending = usernames.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .filter { username ->
                val key = username.lowercase()
                key !in publicProfiles && loadingPublicProfiles.add(key)
            }
            .toList()
        if (pending.isEmpty()) return
        // Submit all profile fetches concurrently; update the map as each one resolves
        pending.forEach { username ->
            val key = username.lowercase()
            CompletableFuture
                .supplyAsync({ runCatching { api.getPublicUserByName(username) }.getOrNull() }, executor)
                .thenAccept { profile ->
                    loadingPublicProfiles.remove(key)
                    if (profile != null) {
                        mainHandler.post { publicProfiles = publicProfiles + (key to profile) }
                    }
                }
        }
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
        private const val RADIO_SYNC_INTERVAL_MS = 20_000L
        private const val ACCESS_POINT_PROBE_INTERVAL_MS = 10_000L
        private const val MAX_MESSAGES = 200
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")
        private val CALLSIGN_PATTERN = Regex("^[A-Z][A-Z0-9]{2,9}$")
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private val PHONE_PATTERN = Regex("^1[3-9]\\d{9}$")
        private val APPROVED_PAGES = setOf(AppPage.RADIO, AppPage.DEVICES, AppPage.GROUPS)

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
