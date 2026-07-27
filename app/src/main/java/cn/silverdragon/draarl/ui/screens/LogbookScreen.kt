package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.tools.LogbookEntry
import cn.silverdragon.draarl.tools.LogbookTime
import cn.silverdragon.draarl.tools.ToolsController

@Composable
internal fun LogbookScreen(controller: AppController, tools: ToolsController, onBack: () -> Unit) {
    var filter by remember(tools.logbookFilter) { mutableStateOf(tools.logbookFilter) }
    var pendingDelete by remember { mutableStateOf<LogbookEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ToolHeader("通联日志", onBack) {
            if (selectionMode) {
                IconButton(onClick = {
                    selectionMode = false
                    selectedIds = emptySet()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "退出批量选择")
                }
                IconButton(
                    onClick = { confirmBatchDelete = true },
                    enabled = selectedIds.isNotEmpty() && !tools.logbookBusy,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除所选日志")
                }
            } else {
                IconButton(onClick = { selectionMode = true }) {
                    Icon(Icons.Default.Checklist, contentDescription = "批量选择")
                }
                IconButton(onClick = { tools.editDraft(null, controller.user) }) {
                    Icon(Icons.Default.Add, contentDescription = "新增通联日志")
                }
            }
        }
        if (tools.error.isNotBlank()) ToolError(tools.error, tools::clearError)
        if (tools.draft != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("有一份未完成的通联日志", modifier = Modifier.weight(1f))
                    TextButton(onClick = tools::resumeDraft) { Text("继续填写") }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it.uppercase() },
                label = { Text("对方呼号") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = { tools.loadLogbooks(reset = true, callsign = filter) }, enabled = !tools.logbookBusy) {
                Icon(Icons.Default.Search, contentDescription = "查询")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!tools.logbookBusy && tools.logbooks.isEmpty()) {
                item { Text("暂无通联日志", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(tools.logbooks.size, key = { tools.logbooks[it].id }) { index ->
                val entry = tools.logbooks[index]
                LogbookCard(
                    entry = entry,
                    selectionMode = selectionMode,
                    selected = entry.id in selectedIds,
                    onSelect = { selected ->
                        selectedIds = if (selected) selectedIds + entry.id else selectedIds - entry.id
                    },
                    onEdit = { tools.editDraft(entry, controller.user) },
                    onDelete = { pendingDelete = entry },
                )
            }
            if (tools.logbooks.size < tools.logbookTotal) {
                item {
                    Button(
                        onClick = { tools.loadLogbooks() },
                        enabled = !tools.logbookBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("加载更多") }
                }
            }
            if (tools.logbookBusy) {
                item { CircularProgressIndicator(Modifier.size(28.dp)) }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除通联日志") },
            text = { Text("确定删除与 ${entry.callsign} 的通联记录？") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; tools.deleteLogbook(entry.id) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("批量删除通联日志") },
            text = { Text("确定删除选中的 ${selectedIds.size} 条通联记录？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBatchDelete = false
                        tools.deleteLogbooks(selectedIds) {
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    },
                    enabled = !tools.logbookBusy,
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LogbookCard(
    entry: LogbookEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = onSelect)
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.callsign, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${LogbookTime.utcToLocal(entry.timeUtc)} · ${entry.mode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selectionMode) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                }
            }
            Text("发射 ${entry.txFrequency} MHz   接收 ${entry.rxFrequency} MHz")
            Text("RST ${entry.myRst} / ${entry.theirRst}   ${entry.myCallsign}")
            if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}
