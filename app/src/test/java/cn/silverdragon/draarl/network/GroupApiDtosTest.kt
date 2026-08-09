package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupApiDtosTest {
    @Test
    fun `group parser reads canonical owner fields`() {
        val group = GroupApiResponseMapper.group(
            response(
                """{"id":7,"name":"Local","owner_id":42,"owner_callsign":"BG7OWNER"}"""
            )
        ).toDomain()

        assertEquals(42, group.ownerId)
        assertEquals("BG7OWNER", group.ownerCallsign)
    }

    @Test
    fun `group parser keeps legacy misspelled owner fields compatible`() {
        val group = GroupApiResponseMapper.group(
            response(
                """{"id":7,"name":"Local","ower_id":41,"ower_callsign":"BG7LEGACY"}"""
            )
        ).toDomain()

        assertEquals(41, group.ownerId)
        assertEquals("BG7LEGACY", group.ownerCallsign)
    }

    @Test
    fun `canonical owner fields take precedence over legacy aliases`() {
        val group = GroupApiResponseMapper.group(
            response(
                """{"id":7,"owner_id":42,"owner_callsign":"BG7OWNER","ower_id":41,"ower_callsign":"BG7LEGACY"}"""
            )
        ).toDomain()

        assertEquals(42, group.ownerId)
        assertEquals("BG7OWNER", group.ownerCallsign)
    }

    private fun response(data: String) = JSONObject().put("code", 200).put("data", JSONObject(data))
}
