package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileApiDtosTest {
    @Test
    fun `upload parser accepts legacy top level file url`() {
        val uploaded = ProfileApiResponseMapper.uploadedFile(
            JSONObject("""{"file_url":"/uploads/avatar/operator.jpg"}""")
        )

        assertEquals("/uploads/avatar/operator.jpg", uploaded.url)
    }

    @Test
    fun `upload parser rejects a response without a file address`() {
        val error = assertThrows(ApiException::class.java) {
            ProfileApiResponseMapper.uploadedFile(JSONObject("""{"data":{}}"""))
        }

        assertEquals(500, error.code)
        assertTrue(error.message.contains("文件地址"))
    }
}
