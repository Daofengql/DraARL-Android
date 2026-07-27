package cn.silverdragon.draarl.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolBackStackTest {
    @Test
    fun `back returns through tool destinations before home`() {
        val stack = ToolBackStack()

        stack.open(ToolDestination.LOGBOOK)
        stack.open(ToolDestination.PRESETS)

        assertEquals(ToolDestination.PRESETS, stack.current)
        assertEquals(ToolDestination.LOGBOOK, stack.back())
        assertEquals(ToolDestination.HOME, stack.back())
        assertFalse(stack.canGoBack)
    }

    @Test
    fun `opening the same destination does not duplicate it`() {
        val stack = ToolBackStack()

        stack.open(ToolDestination.RELAYS)
        stack.open(ToolDestination.RELAYS)

        assertEquals(listOf(ToolDestination.HOME, ToolDestination.RELAYS), stack.snapshot())
        assertTrue(stack.canGoBack)
    }

    @Test
    fun `opening home resets nested tool state`() {
        val stack = ToolBackStack()
        stack.open(ToolDestination.LOGBOOK)
        stack.open(ToolDestination.PRESETS)

        stack.open(ToolDestination.HOME)

        assertEquals(listOf(ToolDestination.HOME), stack.snapshot())
    }
}
