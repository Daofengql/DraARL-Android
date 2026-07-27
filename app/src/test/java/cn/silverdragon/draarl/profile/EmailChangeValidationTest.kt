package cn.silverdragon.draarl.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailChangeValidationTest {
    @Test
    fun `verified current email requires both old and new verification`() {
        assertEquals(
            "请先完成当前邮箱验证",
            EmailChangeValidation.validate(true, "", "", "new-session", "222222"),
        )
        assertNull(
            EmailChangeValidation.validate(true, "old-session", "111111", "new-session", "222222"),
        )
    }

    @Test
    fun `account without verified email only requires new verification`() {
        assertNull(EmailChangeValidation.validate(false, "", "", "new-session", "222222"))
        assertEquals(
            "请先向新邮箱发送验证码",
            EmailChangeValidation.validate(false, "", "", "", "222222"),
        )
    }

    @Test
    fun `request json uses the server double verification field names`() {
        val body = emailChangeRequestJson("old-session", " 111111 ", "new-session", " 222222 ")

        assertEquals("old-session", body.getString("old_session_id"))
        assertEquals("111111", body.getString("old_code"))
        assertEquals("new-session", body.getString("new_session_id"))
        assertEquals("222222", body.getString("new_code"))
    }
}
