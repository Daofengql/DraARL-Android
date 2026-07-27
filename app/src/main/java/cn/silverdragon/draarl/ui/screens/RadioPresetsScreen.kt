package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.silverdragon.draarl.tools.RadioPreset
import cn.silverdragon.draarl.tools.ToolsController
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun RadioPresetsScreen(tools: ToolsController, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<RadioPreset?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RadioPreset?>(null) }
    var draggedPresetId by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        tools.loadPresets()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电台预设") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增预设")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (tools.presetErrorMessage.isNotBlank()) {
                ToolError(tools.presetErrorMessage, tools::clearPresetError)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!tools.presetBusy && tools.presets.isEmpty()) item { Text("暂无电台预设") }
                items(tools.presets.size, key = { tools.presets[it].id }) { index ->
                    val preset = tools.presets[index]
                    val isDragging = draggedPresetId == preset.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) draggedOffset else 0f },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(listOf(preset.radio, preset.antenna).filter(String::isNotBlank).joinToString(" · "))
                                Text(
                                    listOfNotNull(preset.power?.let { "${it}W" }, preset.qth.takeIf(String::isNotBlank)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editing = preset }) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                            IconButton(onClick = { deleting = preset }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(preset.id, tools.presetBusy) {
                                        if (tools.presetBusy) return@pointerInput
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedPresetId = preset.id
                                                draggedOffset = 0f
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                draggedOffset += dragAmount.y

                                                val layoutInfo = listState.layoutInfo
                                                val draggedInfo = layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == preset.id }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val draggedCenter = draggedInfo.offset + draggedOffset + draggedInfo.size / 2f
                                                val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                                    item.key != preset.id &&
                                                        draggedCenter >= item.offset &&
                                                        draggedCenter <= item.offset + item.size
                                                }
                                                val currentIndex = tools.presets.indexOfFirst { it.id == preset.id }
                                                val targetId = targetInfo?.key as? Int
                                                val targetIndex = tools.presets.indexOfFirst { it.id == targetId }
                                                if (
                                                    targetInfo != null &&
                                                    currentIndex >= 0 &&
                                                    targetIndex >= 0 &&
                                                    currentIndex != targetIndex
                                                ) {
                                                    tools.previewPresetMove(currentIndex, targetIndex)
                                                    draggedOffset += (draggedInfo.offset - targetInfo.offset).toFloat()
                                                }

                                                val edgeSize = 72f
                                                val scrollDelta = when {
                                                    draggedCenter < layoutInfo.viewportStartOffset + edgeSize -> -18f
                                                    draggedCenter > layoutInfo.viewportEndOffset - edgeSize -> 18f
                                                    else -> 0f
                                                }
                                                if (scrollDelta != 0f) {
                                                    coroutineScope.launch { listState.scrollBy(scrollDelta) }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedPresetId = null
                                                draggedOffset = 0f
                                                tools.commitPresetOrder()
                                            },
                                            onDragCancel = {
                                                draggedPresetId = null
                                                draggedOffset = 0f
                                                tools.commitPresetOrder()
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "长按拖动排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (tools.presetBusy) item { CircularProgressIndicator(Modifier.size(28.dp)) }
            }
        }
    }
    if (creating || editing != null) {
        PresetEditorDialog(
            initial = editing ?: RadioPreset(name = "", radio = "", antenna = ""),
            busy = tools.presetBusy,
            onDismiss = { creating = false; editing = null },
            onSave = { value ->
                tools.savePreset(value) { creating = false; editing = null }
            },
        )
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除预设") },
            text = { Text("确定删除“${preset.name}”？") },
            confirmButton = { TextButton(onClick = { deleting = null; tools.deletePreset(preset.id) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PresetEditorDialog(
    initial: RadioPreset,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (RadioPreset) -> Unit,
) {
    var value by remember(initial.id) { mutableStateOf(initial) }
    var power by remember(initial.id) { mutableStateOf(initial.power?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id > 0) "编辑预设" else "新增预设") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value.name, { value = value.copy(name = it) }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value.radio, { value = value.copy(radio = it) }, label = { Text("电台") }, singleLine = true)
                OutlinedTextField(value.antenna, { value = value.copy(antenna = it) }, label = { Text("天线") }, singleLine = true)
                OutlinedTextField(power, { power = it.filter(Char::isDigit) }, label = { Text("功率 W") }, singleLine = true)
                OutlinedTextField(value.qth, { value = value.copy(qth = it) }, label = { Text("QTH") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value.copy(power = power.toIntOrNull())) },
                enabled = !busy && value.name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
