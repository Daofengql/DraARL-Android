package cn.silverdragon.draarl.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.network.GroupUpdateRequest
import cn.silverdragon.draarl.network.GroupsApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class GroupManagementController(
    private val api: GroupsApi,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val currentGroups: () -> List<Group>,
    private val updateGroups: (List<Group>) -> Unit,
    private val refreshAll: () -> Unit,
    private val showNotice: (String) -> Unit,
    private val friendlyError: (Throwable) -> String
) {
    private var closed = false

    var busy by mutableStateOf(false)
        private set
    private val tasks = ControllerTaskRunner(scope, ioDispatcher) { busy = it }
    var searchResults by mutableStateOf<List<Group>>(emptyList())
        private set
    var managedDevices by mutableStateOf<List<Device>>(emptyList())
        private set
    var managedGroupId by mutableStateOf<Int?>(null)
        private set

    fun search(keyword: String) {
        val query = keyword.trim()
        if (query.isBlank()) {
            showNotice("请输入群组 ID 或名称")
            return
        }
        launch(operation = { api.searchGroups(query) }, onSuccess = { searchResults = it })
    }

    fun clearSearch() {
        searchResults = emptyList()
    }

    fun save(editing: Group?, name: String, type: Int, password: String, note: String, onSuccess: () -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            showNotice("请输入群组名称")
            return
        }
        if (editing == null && type == 2 && password.isBlank()) {
            showNotice("私有群组必须设置加入密码")
            return
        }
        launch(
            operation = {
                if (editing == null) {
                    api.createGroup(trimmedName, type, password, note.trim())
                } else {
                    api.updateGroup(
                        GroupUpdateRequest(editing.id, trimmedName, type, password, note.trim())
                    )
                }
            },
            onSuccess = {
                showNotice(if (editing == null) "群组已创建" else "群组设置已保存")
                onSuccess()
                refreshAll()
            }
        )
    }

    fun setEnabled(group: Group, enabled: Boolean) = launch(
        operation = {
            api.updateGroup(GroupUpdateRequest(groupId = group.id, status = if (enabled) 1 else 0))
        },
        onSuccess = {
            updateGroups(
                currentGroups().map { current ->
                    if (current.id == group.id) current.copy(status = if (enabled) 1 else 0) else current
                }
            )
            showNotice(if (enabled) "群组已启用" else "群组已停用")
        }
    )

    fun delete(group: Group, onSuccess: () -> Unit = {}) = launch(
        operation = { api.deleteGroup(group.id) },
        onSuccess = {
            updateGroups(currentGroups().filterNot { it.id == group.id })
            showNotice("群组已删除")
            onSuccess()
            refreshAll()
        }
    )

    fun loadDevices(groupId: Int) {
        managedGroupId = groupId
        managedDevices = emptyList()
        launch(
            operation = { api.getGroupDevices(groupId) },
            onSuccess = { if (managedGroupId == groupId) managedDevices = it }
        )
    }

    fun closeDevices() {
        tasks.cancel()
        managedGroupId = null
        managedDevices = emptyList()
    }

    fun updateDeviceControl(
        groupId: Int,
        device: Device,
        disableSend: Boolean = device.disableSend,
        disableReceive: Boolean = device.disableReceive
    ) = launch(
        operation = { api.updateGroupDeviceCommControl(groupId, device.id, disableSend, disableReceive) },
        onSuccess = { (sendDisabled, receiveDisabled) ->
            managedDevices = managedDevices.map { current ->
                if (current.id == device.id) {
                    current.copy(disableSend = sendDisabled, disableReceive = receiveDisabled)
                } else {
                    current
                }
            }
        }
    )

    fun kickDevice(groupId: Int, device: Device) = launch(
        operation = { api.kickGroupDevice(groupId, device.id) },
        onSuccess = {
            managedDevices = managedDevices.filterNot { it.id == device.id }
            showNotice("设备已移出群组")
            refreshAll()
        }
    )

    fun reset() {
        tasks.cancel()
        clearState()
    }

    fun close() {
        if (closed) return
        closed = true
        tasks.close()
        clearState()
    }

    private fun <T> launch(operation: () -> T, onSuccess: (T) -> Unit) {
        if (closed) return
        tasks.launch(operation, onSuccess) { error -> showNotice(friendlyError(error)) }
    }

    private fun clearState() {
        searchResults = emptyList()
        managedDevices = emptyList()
        managedGroupId = null
    }
}
