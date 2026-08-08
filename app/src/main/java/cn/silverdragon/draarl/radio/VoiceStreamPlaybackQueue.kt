package cn.silverdragon.draarl.radio

import java.util.ArrayDeque

internal class VoiceStreamPlaybackQueue {
    private val pending = ArrayDeque<VoiceStreamKey>()
    var active: VoiceStreamKey? = null
        private set

    fun onStream(key: VoiceStreamKey): Boolean {
        if (active == key) return true
        if (active == null) {
            active = key
            return true
        }
        if (!pending.contains(key)) pending.addLast(key)
        return false
    }

    fun remove(key: VoiceStreamKey) {
        if (active == key) active = null
        pending.remove(key)
    }

    fun advance(): VoiceStreamKey? {
        if (active != null) return null
        active = pending.pollFirst()
        return active
    }

    fun evictOldestPending(): VoiceStreamKey? = pending.pollFirst()

    fun clear() {
        active = null
        pending.clear()
    }
}
