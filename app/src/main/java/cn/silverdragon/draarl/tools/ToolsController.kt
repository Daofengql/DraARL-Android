package cn.silverdragon.draarl.tools

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ToolsApi
import cn.silverdragon.draarl.tools.ble.BleProvisionController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class ToolsController internal constructor(
    private val api: ToolsApi,
    private val cache: ToolCache,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val bleController: BleProvisionController? = null,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    constructor(
        context: Context,
        api: ToolsApi,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        api = api,
        cache = ToolCacheStore(context.applicationContext),
        scope = scope,
        ioDispatcher = ioDispatcher,
        bleController = BleProvisionController(context.applicationContext)
    )

    private var closed = false
    val ble: BleProvisionController
        get() = checkNotNull(bleController)
    private val navigation = ToolBackStack()

    var destination by mutableStateOf(ToolDestination.HOME)
        private set
    val canGoBack: Boolean
        get() = navigation.canGoBack
    var relayBusy by mutableStateOf(false)
        private set
    var logbookBusy by mutableStateOf(false)
        private set
    var presetBusy by mutableStateOf(false)
        private set
    private val relayTasks = ControllerTaskRunner(scope, ioDispatcher) { relayBusy = it }
    private val relayCacheTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private val logbookTasks = ControllerTaskRunner(scope, ioDispatcher) { logbookBusy = it }
    private val presetTasks = ControllerTaskRunner(scope, ioDispatcher) { presetBusy = it }
    private val draftLoadTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private val cacheWriteTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private var homeError by mutableStateOf("")
    private var relayError by mutableStateOf("")
    private var logbookError by mutableStateOf("")
    private var presetError by mutableStateOf("")
    val error: String
        get() = when (destination) {
            ToolDestination.HOME, ToolDestination.BLE, ToolDestination.MAIDENHEAD -> homeError
            ToolDestination.RELAYS -> relayError
            ToolDestination.LOGBOOK, ToolDestination.LOGBOOK_EDITOR -> logbookError
        }
    val presetErrorMessage: String
        get() = presetError
    var relayLocation by mutableStateOf("")
        private set
    var relays by mutableStateOf<List<RelayStation>>(emptyList())
        private set
    var relayCacheTime by mutableLongStateOf(0L)
        private set
    var logbooks by mutableStateOf<List<LogbookEntry>>(emptyList())
        private set
    var logbookPage by mutableIntStateOf(1)
        private set
    var logbookTotal by mutableIntStateOf(0)
        private set
    var logbookFilter by mutableStateOf("")
        private set
    var presets by mutableStateOf<List<RadioPreset>>(emptyList())
        private set
    var draft by mutableStateOf<LogbookDraft?>(null)
        private set
    private var activeUserId = 0
    private var presetOrderDirty = false

    init {
        relayCacheTasks.replace(
            operation = cache::loadRelays,
            onSuccess = { cached ->
                cached?.let {
                    relayLocation = it.location
                    relays = it.items
                    relayCacheTime = it.savedAt
                }
            },
            onFailure = {}
        )
    }

    fun open(target: ToolDestination, user: User?): Boolean {
        if (target == ToolDestination.LOGBOOK && user?.isApproved != true) {
            homeError = "账号审核通过后才能使用该功能"
            return false
        }
        navigation.open(target)
        destination = navigation.current
        setError(target, "")
        when (target) {
            ToolDestination.LOGBOOK -> {
                activeUserId = user?.id ?: 0
                val draftUserId = activeUserId
                draftLoadTasks.replace(
                    operation = { cache.loadDraft(draftUserId) },
                    onSuccess = { loaded -> if (draftUserId == activeUserId) draft = loaded },
                    onFailure = {}
                )
                if (logbooks.isEmpty()) loadLogbooks(reset = true)
            }

            else -> Unit
        }
        return true
    }

    fun back() {
        destination = navigation.back()
        homeError = ""
    }

    fun clearError() {
        setError(destination, "")
    }

    fun searchRelays(location: String) {
        if (closed || relayBusy) return
        val normalized = location.trim().replace(Regex("\\s+"), " ")
        if (normalized.split(' ').filter(String::isNotBlank).size < 2) {
            relayError = "请至少填写省和城市"
            return
        }
        relayCacheTasks.cancel()
        relayError = ""
        relayTasks.launch(
            operation = {
                val loaded = api.searchPublicRelays(normalized)
                val savedAt = currentTimeMillis()
                cache.saveRelays(normalized, loaded, savedAt)
                CachedRelays(normalized, loaded, savedAt)
            },
            onSuccess = { loaded ->
                relayLocation = loaded.location
                relays = loaded.items
                relayCacheTime = loaded.savedAt
            },
            onFailure = { failure -> relayError = failure.toToolError() }
        )
    }

    fun loadLogbooks(reset: Boolean = false, callsign: String = logbookFilter) {
        if (closed || logbookBusy) return
        val targetPage = if (reset) 1 else logbookPage + 1
        logbookError = ""
        logbookTasks.launch(
            operation = { api.getLogbooks(targetPage, LOGBOOK_PAGE_SIZE, callsign) },
            onSuccess = { loaded ->
                logbookFilter = callsign.trim()
                logbookPage = loaded.page
                logbookTotal = loaded.total
                logbooks = if (reset) loaded.items else (logbooks + loaded.items).distinctBy(LogbookEntry::id)
            },
            onFailure = { failure -> logbookError = failure.toToolError() }
        )
    }

    fun editDraft(entry: LogbookEntry?, user: User?) {
        draftLoadTasks.cancel()
        if (user != null) activeUserId = user.id
        draft = entry?.let {
            LogbookDraft(
                editingId = it.id,
                myCallsign = it.myCallsign,
                localTime = LogbookTime.utcToLocal(it.timeUtc),
                txFrequency = it.txFrequency.toString(),
                rxFrequency = it.rxFrequency.toString(),
                cqZone = it.cqZone.takeIf { value -> value > 0 }?.toString().orEmpty(),
                ituZone = it.ituZone.takeIf { value -> value > 0 }?.toString().orEmpty(),
                mode = it.mode,
                callsign = it.callsign,
                theirRst = it.theirRst,
                theirPower = it.theirPower?.toString().orEmpty(),
                theirQth = it.theirQth,
                theirRadio = it.theirRadio,
                theirAntenna = it.theirAntenna,
                myRst = it.myRst,
                myPower = it.myPower?.toString().orEmpty(),
                myQth = it.myQth,
                myRadio = it.myRadio,
                myAntenna = it.myAntenna,
                notes = it.notes
            )
        } ?: LogbookDraft(myCallsign = user?.callsign.orEmpty(), localTime = LogbookTime.nowLocal())
        val draftUserId = activeUserId
        draft?.let { value ->
            cacheWriteTasks.enqueue(operation = { cache.saveDraft(draftUserId, value) })
        }
        navigation.open(ToolDestination.LOGBOOK_EDITOR)
        destination = navigation.current
    }

    fun resumeDraft() {
        if (draft == null) return
        navigation.open(ToolDestination.LOGBOOK_EDITOR)
        destination = navigation.current
    }

    fun updateDraft(value: LogbookDraft) {
        draftLoadTasks.cancel()
        draft = value
        val draftUserId = activeUserId
        cacheWriteTasks.enqueue(operation = { cache.saveDraft(draftUserId, value) })
    }

    fun applyPreset(preset: RadioPreset) {
        val current = draft ?: return
        updateDraft(
            current.copy(
                myPower = preset.power?.toString().orEmpty(),
                myQth = preset.qth,
                myRadio = preset.radio,
                myAntenna = preset.antenna
            )
        )
    }

    fun saveDraft(onSuccess: () -> Unit) {
        if (closed || logbookBusy) return
        val current = draft ?: return
        val entry = runCatching(current::toLogbookEntry).getOrElse {
            logbookError = it.message ?: "日志内容格式不正确"
            return
        }
        val draftUserId = activeUserId
        logbookError = ""
        logbookTasks.launch(
            operation = { api.saveLogbook(entry) },
            onSuccess = {
                cacheWriteTasks.enqueue(operation = { cache.clearDraft(draftUserId) })
                draft = null
                onSuccess()
                loadLogbooks(reset = true)
            },
            onFailure = { failure -> logbookError = failure.toToolError() }
        )
    }

    fun deleteLogbook(id: Int) = runLogbookMutation({ api.deleteLogbook(id) }) { loadLogbooks(reset = true) }

    fun deleteLogbooks(ids: Collection<Int>, onSuccess: () -> Unit = {}) {
        if (ids.isEmpty()) return
        runLogbookMutation({ api.deleteLogbooks(ids) }) {
            onSuccess()
            loadLogbooks(reset = true)
        }
    }

    fun loadPresets() {
        if (closed || presetBusy) return
        presetError = ""
        presetTasks.launch(
            operation = api::getRadioPresets,
            onSuccess = { loaded ->
                presets = loaded
                presetOrderDirty = false
            },
            onFailure = { failure -> presetError = failure.toToolError() }
        )
    }

    fun savePreset(preset: RadioPreset, onSuccess: () -> Unit = {}) =
        runPresetMutation({ api.saveRadioPreset(preset) }) {
            onSuccess()
            loadPresets()
        }

    fun deletePreset(id: Int) = runPresetMutation({ api.deleteRadioPreset(id) }) { loadPresets() }

    fun previewPresetMove(fromIndex: Int, toIndex: Int) {
        if (presetBusy) return
        if (fromIndex !in presets.indices || toIndex !in presets.indices || fromIndex == toIndex) return
        presets = presets.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        presetOrderDirty = true
    }

    fun commitPresetOrder() {
        if (presetBusy || !presetOrderDirty) return
        val reordered = presets.mapIndexed { position, preset -> preset.copy(sortOrder = position + 1) }
        presets = reordered
        presetOrderDirty = false
        runPresetMutation(
            block = { api.reorderRadioPresets(reordered.map { it.id to it.sortOrder }) },
            onSuccess = { loadPresets() }
        )
    }

    fun reset() {
        bleController?.disconnect()
        navigation.reset()
        destination = navigation.current
        relayTasks.cancel()
        relayCacheTasks.cancel()
        logbookTasks.cancel()
        presetTasks.cancel()
        draftLoadTasks.cancel()
        homeError = ""
        relayError = ""
        logbookError = ""
        presetError = ""
        presetOrderDirty = false
        logbooks = emptyList()
        presets = emptyList()
        draft = null
        activeUserId = 0
    }

    fun close() {
        if (closed) return
        closed = true
        bleController?.close()
        relayTasks.close()
        relayCacheTasks.close()
        logbookTasks.close()
        presetTasks.close()
        draftLoadTasks.close()
        cacheWriteTasks.close()
    }

    private fun runLogbookMutation(block: () -> Any?, onSuccess: () -> Unit) {
        if (closed || logbookBusy) return
        logbookError = ""
        logbookTasks.launch(
            operation = block,
            onSuccess = { onSuccess() },
            onFailure = { failure -> logbookError = failure.toToolError() }
        )
    }

    private fun runPresetMutation(block: () -> Any?, onSuccess: () -> Unit) {
        if (closed || presetBusy) return
        presetError = ""
        presetTasks.launch(
            operation = block,
            onSuccess = { onSuccess() },
            onFailure = { failure -> presetError = failure.toToolError() }
        )
    }

    fun clearPresetError() {
        presetError = ""
    }

    private fun setError(target: ToolDestination, message: String) {
        when (target) {
            ToolDestination.HOME, ToolDestination.BLE, ToolDestination.MAIDENHEAD -> homeError = message
            ToolDestination.RELAYS -> relayError = message
            ToolDestination.LOGBOOK, ToolDestination.LOGBOOK_EDITOR -> logbookError = message
        }
    }

    private companion object {
        const val LOGBOOK_PAGE_SIZE = 20
    }
}

private fun Throwable.toToolError(): String = message ?: "操作失败，请稍后重试"
