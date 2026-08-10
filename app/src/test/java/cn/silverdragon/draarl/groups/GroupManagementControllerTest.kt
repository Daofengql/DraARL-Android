package cn.silverdragon.draarl.groups

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.network.GroupUpdateRequest
import cn.silverdragon.draarl.network.GroupsApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupManagementControllerTest {
    @Test
    fun joinAndLeavePublishNoticeAndRefresh() = runBlocking {
        val api = FakeGroupsApi()
        val fixture = fixture(this, api)
        try {
            fixture.controller.join(GROUP, "secret")
            awaitCondition { fixture.refreshCalls.get() == 1 }
            fixture.controller.leave(GROUP)
            awaitCondition { fixture.refreshCalls.get() == 2 }

            assertEquals(listOf(1 to "secret"), api.joinCalls)
            assertEquals(listOf(1), api.leaveCalls)
            assertEquals(listOf("已加入 测试群组", "已退出 测试群组"), fixture.notices)
            assertFalse(fixture.controller.busy)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun resetDropsLateJoinResultAndRestoresIdleState() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val api = FakeGroupsApi(
            joinAction = { _, _ ->
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
            }
        )
        val fixture = fixture(this, api)
        try {
            fixture.controller.join(GROUP, "secret")
            awaitCondition { started.count == 0L }
            assertTrue(fixture.controller.busy)

            fixture.controller.reset()
            assertFalse(fixture.controller.busy)
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertTrue(fixture.notices.isEmpty())
            assertEquals(0, fixture.refreshCalls.get())
            assertFalse(fixture.controller.busy)
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun closeDropsLateJoinResultWithoutNoticeOrRefresh() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val api = FakeGroupsApi(
            joinAction = { _, _ ->
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
            }
        )
        val fixture = fixture(this, api)
        try {
            fixture.controller.join(GROUP, "secret")
            awaitCondition { started.count == 0L }
            assertTrue(fixture.controller.busy)

            fixture.controller.close()
            assertFalse(fixture.controller.busy)
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertTrue(fixture.notices.isEmpty())
            assertEquals(0, fixture.refreshCalls.get())
            assertFalse(fixture.controller.busy)
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    private fun fixture(scope: CoroutineScope, api: FakeGroupsApi): Fixture {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val notices = mutableListOf<String>()
        val refreshCalls = AtomicInteger(0)
        return Fixture(
            controller = GroupManagementController(
                api = api,
                scope = scope,
                ioDispatcher = dispatcher,
                currentGroups = { emptyList() },
                updateGroups = {},
                refreshAll = { refreshCalls.incrementAndGet() },
                showNotice = notices::add,
                friendlyError = { it.message ?: "request failed" }
            ),
            dispatcher = dispatcher,
            notices = notices,
            refreshCalls = refreshCalls
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }

    private companion object {
        val GROUP = Group(id = 1, name = "测试群组", type = 1, status = 1)
    }
}

private data class Fixture(
    val controller: GroupManagementController,
    val dispatcher: ExecutorCoroutineDispatcher,
    val notices: List<String>,
    val refreshCalls: AtomicInteger
) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeGroupsApi(
    private val joinAction: (Int, String) -> Unit = { _, _ -> },
    private val leaveAction: (Int) -> Unit = {}
) : GroupsApi {
    val joinCalls = mutableListOf<Pair<Int, String>>()
    val leaveCalls = mutableListOf<Int>()

    override fun joinGroup(groupId: Int, password: String) {
        joinCalls += groupId to password
        joinAction(groupId, password)
    }

    override fun leaveGroup(groupId: Int) {
        leaveCalls += groupId
        leaveAction(groupId)
    }

    override fun getGroups(): List<Group> = error("Unexpected group request")

    override fun getGroupStats(): Map<Int, Pair<Int, Int>> = error("Unexpected group stats request")

    override fun getOnlineDevices(groupId: Int): List<OnlineDevice> = error("Unexpected online devices request")

    override fun searchGroups(keyword: String): List<Group> = error("Unexpected group search")

    override fun createGroup(name: String, type: Int, password: String, note: String): Group =
        error("Unexpected group creation")

    override fun updateGroup(request: GroupUpdateRequest): Group = error("Unexpected group update")

    override fun deleteGroup(groupId: Int) = error("Unexpected group deletion")

    override fun getGroupDevices(groupId: Int): List<Device> = error("Unexpected group devices request")

    override fun updateGroupDeviceCommControl(
        groupId: Int,
        deviceId: Int,
        disableSend: Boolean,
        disableReceive: Boolean
    ): Pair<Boolean, Boolean> = error("Unexpected group device update")

    override fun kickGroupDevice(groupId: Int, deviceId: Int) = error("Unexpected group device removal")
}

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking dependency that cannot be cancelled cooperatively.
        }
    }
}
