package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.silverdragon.draarl.tools.RadioPreset
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun RadioPresetsScreen(tools: ToolsController, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<RadioPreset?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RadioPreset?>(null) }
    LaunchedEffect(Unit) {
        tools.loadPresets()
    }
    Column(Modifier.fillMaxSize()) {
        ToolHeader("电台预设", onBack, applyWindowInsets = true) {
            DraarlIconButton(
                icon = Icons.Default.Add,
                label = "新增预设",
                onClick = { creating = true }
            )
        }
        if (tools.presetErrorMessage.isNotBlank()) {
            ToolError(tools.presetErrorMessage, tools::clearPresetError)
        }
        RadioPresetList(
            presets = tools.presets,
            busy = tools.presetBusy,
            actions = RadioPresetListActions(
                onEdit = { editing = it },
                onDelete = { deleting = it },
                onMove = tools::previewPresetMove,
                onCommitOrder = tools::commitPresetOrder
            )
        )
    }
    if (creating || editing != null) {
        PresetEditorDialog(
            initial = editing ?: RadioPreset(name = "", radio = "", antenna = ""),
            busy = tools.presetBusy,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { value ->
                tools.savePreset(value) {
                    creating = false
                    editing = null
                }
            }
        )
    }
    deleting?.let { preset ->
        DraarlDialog(
            title = "删除预设",
            onDismissRequest = { deleting = null },
            dismissAction = DraarlAction("取消", { deleting = null }),
            confirmAction = DraarlAction(
                label = "删除",
                onClick = {
                    deleting = null
                    tools.deletePreset(preset.id)
                },
                style = CommandStyle.DANGER
            )
        ) {
            Text("确定删除“${preset.name}”？", modifier = Modifier.padding(18.dp))
        }
    }
}

