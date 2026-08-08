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

    @Test
    fun `switching transmit group replaces the only receive group`() {
        val routing = RadioRouting.forTransmitGroupSwitch(
            currentTxGroupId = 1001,
            currentRxGroupIds = listOf(1001),
            nextTxGroupId = 1002,
        )

        assertEquals(1002, routing.txGroupId)
        assertEquals(listOf(1002), routing.rxGroupIds)
    }

    @Test
    fun `switching transmit group preserves multiple receive groups and appends the new group`() {
        val routing = RadioRouting.forTransmitGroupSwitch(
            currentTxGroupId = 1001,
            currentRxGroupIds = listOf(1002, 1001),
            nextTxGroupId = 1003,
        )

        assertEquals(1003, routing.txGroupId)
        assertEquals(listOf(1001, 1002, 1003), routing.rxGroupIds)
    }

    @Test
    fun `switching to an existing receive group keeps routing deduplicated`() {
        val routing = RadioRouting.forTransmitGroupSwitch(
            currentTxGroupId = 1001,
            currentRxGroupIds = listOf(1001, 1002, 1002),
            nextTxGroupId = 1002,
        )

        assertEquals(1002, routing.txGroupId)
        assertEquals(listOf(1001, 1002), routing.rxGroupIds)
    }

    @Test
    fun `switching a full multi receive routing rejects a seventeenth group`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadioRouting.forTransmitGroupSwitch(
                currentTxGroupId = 1001,
                currentRxGroupIds = (1001..1016).toList(),
                nextTxGroupId = 1017,
            )
        }
    }
}
