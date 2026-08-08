package cn.silverdragon.draarl.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import cn.silverdragon.draarl.ui.theme.appColors
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.deviceModelName
import cn.silverdragon.draarl.ui.components.EmptyState
import cn.silverdragon.draarl.ui.components.StatusPill
import cn.silverdragon.draarl.ui.state.visibleGroupSections

@Composable
fun GroupsScreen(controller: AppController) {
    var filter by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var detailGroupId by remember { mutableStateOf<Int?>(null) }
    var localSearchActive by rememberSaveable { mutableStateOf(false) }
    var showJoinSearch by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var joinTarget by remember { mutableStateOf<Group?>(null) }
    var leaveTarget by remember { mutableStateOf<Group?>(null) }
    var deleteTarget by remember { mutableStateOf<Group?>(null) }
    var managedGroup by remember { mutableStateOf<Group?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeLocalSearch() {
        filter = ""
        localSearchActive = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(localSearchActive) {
        if (localSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val groups = controller.groups
    val query = filter.trim()
    val sections = remember(groups, query) { visibleGroupSections(groups, query) }
    val groupsById = remember(groups) { groups.associateBy(Group::id) }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = localSearchActive,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "groupSearch",
        ) { searching ->
            if (searching) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::closeLocalSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出搜索")
                    }
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text("搜索群组名称或 ID") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filter.isNotBlank()) {
                                IconButton(onClick = { filter = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除搜索")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                        ),
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { localSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索群组")
                    }
                    IconButton(onClick = {
                        controller.groupManagement.clearSearch()
                        showJoinSearch = true
                    }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "搜索并加入群组")
                    }
                    IconButton(onClick = {
                        editingGroup = null
                        showEditor = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新建群组")
                    }
                }
            }
        }

        if (groups.isEmpty() && !controller.contentLoading) {
            EmptyState(Icons.Default.Groups, "暂无群组", "可搜索加入私有群组，或创建新群组")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                groupSection(
                    title = "公开群组",
                    groups = sections.publicGroups,
                    emptyText = if (query.isBlank()) "暂无公开群组" else "没有匹配的公开群组",
                    onOpen = { detailGroupId = it.id },
                )
                groupSection(
                    title = "我的私有群组",
                    groups = sections.privateGroups,
                    emptyText = if (query.isBlank()) "尚未加入私有群组" else "没有匹配的私有群组",
                    onOpen = { detailGroupId = it.id },
                )
            }
        }
    }

    val detailGroup = detailGroupId?.let(groupsById::get)
    detailGroup?.let { group ->
        GroupDetailDialog(
            group = group,
            busy = controller.groupManagement.busy,
            onClose = { detailGroupId = null },
            onEdit = {
                editingGroup = group
                showEditor = true
            },
            onManageDevices = {
                managedGroup = group
                controller.groupManagement.loadDevices(group.id)
            },
            onEnabledChange = { controller.groupManagement.setEnabled(group, it) },
            onLeave = { leaveTarget = group },
            onDelete = { deleteTarget = group },
        )
    }

    if (showJoinSearch) {
        GroupSearchDialog(
            controller = controller,
            onClose = {
                showJoinSearch = false
                controller.groupManagement.clearSearch()
            },
            onJoin = { joinTarget = it },
        )
    }

    if (showEditor) {
        GroupEditorDialog(
            group = editingGroup,
            busy = controller.groupManagement.busy,
            onDismiss = { showEditor = false },
            onSave = { name, type, password, note ->
                controller.groupManagement.save(editingGroup, name, type, password, note) {
                    showEditor = false
                }
            },
        )
    }

    joinTarget?.let { group ->
        PasswordJoinDialog(
            group = group,
            onDismiss = { joinTarget = null },
            onJoin = { password ->
                controller.joinGroup(group, password)
                joinTarget = null
                showJoinSearch = false
                controller.groupManagement.clearSearch()
            },
        )
    }

    leaveTarget?.let { group ->
        ConfirmActionDialog(
            title = "退出群组",
            message = "退出“${group.name}”后，您在该群组的设备将移至系统公共群组 999。",
            confirmText = "退出",
            onDismiss = { leaveTarget = null },
            onConfirm = {
                controller.leaveGroup(group)
                leaveTarget = null
                detailGroupId = null
            },
        )
    }

    deleteTarget?.let { group ->
        ConfirmActionDialog(
            title = "删除群组",
            message = "确定删除“${group.name}”吗？此操作不可撤销。",
            confirmText = "删除",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                controller.groupManagement.delete(group) {
                    deleteTarget = null
                    detailGroupId = null
                }
            },
        )
    }

    managedGroup?.let { group ->
        GroupDevicesDialog(
            group = group,
            devices = if (controller.groupManagement.managedGroupId == group.id) controller.groupManagement.managedDevices else emptyList(),
            busy = controller.groupManagement.busy,
            onClose = {
                managedGroup = null
                controller.groupManagement.closeDevices()
            },
            onRefresh = { controller.groupManagement.loadDevices(group.id) },
            onCommControl = { device, disableSend, disableReceive ->
                controller.groupManagement.updateDeviceControl(group.id, device, disableSend, disableReceive)
            },
            onKick = { controller.groupManagement.kickDevice(group.id, it) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupSection(
    title: String,
    groups: List<Group>,
    emptyText: String,
    onOpen: (Group) -> Unit,
) {
    item(key = "header-$title") {
        Text(
            text = "$title  ${groups.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
    if (groups.isEmpty()) {
        item(key = "empty-$title") {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            )
        }
    } else {
        items(groups, key = Group::id) { group ->
            GroupListRow(group, onClick = { onOpen(group) })
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
        }
    }
}

@Composable
private fun GroupListRow(group: Group, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupAvatar(group, 48)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (group.status == 0) StatusPill("已停用", MaterialTheme.colorScheme.error)
            }
            Text(
                "${group.onlineCount} 在线 / ${group.totalCount} 设备 · ID ${group.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (group.note.isNotBlank()) {
                Text(
                    group.note,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun GroupAvatar(group: Group, size: Int) {
    val background = if (group.isPrivate) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.appColors.receiveContainer
    }
    val foreground = if (group.isPrivate) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.appColors.onReceiveContainer
    }
    Surface(shape = CircleShape, color = background, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (group.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size((size * 0.48f).dp),
            )
        }
    }
}

@Composable
private fun GroupDetailDialog(
    group: Group,
    busy: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onManageDevices: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
) {
    FullScreenDialog(onClose) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text("群资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                GroupAvatar(group, 76)
                Spacer(Modifier.height(12.dp))
                Text(group.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "群号 ${group.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                DetailValueRow("群类型", if (group.isPrivate) "私有群组" else "公开群组")
                DetailValueRow("群主", group.ownerCallsign.ifBlank { "系统" })
                DetailValueRow("设备", "${group.onlineCount} 在线 / ${group.totalCount} 台")
                DetailValueRow("状态", if (group.status == 1) "正常" else "已停用")
                DetailValueRow("群公告", group.note.ifBlank { "暂无公告" })

                if (group.owner) {
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                    ManagementRow(Icons.Default.Router, "群内设备", "禁发、禁收与移出群组", onManageDevices)
                    ManagementRow(Icons.Default.Edit, "编辑群资料", "名称、类型、密码与公告", onEdit)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("启用群组", fontWeight = FontWeight.Medium)
                            Text(
                                if (group.status == 1) "当前允许设备通信" else "当前已停止群组通信",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = group.status == 1,
                            onCheckedChange = onEnabledChange,
                            enabled = !busy,
                        )
                    }
                    TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.padding(vertical = 16.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(" 删除群组")
                    }
                } else if (group.isPrivate && group.joined) {
                    OutlinedButton(onClick = onLeave, enabled = !busy, modifier = Modifier.padding(top = 28.dp)) {
                        Text("退出群组")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(74.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ManagementRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun GroupSearchDialog(controller: AppController, onClose: () -> Unit, onJoin: (Group) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("搜索群组", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("输入群组 ID 或名称") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { controller.groupManagement.search(keyword) }, enabled = !controller.groupManagement.busy) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (controller.groupManagement.busy) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (controller.groupManagement.searchResults.isEmpty()) {
                    Text(
                        "搜索结果会显示在这里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                        items(controller.groupManagement.searchResults, key = Group::id) { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GroupAvatar(group, 42)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "ID ${group.id} · ${group.ownerCallsign.ifBlank { "系统" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when {
                                    group.isPrivate && !group.joined -> OutlinedButton(onClick = { onJoin(group) }) {
                                        Text("加入")
                                    }
                                    group.joined -> StatusPill("已加入", MaterialTheme.appColors.statusConnected)
                                    else -> StatusPill("公开", MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun GroupEditorDialog(
    group: Group?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String) -> Unit,
) {
    var name by remember(group) { mutableStateOf(group?.name.orEmpty()) }
    var type by remember(group) { mutableIntStateOf(group?.type ?: 1) }
    var password by remember(group) { mutableStateOf("") }
    var note by remember(group) { mutableStateOf(group?.note.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (group == null) "新建群组" else "群组设置", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it }, label = { Text("群组名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == 1,
                        onClick = { type = 1 },
                        label = { Text("公开") },
                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = type == 2,
                        onClick = { type = 2 },
                        label = { Text("私有") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                if (type == 2) {
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text(if (group == null) "加入密码" else "重置密码（留空不修改）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    note,
                    { note = it },
                    label = { Text("群公告（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                    Button(onClick = { onSave(name, type, password, note) }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordJoinDialog(group: Group, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var password by remember(group.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("加入 ${group.name}") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("群组密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onJoin(password) }, enabled = password.isNotBlank()) { Text("加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GroupDevicesDialog(
    group: Group,
    devices: List<Device>,
    busy: Boolean,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onCommControl: (Device, Boolean, Boolean) -> Unit,
    onKick: (Device) -> Unit,
) {
    var kickTarget by remember { mutableStateOf<Device?>(null) }
    FullScreenDialog(onClose) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("群内设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(group.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Router, "刷新设备") }
                }
            },
        ) { padding ->
            when {
                busy && devices.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                devices.isEmpty() -> EmptyState(
                    Icons.Default.Router,
                    "暂无设备",
                    "群组内目前没有普通设备",
                    Modifier.padding(padding),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(devices, key = Device::id) { device ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.name.ifBlank { "设备 ${device.id}" }, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${device.callsign}-${device.ssid} · ${deviceModelName(device.model)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill(
                                    if (device.online) "在线" else "离线",
                                    if (device.online) {
                                        MaterialTheme.appColors.statusConnected
                                    } else {
                                        MaterialTheme.appColors.statusOffline
                                    },
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("禁发", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = device.disableSend,
                                    onCheckedChange = { onCommControl(device, it, device.disableReceive) },
                                    enabled = !busy,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("禁收")
                                Switch(
                                    checked = device.disableReceive,
                                    onCheckedChange = { onCommControl(device, device.disableSend, it) },
                                    enabled = !busy,
                                )
                                IconButton(onClick = { kickTarget = device }, enabled = !busy) {
                                    Icon(Icons.Default.PersonOff, "移出群组", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    kickTarget?.let { device ->
        ConfirmActionDialog(
            title = "移出设备",
            message = "确定将“${device.name.ifBlank { device.callsign }}”移出群组吗？",
            confirmText = "移出",
            onDismiss = { kickTarget = null },
            onConfirm = {
                onKick(device)
                kickTarget = null
            },
        )
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
