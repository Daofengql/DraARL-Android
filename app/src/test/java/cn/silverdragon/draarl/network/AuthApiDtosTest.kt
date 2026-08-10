package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiDtosTest {
    @Test
    fun `captcha parser keeps legacy image and compatibility defaults`() {
        val captcha = AuthApiResponseMapper.captcha(
            JSONObject("""{"data":{"captcha_id":"captcha-1","image_base64":"legacy-image"}}""")
        )
        val registration = AuthApiResponseMapper.registrationConfig(JSONObject("""{"data":{}}"""))

        assertEquals("captcha-1", captcha.id)
        assertEquals("legacy-image", captcha.imageBase64)
        assertEquals(300, captcha.expiresInSeconds)
        assertTrue(registration.requiresEmailVerification)
    }

    @Test
    fun `login parser reports missing token as mapping exception`() {
        val error = assertThrows(ApiException::class.java) {
            AuthApiResponseMapper.login(
                JSONObject("""{"data":{"user":{"id":1,"username":"operator"}}}""")
            )
        }

        assertEquals(500, error.code)
        assertTrue(error.message.contains("token"))
    }
}
