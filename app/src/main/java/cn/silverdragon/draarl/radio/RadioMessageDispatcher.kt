package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioMessage

internal class RadioMessageDispatcher(capacity: Int) {
    private val lock = Any()
    private val buffer = RadioMessageBuffer(capacity)

    @Volatile
    var listener: RadioServiceListener? = null
        private set

    fun dispatch(message: RadioMessage): RadioServiceListener? = synchronized(lock) {
        listener ?: run {
            buffer.offer(message)
            null
        }
    }

    fun setListener(value: RadioServiceListener?): List<RadioMessage> = synchronized(lock) {
        listener = value
        if (value == null) emptyList() else buffer.drain()
    }

    fun clear() = synchronized(lock) {
        listener = null
        buffer.clear()
    }
}
