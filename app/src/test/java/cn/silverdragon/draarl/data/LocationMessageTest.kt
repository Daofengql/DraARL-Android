package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationMessageTest {
    @Test
    fun `location tuple preserves kind coordinates and altitude`() {
        val value = Wgs84LocationMessage(LocationMessageKind.CURRENT, 23.123456, 113.654321, 42.5)
        assertEquals(value, decodeLocationMessage(encodeLocationMessage(value)))
    }

    @Test
    fun `pinned location can carry an unavailable altitude`() {
        val value = Wgs84LocationMessage(LocationMessageKind.PINNED, 39.908823, 116.39747, null)
        assertEquals(value, decodeLocationMessage(encodeLocationMessage(value)))
    }

    @Test
    fun `ordinary text is not parsed as a location`() {
        assertNull(decodeLocationMessage("WGS-84 23.1, 113.2"))
    }
}
