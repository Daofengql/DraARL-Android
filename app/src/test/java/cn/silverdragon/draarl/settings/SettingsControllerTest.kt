package cn.silverdragon.draarl.settings

import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.data.StorageUsage
import cn.silverdragon.draarl.radio.TransmitTailTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsControllerTest {
    @Test
    fun preferenceUpdatesPersistAndPublishCompleteAudioSettings() = runBlocking {
        val store = FakeStore()
        val effects = FakeEffects()
        val controller = controller(store = store, effects = effects)

        controller.onEvent(SettingsEvent.ToggleMuted)
        controller.onEvent(SettingsEvent.TogglePlaybackDenoise)
        controller.onEvent(SettingsEvent.PlaybackDenoiseStrengthChanged(120))
        controller.onEvent(SettingsEvent.TransmitTimeoutChanged(5))
        controller.onEvent(SettingsEvent.TransmitTailToneChanged(TransmitTailTone.DOUBLE_BEEP))
        controller.onEvent(SettingsEvent.TransmitTailToneToRemoteChanged(false))
        controller.onEvent(SettingsEvent.ReceiveTailToneChanged(true))
        controller.onEvent(SettingsEvent.DisplayScaleChanged(AppDisplayScale.COMFORTABLE))
        controller.onEvent(SettingsEvent.ThemeModeChanged(AppThemeMode.DARK))

        assertEquals(
            RadioAudioSettings(
                muted = true,
                playbackDenoiseEnabled = true,
                playbackDenoiseStrengthPercent = 100,
                transmitTimeoutSeconds = 10,
                transmitTailTone = TransmitTailTone.DOUBLE_BEEP,
                transmitTailToneToRemoteEnabled = false,
                receiveTailToneEnabled = true
            ),
            effects.audioSettings.last()
        )
        assertEquals(AppDisplayScale.COMFORTABLE, controller.uiState.appDisplayScale)
        assertEquals(AppThemeMode.DARK, controller.uiState.appThemeMode)
        assertEquals(9, store.changes.size)
    }

    @Test
    fun overlayRequiresApprovalAndPermissionAndReconcilesRevocation() = runBlocking {
        val store = FakeStore()
        val effects = FakeEffects(overlayAllowed = false, canDrawOverlay = true)
        val controller = controller(store = store, effects = effects)

        assertFalse(controller.setPttOverlayEnabled(true))
        assertEquals("账号审核通过后才能开启悬浮 PTT", effects.notices.single())

        effects.overlayAllowed = true
        effects.canDrawOverlay = false
        assertFalse(controller.setPttOverlayEnabled(true))

        effects.canDrawOverlay = true
        assertTrue(controller.setPttOverlayEnabled(true))
        assertTrue(controller.uiState.pttOverlayEnabled)

        effects.canDrawOverlay = false
        controller.reconcilePttOverlayPermission()
        assertFalse(controller.uiState.pttOverlayEnabled)
        assertEquals(2, effects.overlaySyncs)
        assertEquals(
            listOf(SettingsChange.PttOverlayEnabled(true), SettingsChange.PttOverlayEnabled(false)),
            store.changes
        )
    }

    @Test
    fun autoUpdateCheckRunsOnlyWhenPreferenceChangesToEnabled() = runBlocking {
        val store = FakeStore()
        val effects = FakeEffects()
        val controller = controller(store = store, effects = effects)

        controller.onEvent(SettingsEvent.AutoCheckAppUpdateChanged(false))
        controller.onEvent(SettingsEvent.AutoCheckAppUpdateChanged(true))
        controller.onEvent(SettingsEvent.AutoCheckAppUpdateChanged(true))

        assertEquals(1, effects.updateChecks)
        assertEquals(
            listOf(
                SettingsChange.AutoCheckAppUpdate(false),
                SettingsChange.AutoCheckAppUpdate(true)
            ),
            store.changes
        )
    }

    @Test
    fun storageRefreshAndMessageClearCoordinateStateOnOwningScope() = runBlocking {
        val storage =
            FakeStorage(StorageUsage(audioBytes = 10, avatarBytes = 20, messageBytes = 30))
        val effects = FakeEffects()
        val controller = controller(storage = storage, effects = effects)

        controller.refreshStorageUsage()
        yield()
        assertEquals(60, controller.uiState.storageUsage.totalBytes)
        assertFalse(controller.uiState.storageBusy)

        storage.usage = StorageUsage(audioBytes = 1, avatarBytes = 2, messageBytes = 0)
        controller.clearStorage(StorageCategory.MESSAGES)
        yield()

        assertEquals(listOf(StorageCategory.MESSAGES), storage.cleared)
        assertEquals(1, effects.beforeMessageClear)
        assertEquals(1, effects.afterMessageClear)
        assertEquals(3, controller.uiState.storageUsage.totalBytes)
        assertFalse(controller.uiState.storageBusy)
    }

    @Test
    fun storageFailureRestoresIdleStateAndReportsError() = runBlocking {
        val storage = FakeStorage(error = IllegalStateException("disk unavailable"))
        val effects = FakeEffects()
        val controller = controller(storage = storage, effects = effects)

        controller.clearStorage(StorageCategory.AUDIO)
        yield()

        assertFalse(controller.uiState.storageBusy)
        assertEquals("清理缓存失败：disk unavailable", effects.notices.single())
    }

    private fun CoroutineScope.controller(
        store: FakeStore = FakeStore(),
        storage: FakeStorage = FakeStorage(),
        effects: FakeEffects = FakeEffects()
    ) = SettingsController(
        store = store,
        storage = storage,
        effects = effects,
        scope = this,
        ioDispatcher = Dispatchers.Unconfined
    )

    private class FakeStore(private val initial: SettingsUiState = SettingsUiState()) : SettingsStore {
        val changes = mutableListOf<SettingsChange>()

        override fun load(canDrawPttOverlay: Boolean): SettingsUiState = initial.copy(
            pttOverlayEnabled = initial.pttOverlayEnabled && canDrawPttOverlay
        )

        override fun write(change: SettingsChange) {
            changes += change
        }
    }

    private class FakeStorage(var usage: StorageUsage = StorageUsage(), private val error: Throwable? = null) :
        SettingsStorage {
        val cleared = mutableListOf<StorageCategory>()

        override fun calculateUsage(): StorageUsage = usage

        override fun clear(category: StorageCategory) {
            error?.let { throw it }
            cleared += category
        }
    }

    private class FakeEffects(var overlayAllowed: Boolean = true, var canDrawOverlay: Boolean = true) :
        SettingsEffects {
        val audioSettings = mutableListOf<RadioAudioSettings>()
        val notices = mutableListOf<String>()
        var overlaySyncs = 0
        var updateChecks = 0
        var beforeMessageClear = 0
        var afterMessageClear = 0

        override fun isPttOverlayAllowed(): Boolean = overlayAllowed
        override fun canDrawPttOverlay(): Boolean = canDrawOverlay
        override fun applyRadioAudioSettings(settings: RadioAudioSettings) {
            audioSettings += settings
        }

        override fun syncPttOverlay() {
            overlaySyncs++
        }

        override fun requestAppUpdateCheck() {
            updateChecks++
        }
        override fun beforeMessageCacheClear() {
            beforeMessageClear++
        }

        override fun afterMessageCacheClear() {
            afterMessageClear++
        }

        override fun friendlyError(error: Throwable): String = error.message.orEmpty()
        override fun showNotice(message: String) {
            notices += message
        }
    }
}
