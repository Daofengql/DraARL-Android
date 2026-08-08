package cn.silverdragon.draarl.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewLifecycleControllerTest {
    @Test
    fun `view resumes only while host and panel are active`() {
        val events = mutableListOf<String>()
        val controller = controller(events)

        controller.onHostResume()
        controller.setActive(true)
        controller.setActive(false)
        controller.setActive(true)
        controller.onHostPause()

        assertEquals(listOf("resume", "pause", "resume", "pause"), events)
    }

    @Test
    fun `duplicate state changes do not repeat lifecycle callbacks`() {
        val events = mutableListOf<String>()
        val controller = controller(events)

        controller.setActive(true)
        controller.setActive(true)
        controller.onHostResume()
        controller.onHostResume()
        controller.onHostPause()
        controller.onHostPause()

        assertEquals(listOf("resume", "pause"), events)
    }

    @Test
    fun `closing pauses an active view and rejects later changes`() {
        val events = mutableListOf<String>()
        val controller = controller(events)

        controller.setActive(true)
        controller.onHostResume()
        controller.close()
        controller.onHostResume()
        controller.setActive(true)

        assertEquals(listOf("resume", "pause"), events)
    }

    private fun controller(events: MutableList<String>) = MapViewLifecycleController(
        resumeView = { events += "resume" },
        pauseView = { events += "pause" },
    )
}
