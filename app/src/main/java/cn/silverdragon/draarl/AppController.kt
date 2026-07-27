package cn.silverdragon.draarl

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cn.silverdragon.draarl.auth.PublicAuthController
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.ApiAppDataSource
import cn.silverdragon.draarl.data.AppDataFallback
import cn.silverdragon.draarl.data.AppDataRefresher
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.data.DashboardCacheStore
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageReconciler
import cn.silverdragon.draarl.data.RadioMessageStore
import cn.silverdragon.draarl.data.RadioMessageSyncState
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.devices.DeviceManagementController
import cn.silverdragon.draarl.groups.GroupManagementController
import cn.silverdragon.draarl.network.ApiClient
import cn.silverdragon.draarl.network.ApiException
import cn.silverdragon.draarl.profile.ProfileController
import cn.silverdragon.draarl.radio.AccessPointProbe
import cn.silverdragon.draarl.radio.AccessPointSelector
import cn.silverdragon.draarl.radio.RadioConnectionConfig
import cn.silverdragon.draarl.radio.RadioConnectionService
import cn.silverdragon.draarl.radio.RadioServiceListener
import cn.silverdragon.draarl.tools.ToolsController
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AppController(application: Application) : AndroidViewModel(application), RadioServiceListener {
    private val appContext = application.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "draarl-app-worker")
    }
    private val sessionStore = SecureSessionStore(appContext)
    private val messageStore = RadioMessageStore(appContext)
    private val dashboardStore = DashboardCacheStore(appContext)
    private var serviceBinder: RadioConnectionService.LocalBinder? = null
    private var serviceBound = false
    private var pendingConnection: RadioConnectionConfig? = null
    private var manualAccessPointSelection = false
    private var manualRadioDisconnect = false
    private val refreshingRadioToken = AtomicBoolean(false)
    private val syncingRadioData = AtomicBoolean(false)
    private val syncingGroupCounts = AtomicBoolean(false)
    private val groupCountsGeneration = AtomicInteger(0)
    private val accessPointDiscoveryGeneration = AtomicInteger(0)
    private val radioDataGeneration = AtomicInteger(0)
    private val groupSwitchGeneration = AtomicInteger(0)
    private val radioConnectionGeneration = AtomicInteger(0)
    private val preparingRadioConnection = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val refreshAllCoordinator = RefreshCoordinator()
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
                refreshAllCoordinator.cancel()
                invalidateBackgroundRequests()
                contentLoading = false
                radioConnectionGeneration.incrementAndGet()
                preparingRadioConnection.set(false)
                pendingConnection = null
                serviceBinder?.disconnect()
                authenticated = false
                syncPttOverlay()
                publicProfiles = emptyMap()
                loadingPublicProfiles.clear()
            }
        }
    }
    val tools: ToolsController by lazy { ToolsController(appContext, api) }
    private val appDataRefresher by lazy { AppDataRefresher(ApiAppDataSource(api), executor) }
    val deviceManagement: DeviceManagementController by lazy {
        DeviceManagementController(
            api = api,
            executor = executor,
            mainHandler = mainHandler,
            currentDevices = { devices },
            updateDevices = { devices = it },
            refreshAll = ::refreshAll,
            showNotice = { notice = it },
            friendlyError = ::friendlyError,
        )
    }
    val groupManagement: GroupManagementController by lazy {
        GroupManagementController(
            api = api,
            executor = executor,
            mainHandler = mainHandler,
            currentGroups = { groups },
            updateGroups = {
                groups = it
                syncPttOverlay()
            },
            refreshAll = ::refreshAll,
            showNotice = { notice = it },
            friendlyError = ::friendlyError,
        )
    }
    val profile: ProfileController by lazy {
        ProfileController(
            api = api,
            executor = executor,
            mainHandler = mainHandler,
            currentUser = { user },
            updateUser = { user = it },
            showNotice = { notice = it },
            friendlyError = ::friendlyError,
        )
    }
    val publicAuth: PublicAuthController by lazy {
        PublicAuthController(
            api = api,
            executor = executor,
            mainHandler = mainHandler,
            showLoginError = { loginError = it },
            friendlyError = ::friendlyError,
        )
    }

    var initializing by mutableStateOf(true)
        private set
    var authenticated by mutableStateOf(false)
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf("")
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
    var pttOverlayEnabled by mutableStateOf(
        sessionStore.isPttOverlayEnabled() && Settings.canDrawOverlays(appContext),
    )
        private set
    private var appInForeground = true
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
            syncPttOverlay()
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

    fun login(username: String, password: String, captchaCode: String) {
        if (loginBusy) return
        if (publicAuth.captchaId.isBlank() || captchaCode.isBlank()) {
            loginError = "请输入图片验证码"
            if (publicAuth.captchaId.isBlank()) publicAuth.loadCaptcha()
            return
        }
        val submittedCaptchaId = publicAuth.captchaId
        loginBusy = true
        loginError = ""
        executor.execute {
            runCatching { api.login(AppConfig.BASE_URL, username, password, submittedCaptchaId, captchaCode) }
                .onSuccess { session ->
                    mainHandler.post {
                        user = session.user
                        dashboard = dashboardStore.load(session.user.id) ?: DashboardData()
                        authenticated = true
                        loginBusy = false
                        selectedGroupId = sessionStore.selectedGroupId(
                            session.user.id,
                            session.user.lastGroupId.takeIf { it > 0 } ?: 999,
                        )
                        page = authenticatedStartPage(session.user.isApproved)
                        manualRadioDisconnect = false
                        syncPttOverlay()
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
                        publicAuth.loadCaptcha()
                    }
                }
        }
    }

    fun logout() {
        tools.reset()
        refreshAllCoordinator.cancel()
        invalidateBackgroundRequests()
        contentLoading = false
        radioConnectionGeneration.incrementAndGet()
        preparingRadioConnection.set(false)
        pendingConnection = null
        manualRadioDisconnect = true
        serviceBinder?.configurePttOverlay(enabled = false, visible = false, groupName = "")
        serviceBinder?.disconnect()
        executor.execute { api.logout() }
        authenticated = false
        user = null
        dashboard = DashboardData()
        devices = emptyList()
        groups = emptyList()
        deviceManagement.reset()
        groupManagement.reset()
        profile.reset()
        publicAuth.reset()
        onlineDevices = emptyList()
        radioMessages.clear()
        displayedRadioMessageGroupId = null
        loadingCachedRadioMessages.clear()
        publicProfiles = emptyMap()
        loadingPublicProfiles.clear()
        playingMessageId = null
        page = AppPage.RADIO
    }

    fun navigate(target: AppPage) {
        if (target in APPROVAL_REQUIRED_PAGES && user?.isApproved != true) {
            notice = "账号审核通过后才能使用该功能"
            return
        }
        page = target
        when (target) {
            AppPage.DEVICES, AppPage.GROUPS, AppPage.PROFILE -> refreshAll()
            AppPage.RADIO -> {
                loadCachedRadioMessages()
                refreshRadioData()
            }
            else -> Unit
        }
    }

    fun clearNotice() {
        notice = ""
    }

    fun showNotice(message: String) {
        notice = message
    }

    fun refreshAll() {
        if (api.currentSession() == null) return
        val generation = refreshAllCoordinator.request() ?: return
        contentLoading = true
        startRefreshAll(generation)
    }

    private fun startRefreshAll(generation: Int) {
        if (disposed.get() || api.currentSession() == null) {
            refreshAllCoordinator.cancel()
            contentLoading = false
            return
        }
        appDataRefresher.refresh(
            AppDataFallback(
                devices = devices,
                groups = groups,
                trend = dashboard.communicationTrend,
            ),
        ).whenComplete { snapshot, error ->
                mainHandler.post {
                    if (disposed.get()) return@post
                    val decision = refreshAllCoordinator.complete(generation)
                    if (decision.applyResults && api.currentSession() != null && error == null && snapshot != null) {
                        val previousGroupId = selectedGroupId
                        devices = snapshot.devices
                        groups = snapshot.groups
                        if (snapshot.defaultDeviceGroup.isSuccess) {
                            deviceManagement.applyDefaultGroup(snapshot.defaultDeviceGroup.getOrNull())
                        }
                        snapshot.user?.let {
                            user = it
                            api.acceptCurrentUser(it)
                            selectedGroupId = selectedGroupId
                                .takeIf { id -> snapshot.groups.any { group -> group.id == id } }
                                ?: it.lastGroupId.takeIf { id -> snapshot.groups.any { group -> group.id == id } }
                                ?: snapshot.groups.firstOrNull { group -> group.id == 999 }?.id
                                ?: snapshot.groups.firstOrNull()?.id
                                ?: 999
                        }
                        syncPttOverlay()
                        dashboard = DashboardData(
                            devices = snapshot.devices.size,
                            onlineDevices = snapshot.devices.count(Device::online),
                            groups = snapshot.groups.size,
                            communications = snapshot.stats?.totalCount ?: dashboard.communications,
                            communicationDurationMs = snapshot.stats?.totalDurationMs ?: dashboard.communicationDurationMs,
                            communicationTrend = snapshot.trend,
                        )
                        user?.id?.let { dashboardStore.save(it, dashboard) }
                        if (selectedGroupId != previousGroupId && page == AppPage.RADIO) {
                            loadCachedRadioMessages()
                            refreshRadioData()
                        }
                    }
                    val nextGeneration = decision.nextGeneration
                    if (nextGeneration != null && api.currentSession() != null) {
                        startRefreshAll(nextGeneration)
                    } else if (decision.isIdle || api.currentSession() == null) {
                        contentLoading = false
                    }
                }
            }
    }

    fun refreshGroupOnlineCounts() {
        val userId = user?.id ?: return
        if (api.currentSession() == null || !syncingGroupCounts.compareAndSet(false, true)) return
        val generation = groupCountsGeneration.incrementAndGet()
        executor.execute {
            try {
                val stats = runCatching(api::getGroupStats).getOrDefault(emptyMap())
                if (stats.isEmpty()) return@execute
                mainHandler.post {
                    if (
                        disposed.get() || generation != groupCountsGeneration.get() ||
                        user?.id != userId || !authenticated
                    ) return@post
                    groups = groups.map { group ->
                        stats[group.id]?.let { (online, total) ->
                            group.copy(onlineCount = online, totalCount = total)
                        } ?: group
                    }
                }
            } finally {
                if (generation == groupCountsGeneration.get()) syncingGroupCounts.set(false)
            }
        }
    }

    fun discoverAccessPoints() {
        val userId = user?.id ?: return
        if (api.currentSession() == null || selectingAccessPoint) return
        val generation = accessPointDiscoveryGeneration.incrementAndGet()
        selectingAccessPoint = true
        executor.execute {
            val discovered = runCatching(api::getAccessPoints).getOrElse {
                derivedAccessPoint()?.let(::listOf).orEmpty()
            }
            if (discovered.isEmpty()) {
                mainHandler.post {
                    if (
                        disposed.get() || generation != accessPointDiscoveryGeneration.get() ||
                        user?.id != userId || !authenticated
                    ) return@post
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
                if (
                    disposed.get() || generation != accessPointDiscoveryGeneration.get() ||
                    user?.id != userId || !authenticated
                ) return@post
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

    fun publicProfile(username: String): User? = publicProfiles[username.lowercase()]

    fun toggleMuted() {
        muted = !muted
        sessionStore.setMuted(muted)
        serviceBinder?.setMuted(muted)
    }

    fun canDrawPttOverlay(): Boolean = Settings.canDrawOverlays(appContext)

    fun setPttOverlayEnabled(enabled: Boolean): Boolean {
        if (enabled && user?.isApproved != true) {
            notice = "账号审核通过后才能开启悬浮 PTT"
            return false
        }
        if (enabled && !canDrawPttOverlay()) return false
        pttOverlayEnabled = enabled
        sessionStore.setPttOverlayEnabled(enabled)
        syncPttOverlay()
        return true
    }

    fun reconcilePttOverlayPermission() {
        if (pttOverlayEnabled && !canDrawPttOverlay()) {
            pttOverlayEnabled = false
            sessionStore.setPttOverlayEnabled(false)
        }
        syncPttOverlay()
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        appInForeground = inForeground
        syncPttOverlay()
    }

    fun switchGroup(group: Group) {
        if (group.id == selectedGroupId) return
        val userId = user?.id ?: return
        val switchGeneration = groupSwitchGeneration.incrementAndGet()
        radioDataGeneration.incrementAndGet()
        syncingRadioData.set(false)
        val previousGroupId = selectedGroupId
        selectedGroupId = group.id
        user?.let { sessionStore.setSelectedGroupId(it.id, group.id) }
        serviceBinder?.setGroup(group.id)
        syncPttOverlay()
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
                        if (
                            disposed.get() || switchGeneration != groupSwitchGeneration.get() ||
                            selectedGroupId != group.id || user?.id != userId || !authenticated
                        ) return@post
                        replaceRadioMessages(cachedMessages, preserveUnsynced = true)
                        onlineDevices = online
                        contentLoading = false
                        notice = "已切换到 ${group.name}"
                    }
                    preloadPublicProfiles(history.map(CommunicationRecord::username))
                }
                .onFailure { error ->
                    mainHandler.post {
                        if (
                            disposed.get() || switchGeneration != groupSwitchGeneration.get() ||
                            selectedGroupId != group.id || user?.id != userId || !authenticated
                        ) return@post
                        selectedGroupId = previousGroupId
                        user?.let { sessionStore.setSelectedGroupId(it.id, previousGroupId) }
                        serviceBinder?.setGroup(previousGroupId)
                        syncPttOverlay()
                        radioMessages.clear()
                        loadCachedRadioMessages(previousGroupId)
                        contentLoading = false
                        notice = friendlyError(error)
                    }
                }
        }
    }

    fun refreshRadioData() {
        val userId = user?.id ?: return
        if (api.currentSession() == null || !syncingRadioData.compareAndSet(false, true)) return
        val generation = radioDataGeneration.incrementAndGet()
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
                    if (
                        disposed.get() || generation != radioDataGeneration.get() ||
                        groupId != selectedGroupId || user?.id != userId || !authenticated
                    ) return@post
                    replaceRadioMessages(cachedMessages, preserveUnsynced = true)
                    onlineDevices = online
                }
                preloadPublicProfiles(history.map(CommunicationRecord::username) + online.map(OnlineDevice::username))
            } finally {
                if (generation == radioDataGeneration.get()) syncingRadioData.set(false)
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
        refreshAllCoordinator.cancel()
        invalidateBackgroundRequests()
        tools.close()
        deviceManagement.close()
        groupManagement.close()
        profile.close()
        publicAuth.close()
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
        dashboard = dashboardStore.load(stored.user.id) ?: DashboardData()
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
                        page = authenticatedStartPage(session.user.isApproved)
                        manualRadioDisconnect = false
                        syncPttOverlay()
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
                        syncPttOverlay()
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

    private fun syncPttOverlay() {
        val enabled = pttOverlayEnabled &&
            authenticated &&
            user?.isApproved == true &&
            canDrawPttOverlay()
        serviceBinder?.configurePttOverlay(
            enabled = enabled,
            visible = enabled && !appInForeground,
            groupName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "群组 $selectedGroupId",
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
        val userId = user?.id ?: return
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
                        mainHandler.post {
                            if (!disposed.get() && authenticated && user?.id == userId) {
                                publicProfiles = publicProfiles + (key to profile)
                            }
                        }
                    }
                }
        }
    }

    private fun invalidateBackgroundRequests() {
        groupCountsGeneration.incrementAndGet()
        accessPointDiscoveryGeneration.incrementAndGet()
        radioDataGeneration.incrementAndGet()
        groupSwitchGeneration.incrementAndGet()
        syncingGroupCounts.set(false)
        syncingRadioData.set(false)
        selectingAccessPoint = false
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
