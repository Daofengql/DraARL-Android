package cn.silverdragon.draarl.profile

import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ProfileApi
import cn.silverdragon.draarl.network.ProfileUpdateRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileControllerTest {
    @Test
    fun closeDropsLateProfileUpdateResult() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val updatedUser = USER.copy(nickname = "迟到结果")
        val api = FakeProfileApi(
            updateProfileAction = {
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
                updatedUser
            }
        )
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val users = mutableListOf<User>()
        val notices = mutableListOf<String>()
        var successCalls = 0
        val controller = controller(
            api = api,
            ioDispatcher = dispatcher,
            updateUser = users::add,
            showNotice = notices::add
        )
        try {
            controller.updateProfile("新昵称", "13800000000", "杭州", "简介") {
                successCalls++
            }
            yield()
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertTrue(controller.busy)

            controller.close()
            assertFalse(controller.busy)
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertTrue(users.isEmpty())
            assertTrue(notices.isEmpty())
            assertEquals(0, successCalls)
            assertFalse(controller.busy)
        } finally {
            release.countDown()
            controller.close()
            dispatcher.close()
        }
    }

    @Test
    fun verifiedEmailRequiresCurrentEmailVerificationBeforeRequest() = runBlocking {
        val api = FakeProfileApi()
        val notices = mutableListOf<String>()
        val controller = controller(
            api = api,
            currentUser = { USER.copy(email = "old@example.com", emailVerified = true) },
            showNotice = notices::add
        )

        controller.changeEmail(
            oldSessionId = "",
            oldCode = "",
            newSessionId = "new-session",
            newCode = "222222"
        )

        assertEquals(listOf("请先完成当前邮箱验证"), notices)
        assertEquals(0, api.changeEmailCalls)
        assertFalse(controller.busy)
        controller.close()
    }

    private fun CoroutineScope.controller(
        api: ProfileApi,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        currentUser: () -> User? = { USER },
        updateUser: (User) -> Unit = {},
        showNotice: (String) -> Unit = {}
    ) = ProfileController(
        api = api,
        scope = this,
        ioDispatcher = ioDispatcher,
        currentUser = currentUser,
        updateUser = updateUser,
        showNotice = showNotice,
        friendlyError = { it.message ?: "request failed" }
    )

    private companion object {
        val USER = User(id = 1, username = "tester")
    }
}

private class FakeProfileApi(private val updateProfileAction: (ProfileUpdateRequest) -> User = { USER_RESULT }) :
    ProfileApi {
    var changeEmailCalls = 0

    override fun updateProfile(request: ProfileUpdateRequest): User = updateProfileAction(request)

    override fun changeEmail(oldSessionId: String, oldCode: String, newSessionId: String, newCode: String): User {
        changeEmailCalls++
        return USER_RESULT
    }

    override fun getMe(updateSession: Boolean): User = error("Unexpected profile request")

    override fun acceptCurrentUser(user: User) = Unit

    override fun uploadFile(fileBytes: ByteArray, fileName: String, fileType: String): String =
        error("Unexpected upload request")

    override fun changePassword(oldPassword: String, newPassword: String) = error("Unexpected password request")

    private companion object {
        val USER_RESULT = User(id = 1, username = "tester")
    }
}

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking API call that cannot be cancelled cooperatively.
        }
    }
}
