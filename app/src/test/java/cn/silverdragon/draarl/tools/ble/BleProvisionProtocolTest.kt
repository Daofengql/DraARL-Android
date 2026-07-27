package cn.silverdragon.draarl.tools.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleProvisionProtocolTest {
    @Test
    fun `chunks and reassembles an rpc payload`() {
        val payload = """{"id":7,"cmd":"set_wifi","data":{"ssid":"DraARL-Test-Network"}}"""
        val frames = BleProvisionProtocol.chunkPayload(payload)
        val assembler = BleProvisionProtocol.FrameAssembler()

        frames.dropLast(1).forEach { assertNull(assembler.append(it)) }
        val assembled = assembler.append(frames.last())!!

        assertEquals(payload, assembled)
    }

    @Test
    fun `parses compact status notification`() {
        val status = BleProvisionProtocol.parseStatus("w3;b2;a1;r-57", BleProvisionStatus())

        assertNotNull(status)
        assertEquals("已连接", status?.wifiState)
        assertTrue(status?.authenticated == true)
        assertEquals(-57, status?.rssi)
        assertFalse(status?.error?.isNotBlank() == true)
    }
}
