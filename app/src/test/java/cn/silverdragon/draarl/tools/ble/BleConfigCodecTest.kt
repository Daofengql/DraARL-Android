package cn.silverdragon.draarl.tools.ble

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConfigCodecTest {
    @Test
    fun networkModeReadsHardwareCapabilityAndOnlyWritesAudioAndIo() {
        val config = BleConfigCodec.parse(
            JSONObject(
                """{"dev_model":2,"radio":{"adc_volume":73,"sql_active_high":true},"server":{"access_point":{"udp_host":"central.test","udp_port":60051}}}"""
            )
        )
        assertEquals(2, config.deviceModel)
        assertEquals("central.test:60051", config.server.activeEndpoint)
        val payload = BleConfigCodec.radioPayload(config.deviceModel, config.radio)
        assertEquals(73, payload.getInt("adc_volume"))
        assertTrue(payload.getBoolean("sql_active_high"))
        assertFalse(payload.has("tx_frequency_mhz"))
        assertFalse(payload.has("rf_guard_enabled"))
        val rf = BleConfigCodec.radioPayload(1, BleRadioConfig(txToneType = "CDCSS_N", txToneIndex = 4))
        assertEquals("CDCSS_N", rf.getString("tx_tone_type"))
        assertEquals(4, rf.getInt("tx_tone_index"))
        assertFalse(rf.has("sql_active_high"))
    }

    @Test
    fun apiOnlySetupSupportsDynamicBindingWithoutObsoleteUdpFields() {
        val payload = BleConfigCodec.serverPayload(
            BleServerConfig(httpApiBaseUrl = "https://central.test/api", nodeSsid = 106)
        )
        assertEquals("", payload.getString("account"))
        assertFalse(payload.has("udp_host"))
        assertFalse(payload.has("udp_port"))
        assertEquals(106, payload.getInt("node_ssid"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun reservedGhostSsidCannotBeProvisionedAsPhysicalDevice() {
        BleConfigCodec.serverPayload(BleServerConfig(nodeSsid = 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun partialCredentialsAreRejected() {
        BleConfigCodec.serverPayload(BleServerConfig(account = "owner"))
    }
}