@Composable
private fun RadioPresetList(presets: List<RadioPreset>, busy: Boolean, actions: RadioPresetListActions) {
    var draggedPresetId by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val currentPresets by rememberUpdatedState(presets)
    val dragRuntime = PresetDragRuntime(listState, coroutineScope, hapticFeedback) { currentPresets }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (presets.isEmpty()) {
            item { RadioPresetListFeedback(busy) }
        }
        items(presets.size, key = { presets[it].id }) { index ->
            val preset = presets[index]
            val dragging = draggedPresetId == preset.id
            RadioPresetCard(
                state = RadioPresetCardState(preset, dragging, if (dragging) draggedOffset else 0f),
                modifier = Modifier.animateItem(),
                dragHandle = Modifier.presetDragHandle(
                    source = PresetDragSource(preset.id, busy),
                    runtime = dragRuntime,
                    callbacks = PresetDragCallbacks(
                        currentOffset = { draggedOffset },
                        onStart = {
                            draggedPresetId = preset.id
                            draggedOffset = 0f
                        },
                        onOffset = { draggedOffset = it },
                        onMove = actions.onMove,
                        onFinish = {
                            draggedPresetId = null
                            draggedOffset = 0f
                            actions.onCommitOrder()
                        }
                    )
                ),
                actions = RadioPresetCardActions(
                    onEdit = { actions.onEdit(preset) },
                    onDelete = { actions.onDelete(preset) }
                )
            )
        }
        if (busy && presets.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
internal fun RadioPresetListFeedback(busy: Boolean) {
    ToolListFeedback(
        loading = busy,
        title = if (busy) "正在加载电台预设" else "暂无电台预设",
        detail = if (busy) "正在同步预设与排序" else "新增后可长按拖动调整常用顺序"
    )
}

@Composable
private fun RadioPresetCard(
    state: RadioPresetCardState,
    modifier: Modifier,
    dragHandle: Modifier,
    actions: RadioPresetCardActions
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (state.dragging) 1f else 0f)
            .graphicsLayer { translationY = state.draggedOffset },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioPresetSummary(state.preset, Modifier.weight(1f))
            DraarlIconButton(icon = Icons.Default.Edit, label = "编辑预设", onClick = actions.onEdit)
            DraarlIconButton(icon = Icons.Default.Delete, label = "删除预设", onClick = actions.onDelete)
            Box(dragHandle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "长按拖动排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RadioPresetSummary(preset: RadioPreset, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(listOf(preset.radio, preset.antenna).filter(String::isNotBlank).joinToString(" · "))
        Text(
            listOfNotNull(preset.power?.let { "${it}W" }, preset.qth.takeIf(String::isNotBlank)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class PresetDragDecision(val offset: Float, val move: Pair<Int, Int>?, val scrollDelta: Float)

private data class RadioPresetListActions(
    val onEdit: (RadioPreset) -> Unit,
    val onDelete: (RadioPreset) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onCommitOrder: () -> Unit
)

private data class RadioPresetCardState(val preset: RadioPreset, val dragging: Boolean, val draggedOffset: Float)

private data class RadioPresetCardActions(val onEdit: () -> Unit, val onDelete: () -> Unit)

private data class PresetDragSource(val presetId: Int, val busy: Boolean)

private data class PresetDragRuntime(
    val listState: LazyListState,
    val coroutineScope: CoroutineScope,
    val hapticFeedback: HapticFeedback,
    val currentPresets: () -> List<RadioPreset>
)

private data class PresetDragCallbacks(
    val currentOffset: () -> Float,
    val onStart: () -> Unit,
    val onOffset: (Float) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onFinish: () -> Unit
)

private fun Modifier.presetDragHandle(
    source: PresetDragSource,
    runtime: PresetDragRuntime,
    callbacks: PresetDragCallbacks
): Modifier = pointerInput(source.presetId, source.busy) {
    if (source.busy) return@pointerInput
    detectDragGesturesAfterLongPress(
        onDragStart = {
            callbacks.onStart()
            runtime.hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onDrag = { change, dragAmount ->
            change.consume()
            val decision = presetDragDecision(
                runtime.listState,
                runtime.currentPresets(),
                source.presetId,
                callbacks.currentOffset() + dragAmount.y
            )
                ?: return@detectDragGesturesAfterLongPress
            decision.move?.let { (from, to) -> callbacks.onMove(from, to) }
            callbacks.onOffset(decision.offset)
            if (decision.scrollDelta != 0f) {
                runtime.coroutineScope.launch { runtime.listState.scrollBy(decision.scrollDelta) }
            }
        },
        onDragEnd = callbacks.onFinish,
        onDragCancel = callbacks.onFinish
    )
}

private fun presetDragDecision(
    listState: LazyListState,
    presets: List<RadioPreset>,
    presetId: Int,
    offset: Float
): PresetDragDecision? {
    val layout = listState.layoutInfo
    val dragged = layout.visibleItemsInfo.firstOrNull { it.key == presetId } ?: return null
    val center = dragged.offset + offset + dragged.size / 2f
    val target = layout.visibleItemsInfo.firstOrNull { item ->
        item.key != presetId && center >= item.offset && center <= item.offset + item.size
    }
    val from = presets.indexOfFirst { it.id == presetId }
    val targetIndex = presets.indexOfFirst { it.id == target?.key as? Int }
    val validIndices = from >= 0 && targetIndex >= 0
    val move = if (target != null && validIndices && from != targetIndex) from to targetIndex else null
    val correctedOffset = target?.takeIf { move != null }?.let { offset + dragged.offset - it.offset } ?: offset
    val scrollDelta = when {
        center < layout.viewportStartOffset + DRAG_EDGE_SIZE -> -DRAG_SCROLL_STEP
        center > layout.viewportEndOffset - DRAG_EDGE_SIZE -> DRAG_SCROLL_STEP
        else -> 0f
    }
    return PresetDragDecision(correctedOffset, move, scrollDelta)
}

private const val DRAG_EDGE_SIZE = 72f
private const val DRAG_SCROLL_STEP = 18f

@Composable
private fun PresetEditorDialog(
    initial: RadioPreset,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (RadioPreset) -> Unit
) {
    var value by remember(initial.id) { mutableStateOf(initial) }
    var power by remember(initial.id) { mutableStateOf(initial.power?.toString().orEmpty()) }
    DraarlDialog(
        title = if (initial.id > 0) "编辑预设" else "新增预设",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss),
        confirmAction = DraarlAction(
            label = "保存",
            onClick = { onSave(value.copy(power = power.toIntOrNull())) },
            enabled = !busy && value.name.isNotBlank(),
            style = CommandStyle.PRIMARY
        )
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(value.name, { value = value.copy(name = it) }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(value.radio, {
                value = value.copy(radio = it)
            }, label = { Text("电台") }, singleLine = true)
            OutlinedTextField(value.antenna, {
                value = value.copy(antenna = it)
            }, label = { Text("天线") }, singleLine = true)
            OutlinedTextField(power, { power = it.filter(Char::isDigit) }, label = { Text("功率 W") }, singleLine = true)
            OutlinedTextField(value.qth, { value = value.copy(qth = it) }, label = { Text("QTH") }, singleLine = true)
        }
    }
}
