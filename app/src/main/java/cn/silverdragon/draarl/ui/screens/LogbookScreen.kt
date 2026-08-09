package cn.silverdragon.draarl.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.tools.LogbookEntry
import cn.silverdragon.draarl.tools.LogbookTime
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlConfirmation
import cn.silverdragon.draarl.ui.components.DraarlConfirmationDialog
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlIconButtonOptions

@Composable
internal fun LogbookScreen(controller: AppController, tools: ToolsController, onBack: () -> Unit) {
    var filter by remember(tools.logbookFilter) { mutableStateOf(tools.logbookFilter) }
    var pendingDelete by remember { mutableStateOf<LogbookEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeSearch() {
        searchExpanded = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun submitSearch() {
        tools.loadLogbooks(reset = true, callsign = filter)
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ToolHeader("通联日志", onBack) {
            if (selectionMode) {
                DraarlIconButton(
                    icon = Icons.Default.Close,
                    label = "退出批量选择",
                    onClick = {
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                )
                DraarlIconButton(
                    icon = Icons.Default.Delete,
                    label = "删除所选日志",
                    onClick = { confirmBatchDelete = true },
                    options = DraarlIconButtonOptions(
                        enabled = selectedIds.isNotEmpty() && !tools.logbookBusy
                    )
                )
            } else {
                DraarlIconButton(
                    icon = Icons.Default.Search,
                    label = if (searchExpanded) "收起搜索" else "搜索通联日志",
                    onClick = { if (searchExpanded) closeSearch() else searchExpanded = true },
                    options = DraarlIconButtonOptions(
                        tint = if (searchExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                )
                DraarlIconButton(
                    icon = Icons.Default.Checklist,
                    label = "批量选择",
                    onClick = {
                        closeSearch()
                        selectionMode = true
                    }
                )
                DraarlIconButton(
                    icon = Icons.Default.Add,
                    label = "新增通联日志",
                    onClick = { tools.editDraft(null, controller.session.uiState.user) }
                )
            }
        }
        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it.uppercase() },
                label = { Text("搜索对方呼号") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocusRequester),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotBlank()) {
                        DraarlIconButton(
                            icon = Icons.Default.Close,
                            label = "清除搜索",
                            onClick = {
                                filter = ""
                                tools.loadLogbooks(reset = true, callsign = "")
                            },
                            options = DraarlIconButtonOptions(enabled = !tools.logbookBusy)
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() })
            )
        }
        if (tools.error.isNotBlank()) ToolError(tools.error, tools::clearError)
        if (tools.draft != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("有一份未完成的通联日志", modifier = Modifier.weight(1f))
                    TextButton(onClick = tools::resumeDraft) { Text("继续填写") }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tools.logbooks.isEmpty()) {
                item {
                    LogbookListFeedback(tools.logbookBusy, filter)
                }
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
                    onEdit = { tools.editDraft(entry, controller.session.uiState.user) },
                    onDelete = { pendingDelete = entry }
                )
            }
            if (tools.logbooks.size < tools.logbookTotal) {
                item {
                    CommandButton(
                        label = "加载更多",
                        onClick = { tools.loadLogbooks() },
                        enabled = !tools.logbookBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (tools.logbookBusy && tools.logbooks.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        DraarlConfirmationDialog(
            confirmation = DraarlConfirmation(
                title = "删除通联日志",
                message = "确定删除与 ${entry.callsign} 的通联记录？",
                confirmLabel = "删除",
                confirmStyle = CommandStyle.DANGER
            ),
            onDismissRequest = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                tools.deleteLogbook(entry.id)
            }
        )
    }
    if (confirmBatchDelete) {
        DraarlConfirmationDialog(
            confirmation = DraarlConfirmation(
                title = "批量删除通联日志",
                message = "确定删除选中的 ${selectedIds.size} 条通联记录？此操作不可撤销。",
                confirmLabel = "删除",
                confirmStyle = CommandStyle.DANGER
            ),
            onDismissRequest = { confirmBatchDelete = false },
            onConfirm = {
                confirmBatchDelete = false
                tools.deleteLogbooks(selectedIds) {
                    selectionMode = false
                    selectedIds = emptySet()
                }
            },
            confirmEnabled = !tools.logbookBusy
        )
    }
}

@Composable
private fun LogbookListFeedback(busy: Boolean, filter: String) {
    val filtered = filter.isNotBlank()
    ToolListFeedback(
        loading = busy,
        title = when {
            busy -> "正在加载通联日志"
            filtered -> "没有匹配的通联日志"
            else -> "暂无通联日志"
        },
        detail = when {
            busy -> "正在同步本机记录与服务端数据"
            filtered -> "可调整呼号后重新搜索"
            else -> "新增记录后会按时间显示在这里"
        }
    )
}

@Composable
private fun LogbookCard(
    entry: LogbookEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!selectionMode) {
                    DraarlIconButton(
                        icon = Icons.Default.Edit,
                        label = "编辑日志",
                        onClick = onEdit
                    )
                    DraarlIconButton(
                        icon = Icons.Default.Delete,
                        label = "删除日志",
                        onClick = onDelete
                    )
                }
            }
            Text("发射 ${entry.txFrequency} MHz   接收 ${entry.rxFrequency} MHz")
            Text("RST ${entry.myRst} / ${entry.theirRst}   ${entry.myCallsign}")
            if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}
