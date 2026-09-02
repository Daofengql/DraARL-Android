package cn.silverdragon.draarl.radio

internal class DefaultNetworkState<T>(initialNetwork: T?) {
    private var currentNetwork = initialNetwork
    private var lastAvailableNetwork = initialNetwork
    private var baselineEstablished = initialNetwork != null

    fun onAvailable(network: T): Boolean {
        val changed = baselineEstablished && lastAvailableNetwork != network
        currentNetwork = network
        lastAvailableNetwork = network
        baselineEstablished = true
        return changed
    }

    fun onLost(network: T): Boolean {
        if (currentNetwork != network) return false
        currentNetwork = null
        return true
    }

    fun isUnavailable(): Boolean = currentNetwork == null
}
