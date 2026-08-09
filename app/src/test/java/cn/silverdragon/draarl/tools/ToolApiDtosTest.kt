package cn.silverdragon.draarl.tools

import cn.silverdragon.draarl.network.ToolApiResponseMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolApiDtosTest {
    @Test
    fun `relay parser follows server legacy field names`() {
        val relay = ToolApiResponseMapper.relays(
            response(
                """{"items":[{"id":7,"name":"广州中继","up_freq":"439.500","down_freq":"438.500","send_ctss":"88.5","recive_ctss":"88.5","ower_callsign":"BG7TEST","location":"广东省 广州市","status":1,"note":"测试"}]}"""
            )
        ).single().toDomain()

        assertEquals("439.500", relay.uplinkFrequency)
        assertEquals("88.5", relay.receiveTone)
        assertEquals("BG7TEST", relay.ownerCallsign)
    }

    @Test
    fun `logbook parser preserves nullable powers and station details`() {
        val logbook = ToolApiResponseMapper.logbook(
            response(
                """{"id":9,"my_callsign":"BG7MINE","time_utc":"2026-07-27 12:00:00","tx_frequency":439.5,"rx_frequency":438.5,"mode":"FM","callsign":"BG7THEIR","their_power":null,"their_radio":"HT","my_power":25,"my_antenna":"Vertical"}"""
            )
        ).toDomain()

        assertNull(logbook.theirPower)
        assertEquals(25, logbook.myPower)
        assertEquals("HT", logbook.theirRadio)
        assertEquals("Vertical", logbook.myAntenna)
    }

    @Test
    fun `preset parser maps server ordering`() {
        val preset = ToolApiResponseMapper.preset(
            response(
                """{"id":3,"name":"车台","radio":"FTM-500","antenna":"SG7900","power":50,"qth":"深圳","sort_order":2}"""
            )
        ).toDomain()

        assertEquals(2, preset.sortOrder)
        assertEquals(50, preset.power)
        assertEquals("FTM-500", preset.radio)
    }

    private fun response(data: String) = JSONObject().put("code", 200).put("data", JSONObject(data))
}
