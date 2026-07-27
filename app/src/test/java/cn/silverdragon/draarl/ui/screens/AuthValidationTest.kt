package cn.silverdragon.draarl.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test
    fun `registration validates each step independently`() {
        assertEquals("用户名长度需为 3-20 个字符", RegistrationValidation.basicInfo("ab", "BG7ABC"))
        assertEquals("呼号需以字母开头，3-10 个字符", RegistrationValidation.basicInfo("tester", "12"))
        assertNull(RegistrationValidation.basicInfo("tester_1", "BG7ABC"))
        assertEquals("邮箱格式不正确", RegistrationValidation.contactInfo("bad", ""))
        assertNull(RegistrationValidation.contactInfo("user@example.com", "13800138000"))
    }

    @Test
    fun `password reset requires valid email captcha and matching password`() {
        assertEquals("请输入图片验证码", PasswordResetValidation.emailStep("user@example.com", ""))
        assertNull(PasswordResetValidation.emailStep("user@example.com", "1234"))
        assertEquals("两次密码不一致", RegistrationValidation.password("secret1", "secret2"))
        assertNull(RegistrationValidation.password("secret1", "secret1"))
    }
}
