package cn.silverdragon.draarl.auth

import cn.silverdragon.draarl.data.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountLoginPolicyTest {
    @Test
    fun `approved active account can log in`() {
        assertNull(accountLoginRejection(user(approvalStatus = 1, status = 1)))
    }

    @Test
    fun `pending account is rejected`() {
        assertEquals(
            "账号尚未审核，暂时无法登录",
            accountLoginRejection(user(approvalStatus = 0, status = 1)),
        )
    }

    @Test
    fun `rejected account includes review note`() {
        assertEquals(
            "账号审核未通过：呼号资料不完整",
            accountLoginRejection(
                user(approvalStatus = 2, status = 1, reviewNote = " 呼号资料不完整 "),
            ),
        )
    }

    @Test
    fun `disabled account is rejected before approval state`() {
        assertEquals(
            "账号已被封禁，无法登录",
            accountLoginRejection(user(approvalStatus = 0, status = 0)),
        )
    }

    @Test
    fun `unknown approval state is rejected`() {
        assertEquals(
            "账号审核状态异常，暂时无法登录",
            accountLoginRejection(user(approvalStatus = 9, status = 1)),
        )
    }

    private fun user(approvalStatus: Int, status: Int, reviewNote: String = "") = User(
        id = 1,
        username = "test",
        approvalStatus = approvalStatus,
        status = status,
        reviewNote = reviewNote,
    )
}
