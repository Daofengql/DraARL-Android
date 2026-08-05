package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RadioRoutingTest {
    @Test
    fun `normalization always includes the transmit group`() {
        val routing = RadioRouting.normalize(1002, listOf(1003, 1001, 1003))

        assertEquals(1002, routing.txGroupId)
        assertEquals(listOf(1001, 1002, 1003), routing.rxGroupIds)
    }

    @Test
    fun `rejects more than sixteen receive groups`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadioRouting.normalize(1001, (1001..1017).toList())
        }
    }
}
