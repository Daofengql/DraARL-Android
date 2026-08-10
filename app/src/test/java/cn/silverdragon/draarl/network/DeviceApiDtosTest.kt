package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceApiDtosTest {
    @Test
    fun `device parser keeps legacy identity and status fields compatible`() {
        val device = DeviceApiResponseMapper.device(
            response(
                """{"id":7,"owner_callsign":"BG7LEGACY","model":3,"online":true}"""
            )
        ).toDomain()

        assertEquals("BG7LEGACY", device.callsign)
        assertEquals(3, device.model)
        assertTrue(device.online)
    }

    private fun response(data: String) = JSONObject().put("code", 200).put("data", JSONObject(data))
}
