package cn.silverdragon.draarl.maps

internal class MapViewLifecycleController(
    private val resumeView: () -> Unit,
    private val pauseView: () -> Unit,
) {
    private var active = false
    private var hostResumed = false
    private var viewResumed = false
    private var closed = false

    fun setActive(value: Boolean) {
        if (closed || active == value) return
        active = value
        synchronizeView()
    }

    fun onHostResume() {
        if (closed || hostResumed) return
        hostResumed = true
        synchronizeView()
    }

    fun onHostPause() {
        if (closed || !hostResumed) return
        hostResumed = false
        synchronizeView()
    }

    fun close() {
        if (closed) return
        active = false
        hostResumed = false
        synchronizeView()
        closed = true
    }

    private fun synchronizeView() {
        val shouldResume = active && hostResumed
        if (shouldResume == viewResumed) return
        viewResumed = shouldResume
        if (shouldResume) resumeView() else pauseView()
    }
}
