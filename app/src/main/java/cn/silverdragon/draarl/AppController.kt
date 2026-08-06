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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import coil3.SingletonImageLoader
import cn.silverdragon.draarl.auth.PublicAuthController
import cn.silverdragon.draarl.aprs.AprsConfig
import cn.silverdragon.draarl.aprs.AprsConfigStore
import cn.silverdragon.draarl.aprs.AprsConnectionState
import cn.silverdragon.draarl.aprs.AprsIsClient
import cn.silverdragon.draarl.aprs.AprsPosition
import cn.silverdragon.draarl.aprs.AprsService
import cn.silverdragon.draarl.aprs.AprsStatus
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.ApiAppDataSource
import cn.silverdragon.draarl.data.AppDataFallback
import cn.silverdragon.draarl.data.AppDataRefresher
import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import cn.silverdragon.draarl.data.ChannelMessage
import cn.silverdragon.draarl.data.ChannelMessageMapper
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
import cn.silverdragon.draarl.data.RadioRouting
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.ServerTimeParser
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.data.StorageUsage
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.VoicePlaybackQueue
import cn.silverdragon.draarl.devices.DeviceManagementController
import cn.silverdragon.draarl.groups.GroupManagementController
import cn.silverdragon.draarl.network.ApiClient
import cn.silverdragon.draarl.network.ApiException
import cn.silverdragon.draarl.profile.ProfileController
import cn.silverdragon.draarl.radio.AccessPointProbe
import cn.silverdragon.draarl.radio.AccessPointSelector
import cn.silverdragon.draarl.radio.PlaybackDenoiseState
import cn.silverdragon.draarl.radio.PLAYBACK_DENOISE_MAX_STRENGTH_PERCENT
import cn.silverdragon.draarl.radio.PLAYBACK_DENOISE_MIN_STRENGTH_PERCENT
import cn.silverdragon.draarl.radio.RadioConnectionConfig
import cn.silverdragon.draarl.radio.RadioConnectionService
import cn.silverdragon.draarl.radio.RadioServiceListener
import cn.silverdragon.draarl.radio.TransmitTailTone
import cn.silverdragon.draarl.radio.denoiseStrengthPercentToWetMix
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.update.AppUpdateInfo
import cn.silverdragon.draarl.update.AppUpdateInstallPermissionException
import cn.silverdragon.draarl.update.AppUpdateManager
import cn.silverdragon.draarl.update.AppUpdateStatus
import java.net.URI
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private data class RadioHistoryState(
    val nextCursor: String = "",
    val hasMore: Boolean = true,
    val dataGeneration: Int = 0,
)

private data class RadioSyncSnapshot(
    val messages: List<RadioMessage>,
    val hasMoreHistory: Boolean,
)

private data class OlderRadioHistoryResult(
    val messages: List<RadioMessage>,
    val hasMore: Boolean,
)

