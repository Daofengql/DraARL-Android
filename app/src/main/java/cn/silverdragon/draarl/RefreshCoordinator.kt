package cn.silverdragon.draarl

/** Coalesces repeated refresh requests while ensuring only the newest result is applied. */
internal class RefreshCoordinator {
    private var generation = 0
    private var activeGeneration: Int? = null
    private var pending = false

    @Synchronized
    fun request(): Int? {
        if (activeGeneration != null) {
            pending = true
            return null
        }
        return nextGeneration().also { activeGeneration = it }
    }

    @Synchronized
    fun complete(completedGeneration: Int): RefreshDecision {
        if (activeGeneration != completedGeneration) return RefreshDecision.Stale
        if (pending) {
            pending = false
            val next = nextGeneration()
            activeGeneration = next
            return RefreshDecision(applyResults = false, nextGeneration = next, isIdle = false)
        }
        activeGeneration = null
        return RefreshDecision(applyResults = true, nextGeneration = null, isIdle = true)
    }

    @Synchronized
    fun cancel() {
        generation++
        activeGeneration = null
        pending = false
    }

    private fun nextGeneration(): Int {
        generation = if (generation == Int.MAX_VALUE) 1 else generation + 1
        return generation
    }
}

internal data class RefreshDecision(
    val applyResults: Boolean,
    val nextGeneration: Int?,
    val isIdle: Boolean,
) {
    companion object {
        val Stale = RefreshDecision(applyResults = false, nextGeneration = null, isIdle = false)
    }
}
