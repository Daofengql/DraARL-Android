package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.ui.components.EmptyState
import cn.silverdragon.draarl.ui.components.StatusPill

@Composable
fun GroupsScreen(controller: AppController) {
    var joinTarget by remember { mutableStateOf<Group?>(null) }
    var password by remember { mutableStateOf("") }
    if (controller.groups.isEmpty() && !controller.contentLoading) {
        EmptyState(Icons.Default.Groups, "暂无可用群组", "服务端当前没有发布可访问的群组")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(controller.groups, key = Group::id) { group ->
            GroupItem(
                group = group,
                selected = group.id == controller.selectedGroupId,
                onSelect = { controller.switchGroup(group) },
                onJoin = {
                    if (group.requiresPassword) {
                        password = ""
                        joinTarget = group
                    } else {
                        controller.joinGroup(group, "")
                    }
                },
                onLeave = { controller.leaveGroup(group) },
            )
        }
    }
    joinTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { joinTarget = null },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("加入 ${group.name}") },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("群组密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.joinGroup(group, password)
                        joinTarget = null
                    },
                    enabled = password.isNotBlank(),
                ) { Text("加入") }
            },
            dismissButton = { TextButton(onClick = { joinTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GroupItem(
    group: Group,
    selected: Boolean,
    onSelect: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "ID ${group.id} · ${if (group.isPrivate) "私有群组" else "公开群组"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) StatusPill("当前", MaterialTheme.colorScheme.primary)
            }
            if (group.note.isNotBlank()) {
                Text(group.note, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${group.onlineCount} 在线 / ${group.totalCount} 设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (group.onlineCount > 0) Color(0xFF087F5B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    group.isPrivate && !group.joined && !group.owner -> OutlinedButton(onClick = onJoin) {
                        if (group.requiresPassword) Icon(Icons.Default.Lock, contentDescription = null)
                        Text(" 加入")
                    }
                    selected -> Button(onClick = {}, enabled = false) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text(" 已选择")
                    }
                    else -> Button(onClick = onSelect, enabled = group.status == 1) { Text("切换") }
                }
            }
            if (group.isPrivate && group.joined && !group.owner && !selected) {
                TextButton(onClick = onLeave, modifier = Modifier.align(Alignment.End)) { Text("退出群组") }
            }
        }
    }
}
