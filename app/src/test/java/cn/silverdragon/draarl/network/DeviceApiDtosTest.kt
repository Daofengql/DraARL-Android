package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceApiDtosTest {
    @Test
    fun configurationReadsCurrentCapabilitiesAndIgnoresRemovedProtection() {
        val config = DeviceApiResponseMapper.configuration(
            response("""{"adc_volume":"80","rf_guard_enabled":"1"}""")
                .put("dev_model", 2)
                .put("supported_keys", org.json.JSONArray(listOf("adc_volume", "sql_active_high")))
        )
        assertEquals(2, config.model)
        assertEquals(mapOf("adc_volume" to "80"), config.values)
        assertEquals(setOf("adc_volume", "sql_active_high"), config.supportedKeys)
        val legacy = DeviceApiResponseMapper.configuration(response("""{"rx_freq":"439500000"}"""))
        assertNull(legacy.model)
        assertNull(legacy.supportedKeys)
        assertEquals("439500000", legacy.values["rx_freq"])
    }

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
