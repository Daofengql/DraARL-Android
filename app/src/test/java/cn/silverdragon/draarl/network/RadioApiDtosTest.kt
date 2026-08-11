package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioApiDtosTest {
    @Test
    fun `access point parser filters invalid endpoints and supplies display defaults`() {
        val accessPoints = RadioSessionResponseMapper.accessPoints(
            JSONObject(
                """{"data":{"items":[{"udp_host":"edge.example.test","udp_port":60050},{"udp_host":"bad.example.test","udp_port":0}]}}"""
            )
        )

        assertEquals(1, accessPoints.size)
        assertEquals("edge.example.test:60050", accessPoints.single().id)
        assertEquals("edge.example.test", accessPoints.single().displayName)
        assertEquals(100, accessPoints.single().priority)
    }

    @Test
    fun `radio session parser rejects a missing session identity`() {
        val error = assertThrows(ApiException::class.java) {
            RadioSessionResponseMapper.session(JSONObject("""{"data":{"tx_group_id":7}}"""))
        }

        assertEquals(500, error.code)
        assertTrue(error.message.contains("session_id"))
    }

    @Test
    fun `message domain mapper discards a cleartext audio url`() {
        val message = RadioMessageResponseMapper.message(
            JSONObject("""{"data":{"id":9,"audio_url":"http://cdn.example.test/voice.raw"}}""")
        ).toDomain("https://api.example.test")

        assertEquals("", message.audioUrl)
    }

    @Test
    fun `message parser keeps the automatic broadcast marker`() {
        val message = RadioMessageResponseMapper.message(
            JSONObject("""{"data":{"id":10,"is_auto_broadcast":true}}""")
        ).toDomain("https://api.example.test")

        assertTrue(message.isAutoBroadcast)
    }
}