class AppController(application: Application) : AndroidViewModel(application), RadioServiceListener {
    private val appContext = application.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "draarl-app-worker")
    }
    private val sessionStore = SecureSessionStore(appContext)
    private val aprsConfigStore = AprsConfigStore(appContext)
    private val aprsClient = AprsIsClient()
    private val messageStore = RadioMessageStore(appContext)
    private val dashboardStore = DashboardCacheStore(appContext)
    private var serviceBinder: RadioConnectionService.LocalBinder? = null
    private var serviceBound = false
    private var pendingConnection: RadioConnectionConfig? = null
    private var manualAccessPointSelection = false
    private var manualRadioDisconnect = false
    private val refreshingRadioToken = AtomicBoolean(false)
    private val syncingRadioData = AtomicBoolean(false)
    private val pendingRadioDataSync = AtomicBoolean(false)
    private val syncingGroupCounts = AtomicBoolean(false)
    private val groupCountsGeneration = AtomicInteger(0)
    private val accessPointDiscoveryGeneration = AtomicInteger(0)
    private val radioDataGeneration = AtomicInteger(0)
    private val radioCacheLoadGeneration = AtomicInteger(0)
    private val radioHistoryGeneration = AtomicInteger(0)
    private val groupSwitchGeneration = AtomicInteger(0)
    private val radioConnectionGeneration = AtomicInteger(0)
    private val preparingRadioConnection = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val sendingAprsPosition = AtomicBoolean(false)
    private val storageOperation = AtomicBoolean(false)
    private val checkingAppUpdate = AtomicBoolean(false)
    private val downloadingAppUpdate = AtomicBoolean(false)
    private val refreshAllCoordinator = RefreshCoordinator()
    private val loadingPublicProfiles = ConcurrentHashMap.newKeySet<String>()
    private val loadingCachedRadioMessages = ConcurrentHashMap.newKeySet<String>()
    private val radioHistoryStates = ConcurrentHashMap<String, RadioHistoryState>()
    private val radioVisibleLimits = ConcurrentHashMap<String, Int>()
    private val voiceAutoPlaySkippedIds = mutableSetOf<String>()
    private var voiceAutoPlayPendingMessageId: String? = null
    private var displayedRadioMessageGroupId: Int? = null
    private val voiceAutoPlayAdvance = Runnable { advanceVoiceAutoPlay() }
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
    private val appUpdateManager by lazy { AppUpdateManager(appContext, api) }
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
    var transmitGroupId by mutableIntStateOf(999)
        private set
    var receiveGroupIds by mutableStateOf<Set<Int>>(setOf(999))
        private set
    var radioRoutingUpdating by mutableStateOf(false)
        private set

    var radioStatus by mutableStateOf(RadioStatus())
        private set
    val radioMessages = mutableStateListOf<RadioMessage>()
    var radioHistoryLoading by mutableStateOf(false)
        private set
    var radioHistoryHasMore by mutableStateOf(true)
        private set
    var radioSyncError by mutableStateOf("")
        private set
    var muted by mutableStateOf(sessionStore.isMuted())
        private set
    var playbackDenoiseEnabled by mutableStateOf(sessionStore.isPlaybackDenoiseEnabled())
        private set
    var playbackDenoiseStrengthPercent by mutableIntStateOf(sessionStore.playbackDenoiseStrengthPercent())
        private set
    var playbackDenoiseState by mutableStateOf(
        if (sessionStore.isPlaybackDenoiseEnabled()) PlaybackDenoiseState.READY else PlaybackDenoiseState.DISABLED,
    )
        private set
    var playbackDenoiseMessage by mutableStateOf("")
        private set
    var pttOverlayEnabled by mutableStateOf(
        sessionStore.isPttOverlayEnabled() && Settings.canDrawOverlays(appContext),
    )
        private set
    var appDisplayScale by mutableStateOf(sessionStore.appDisplayScale())
        private set
    var appThemeMode by mutableStateOf(sessionStore.appThemeMode())
        private set
    var transmitTimeoutSeconds by mutableIntStateOf(sessionStore.transmitTimeoutSeconds())
        private set
    var transmitTailTone by mutableStateOf(sessionStore.transmitTailTone())
        private set
    var transmitTailToneToRemoteEnabled by mutableStateOf(sessionStore.isTransmitTailToneToRemoteEnabled())
        private set
    var receiveTailToneEnabled by mutableStateOf(sessionStore.isReceiveTailToneEnabled())
        private set
    var aprsConfig by mutableStateOf(AprsConfig())
        private set
    var aprsStatus by mutableStateOf(AprsStatus())
        private set
    var storageUsage by mutableStateOf(StorageUsage())
        private set
    var storageBusy by mutableStateOf(false)
        private set
    val currentAppVersionName: String get() = appUpdateManager.currentVersionName
    var autoCheckAppUpdate by mutableStateOf(sessionStore.isAutoCheckAppUpdateEnabled())
        private set
    var appUpdateStatus by mutableStateOf(AppUpdateStatus.IDLE)
        private set
    var appUpdateInfo by mutableStateOf<AppUpdateInfo?>(null)
        private set
    var appUpdateMessage by mutableStateOf("")
        private set
    var appUpdateProgress by mutableFloatStateOf(0f)
        private set
    private var appInForeground = true
    private var pendingAppUpdateInstallAfterPermission = false
    var playingMessageId by mutableStateOf<String?>(null)
        private set
    var voiceAutoPlayEnabled by mutableStateOf(false)
        private set
    val unplayedVoiceCount: Int
        get() = radioMessages.count(VoicePlaybackQueue::isUnplayed)
    var playbackLevel by mutableFloatStateOf(0f)
        private set
    var transmitLevel by mutableFloatStateOf(0f)
        private set
    var cwTransmitting by mutableStateOf(false)
        private set
    var cwPreviewing by mutableStateOf(false)
        private set
    var publicProfiles by mutableStateOf<Map<String, User>>(emptyMap())
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? RadioConnectionService.LocalBinder
            serviceBound = serviceBinder != null
            serviceBinder?.setListener(this@AppController)
            serviceBinder?.setMuted(muted)
            serviceBinder?.setPlaybackDenoiseWetMix(denoiseStrengthPercentToWetMix(playbackDenoiseStrengthPercent))
            serviceBinder?.setPlaybackDenoiseEnabled(playbackDenoiseEnabled)
            serviceBinder?.setTransmitTimeoutSeconds(transmitTimeoutSeconds)
            serviceBinder?.setTransmitTailTone(transmitTailTone)
            serviceBinder?.setTransmitTailToneToRemoteEnabled(transmitTailToneToRemoteEnabled)
            serviceBinder?.setReceiveTailToneEnabled(receiveTailToneEnabled)
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
                        aprsConfig = aprsConfigStore.load(session.user.id)
                        dashboard = dashboardStore.load(session.user.id) ?: DashboardData()
                        authenticated = true
                        loginBusy = false
                        selectedGroupId = sessionStore.selectedGroupId(
                            session.user.id,
                            session.user.lastGroupId.takeIf { it > 0 } ?: 999,
                        )
                        transmitGroupId = sessionStore.transmitGroupId(session.user.id, selectedGroupId)
                        receiveGroupIds = sessionStore.receiveGroupIds(session.user.id, transmitGroupId)
                        page = AppPage.RADIO
                        manualRadioDisconnect = false
                        syncPttOverlay()
                        if (session.user.isApproved) loadCachedRadioMessages()
                    }
                    mainHandler.post {
                        refreshAll()
                        discoverAccessPoints()
                        if (autoCheckAppUpdate) checkAppUpdate(manual = false)
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
        stopVoiceAutoPlay(stopCurrent = false)
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
        aprsConfig = AprsConfig()
        aprsStatus = AprsStatus()
        appContext.stopService(AprsService.stopIntent(appContext))
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
        radioHistoryStates.clear()
        radioVisibleLimits.clear()
        radioHistoryLoading = false
        radioHistoryHasMore = true
        radioSyncError = ""
        publicProfiles = emptyMap()
        loadingPublicProfiles.clear()
        playingMessageId = null
        playbackLevel = 0f
        transmitLevel = 0f
        cwTransmitting = false
        cwPreviewing = false
        appUpdateStatus = AppUpdateStatus.IDLE
        appUpdateInfo = null
        appUpdateMessage = ""
        appUpdateProgress = 0f
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

    fun checkAppUpdate(manual: Boolean = true) {
        if (!manual && !autoCheckAppUpdate) return
        if (api.currentSession() == null) {
            if (manual) notice = "请先登录后检查更新"
            return
        }
        if (!checkingAppUpdate.compareAndSet(false, true)) return
        appUpdateStatus = AppUpdateStatus.CHECKING
        appUpdateMessage = "正在检查客户端更新"
        appUpdateProgress = 0f
        executor.execute {
            val result = runCatching { appUpdateManager.checkForUpdate() }
            mainHandler.post {
                checkingAppUpdate.set(false)
                result
                    .onSuccess { update ->
                        if (update == null) {
                            appUpdateInfo = null
                            appUpdateStatus = AppUpdateStatus.UP_TO_DATE
                            appUpdateMessage = "当前已是最新版本"
                            if (manual) notice = "当前已是最新版本"
                        } else {
                            appUpdateInfo = update
                            appUpdateStatus = AppUpdateStatus.AVAILABLE
                            appUpdateMessage = buildString {
                                append("发现新版本 ").append(update.version)
                                if (update.forceUpdate) append("（强制更新）")
                            }
                            notice = appUpdateMessage
                        }
                    }
                    .onFailure { error ->
                        if (!manual && isClientUpdateUnsupported(error)) {
                            appUpdateStatus = AppUpdateStatus.IDLE
                            appUpdateMessage = ""
                            return@onFailure
                        }
                        appUpdateStatus = AppUpdateStatus.ERROR
                        appUpdateMessage = if (isClientUpdateUnsupported(error)) {
                            "服务器暂不支持客户端更新"
                        } else {
                            friendlyError(error)
                        }
                        if (manual) notice = appUpdateMessage
                    }
            }
        }
    }

    fun downloadAndInstallAppUpdate() {
        val update = appUpdateInfo ?: run {
            checkAppUpdate(manual = true)
            return
        }
        if (!appUpdateManager.canRequestPackageInstalls()) {
            pendingAppUpdateInstallAfterPermission = true
            appUpdateStatus = AppUpdateStatus.INSTALL_PERMISSION_REQUIRED
            appUpdateMessage = "需要允许本应用安装更新包，正在打开系统权限设置"
            notice = appUpdateMessage
            openAppUpdateInstallPermissionSettings()
            return
        }
        if (!downloadingAppUpdate.compareAndSet(false, true)) return
        pendingAppUpdateInstallAfterPermission = false
        appUpdateStatus = AppUpdateStatus.DOWNLOADING
        appUpdateMessage = "正在下载 ${update.version}"
        appUpdateProgress = 0f
        executor.execute {
            val result = runCatching {
                val apk = appUpdateManager.downloadUpdate(update) { progress ->
                    mainHandler.post {
                        if (appUpdateStatus == AppUpdateStatus.DOWNLOADING) {
                            appUpdateProgress = progress
                        }
                    }
                }
                appUpdateManager.installUpdate(apk)
            }
            mainHandler.post {
                downloadingAppUpdate.set(false)
                result
                    .onSuccess {
                        appUpdateStatus = AppUpdateStatus.READY_TO_INSTALL
                        appUpdateProgress = 1f
                        appUpdateMessage = "已打开系统安装器"
                        notice = "已打开系统安装器"
                    }
                    .onFailure { error ->
                        if (error is AppUpdateInstallPermissionException) {
                            pendingAppUpdateInstallAfterPermission = true
                            appUpdateStatus = AppUpdateStatus.INSTALL_PERMISSION_REQUIRED
                            appUpdateMessage = "需要允许本应用安装更新包，正在打开系统权限设置"
                            openAppUpdateInstallPermissionSettings()
                        } else {
                            appUpdateStatus = AppUpdateStatus.ERROR
                            appUpdateMessage = "更新失败：${friendlyError(error)}"
                        }
                        notice = appUpdateMessage
                    }
            }
        }
    }

    fun openAppUpdateInstallPermissionSettings() {
        runCatching { appUpdateManager.openInstallPermissionSettings() }
            .onFailure { notice = "无法打开安装权限设置：${friendlyError(it)}" }
    }

    fun resumePendingAppUpdateInstall() {
        if (!pendingAppUpdateInstallAfterPermission) return
        if (appUpdateInfo == null) {
            pendingAppUpdateInstallAfterPermission = false
            return
        }
        if (appUpdateManager.canRequestPackageInstalls()) {
            pendingAppUpdateInstallAfterPermission = false
            downloadAndInstallAppUpdate()
        }
    }

    fun setAutoCheckAppUpdateEnabled(enabled: Boolean) {
        if (autoCheckAppUpdate == enabled) return
        autoCheckAppUpdate = enabled
        sessionStore.setAutoCheckAppUpdateEnabled(enabled)
        if (enabled) checkAppUpdate(manual = false)
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
                RadioConnectionConfig(
                    accessPoint = point,
                    accessToken = api.freshAccessToken(),
                    clientInstanceId = sessionStore.clientInstanceId(),
                    groupId = transmitGroupId,
                )
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

    fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        stopVoiceAutoPlay(stopCurrent = true)
        if (cwPreviewing) stopCwPreview()
        if (!radioStatus.connected) {
            notice = "电台尚未连接"
            return false
        }
        if (radioStatus.transmitting || radioStatus.speaker.isNotBlank()) {
            notice = "当前信道正忙，稍后再发送 CW"
            return false
        }
        val sent = serviceBinder?.sendCw(text, wordsPerMinute, toneHz) == true
        if (sent) cwTransmitting = true
        if (!sent) notice = "CW 发送失败，请检查内容后重试"
        return sent
    }

    fun stopCw() {
        if (serviceBinder?.stopCw() == true) cwTransmitting = false
    }

    fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        stopVoiceAutoPlay(stopCurrent = true)
        if (radioStatus.transmitting || radioStatus.speaker.isNotBlank()) {
            notice = "当前信道正忙，稍后再试听 CW"
            return false
        }
        val started = serviceBinder?.previewCw(text, wordsPerMinute, toneHz) == true
        if (started) cwPreviewing = true else notice = "CW 试听失败，请检查内容后重试"
        return started
    }

    fun stopCwPreview() {
        if (serviceBinder?.stopCwPreview() == true) cwPreviewing = false
    }

    fun startPtt(): Boolean {
        val started = serviceBinder?.startPtt() == true
        if (!started && !radioStatus.connected) notice = "请先连接电台"
        return started
    }

    fun stopPtt() {
        serviceBinder?.stopPtt()
    }

    fun toggleVoicePlayback(message: RadioMessage) {
        if (voiceAutoPlayEnabled) stopVoiceAutoPlay(stopCurrent = false)
        requestVoicePlayback(message, fromAutoPlay = false)
    }

    fun toggleVoiceAutoPlay() {
        if (voiceAutoPlayEnabled) {
            stopVoiceAutoPlay(stopCurrent = true)
            return
        }
        voiceAutoPlaySkippedIds.clear()
        if (VoicePlaybackQueue.nextUnplayed(radioMessages) == null) {
            notice = "当前没有可连续播放的未听语音"
            return
        }
        stopCwPreview()
        voiceAutoPlayEnabled = true
        scheduleVoiceAutoPlayAdvance(delayMs = 0L)
    }

    fun stopVoiceAutoPlay() {
        stopVoiceAutoPlay(stopCurrent = true)
    }

    fun clearUnplayedVoiceMessages() {
        if (unplayedVoiceCount == 0) return
        stopVoiceAutoPlay(stopCurrent = true)
        radioMessages.indices.forEach { index ->
            val message = radioMessages[index]
            if (VoicePlaybackQueue.isUnplayed(message)) {
                radioMessages[index] = message.copy(played = true)
            }
        }
        val accountKey = messageAccountKey() ?: return
        val groupId = selectedGroupId
        val cacheGeneration = messageStore.generation()
        executor.execute {
            messageStore.markAllPlayed(
                accountKey = accountKey,
                groupId = groupId,
                expectedGeneration = cacheGeneration,
            )
        }
    }

    private fun stopVoiceAutoPlay(stopCurrent: Boolean) {
        voiceAutoPlayEnabled = false
        voiceAutoPlayPendingMessageId = null
        voiceAutoPlaySkippedIds.clear()
        mainHandler.removeCallbacks(voiceAutoPlayAdvance)
        if (stopCurrent && playingMessageId != null) serviceBinder?.stopPlayback()
    }

    private fun scheduleVoiceAutoPlayAdvance(delayMs: Long = VOICE_AUTO_PLAY_ADVANCE_DELAY_MS) {
        mainHandler.removeCallbacks(voiceAutoPlayAdvance)
        if (voiceAutoPlayEnabled) mainHandler.postDelayed(voiceAutoPlayAdvance, delayMs)
    }

    private fun advanceVoiceAutoPlay() {
        if (!voiceAutoPlayEnabled || disposed.get()) return
        if (
            playingMessageId != null || voiceAutoPlayPendingMessageId != null ||
            radioStatus.transmitting || radioStatus.speaker.isNotBlank()
        ) return
        val next = VoicePlaybackQueue.nextUnplayed(radioMessages, voiceAutoPlaySkippedIds)
        if (next == null) {
            voiceAutoPlayEnabled = false
            voiceAutoPlaySkippedIds.clear()
            notice = "未听语音已连续播放完毕"
            return
        }
        voiceAutoPlayPendingMessageId = next.id
        requestVoicePlayback(next, fromAutoPlay = true)
    }

    private fun requestVoicePlayback(message: RadioMessage, fromAutoPlay: Boolean) {
        if (!fromAutoPlay) stopCwPreview()
        val playableMessage = withStableAudioCacheKey(message)
        if (!VoicePlaybackQueue.isPlayable(playableMessage)) {
            handleVoicePlaybackFailure(playableMessage, fromAutoPlay, "这条语音暂时还没有可回放的数据")
            return
        }
        val cacheKey = playableMessage.audioCacheKey
        if (cacheKey.isNotBlank() && serviceBinder?.hasAudioCacheKey(cacheKey) == true) {
            playVoiceMessage(playableMessage, fromAutoPlay)
            return
        }
        if (playableMessage.serverRecordId != null && playableMessage.syncState == RadioMessageSyncState.CONFIRMED) {
            refreshServerVoiceUrlAndPlay(playableMessage, fromAutoPlay)
            return
        }
        playVoiceMessage(playableMessage, fromAutoPlay)
    }

    private fun withStableAudioCacheKey(message: RadioMessage): RadioMessage {
        val recordId = message.serverRecordId ?: return message
        if (message.audioCacheKey.isNotBlank()) return message
        return message.copy(audioCacheKey = "record:$recordId")
    }

    private fun playVoiceMessage(message: RadioMessage, fromAutoPlay: Boolean) {
        if (serviceBinder?.togglePlayback(message) == true) {
            if (fromAutoPlay) voiceAutoPlayPendingMessageId = null
            markVoiceMessagePlayed(message)
        } else {
            handleVoicePlaybackFailure(message, fromAutoPlay, "无法播放这条语音")
        }
    }

    private fun refreshServerVoiceUrlAndPlay(message: RadioMessage, fromAutoPlay: Boolean) {
        val recordId = message.serverRecordId ?: run {
            playVoiceMessage(message, fromAutoPlay)
            return
        }
        val requestedGroupId = selectedGroupId
        val accountUser = user ?: run {
            playVoiceMessage(message, fromAutoPlay)
            return
        }
        executor.execute {
            val result = runCatching {
                val refreshed = channelMessageToRadio(api.getGroupMessage(requestedGroupId, recordId), accountUser)
                    ?: error("服务器没有返回可播放的语音记录")
                withStableAudioCacheKey(
                    message.copy(
                        audioUrl = refreshed.audioUrl,
                        durationMs = refreshed.durationMs.takeIf { it > 0 } ?: message.durationMs,
                        content = refreshed.content.ifBlank { message.content },
                    ),
                )
            }
            mainHandler.post {
                result
                    .onSuccess { refreshed ->
                        applyRefreshedRadioMessage(refreshed)
                        if (!fromAutoPlay || voiceAutoPlayEnabled && voiceAutoPlayPendingMessageId == message.id) {
                            playVoiceMessage(refreshed, fromAutoPlay)
                        }
                    }
                    .onFailure { error ->
                        if (message.audioUrl.isNotBlank()) {
                            playVoiceMessage(message, fromAutoPlay)
                        } else {
                            handleVoicePlaybackFailure(message, fromAutoPlay, friendlyError(error))
                        }
                    }
            }
        }
    }

    private fun applyRefreshedRadioMessage(message: RadioMessage) {
        val index = radioMessages.indexOfFirst { existing ->
            existing.id == message.id ||
                (message.serverRecordId != null && existing.serverRecordId == message.serverRecordId)
        }
        if (index >= 0) radioMessages[index] = message
        val accountKey = messageAccountKey() ?: return
        val cacheGeneration = messageStore.generation()
        val groupId = if (message.groupId > 0) message.groupId else selectedGroupId
        executor.execute {
            runCatching {
                messageStore.save(
                    accountKey,
                    groupId,
                    message,
                    expectedGeneration = cacheGeneration,
                )
            }
        }
    }

    private fun handleVoicePlaybackFailure(message: RadioMessage, fromAutoPlay: Boolean, error: String) {
        if (!fromAutoPlay) {
            notice = error
            return
        }
        voiceAutoPlayPendingMessageId = null
        voiceAutoPlaySkippedIds += message.id
        scheduleVoiceAutoPlayAdvance()
    }

    private fun markVoiceMessagePlayed(message: RadioMessage) {
        if (message.type != RadioMessageType.VOICE || message.played) return
        val index = radioMessages.indexOfFirst { existing ->
            existing.id == message.id ||
                (message.serverRecordId != null && existing.serverRecordId == message.serverRecordId)
        }
        if (index >= 0 && !radioMessages[index].played) {
            radioMessages[index] = radioMessages[index].copy(played = true)
        }
        val accountKey = messageAccountKey() ?: return
        val groupId = if (message.groupId > 0) message.groupId else selectedGroupId
        val cacheGeneration = messageStore.generation()
        executor.execute {
            messageStore.markPlayed(
                accountKey = accountKey,
                groupId = groupId,
                localId = message.id,
                serverRecordId = message.serverRecordId,
                expectedGeneration = cacheGeneration,
            )
        }
    }

    fun publicProfile(username: String): User? = publicProfiles[username.lowercase()]

    fun toggleMuted() {
        muted = !muted
        sessionStore.setMuted(muted)
        serviceBinder?.setMuted(muted)
    }

    fun togglePlaybackDenoise() {
        playbackDenoiseEnabled = !playbackDenoiseEnabled
        sessionStore.setPlaybackDenoiseEnabled(playbackDenoiseEnabled)
        serviceBinder?.setPlaybackDenoiseWetMix(denoiseStrengthPercentToWetMix(playbackDenoiseStrengthPercent))
        serviceBinder?.setPlaybackDenoiseEnabled(playbackDenoiseEnabled)
        if (playbackDenoiseEnabled) {
            playbackDenoiseState = PlaybackDenoiseState.READY
            playbackDenoiseMessage = "神经网络降噪已开启"
        } else {
            playbackDenoiseState = PlaybackDenoiseState.DISABLED
            playbackDenoiseMessage = ""
        }
    }

    fun updatePlaybackDenoiseStrengthPercent(percent: Int) {
        val normalized = percent.coerceIn(
            PLAYBACK_DENOISE_MIN_STRENGTH_PERCENT,
            PLAYBACK_DENOISE_MAX_STRENGTH_PERCENT,
        )
        if (playbackDenoiseStrengthPercent == normalized) return
        playbackDenoiseStrengthPercent = normalized
        sessionStore.setPlaybackDenoiseStrengthPercent(normalized)
        serviceBinder?.setPlaybackDenoiseWetMix(denoiseStrengthPercentToWetMix(normalized))
        if (playbackDenoiseEnabled) {
            playbackDenoiseMessage = "神经网络降噪强度 $normalized%"
        }
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

    fun updateAppDisplayScale(scale: AppDisplayScale) {
        if (appDisplayScale == scale) return
        appDisplayScale = scale
        sessionStore.setAppDisplayScale(scale)
    }

    fun updateAppThemeMode(mode: AppThemeMode) {
        if (appThemeMode == mode) return
        appThemeMode = mode
        sessionStore.setAppThemeMode(mode)
    }

    fun updateTransmitTimeoutSeconds(seconds: Int) {
        val normalized = seconds.coerceIn(MIN_TRANSMIT_TIMEOUT_SECONDS, MAX_TRANSMIT_TIMEOUT_SECONDS)
        if (transmitTimeoutSeconds == normalized) return
        transmitTimeoutSeconds = normalized
        sessionStore.setTransmitTimeoutSeconds(normalized)
        serviceBinder?.setTransmitTimeoutSeconds(normalized)
    }

    fun updateTransmitTailTone(tone: TransmitTailTone) {
        if (transmitTailTone == tone) return
        transmitTailTone = tone
        sessionStore.setTransmitTailTone(tone)
        serviceBinder?.setTransmitTailTone(tone)
    }

    fun updateTransmitTailToneToRemoteEnabled(enabled: Boolean) {
        if (transmitTailToneToRemoteEnabled == enabled) return
        transmitTailToneToRemoteEnabled = enabled
        sessionStore.setTransmitTailToneToRemoteEnabled(enabled)
        serviceBinder?.setTransmitTailToneToRemoteEnabled(enabled)
    }

    fun updateReceiveTailToneEnabled(enabled: Boolean) {
        if (receiveTailToneEnabled == enabled) return
        receiveTailToneEnabled = enabled
        sessionStore.setReceiveTailToneEnabled(enabled)
        serviceBinder?.setReceiveTailToneEnabled(enabled)
    }

    fun updateAprsConfig(config: AprsConfig) {
        val normalized = config.copy(
            server = config.server.trim().ifBlank { "rotate.aprs2.net" },
            port = config.port.coerceIn(1, 65_535),
            callsign = config.callsign.trim().uppercase(),
            movingIntervalSeconds = config.movingIntervalSeconds.coerceIn(60, 600),
            stationaryIntervalSeconds = config.stationaryIntervalSeconds.coerceIn(60, 3_600),
        )
        aprsConfig = normalized
        user?.id?.let { aprsConfigStore.save(it, normalized) }
        val currentUserId = user?.id
        if (normalized.enabled && normalized.autoReport && currentUserId != null) {
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    AprsService.startIntent(appContext, currentUserId),
                )
            }.onFailure { notice = "无法启动 APRS 后台上报：${friendlyError(it)}" }
        } else {
            appContext.stopService(AprsService.stopIntent(appContext))
        }
    }

    fun sendAprsPosition(position: AprsPosition): Boolean {
        val config = aprsConfig
        if (!config.enabled) {
            notice = "请先在设置中启用 APRS"
            return false
        }
        if (!sendingAprsPosition.compareAndSet(false, true)) {
            notice = "APRS 位置正在发送"
            return false
        }
        aprsStatus = AprsStatus(AprsConnectionState.CONNECTING, "正在连接 APRS-IS")
        executor.execute {
            runCatching {
                mainHandler.post { aprsStatus = AprsStatus(AprsConnectionState.SENDING, "正在发送 APRS 位置") }
                kotlinx.coroutines.runBlocking { aprsClient.sendPosition(config, position) }
            }.onSuccess {
                mainHandler.post {
                    aprsStatus = AprsStatus(
                        state = AprsConnectionState.SENT,
                        message = "APRS 位置已发送",
                        lastSentAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
                mainHandler.post {
                    aprsStatus = AprsStatus(AprsConnectionState.ERROR, error.message ?: "APRS 发送失败")
                    notice = error.message ?: "APRS 发送失败"
                }
            }
            sendingAprsPosition.set(false)
        }
        return true
    }

    fun refreshStorageUsage() {
        if (!storageOperation.compareAndSet(false, true)) return
        storageBusy = true
        executor.execute {
            val usage = calculateStorageUsage()
            mainHandler.post {
                storageUsage = usage
                storageBusy = false
                storageOperation.set(false)
            }
        }
    }

    fun clearStorage(category: StorageCategory) {
        if (!storageOperation.compareAndSet(false, true)) return
        storageBusy = true
        executor.execute {
            runCatching {
                if (category == StorageCategory.AUDIO || category == StorageCategory.ALL) {
                    serviceBinder?.clearAudioCache()
                        ?: clearDirectoryContents(File(appContext.filesDir, AUDIO_CACHE_DIRECTORY))
                }
                if (category == StorageCategory.AVATARS || category == StorageCategory.ALL) {
                    val imageLoader = SingletonImageLoader.get(appContext)
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                }
                if (category == StorageCategory.MESSAGES || category == StorageCategory.ALL) {
                    mainHandler.post { stopVoiceAutoPlay(stopCurrent = true) }
                    radioDataGeneration.incrementAndGet()
                    radioHistoryGeneration.incrementAndGet()
                    messageStore.clearAll()
                    mainHandler.post {
                        radioMessages.clear()
                        radioHistoryStates.clear()
                        radioVisibleLimits.clear()
                        displayedRadioMessageGroupId = null
                        radioHistoryLoading = false
                        radioHistoryHasMore = true
                        radioSyncError = ""
                    }
                }
            }.onFailure { error ->
                mainHandler.post { notice = "清理缓存失败：${friendlyError(error)}" }
            }
            val usage = calculateStorageUsage()
            mainHandler.post {
                storageUsage = usage
                storageBusy = false
                storageOperation.set(false)
            }
        }
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
        if (inForeground) resumePendingAppUpdateInstall()
        syncPttOverlay()
    }

    fun switchGroup(group: Group) {
        if (group.id == selectedGroupId) return
        stopVoiceAutoPlay(stopCurrent = true)
        user?.id ?: return
        groupSwitchGeneration.incrementAndGet()
        radioDataGeneration.incrementAndGet()
        radioCacheLoadGeneration.incrementAndGet()
        radioHistoryGeneration.incrementAndGet()
        pendingRadioDataSync.set(false)
        selectedGroupId = group.id
        user?.let { sessionStore.setSelectedGroupId(it.id, group.id) }
        radioMessages.clear()
        displayedRadioMessageGroupId = null
        radioHistoryLoading = false
        radioHistoryHasMore = true
        radioSyncError = ""
        loadCachedRadioMessages(group.id)
        notice = "正在查看 ${group.name}"
        refreshRadioData()
    }

    fun updateRadioRouting(txGroupId: Int, rxGroupIds: Collection<Int>) {
        val sessionId = radioStatus.sessionId
        val userId = user?.id ?: return
        if (!radioStatus.connected || sessionId.isBlank()) {
            notice = "请先连接电台，再修改发送与收听频道"
            return
        }
        if (radioRoutingUpdating) return
        val routing = runCatching { RadioRouting.normalize(txGroupId, rxGroupIds) }.getOrElse { error ->
            notice = error.message ?: "发送与收听频道无效"
            return
        }
        radioRoutingUpdating = true
        executor.execute {
            val result = runCatching {
                api.updateRadioSessionRouting(sessionId, routing.txGroupId, routing.rxGroupIds)
            }
            mainHandler.post {
                radioRoutingUpdating = false
                if (disposed.get() || user?.id != userId || radioStatus.sessionId != sessionId) return@post
                result
                    .onSuccess { session ->
                        transmitGroupId = session.txGroupId
                        receiveGroupIds = session.rxGroupIds.toSet()
                        sessionStore.setRadioRouting(userId, transmitGroupId, receiveGroupIds)
                        serviceBinder?.setRouting(transmitGroupId, receiveGroupIds)
                        syncPttOverlay()
                        notice = "发送与收听频道已更新"
                    }
                    .onFailure { error -> notice = friendlyError(error) }
            }
        }
    }

    fun refreshRadioData() {
        val accountUser = user ?: return
        val userId = accountUser.id
        if (api.currentSession() == null) return
        if (!syncingRadioData.compareAndSet(false, true)) {
            pendingRadioDataSync.set(true)
            return
        }
        pendingRadioDataSync.set(false)
        val generation = radioDataGeneration.incrementAndGet()
        val groupId = selectedGroupId
        val accountKey = messageAccountKey()
        executor.execute {
            try {
                val historyResult = runCatching {
                    synchronizeLatestRadioHistory(groupId, accountKey, accountUser, generation)
                }
                val onlineResult = runCatching { api.getOnlineDevices(groupId) }
                mainHandler.post {
                    if (
                        disposed.get() || generation != radioDataGeneration.get() ||
                        groupId != selectedGroupId || user?.id != userId || !authenticated
                    ) return@post
                    historyResult
                        .onSuccess { snapshot ->
                            radioCacheLoadGeneration.incrementAndGet()
                            replaceRadioMessages(snapshot.messages, preserveUnsynced = true)
                            radioHistoryHasMore = snapshot.hasMoreHistory
                            radioSyncError = ""
                        }
                        .onFailure { error ->
                            radioSyncError = friendlyError(error)
                        }
                    onlineResult.onSuccess { onlineDevices = it }
                }
                historyResult.getOrNull()?.messages?.let { messages ->
                    preloadPublicProfiles(messages.map(RadioMessage::senderUsername))
                }
                onlineResult.getOrNull()?.let { online ->
                    preloadPublicProfiles(online.map(OnlineDevice::username))
                }
            } finally {
                syncingRadioData.set(false)
                if (pendingRadioDataSync.getAndSet(false) && !disposed.get()) {
                    mainHandler.post { refreshRadioData() }
                }
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
            radioStatus = status
            if (status.connected && status.sessionId.isNotBlank()) {
                transmitGroupId = status.groupId
                receiveGroupIds = status.receiveGroupIds.toSet().ifEmpty { setOf(status.groupId) }
                user?.id?.let { sessionStore.setRadioRouting(it, transmitGroupId, receiveGroupIds) }
            }
            if (status.speaker.isBlank() && playingMessageId == null) playbackLevel = 0f
            if (!status.transmitting) transmitLevel = 0f
            if (!status.transmitting) cwTransmitting = false
            if (status.transmitting || status.speaker.isNotBlank()) cwPreviewing = false
            if (
                voiceAutoPlayEnabled && !status.transmitting && status.speaker.isBlank() &&
                playingMessageId == null && voiceAutoPlayPendingMessageId == null
            ) {
                scheduleVoiceAutoPlayAdvance()
            }
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
            val currentClientMessage = !message.mine && currentUser != null &&
                message.senderUsername.equals(currentUser.username, ignoreCase = true) &&
                message.senderSsid == radioStatus.ssid
            val enriched = message.copy(
                mine = message.mine || currentClientMessage,
                played = message.played || message.mine || currentClientMessage ||
                    (message.type == RadioMessageType.VOICE && !muted),
                senderUsername = message.senderUsername.ifBlank {
                    if (message.mine || currentClientMessage) currentUser?.username.orEmpty() else online?.username.orEmpty()
                },
                senderNickname = message.senderNickname.ifBlank {
                    if (message.mine || currentClientMessage) currentUser?.nickname.orEmpty() else online?.nickname.orEmpty()
                },
                senderCallsign = message.senderCallsign.ifBlank {
                    if (message.mine || currentClientMessage) currentUser?.callsign.orEmpty() else online?.callsign.orEmpty()
                },
            )
            // 只有当消息属于当前群组时才添加到列表
            val messageGroupId = if (enriched.groupId > 0) enriched.groupId else selectedGroupId
            var messageToStore = enriched
            if (messageGroupId == selectedGroupId) {
                displayedRadioMessageGroupId = selectedGroupId
                val duplicateIndex = radioMessages.indexOfLast { existing ->
                    RadioMessageReconciler.isDuplicateLiveDelivery(existing, enriched)
                }
                if (duplicateIndex >= 0) {
                    val existing = radioMessages[duplicateIndex]
                    messageToStore = existing.copy(
                        senderUsername = existing.senderUsername.ifBlank { enriched.senderUsername },
                        senderNickname = existing.senderNickname.ifBlank { enriched.senderNickname },
                        senderCallsign = existing.senderCallsign.ifBlank { enriched.senderCallsign },
                        audioCacheKey = existing.audioCacheKey.ifBlank { enriched.audioCacheKey },
                        durationMs = maxOf(existing.durationMs, enriched.durationMs),
                    )
                    radioMessages[duplicateIndex] = messageToStore
                } else {
                    radioMessages += enriched
                }
                while (radioMessages.size > MAX_MESSAGES) radioMessages.removeAt(0)
                if (voiceAutoPlayEnabled && playingMessageId == null && voiceAutoPlayPendingMessageId == null) {
                    scheduleVoiceAutoPlayAdvance()
                }
            }
            val accountKey = messageAccountKey()
            if (accountKey != null) {
                val cachedMessage = messageToStore
                val cacheGeneration = messageStore.generation()
                executor.execute {
                    runCatching {
                        messageStore.save(
                            accountKey,
                            messageGroupId,
                            cachedMessage,
                            expectedGeneration = cacheGeneration,
                        )
                    }
                }
            }
            preloadPublicProfiles(listOf(enriched.senderUsername))
        }
    }

    override fun onPlaybackState(messageId: String?) {
        mainHandler.post {
            val previousMessageId = playingMessageId
            playingMessageId = messageId
            if (messageId == null && radioStatus.speaker.isBlank()) playbackLevel = 0f
            if (messageId == null) cwPreviewing = false
            if (
                voiceAutoPlayEnabled && messageId == null && previousMessageId != null &&
                voiceAutoPlayPendingMessageId == null
            ) {
                scheduleVoiceAutoPlayAdvance()
            }
        }
    }

    override fun onPlaybackLevel(level: Float) {
        mainHandler.post { playbackLevel = level.coerceIn(0f, 1f) }
    }

    override fun onTransmitLevel(level: Float) {
        mainHandler.post { transmitLevel = level.coerceIn(0f, 1f) }
    }

    override fun onCwPreviewState(active: Boolean) {
        mainHandler.post { cwPreviewing = active }
    }

    override fun onCleared() {
        if (!disposed.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(voiceAutoPlayAdvance)
        refreshAllCoordinator.cancel()
        invalidateBackgroundRequests()
        tools.close()
        deviceManagement.close()
        groupManagement.close()
        profile.close()
        publicAuth.close()
        appContext.stopService(AprsService.stopIntent(appContext))
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
        aprsConfig = aprsConfigStore.load(stored.user.id)
        dashboard = dashboardStore.load(stored.user.id) ?: DashboardData()
        executor.execute {
            runCatching(api::restoreAndValidate)
                .onSuccess { session ->
                    mainHandler.post {
                        user = session.user
                        aprsConfig = aprsConfigStore.load(session.user.id)
                        authenticated = true
                        initializing = false
                        selectedGroupId = sessionStore.selectedGroupId(
                            session.user.id,
                            session.user.lastGroupId.takeIf { it > 0 } ?: 999,
                        )
                        transmitGroupId = sessionStore.transmitGroupId(session.user.id, selectedGroupId)
                        receiveGroupIds = sessionStore.receiveGroupIds(session.user.id, transmitGroupId)
                        page = AppPage.RADIO
                        manualRadioDisconnect = false
                        syncPttOverlay()
                        if (session.user.isApproved) loadCachedRadioMessages()
                    }
                    mainHandler.post {
                        refreshAll()
                        discoverAccessPoints()
                        if (autoCheckAppUpdate) checkAppUpdate(manual = false)
                        if (session.user.isApproved) refreshRadioData()
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        initializing = false
                        authenticated = false
                        user = null
                        syncPttOverlay()
                        loginError = if (error is ApiException && error.code == 403) {
                            friendlyError(error)
                        } else {
                            "登录状态已过期，请重新登录"
                        }
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
            groupName = groups.firstOrNull { it.id == transmitGroupId }?.name ?: "群组 $transmitGroupId",
        )
    }

    private fun calculateStorageUsage(): StorageUsage {
        return StorageUsage(
            audioBytes = File(appContext.filesDir, AUDIO_CACHE_DIRECTORY).directorySizeBytes(),
            avatarBytes = File(appContext.cacheDir, AVATAR_CACHE_DIRECTORY).directorySizeBytes(),
            messageBytes = messageStore.sizeBytes(),
        )
    }

    private fun File.directorySizeBytes(): Long = if (!exists()) 0L else walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)

    private fun clearDirectoryContents(directory: File) {
        directory.listFiles()?.forEach { it.deleteRecursively() }
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
        val cacheGeneration = radioCacheLoadGeneration.incrementAndGet()
        val visibleLimit = radioVisibleLimits.getOrDefault(loadKey, INITIAL_VISIBLE_MESSAGES)
        executor.execute {
            val cachedMessages = runCatching { messageStore.load(accountKey, groupId, visibleLimit) }.getOrDefault(emptyList())
            mainHandler.post {
                loadingCachedRadioMessages.remove(loadKey)
                if (
                    cacheGeneration == radioCacheLoadGeneration.get() &&
                    groupId == selectedGroupId && accountKey == messageAccountKey()
                ) {
                    displayedRadioMessageGroupId = groupId
                    replaceRadioMessages(cachedMessages, preserveUnsynced = true)
                    radioHistoryHasMore = cachedMessages.size >= visibleLimit ||
                        radioHistoryStates[loadKey]
                            ?.takeIf { it.dataGeneration == radioDataGeneration.get() }
                            ?.hasMore != false
                }
            }
        }
    }

    fun loadOlderRadioMessages() {
        val accountKey = messageAccountKey() ?: return
        val groupId = selectedGroupId
        val accountUser = user ?: return
        if (radioHistoryLoading || !radioHistoryHasMore) return
        val stateKey = "$accountKey#$groupId"
        val dataGeneration = radioDataGeneration.get()
        val previousLimit = radioVisibleLimits.getOrDefault(stateKey, INITIAL_VISIBLE_MESSAGES)
        if (previousLimit >= MAX_MESSAGES) {
            radioHistoryHasMore = false
            return
        }
        val requestedLimit = (previousLimit + HISTORY_LOAD_BATCH).coerceAtMost(MAX_MESSAGES)
        radioHistoryLoading = true
        val requestGeneration = radioHistoryGeneration.incrementAndGet()
        executor.execute {
            val result = runCatching {
                var historyState = radioHistoryStates[stateKey]
                    ?.takeIf { it.dataGeneration == dataGeneration }
                    ?: RadioHistoryState(dataGeneration = dataGeneration)
                var cachedMessages = messageStore.load(accountKey, groupId, requestedLimit)
                var pagesLoaded = 0
                while (
                    cachedMessages.size <= previousLimit && historyState.hasMore &&
                    pagesLoaded < MAX_HISTORY_PAGES_PER_LOAD
                ) {
                    check(requestGeneration == radioHistoryGeneration.get())
                    check(dataGeneration == radioDataGeneration.get())
                    val page = api.getGroupMessages(
                        groupId = groupId,
                        cursor = historyState.nextCursor,
                    )
                    val remoteMessages = page.messages.mapNotNull { channelMessageToRadio(it, accountUser) }
                    messageStore.reconcile(accountKey, groupId, remoteMessages)
                    val nextCursor = page.nextCursor
                    historyState = RadioHistoryState(
                        nextCursor = nextCursor,
                        hasMore = page.hasMore && nextCursor.isNotBlank() && nextCursor != historyState.nextCursor,
                        dataGeneration = dataGeneration,
                    )
                    check(requestGeneration == radioHistoryGeneration.get())
                    check(dataGeneration == radioDataGeneration.get())
                    radioHistoryStates[stateKey] = historyState
                    pagesLoaded++
                    cachedMessages = messageStore.load(accountKey, groupId, requestedLimit)
                    if (page.messages.isEmpty()) break
                }
                radioVisibleLimits[stateKey] = requestedLimit
                OlderRadioHistoryResult(
                    messages = cachedMessages,
                    hasMore = requestedLimit < MAX_MESSAGES &&
                        (cachedMessages.size >= requestedLimit || historyState.hasMore),
                )
            }
            mainHandler.post {
                if (requestGeneration != radioHistoryGeneration.get()) return@post
                radioHistoryLoading = false
                if (groupId != selectedGroupId || accountKey != messageAccountKey() || !authenticated) return@post
                result
                    .onSuccess { history ->
                        radioCacheLoadGeneration.incrementAndGet()
                        replaceRadioMessages(history.messages, preserveUnsynced = true)
                        radioHistoryHasMore = history.hasMore
                        radioSyncError = ""
                        preloadPublicProfiles(history.messages.map(RadioMessage::senderUsername))
                    }
                    .onFailure { error -> radioSyncError = friendlyError(error) }
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
            val localSettleCutoff = System.currentTimeMillis() - RadioMessageReconciler.REMOTE_SETTLE_DELAY_MS
            val pending = radioMessages.filter { local ->
                local.syncState == RadioMessageSyncState.LOCAL &&
                    RadioMessageReconciler.isStillSettling(
                        local.timestamp,
                        local.durationMs,
                        localSettleCutoff,
                    ) &&
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

    private fun synchronizeLatestRadioHistory(
        groupId: Int,
        accountKey: String?,
        accountUser: User,
        expectedGeneration: Int,
    ): RadioSyncSnapshot {
        val now = System.currentTimeMillis()
        check(expectedGeneration == radioDataGeneration.get())
        val page = api.getGroupMessages(groupId = groupId)
        val remoteMessages = page.messages
            .mapNotNull { channelMessageToRadio(it, accountUser) }
            .distinctBy { it.serverRecordId ?: it.id }
            .sortedBy(RadioMessage::timestamp)
        if (accountKey != null) {
            check(expectedGeneration == radioDataGeneration.get())
            val stateKey = "$accountKey#$groupId"
            val settleCutoff = now - RadioMessageReconciler.REMOTE_SETTLE_DELAY_MS
            val authoritativeWindow = when {
                remoteMessages.isNotEmpty() -> remoteMessages.first().timestamp..settleCutoff
                !page.hasMore -> 0L..settleCutoff
                else -> null
            }
            messageStore.reconcile(accountKey, groupId, remoteMessages, authoritativeWindow)
            check(expectedGeneration == radioDataGeneration.get())
            radioHistoryStates[stateKey] = RadioHistoryState(
                nextCursor = page.nextCursor,
                hasMore = page.hasMore && page.nextCursor.isNotBlank(),
                dataGeneration = expectedGeneration,
            )
            val visibleLimit = radioVisibleLimits.getOrDefault(stateKey, INITIAL_VISIBLE_MESSAGES)
            val cachedMessages = messageStore.load(accountKey, groupId, visibleLimit + 1)
            return RadioSyncSnapshot(
                messages = cachedMessages.takeLast(visibleLimit),
                hasMoreHistory = visibleLimit < MAX_MESSAGES &&
                    (cachedMessages.size > visibleLimit || page.hasMore),
            )
        }
        return RadioSyncSnapshot(
            messages = remoteMessages.takeLast(INITIAL_VISIBLE_MESSAGES),
            hasMoreHistory = page.hasMore || remoteMessages.size > INITIAL_VISIBLE_MESSAGES,
        )
    }

    private fun channelMessageToRadio(message: ChannelMessage, accountUser: User): RadioMessage? {
        val timestamp = parseServerTime(message.sentAt) ?: return null
        return ChannelMessageMapper.toRadioMessage(message, accountUser, timestamp)
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
        radioCacheLoadGeneration.incrementAndGet()
        radioHistoryGeneration.incrementAndGet()
        groupSwitchGeneration.incrementAndGet()
        syncingGroupCounts.set(false)
        pendingRadioDataSync.set(false)
        radioHistoryLoading = false
        selectingAccessPoint = false
    }

    private fun parseServerTime(value: String): Long? = ServerTimeParser.parseMillis(value)

    private fun isClientUpdateUnsupported(error: Throwable): Boolean =
        error is ApiException && error.code in setOf(404, 405)

    private fun friendlyError(error: Throwable): String = when (error) {
        is ApiException -> error.message
        else -> error.message ?: "操作失败，请稍后重试"
    }

    companion object {
        private const val DEFAULT_UDP_PORT = 60_050
        private const val ANDROID_CLIENT_SSID = 101
        private const val RADIO_SYNC_INTERVAL_MS = 20_000L
        private const val ACCESS_POINT_PROBE_INTERVAL_MS = 10_000L
        private const val INITIAL_VISIBLE_MESSAGES = 200
        private const val HISTORY_LOAD_BATCH = 100
        private const val MAX_HISTORY_PAGES_PER_LOAD = 5
        private const val MAX_MESSAGES = 1_000
        private const val VOICE_AUTO_PLAY_ADVANCE_DELAY_MS = 300L
        private const val AUDIO_CACHE_DIRECTORY = "radio_audio"
        private const val AVATAR_CACHE_DIRECTORY = "avatar_images"
        private const val MIN_TRANSMIT_TIMEOUT_SECONDS = 10
        private const val MAX_TRANSMIT_TIMEOUT_SECONDS = 600
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
