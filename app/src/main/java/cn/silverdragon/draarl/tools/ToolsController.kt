package cn.silverdragon.draarl.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ToolsApi
import cn.silverdragon.draarl.tools.ble.BleProvisionController
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ToolsController(context: Context, private val api: ToolsApi) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "draarl-tools") }
    private val cache = ToolCacheStore(context.applicationContext)
    private val closed = AtomicBoolean(false)
    val ble = BleProvisionController(context.applicationContext)
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
    private val relayGeneration = AtomicInteger(0)
    private val logbookGeneration = AtomicInteger(0)
    private val presetGeneration = AtomicInteger(0)
    private var presetOrderDirty = false

    init {
        cache.loadRelays()?.let {
            relayLocation = it.location
            relays = it.items
            relayCacheTime = it.savedAt
        }
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
                draft = cache.loadDraft(activeUserId)
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
        if (relayBusy) return
        val normalized = location.trim().replace(Regex("\\s+"), " ")
        if (normalized.split(' ').filter(String::isNotBlank).size < 2) {
            relayError = "请至少填写省和城市"
            return
        }
        val generation = relayGeneration.incrementAndGet()
        relayBusy = true
        relayError = ""
        executor.execute {
            runCatching { api.searchPublicRelays(normalized) }
                .onSuccess { loaded ->
                    mainHandler.post {
                        if (closed.get() || generation != relayGeneration.get()) return@post
                        cache.saveRelays(normalized, loaded)
                        relayLocation = normalized
                        relays = loaded
                        relayCacheTime = System.currentTimeMillis()
                        relayBusy = false
                    }
                }
                .onFailure { postRelayError(generation, it) }
        }
    }

    fun loadLogbooks(reset: Boolean = false, callsign: String = logbookFilter) {
        if (logbookBusy) return
        val targetPage = if (reset) 1 else logbookPage + 1
        val generation = logbookGeneration.incrementAndGet()
        logbookBusy = true
        logbookError = ""
        executor.execute {
            runCatching { api.getLogbooks(targetPage, LOGBOOK_PAGE_SIZE, callsign) }
                .onSuccess { loaded ->
                    mainHandler.post {
                        if (closed.get() || generation != logbookGeneration.get()) return@post
                        logbookFilter = callsign.trim()
                        logbookPage = loaded.page
                        logbookTotal = loaded.total
                        logbooks = if (reset) loaded.items else (logbooks + loaded.items).distinctBy(LogbookEntry::id)
                        logbookBusy = false
                    }
                }
                .onFailure { postLogbookError(generation, it) }
        }
    }

    fun editDraft(entry: LogbookEntry?, user: User?) {
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
        draft?.let { cache.saveDraft(activeUserId, it) }
        navigation.open(ToolDestination.LOGBOOK_EDITOR)
        destination = navigation.current
    }

    fun resumeDraft() {
        if (draft == null) return
        navigation.open(ToolDestination.LOGBOOK_EDITOR)
        destination = navigation.current
    }

    fun updateDraft(value: LogbookDraft) {
        draft = value
        cache.saveDraft(activeUserId, value)
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
        if (logbookBusy) return
        val current = draft ?: return
        val entry = runCatching(current::toLogbookEntry).getOrElse {
            logbookError = it.message ?: "日志内容格式不正确"
            return
        }
        val draftUserId = activeUserId
        val generation = logbookGeneration.incrementAndGet()
        logbookBusy = true
        logbookError = ""
        executor.execute {
            runCatching { api.saveLogbook(entry) }
                .onSuccess {
                    mainHandler.post {
                        if (closed.get() || generation != logbookGeneration.get()) return@post
                        cache.clearDraft(draftUserId)
                        draft = null
                        logbookBusy = false
                        onSuccess()
                        loadLogbooks(reset = true)
                    }
                }
                .onFailure { postLogbookError(generation, it) }
        }
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
        if (presetBusy) return
        val generation = presetGeneration.incrementAndGet()
        presetBusy = true
        presetError = ""
        executor.execute {
            runCatching(api::getRadioPresets)
                .onSuccess { loaded ->
                    mainHandler.post {
                        if (closed.get() || generation != presetGeneration.get()) return@post
                        presets = loaded
                        presetOrderDirty = false
                        presetBusy = false
                    }
                }
                .onFailure { postPresetError(generation, it) }
        }
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
        ble.disconnect()
        navigation.reset()
        destination = navigation.current
        relayGeneration.incrementAndGet()
        logbookGeneration.incrementAndGet()
        presetGeneration.incrementAndGet()
        relayBusy = false
        logbookBusy = false
        presetBusy = false
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
        if (!closed.compareAndSet(false, true)) return
        ble.close()
        executor.shutdownNow()
    }

    private fun runLogbookMutation(block: () -> Any?, onSuccess: () -> Unit) {
        if (logbookBusy) return
        val generation = logbookGeneration.incrementAndGet()
        logbookBusy = true
        logbookError = ""
        executor.execute {
            runCatching(block)
                .onSuccess {
                    mainHandler.post {
                        if (closed.get() || generation != logbookGeneration.get()) return@post
                        logbookBusy = false
                        onSuccess()
                    }
                }
                .onFailure { postLogbookError(generation, it) }
        }
    }

    private fun runPresetMutation(block: () -> Any?, onSuccess: () -> Unit) {
        if (presetBusy) return
        val generation = presetGeneration.incrementAndGet()
        presetBusy = true
        presetError = ""
        executor.execute {
            runCatching(block)
                .onSuccess {
                    mainHandler.post {
                        if (closed.get() || generation != presetGeneration.get()) return@post
                        presetBusy = false
                        onSuccess()
                    }
                }
                .onFailure { postPresetError(generation, it) }
        }
    }

    private fun postRelayError(generation: Int, throwable: Throwable) {
        mainHandler.post {
            if (closed.get() || generation != relayGeneration.get()) return@post
            relayBusy = false
            relayError = throwable.message ?: "操作失败，请稍后重试"
        }
    }

    private fun postLogbookError(generation: Int, throwable: Throwable) {
        mainHandler.post {
            if (closed.get() || generation != logbookGeneration.get()) return@post
            logbookBusy = false
            logbookError = throwable.message ?: "操作失败，请稍后重试"
        }
    }

    private fun postPresetError(generation: Int, throwable: Throwable) {
        mainHandler.post {
            if (closed.get() || generation != presetGeneration.get()) return@post
            presetBusy = false
            presetError = throwable.message ?: "操作失败，请稍后重试"
        }
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
