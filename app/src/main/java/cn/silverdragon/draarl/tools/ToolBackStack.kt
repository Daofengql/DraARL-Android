package cn.silverdragon.draarl.tools

internal class ToolBackStack {
    private val entries = mutableListOf(ToolDestination.HOME)

    val current: ToolDestination
        get() = entries.last()

    val canGoBack: Boolean
        get() = entries.size > 1

    fun open(destination: ToolDestination) {
        if (destination == ToolDestination.HOME) {
            reset()
        } else if (destination != current) {
            entries += destination
        }
    }

    fun back(): ToolDestination {
        if (canGoBack) entries.removeAt(entries.lastIndex)
        return current
    }

    fun reset() {
        entries.clear()
        entries += ToolDestination.HOME
    }

    internal fun snapshot(): List<ToolDestination> = entries.toList()
}
