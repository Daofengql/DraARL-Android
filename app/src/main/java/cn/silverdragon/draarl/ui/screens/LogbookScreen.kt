package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.tools.LogbookDraft
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
    tools.draft?.takeIf { tools.logbookEditorOpen }?.let { draft ->
        LogbookEditorDialog(
            draft = draft,
            tools = tools,
            onDismiss = tools::closeDraftEditor,
            onSave = { tools.saveDraft {} },
        )
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

@Composable
private fun LogbookEditorDialog(
    draft: LogbookDraft,
    tools: ToolsController,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var showPresets by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (tools.presets.isEmpty()) tools.loadPresets() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.editingId > 0) "编辑通联日志" else "新增通联日志") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { DraftField(draft.myCallsign, { tools.updateDraft(draft.copy(myCallsign = it.uppercase())) }, "我方呼号") }
                item { DraftField(draft.callsign, { tools.updateDraft(draft.copy(callsign = it.uppercase())) }, "对方呼号") }
                item { DraftField(draft.localTime, { tools.updateDraft(draft.copy(localTime = it)) }, "本地时间") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DraftField(draft.txFrequency, { tools.updateDraft(draft.copy(txFrequency = it)) }, "发射 MHz", Modifier.weight(1f), true)
                        DraftField(draft.rxFrequency, { tools.updateDraft(draft.copy(rxFrequency = it)) }, "接收 MHz", Modifier.weight(1f), true)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DraftField(draft.cqZone, { tools.updateDraft(draft.copy(cqZone = it)) }, "CQ 分区", Modifier.weight(1f), true)
                        DraftField(draft.ituZone, { tools.updateDraft(draft.copy(ituZone = it)) }, "ITU 分区", Modifier.weight(1f), true)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("FM", "DMR", "SSB", "CW").forEach { mode ->
                            FilterChip(
                                selected = draft.mode == mode,
                                onClick = { tools.updateDraft(draft.copy(mode = mode)) },
                                label = { Text(mode) },
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DraftField(draft.myRst, { tools.updateDraft(draft.copy(myRst = it)) }, "我方 RST", Modifier.weight(1f))
                        DraftField(draft.theirRst, { tools.updateDraft(draft.copy(theirRst = it)) }, "对方 RST", Modifier.weight(1f))
                    }
                }
                item {
                    TextButton(onClick = { showPresets = true }, enabled = tools.presets.isNotEmpty()) {
                        Text("使用电台预设")
                    }
                }
                item { Text("我方信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                item { DraftField(draft.myRadio, { tools.updateDraft(draft.copy(myRadio = it)) }, "我的电台") }
                item { DraftField(draft.myAntenna, { tools.updateDraft(draft.copy(myAntenna = it)) }, "我的天线") }
                item { DraftField(draft.myPower, { tools.updateDraft(draft.copy(myPower = it)) }, "功率 W", numeric = true) }
                item { DraftField(draft.myQth, { tools.updateDraft(draft.copy(myQth = it)) }, "我的 QTH") }
                item { Text("对方信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                item { DraftField(draft.theirRadio, { tools.updateDraft(draft.copy(theirRadio = it)) }, "对方电台") }
                item { DraftField(draft.theirAntenna, { tools.updateDraft(draft.copy(theirAntenna = it)) }, "对方天线") }
                item { DraftField(draft.theirPower, { tools.updateDraft(draft.copy(theirPower = it)) }, "对方功率 W", numeric = true) }
                item { DraftField(draft.theirQth, { tools.updateDraft(draft.copy(theirQth = it)) }, "对方 QTH") }
                item {
                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = { tools.updateDraft(draft.copy(notes = it)) },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !tools.logbookBusy) {
                Text(if (tools.logbookBusy) "保存中" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后填写") } },
    )
    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("选择电台预设") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(tools.presets.size) { index ->
                        val preset = tools.presets[index]
                        TextButton(
                            onClick = { tools.applyPreset(preset); showPresets = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(preset.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresets = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DraftField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}
