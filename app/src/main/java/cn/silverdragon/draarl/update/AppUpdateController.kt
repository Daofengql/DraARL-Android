package cn.silverdragon.draarl.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.network.ApiException
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal interface AppUpdateGateway {
    val currentVersionName: String

    fun checkForUpdate(channel: String = "stable"): AppUpdateInfo?

    fun downloadUpdate(update: AppUpdateInfo, onProgress: (Float) -> Unit = {}): File

    fun installUpdate(apk: File)

    fun canRequestPackageInstalls(): Boolean

    fun openInstallPermissionSettings()
}

internal interface AppUpdateEffects {
    fun hasAuthenticatedSession(): Boolean

    fun automaticCheckEnabled(): Boolean

    fun showNotice(message: String)

    fun friendlyError(error: Throwable): String
}

data class AppUpdateUiState(
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val info: AppUpdateInfo? = null,
    val message: String = ""
)

class AppUpdateController internal constructor(
    private val gateway: AppUpdateGateway,
    parentScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val effects: AppUpdateEffects
) {
    private val controllerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + controllerJob)
    private val checkTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private val downloadTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private var downloadGeneration = 0
    private var pendingInstallAfterPermission = false
    private var closed = false

    var uiState by mutableStateOf(AppUpdateUiState())
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    val currentVersionName: String
        get() = gateway.currentVersionName

    fun check(manual: Boolean = true) {
        val unavailable = closed || (!manual && !effects.automaticCheckEnabled())
        if (unavailable || uiState.status in setOf(AppUpdateStatus.CHECKING, AppUpdateStatus.DOWNLOADING)) return
        if (!effects.hasAuthenticatedSession()) {
            if (manual) effects.showNotice("请先登录后检查更新")
            return
        }
        progress = 0f
        uiState = uiState.copy(
            status = AppUpdateStatus.CHECKING,
            message = "正在检查客户端更新"
        )
        checkTasks.launch(
            operation = gateway::checkForUpdate,
            onSuccess = { update -> publishCheckResult(Result.success(update), manual) },
            onFailure = { failure -> publishCheckResult(Result.failure(failure), manual) }
        )
    }

    fun downloadAndInstall() {
        if (closed || uiState.status in setOf(AppUpdateStatus.CHECKING, AppUpdateStatus.DOWNLOADING)) return
        val update = uiState.info ?: run {
            check(manual = true)
            return
        }
        if (!gateway.canRequestPackageInstalls()) {
            requireInstallPermission()
        } else {
            val generation = ++downloadGeneration
            pendingInstallAfterPermission = false
            progress = 0f
            uiState = uiState.copy(
                status = AppUpdateStatus.DOWNLOADING,
                message = "正在下载 ${update.version}"
            )
            downloadTasks.launch(
                operation = {
                    gateway.downloadUpdate(update) { progress -> publishProgress(generation, progress) }
                },
                onSuccess = { apk -> installDownloadedUpdate(generation, apk) },
                onFailure = { failure -> publishDownloadFailure(generation, failure) }
            )
        }
    }

    fun openInstallPermissionSettings() {
        runCatching(gateway::openInstallPermissionSettings)
            .onFailure { effects.showNotice("无法打开安装权限设置：${effects.friendlyError(it)}") }
    }

    fun resumePendingInstall() {
        if (closed || !pendingInstallAfterPermission) return
        if (uiState.info == null) {
            pendingInstallAfterPermission = false
            return
        }
        if (gateway.canRequestPackageInstalls()) {
            pendingInstallAfterPermission = false
            downloadAndInstall()
        }
    }

    fun reset() {
        checkTasks.cancel()
        downloadTasks.cancel()
        downloadGeneration++
        pendingInstallAfterPermission = false
        uiState = AppUpdateUiState()
        progress = 0f
    }

    fun close() {
        if (closed) return
        closed = true
        reset()
        checkTasks.close()
        downloadTasks.close()
        scope.cancel()
    }

    private fun publishCheckResult(result: Result<AppUpdateInfo?>, manual: Boolean) {
        val presentation = result.toCheckPresentation(manual, effects::friendlyError)
        uiState = presentation.state
        presentation.notice?.let(effects::showNotice)
    }

    private fun publishProgress(generation: Int, progress: Float) {
        scope.launch {
            if (closed || generation != downloadGeneration) return@launch
            if (uiState.status == AppUpdateStatus.DOWNLOADING) {
                this@AppUpdateController.progress = progress
            }
        }
    }

    private fun installDownloadedUpdate(generation: Int, apk: File) {
        if (generation != downloadGeneration) return
        runCatching { gateway.installUpdate(apk) }
            .onSuccess {
                progress = 1f
                uiState = uiState.copy(
                    status = AppUpdateStatus.READY_TO_INSTALL,
                    message = "已打开系统安装器"
                )
                effects.showNotice("已打开系统安装器")
            }
            .onFailure { failure -> publishDownloadFailure(generation, failure) }
    }

    private fun publishDownloadFailure(generation: Int, error: Throwable) {
        if (generation != downloadGeneration) return
        if (error is AppUpdateInstallPermissionException) {
            requireInstallPermission()
        } else {
            val message = "更新失败：${effects.friendlyError(error)}"
            uiState = uiState.copy(status = AppUpdateStatus.ERROR, message = message)
            effects.showNotice(message)
        }
    }

    private fun requireInstallPermission() {
        pendingInstallAfterPermission = true
        val message = "需要允许本应用安装更新包，正在打开系统权限设置"
        uiState = uiState.copy(status = AppUpdateStatus.INSTALL_PERMISSION_REQUIRED, message = message)
        effects.showNotice(message)
        openInstallPermissionSettings()
    }
}

private fun Throwable.isClientUpdateUnsupported(): Boolean =
    this is ApiException && code in UNSUPPORTED_UPDATE_STATUS_CODES

private data class AppUpdateCheckPresentation(val state: AppUpdateUiState, val notice: String?)

private fun Result<AppUpdateInfo?>.toCheckPresentation(
    manual: Boolean,
    friendlyError: (Throwable) -> String
): AppUpdateCheckPresentation = fold(
    onSuccess = { update ->
        if (update == null) {
            val message = "当前已是最新版本"
            AppUpdateCheckPresentation(
                state = AppUpdateUiState(status = AppUpdateStatus.UP_TO_DATE, message = message),
                notice = message.takeIf { manual }
            )
        } else {
            val message = "发现新版本 ${update.version}${if (update.forceUpdate) "（强制更新）" else ""}"
            AppUpdateCheckPresentation(
                state = AppUpdateUiState(status = AppUpdateStatus.AVAILABLE, info = update, message = message),
                notice = message
            )
        }
    },
    onFailure = { error ->
        if (!manual && error.isClientUpdateUnsupported()) {
            AppUpdateCheckPresentation(AppUpdateUiState(), null)
        } else {
            val message = if (error.isClientUpdateUnsupported()) {
                "服务器暂不支持客户端更新"
            } else {
                friendlyError(error)
            }
            AppUpdateCheckPresentation(
                state = AppUpdateUiState(status = AppUpdateStatus.ERROR, message = message),
                notice = message.takeIf { manual }
            )
        }
    }
)

private val UNSUPPORTED_UPDATE_STATUS_CODES = setOf(404, 405)
