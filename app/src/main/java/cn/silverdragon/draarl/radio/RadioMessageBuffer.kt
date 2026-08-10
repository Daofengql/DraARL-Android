package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioMessage
import java.util.ArrayDeque

internal class RadioMessageBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val messages = ArrayDeque<RadioMessage>(capacity)

    @Synchronized
    fun offer(message: RadioMessage) {
        if (messages.size >= capacity) messages.removeFirst()
        messages.addLast(message)
    }

    @Synchronized
    fun drain(): List<RadioMessage> = buildList(messages.size) {
        while (messages.isNotEmpty()) add(messages.removeFirst())
    }

    @Synchronized
    fun clear() = messages.clear()
}
