package cn.silverdragon.draarl.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserJsonMapperTest {
    @Test
    fun `user mapper keeps role avatar and account defaults compatible`() {
        val mapper = UserJsonMapper { "https://api.example.test" }
        val stringRole = mapper.fromJson(
            JSONObject(
                """{"id":1,"username":"operator","roles":"admin,user","avatar_thumb":"/avatars/operator.jpg","last_group_id":0}"""
            )
        )
        val arrayRole = mapper.fromJson(
            JSONObject()
                .put("id", 2)
                .put("username", "reviewer")
                .put("roles", JSONArray().put("reviewer"))
        )
        val legacyAdmin = mapper.fromJson(
            JSONObject("""{"id":3,"username":"legacy","isAdmin":true}""")
        )

        assertEquals("admin", stringRole.role)
        assertEquals("https://api.example.test/avatars/operator.jpg", stringRole.avatarUrl)
        assertEquals(999, stringRole.lastGroupId)
        assertEquals(1, stringRole.status)
        assertEquals("reviewer", arrayRole.role)
        assertEquals("admin", legacyAdmin.role)
    }

    @Test
    fun `user mapper rejects a missing username with ApiException`() {
        val error = assertThrows(ApiException::class.java) {
            UserJsonMapper { "https://api.example.test" }.fromJson(JSONObject("""{"id":1}"""))
        }

        assertEquals(500, error.code)
        assertTrue(error.message.contains("username"))
    }
}
