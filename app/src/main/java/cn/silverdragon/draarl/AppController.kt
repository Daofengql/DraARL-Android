package cn.silverdragon.draarl

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.silverdragon.draarl.aprs.AprsConfigStore
import cn.silverdragon.draarl.aprs.AprsController
import cn.silverdragon.draarl.aprs.AprsEffects
import cn.silverdragon.draarl.aprs.AprsIsClient
import cn.silverdragon.draarl.aprs.AprsService
import cn.silverdragon.draarl.auth.PublicAuthController
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.data.ApiAppDataSource
import cn.silverdragon.draarl.data.AppDataFallback
import cn.silverdragon.draarl.data.AppDataRefresher
import cn.silverdragon.draarl.data.DashboardCacheStore
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageStore
import cn.silverdragon.draarl.data.RadioMessageSyncState
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.VoicePlaybackQueue
import cn.silverdragon.draarl.devices.DeviceManagementController
import cn.silverdragon.draarl.groups.GroupManagementController
import cn.silverdragon.draarl.network.ApiClient
import cn.silverdragon.draarl.network.ApiException
import cn.silverdragon.draarl.profile.ProfileController
import cn.silverdragon.draarl.radio.AudioLevelThrottler
import cn.silverdragon.draarl.radio.messages.ApiRadioMessageRemoteDataSource
import cn.silverdragon.draarl.radio.messages.RadioMessageAccount
import cn.silverdragon.draarl.radio.messages.RadioMessageController
import cn.silverdragon.draarl.radio.messages.RadioMessageEvent
import cn.silverdragon.draarl.radio.messages.RadioMessageIdentityContext
import cn.silverdragon.draarl.radio.messages.StoredRadioMessageCache
import cn.silverdragon.draarl.radio.session.ApiRadioSessionRemoteDataSource
import cn.silverdragon.draarl.radio.session.RadioPttOverlayConfig
import cn.silverdragon.draarl.radio.session.RadioSessionAccount
import cn.silverdragon.draarl.radio.session.RadioSessionController
import cn.silverdragon.draarl.radio.session.RadioSessionDependencies
import cn.silverdragon.draarl.radio.session.RadioSessionEffects
import cn.silverdragon.draarl.radio.session.RadioSessionExecution
import cn.silverdragon.draarl.radio.session.StoredRadioSessionStorage
import cn.silverdragon.draarl.radio.session.createAndroidRadioServiceGateway
import cn.silverdragon.draarl.session.ApiSessionRemoteDataSource
import cn.silverdragon.draarl.session.SessionController
import cn.silverdragon.draarl.session.SessionEffects
import cn.silverdragon.draarl.session.SessionEntryPoint
import cn.silverdragon.draarl.settings.AndroidSettingsStorage
import cn.silverdragon.draarl.settings.RadioAudioSettings
import cn.silverdragon.draarl.settings.SecureSettingsStore
import cn.silverdragon.draarl.settings.SettingsController
import cn.silverdragon.draarl.settings.SettingsEffects
import cn.silverdragon.draarl.settings.radioAudioSettings
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.update.AppUpdateController
import cn.silverdragon.draarl.update.AppUpdateEffects
import cn.silverdragon.draarl.update.AppUpdateInfo
import cn.silverdragon.draarl.update.AppUpdateManager
import cn.silverdragon.draarl.update.AppUpdateStatus
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppController internal constructor(application: Application, ioDispatcher: CoroutineDispatcher) :
    AndroidViewModel(application) {
    @Suppress("InjectDispatcher")
    constructor(application: Application) : this(application, Dispatchers.IO)

    private val appContext = application.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "draarl-app-worker")
    }
    private val sessionStore = SecureSessionStore(appContext)
    private val messageStore = RadioMessageStore(appContext)
    private val dashboardStore = DashboardCacheStore(appContext)
    private val disposed = AtomicBoolean(false)
    private val refreshAllCoordinator = RefreshCoordinator()
    private val onlineDevicesCoordinator = RefreshCoordinator()
    private val voiceAutoPlaySkippedIds = mutableSetOf<String>()
    private val playbackLevelThrottler = AudioLevelThrottler()
    private val transmitLevelThrottler = AudioLevelThrottler()
    private var voiceAutoPlayPendingMessageId: String? = null
    private val voiceAutoPlayAdvance = Runnable { advanceVoiceAutoPlay() }
    private val periodicRadioSync = object : Runnable {
        override fun run() {
            if (disposed.get()) return
            if (session.uiState.authenticated && session.uiState.user?.isApproved == true) {
                refreshGroupOnlineCounts()
                if (page == AppPage.RADIO) refreshRadioData()
            }
            mainHandler.postDelayed(this, RADIO_SYNC_INTERVAL_MS)
        }
    }
    private val api: ApiClient = ApiClient(sessionStore) { updatedSession ->
        mainHandler.post {
            if (disposed.get()) return@post
            session.onRemoteSessionChanged(updatedSession)
        }
    }
    private val groupCountsTasks = ControllerTaskRunner(viewModelScope, ioDispatcher) {}
    private val onlineDevicesTasks = ControllerTaskRunner(viewModelScope, ioDispatcher) {}
    val messageController: RadioMessageController = RadioMessageController(
        remote = ApiRadioMessageRemoteDataSource(api),
        cache = StoredRadioMessageCache(messageStore),
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        friendlyError = ::friendlyError
    )
    private val toolsDelegate = lazy { ToolsController(appContext, api, viewModelScope, ioDispatcher) }
    val tools: ToolsController
        get() = toolsDelegate.value
    private val appUpdateControllerDelegate = lazy {
        AppUpdateController(
            gateway = AppUpdateManager(appContext, api),
            parentScope = viewModelScope,
            ioDispatcher = ioDispatcher,
            effects = object : AppUpdateEffects {
                override fun hasAuthenticatedSession(): Boolean = api.currentSession() != null

                override fun automaticCheckEnabled(): Boolean = settings.uiState.autoCheckAppUpdate

                override fun showNotice(message: String) {
                    notice = message
                }

                override fun friendlyError(error: Throwable): String = this@AppController.friendlyError(error)
            }
        )
    }
    private val appUpdateController by appUpdateControllerDelegate
    private val appDataRefresher by lazy { AppDataRefresher(ApiAppDataSource(api), ioDispatcher) }
    private var refreshAllJob: Job? = null
    private val deviceManagementDelegate = lazy {
        DeviceManagementController(
            api = api,
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            currentDevices = { devices },
            updateDevices = { devices = it },
            refreshAll = ::refreshAll,
            showNotice = { notice = it },
            friendlyError = ::friendlyError
        )
    }
    val deviceManagement: DeviceManagementController
        get() = deviceManagementDelegate.value
    private val groupManagementDelegate = lazy {
        GroupManagementController(
            api = api,
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            currentGroups = { groups },
            updateGroups = {
                groups = it
                radioSession.onAvailableGroupsChanged(it, session.uiState.user?.lastGroupId ?: 999)
                syncPttOverlay()
            },
            refreshAll = ::refreshAll,
            showNotice = { notice = it },
            friendlyError = ::friendlyError
        )
    }
    val groupManagement: GroupManagementController
        get() = groupManagementDelegate.value
    private val profileDelegate = lazy {
        ProfileController(
            api = api,
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            currentUser = { session.uiState.user },
            updateUser = session::acceptUser,
            showNotice = { notice = it },
            friendlyError = ::friendlyError
        )
    }
    val profile: ProfileController
        get() = profileDelegate.value
    private val publicAuthDelegate = lazy {
        PublicAuthController(
            api = api,
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            showLoginError = session::reportLoginError,
            friendlyError = ::friendlyError
        )
    }
    val publicAuth: PublicAuthController
        get() = publicAuthDelegate.value
    private val settingsEffects = object : SettingsEffects {
        override fun isPttOverlayAllowed(): Boolean = session.uiState.user?.isApproved == true

        override fun canDrawPttOverlay(): Boolean = Settings.canDrawOverlays(appContext)

        override fun applyRadioAudioSettings(settings: RadioAudioSettings) {
            this@AppController.applyRadioAudioSettings(settings)
        }

        override fun syncPttOverlay() = this@AppController.syncPttOverlay()

        override fun requestAppUpdateCheck() = checkAppUpdate(manual = false)

        override fun beforeMessageCacheClear() {
            stopVoiceAutoPlay(stopCurrent = true)
            messageController.onEvent(RadioMessageEvent.BeforeCacheClear)
        }

        override fun afterMessageCacheClear() = messageController.onEvent(RadioMessageEvent.AfterCacheClear)

        override fun friendlyError(error: Throwable): String = this@AppController.friendlyError(error)

        override fun showNotice(message: String) {
            notice = message
        }
    }
    val settings: SettingsController = SettingsController(
        store = SecureSettingsStore(sessionStore),
        storage = AndroidSettingsStorage(
            context = appContext,
            messageStore = messageStore,
            clearBoundAudioCache = { radioSession.controls.clearAudioCache() }
        ),
        effects = settingsEffects,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher
    )
    private val radioSessionEffects = object : RadioSessionEffects {
        override fun onContextChanged(groupId: Int, selectionChanged: Boolean) {
            if (selectionChanged) {
                stopVoiceAutoPlay(stopCurrent = true)
                cancelOnlineDevicesRequests()
            }
            messageController.onContextChanged(
                updatedAccount = api.currentSession()?.toRadioMessageAccount(),
                groupId = groupId
            )
            if (selectionChanged && session.uiState.authenticated) {
                if (page == AppPage.RADIO && groupId > 0) refreshRadioData()
            }
        }

        override fun onStatusChanged(previous: RadioStatus, current: RadioStatus) {
            if (current.speaker.isBlank() && playingMessageId == null) {
                playbackLevelThrottler.reset()
                playbackLevel = 0f
            }
            if (!current.transmitting) {
                transmitLevelThrottler.reset()
                transmitLevel = 0f
                cwTransmitting = false
            }
            if (current.transmitting || current.speaker.isNotBlank()) cwPreviewing = false
            if (voiceAutoPlayEnabled && !current.transmitting) {
                if (current.speaker.isBlank() && playingMessageId == null) {
                    if (voiceAutoPlayPendingMessageId == null) scheduleVoiceAutoPlayAdvance()
                }
            }
            if (!previous.connected && current.connected) refreshRadioData()
        }

        override fun onRadioMessage(message: RadioMessage) {
            val sessionState = radioSession.uiState
            val messageGroupId = message.groupId.takeIf { it > 0 } ?: sessionState.selectedGroupId
            messageController.onLiveMessage(
                message,
                RadioMessageIdentityContext(
                    onlineDevices = onlineDevices,
                    currentSsid = sessionState.status.ssid,
                    muted = settings.uiState.muted
                )
            )
            if (messageGroupId == sessionState.selectedGroupId && voiceAutoPlayEnabled) {
                if (playingMessageId == null && voiceAutoPlayPendingMessageId == null) {
                    scheduleVoiceAutoPlayAdvance()
                }
            }
        }

        override fun onPlaybackState(messageId: String?) {
            val previousMessageId = playingMessageId
            playingMessageId = messageId
            if (messageId == null && radioSession.uiState.status.speaker.isBlank()) playbackLevel = 0f
            if (messageId == null) cwPreviewing = false
            if (voiceAutoPlayEnabled && messageId == null) {
                if (previousMessageId != null && voiceAutoPlayPendingMessageId == null) {
                    scheduleVoiceAutoPlayAdvance()
                }
            }
        }

        override fun onPlaybackLevel(level: Float) {
            val displayLevel = playbackLevelThrottler.update(level) ?: return
            mainHandler.post {
                if (!disposed.get()) playbackLevel = displayLevel
            }
        }

        override fun onTransmitLevel(level: Float) {
            val displayLevel = transmitLevelThrottler.update(level) ?: return
            mainHandler.post {
                if (!disposed.get()) transmitLevel = displayLevel
            }
        }

        override fun onCwPreviewState(active: Boolean) {
            cwPreviewing = active
        }

        override fun showNotice(message: String) {
            notice = message
        }
    }
    val radioSession: RadioSessionController = RadioSessionController(
        dependencies = RadioSessionDependencies(
            remote = ApiRadioSessionRemoteDataSource(api),
            storage = StoredRadioSessionStorage(sessionStore),
            service = createAndroidRadioServiceGateway(appContext),
            effects = radioSessionEffects
        ),
        execution = RadioSessionExecution(
            scope = viewModelScope,
            ioDispatcher = ioDispatcher
        ),
        initialAudioSettings = settings.uiState.radioAudioSettings()
    )
    private val aprsEffects = object : AprsEffects {
        override fun startBackgroundReporting(userId: Int) {
            ContextCompat.startForegroundService(appContext, AprsService.startIntent(appContext, userId))
        }

        override fun stopBackgroundReporting() {
            appContext.stopService(AprsService.stopIntent(appContext))
        }

        override fun showNotice(message: String) {
            notice = message
        }

        override fun friendlyError(error: Throwable): String = this@AppController.friendlyError(error)

        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }
    val aprs = AprsController(
        storage = AprsConfigStore(appContext),
        sender = AprsIsClient(),
        effects = aprsEffects,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher
    )

    private val sessionEffects = object : SessionEffects {
        override fun onStoredSessionPrepared(session: Session) = prepareSessionResources(session)

        override fun onSessionActivated(session: Session, entryPoint: SessionEntryPoint) {
            when (entryPoint) {
                SessionEntryPoint.LOGIN, SessionEntryPoint.RESTORE -> activateSession(session)
            }
        }

        override fun onSessionUpdated(session: Session) {
            radioSession.onAccountChanged(session.toRadioSessionAccount())
            syncPttOverlay()
        }

        override fun onSessionCleared() = clearSessionResources()

        override fun requestLoginCaptcha() {
            publicAuth.loadCaptcha()
        }

        override fun friendlyError(error: Throwable): String = this@AppController.friendlyError(error)
    }
    val session = SessionController(
        remote = ApiSessionRemoteDataSource(api),
        effects = sessionEffects,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        serverUrl = AppConfig.BASE_URL
    )

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

    val currentAppVersionName: String get() = appUpdateController.currentVersionName
    val appUpdateStatus: AppUpdateStatus
        get() = appUpdateController.uiState.status
    val appUpdateInfo: AppUpdateInfo?
        get() = appUpdateController.uiState.info
    val appUpdateMessage: String
        get() = appUpdateController.uiState.message
    val appUpdateProgress: Float
        get() = appUpdateController.uiState.progress
    private var appInForeground = true
    var playingMessageId by mutableStateOf<String?>(null)
        private set
    var voiceAutoPlayEnabled by mutableStateOf(false)
        private set
    val unplayedVoiceCount: Int
        get() = messageController.uiState.unplayedVoiceCount
    var playbackLevel by mutableFloatStateOf(0f)
        private set
    var transmitLevel by mutableFloatStateOf(0f)
        private set
    var cwTransmitting by mutableStateOf(false)
        private set
    var cwPreviewing by mutableStateOf(false)
        private set

    init {
        session.start()
        mainHandler.postDelayed(periodicRadioSync, RADIO_SYNC_INTERVAL_MS)
    }

    fun navigate(target: AppPage) {
        if (target in APPROVAL_REQUIRED_PAGES && session.uiState.user?.isApproved != true) {
            notice = "账号审核通过后才能使用该功能"
            return
        }
        page = target
        when (target) {
            AppPage.DEVICES, AppPage.GROUPS, AppPage.PROFILE -> refreshAll()

            AppPage.RADIO -> {
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

    fun checkAppUpdate(manual: Boolean = true) = appUpdateController.check(manual)

    fun downloadAndInstallAppUpdate() = appUpdateController.downloadAndInstall()

    fun openAppUpdateInstallPermissionSettings() = appUpdateController.openInstallPermissionSettings()

    fun resumePendingAppUpdateInstall() = appUpdateController.resumePendingInstall()

    fun refreshAll() {
        if (api.currentSession() == null) return
        val generation = refreshAllCoordinator.request() ?: return
        contentLoading = true
        startRefreshAll(generation)
    }

    private fun startRefreshAll(generation: Int) {
        if (disposed.get() || api.currentSession() == null) {
            cancelRefreshAll()
            contentLoading = false
            return
        }
        refreshAllJob = viewModelScope.launch {
            val result = runCatching {
                appDataRefresher.refresh(
                    AppDataFallback(
                        devices = devices,
                        groups = groups,
                        trend = dashboard.communicationTrend
                    )
                )
            }
            result.exceptionOrNull()?.let { failure ->
                if (failure is CancellationException) throw failure
            }
            if (disposed.get()) return@launch
            val decision = refreshAllCoordinator.complete(generation)
            val snapshot = result.getOrNull()
            if (decision.applyResults && api.currentSession() != null && snapshot != null) {
                devices = snapshot.devices
                groups = snapshot.groups
                if (snapshot.defaultDeviceGroup.isSuccess) {
                    deviceManagement.applyDefaultGroup(snapshot.defaultDeviceGroup.getOrNull())
                }
                snapshot.user?.let {
                    session.acceptUser(it)
                    api.acceptCurrentUser(it)
                }
                radioSession.onAvailableGroupsChanged(
                    snapshot.groups,
                    session.uiState.user?.lastGroupId ?: 999
                )
                syncPttOverlay()
                dashboard = DashboardData(
                    devices = snapshot.devices.size,
                    onlineDevices = snapshot.devices.count(Device::online),
                    groups = snapshot.groups.size,
                    communications = snapshot.stats?.totalCount ?: dashboard.communications,
                    communicationDurationMs =
                        snapshot.stats?.totalDurationMs ?: dashboard.communicationDurationMs,
                    communicationTrend = snapshot.trend
                )
                session.uiState.user?.id?.let { dashboardStore.save(it, dashboard) }
            }
            val nextGeneration = decision.nextGeneration
            if (nextGeneration != null && api.currentSession() != null) {
                startRefreshAll(nextGeneration)
            } else if (decision.isIdle || api.currentSession() == null) {
                refreshAllJob = null
                contentLoading = false
            }
        }
    }

    fun refreshGroupOnlineCounts() {
        val userId = session.uiState.user?.id ?: return
        if (api.currentSession() == null) return
        groupCountsTasks.launch(
            operation = { api.getGroupStats() },
            onSuccess = { stats ->
                val active = !disposed.get() && session.uiState.authenticated
                if (!active || session.uiState.user?.id != userId || stats.isEmpty()) return@launch
                groups = groups.map { group ->
                    stats[group.id]?.let { (online, total) ->
                        group.copy(onlineCount = online, totalCount = total)
                    } ?: group
                }
            },
            onFailure = {}
        )
    }

    fun sendText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val status = radioSession.uiState.status
        if (!status.connected) {
            notice = "电台尚未连接"
            return false
        }
        if (status.transmitting) {
            notice = "正在发射中，稍后再发送文本"
            return false
        }
        if (status.speaker.isNotBlank()) {
            notice = "正在接收语音，发言结束后再发送文本"
            return false
        }
        val sent = radioSession.controls.sendText(text)
        if (!sent) notice = "文本发送失败，请稍后重试"
        return sent
    }

    fun canSendText(): Boolean = radioSession.uiState.status.let { status ->
        status.connected && !status.transmitting && status.speaker.isBlank()
    }

    fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        stopVoiceAutoPlay(stopCurrent = true)
        if (cwPreviewing) stopCwPreview()
        val status = radioSession.uiState.status
        if (!status.connected) {
            notice = "电台尚未连接"
            return false
        }
        if (status.transmitting || status.speaker.isNotBlank()) {
            notice = "当前信道正忙，稍后再发送 CW"
            return false
        }
        val sent = radioSession.controls.sendCw(text, wordsPerMinute, toneHz)
        if (sent) cwTransmitting = true
        if (!sent) notice = "CW 发送失败，请检查内容后重试"
        return sent
    }

    fun stopCw() {
        if (radioSession.controls.stopCw()) cwTransmitting = false
    }

    fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        stopVoiceAutoPlay(stopCurrent = true)
        val status = radioSession.uiState.status
        if (status.transmitting || status.speaker.isNotBlank()) {
            notice = "当前信道正忙，稍后再试听 CW"
            return false
        }
        val started = radioSession.controls.previewCw(text, wordsPerMinute, toneHz)
        if (started) cwPreviewing = true else notice = "CW 试听失败，请检查内容后重试"
        return started
    }

    fun stopCwPreview() {
        if (radioSession.controls.stopCwPreview()) cwPreviewing = false
    }

    fun startPtt(): Boolean {
        val started = radioSession.controls.startPtt()
        if (!started && !radioSession.uiState.status.connected) notice = "请先连接电台"
        return started
    }

    fun stopPtt() {
        radioSession.controls.stopPtt()
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
        if (VoicePlaybackQueue.nextUnplayed(messageController.uiState.messages) == null) {
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
        messageController.onEvent(RadioMessageEvent.MarkAllPlayed)
    }

    private fun stopVoiceAutoPlay(stopCurrent: Boolean) {
        voiceAutoPlayEnabled = false
        voiceAutoPlayPendingMessageId = null
        voiceAutoPlaySkippedIds.clear()
        mainHandler.removeCallbacks(voiceAutoPlayAdvance)
        if (stopCurrent && playingMessageId != null) radioSession.controls.stopPlayback()
    }

    private fun scheduleVoiceAutoPlayAdvance(delayMs: Long = VOICE_AUTO_PLAY_ADVANCE_DELAY_MS) {
        mainHandler.removeCallbacks(voiceAutoPlayAdvance)
        if (voiceAutoPlayEnabled) mainHandler.postDelayed(voiceAutoPlayAdvance, delayMs)
    }

    private fun advanceVoiceAutoPlay() {
        if (!voiceAutoPlayEnabled || disposed.get()) return
        if (playingMessageId != null || voiceAutoPlayPendingMessageId != null) return
        val status = radioSession.uiState.status
        if (status.transmitting || status.speaker.isNotBlank()) return
        val next = VoicePlaybackQueue.nextUnplayed(messageController.uiState.messages, voiceAutoPlaySkippedIds)
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
        if (cacheKey.isNotBlank() && radioSession.controls.hasAudioCacheKey(cacheKey)) {
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
        if (radioSession.controls.togglePlayback(message)) {
            if (fromAutoPlay) voiceAutoPlayPendingMessageId = null
            markVoiceMessagePlayed(message)
        } else {
            handleVoicePlaybackFailure(message, fromAutoPlay, "无法播放这条语音")
        }
    }

    private fun refreshServerVoiceUrlAndPlay(message: RadioMessage, fromAutoPlay: Boolean) {
        if (message.serverRecordId == null) {
            playVoiceMessage(message, fromAutoPlay)
            return
        }
        messageController.refreshServerMessage(message) { result ->
            result
                .map { refreshed ->
                    withStableAudioCacheKey(
                        message.copy(
                            audioUrl = refreshed.audioUrl,
                            durationMs = refreshed.durationMs.takeIf { it > 0 } ?: message.durationMs,
                            content = refreshed.content.ifBlank { message.content }
                        )
                    )
                }
                .onSuccess { refreshed ->
                    messageController.updateMessage(refreshed)
                    if (!fromAutoPlay || (voiceAutoPlayEnabled && voiceAutoPlayPendingMessageId == message.id)) {
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
        messageController.markPlayed(message)
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        appInForeground = inForeground
        if (inForeground) resumePendingAppUpdateInstall()
        syncPttOverlay()
    }

    fun refreshRadioData() {
        messageController.onEvent(RadioMessageEvent.Refresh)
        refreshOnlineDevices()
    }

    private fun refreshOnlineDevices() {
        if (session.uiState.user == null || api.currentSession() == null) return
        val generation = onlineDevicesCoordinator.request() ?: return
        startOnlineDevicesRefresh(generation)
    }

    private fun startOnlineDevicesRefresh(generation: Int) {
        val userId = session.uiState.user?.id
        if (userId == null || api.currentSession() == null || disposed.get()) {
            cancelOnlineDevicesRequests()
            return
        }
        val groupId = radioSession.uiState.selectedGroupId
        val started = onlineDevicesTasks.launch(
            operation = { api.getOnlineDevices(groupId) },
            onSuccess = { loaded -> completeOnlineDevicesRefresh(generation, userId, groupId, loaded) },
            onFailure = { completeOnlineDevicesRefresh(generation, userId, groupId, null) }
        )
        if (!started) onlineDevicesCoordinator.cancel()
    }

    private fun completeOnlineDevicesRefresh(generation: Int, userId: Int, groupId: Int, loaded: List<OnlineDevice>?) {
        val decision = onlineDevicesCoordinator.complete(generation)
        val sameRequest = groupId == radioSession.uiState.selectedGroupId && session.uiState.user?.id == userId
        val active = !disposed.get() && session.uiState.authenticated
        val canApply = decision.applyResults && active && sameRequest
        if (canApply && loaded != null) {
            onlineDevices = loaded
            messageController.onEvent(
                RadioMessageEvent.OnlineDevicesChanged(loaded.map(OnlineDevice::username))
            )
        }
        decision.nextGeneration?.let { nextGeneration ->
            if (!disposed.get()) startOnlineDevicesRefresh(nextGeneration)
        }
    }

    private fun cancelOnlineDevicesRequests() {
        onlineDevicesCoordinator.cancel()
        onlineDevicesTasks.cancel()
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

    override fun onCleared() {
        if (!disposed.compareAndSet(false, true)) return
        mainHandler.removeCallbacksAndMessages(null)
        cancelRefreshAll()
        invalidateBackgroundRequests()
        groupCountsTasks.close()
        onlineDevicesTasks.close()
        if (appUpdateControllerDelegate.isInitialized()) appUpdateController.close()
        if (toolsDelegate.isInitialized()) toolsDelegate.value.close()
        if (deviceManagementDelegate.isInitialized()) deviceManagementDelegate.value.close()
        if (groupManagementDelegate.isInitialized()) groupManagementDelegate.value.close()
        if (profileDelegate.isInitialized()) profileDelegate.value.close()
        if (publicAuthDelegate.isInitialized()) publicAuthDelegate.value.close()
        session.close()
        settings.close()
        aprs.close()
        messageController.close()
        radioSession.close()
        executor.shutdownNow()
    }

    private fun prepareSessionResources(session: Session) {
        aprs.onUserChanged(session.user.id)
        dashboard = dashboardStore.load(session.user.id) ?: DashboardData()
    }

    private fun activateSession(session: Session) {
        prepareSessionResources(session)
        radioSession.onAccountChanged(session.toRadioSessionAccount())
        page = AppPage.RADIO
        syncPttOverlay()
        refreshAll()
        radioSession.discoverAccessPoints()
        if (settings.uiState.autoCheckAppUpdate) checkAppUpdate(manual = false)
        if (session.user.isApproved) refreshRadioData()
    }

    private fun clearSessionResources() {
        stopVoiceAutoPlay(stopCurrent = false)
        if (toolsDelegate.isInitialized()) toolsDelegate.value.reset()
        cancelRefreshAll()
        invalidateBackgroundRequests()
        contentLoading = false
        radioSession.onAccountChanged(null)
        aprs.onUserChanged(null)
        dashboard = DashboardData()
        devices = emptyList()
        groups = emptyList()
        if (deviceManagementDelegate.isInitialized()) deviceManagementDelegate.value.reset()
        if (groupManagementDelegate.isInitialized()) groupManagementDelegate.value.reset()
        if (profileDelegate.isInitialized()) profileDelegate.value.reset()
        if (publicAuthDelegate.isInitialized()) publicAuthDelegate.value.reset()
        onlineDevices = emptyList()
        playingMessageId = null
        playbackLevelThrottler.reset()
        transmitLevelThrottler.reset()
        playbackLevel = 0f
        transmitLevel = 0f
        cwTransmitting = false
        cwPreviewing = false
        if (appUpdateControllerDelegate.isInitialized()) appUpdateController.reset()
        page = AppPage.RADIO
        syncPttOverlay()
    }

    private fun syncPttOverlay() {
        val enabled = settings.uiState.pttOverlayEnabled &&
            session.uiState.authenticated &&
            session.uiState.user?.isApproved == true &&
            Settings.canDrawOverlays(appContext)
        radioSession.configurePttOverlay(
            RadioPttOverlayConfig(
                enabled = enabled,
                visible = enabled && !appInForeground,
                groupName = ""
            )
        )
    }

    private fun applyRadioAudioSettings(settings: RadioAudioSettings) {
        radioSession.applyAudioSettings(settings)
    }

    private fun invalidateBackgroundRequests() {
        groupCountsTasks.cancel()
        cancelOnlineDevicesRequests()
    }

    private fun cancelRefreshAll() {
        refreshAllCoordinator.cancel()
        refreshAllJob?.cancel()
        refreshAllJob = null
    }

    private fun Session.toRadioMessageAccount() = RadioMessageAccount(
        key = "${baseUrl.trimEnd('/')}#${user.id}",
        user = user
    )

    private fun Session.toRadioSessionAccount() = RadioSessionAccount(
        key = "${baseUrl.trimEnd('/')}#${user.id}",
        userId = user.id,
        approved = user.isApproved,
        baseUrl = baseUrl,
        accessToken = accessToken,
        defaultGroupId = user.lastGroupId.takeIf { it > 0 } ?: 999
    )

    private fun friendlyError(error: Throwable): String = when (error) {
        is ApiException -> error.message
        else -> error.message ?: "操作失败，请稍后重试"
    }

    companion object {
        private const val ANDROID_CLIENT_SSID = 101
        private const val RADIO_SYNC_INTERVAL_MS = 20_000L
        private const val VOICE_AUTO_PLAY_ADVANCE_DELAY_MS = 300L
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
