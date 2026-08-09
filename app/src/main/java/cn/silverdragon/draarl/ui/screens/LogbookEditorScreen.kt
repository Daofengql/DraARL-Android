package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.LogbookDraft
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.DraarlIconButton

@Composable
internal fun LogbookEditorScreen(tools: ToolsController, onBack: () -> Unit) {
    val draft = tools.draft
    var showPresets by remember { mutableStateOf(false) }
    var qthPickerTarget by rememberSaveable { mutableStateOf<QthPickerTarget?>(null) }
    LaunchedEffect(Unit) {
        if (tools.presets.isEmpty()) tools.loadPresets()
    }
    if (draft == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    qthPickerTarget?.let { target ->
        LogbookPlacePickerScreen(
            title = if (target == QthPickerTarget.MY) "选择我的 QTH" else "选择对方 QTH",
            onBack = { qthPickerTarget = null },
            onConfirm = { selection ->
                val current = tools.draft ?: return@LogbookPlacePickerScreen
                tools.updateDraft(
                    if (target == QthPickerTarget.MY) {
                        current.copy(myQth = selection.qth)
                    } else {
                        current.copy(theirQth = selection.qth)
                    }
                )
                qthPickerTarget = null
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ToolHeader(if (draft.editingId > 0) "编辑通联日志" else "新增通联日志", onBack)
        if (tools.error.isNotBlank()) ToolError(tools.error, tools::clearError)
        if (tools.presetErrorMessage.isNotBlank()) {
            ToolError(tools.presetErrorMessage, tools::clearPresetError)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { LogbookSectionTitle("通联信息") }
            item {
                DraftField(draft.myCallsign, { tools.updateDraft(draft.copy(myCallsign = it.uppercase())) }, "我方呼号")
            }
            item { DraftField(draft.callsign, { tools.updateDraft(draft.copy(callsign = it.uppercase())) }, "对方呼号") }
            item { DraftField(draft.localTime, { tools.updateDraft(draft.copy(localTime = it)) }, "本地时间") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftField(
                        draft.txFrequency,
                        { tools.updateDraft(draft.copy(txFrequency = it)) },
                        "发射 MHz",
                        Modifier.weight(1f),
                        true
                    )
                    DraftField(
                        draft.rxFrequency,
                        { tools.updateDraft(draft.copy(rxFrequency = it)) },
                        "接收 MHz",
                        Modifier.weight(1f),
                        true
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftField(
                        draft.cqZone,
                        { tools.updateDraft(draft.copy(cqZone = it)) },
                        "CQ 分区",
                        Modifier.weight(1f),
                        true
                    )
                    DraftField(
                        draft.ituZone,
                        { tools.updateDraft(draft.copy(ituZone = it)) },
                        "ITU 分区",
                        Modifier.weight(1f),
                        true
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FM", "DMR", "SSB", "CW").forEach { mode ->
                        FilterChip(
                            selected = draft.mode == mode,
                            onClick = { tools.updateDraft(draft.copy(mode = mode)) },
                            label = { Text(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftField(
                        draft.myRst,
                        { tools.updateDraft(draft.copy(myRst = it)) },
                        "我方 RST",
                        Modifier.weight(1f)
                    )
                    DraftField(
                        draft.theirRst,
                        { tools.updateDraft(draft.copy(theirRst = it)) },
                        "对方 RST",
                        Modifier.weight(1f)
                    )
                }
            }

            item { LogbookSectionTitle("我方设备") }
            item {
                OutlinedButton(
                    onClick = { showPresets = true },
                    enabled = tools.presets.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Text(
                        if (tools.presetBusy) "正在加载预设" else "使用电台预设",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            item { DraftField(draft.myRadio, { tools.updateDraft(draft.copy(myRadio = it)) }, "我的电台") }
            item { DraftField(draft.myAntenna, { tools.updateDraft(draft.copy(myAntenna = it)) }, "我的天线") }
            item { DraftField(draft.myPower, { tools.updateDraft(draft.copy(myPower = it)) }, "功率 W", numeric = true) }
            item {
                QthDraftField(
                    value = draft.myQth,
                    onValueChange = { tools.updateDraft(draft.copy(myQth = it)) },
                    label = "我的 QTH",
                    onPick = { qthPickerTarget = QthPickerTarget.MY }
                )
            }

            item { LogbookSectionTitle("对方设备") }
            item { DraftField(draft.theirRadio, { tools.updateDraft(draft.copy(theirRadio = it)) }, "对方电台") }
            item { DraftField(draft.theirAntenna, { tools.updateDraft(draft.copy(theirAntenna = it)) }, "对方天线") }
            item {
                DraftField(
                    draft.theirPower,
                    { tools.updateDraft(draft.copy(theirPower = it)) },
                    "对方功率 W",
                    numeric = true
                )
            }
            item {
                QthDraftField(
                    value = draft.theirQth,
                    onValueChange = { tools.updateDraft(draft.copy(theirQth = it)) },
                    label = "对方 QTH",
                    onPick = { qthPickerTarget = QthPickerTarget.THEIR }
                )
            }
            item {
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { tools.updateDraft(draft.copy(notes = it)) },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { tools.saveDraft(onSuccess = onBack) },
                enabled = !tools.logbookBusy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (tools.logbookBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                }
                Text(
                    if (tools.logbookBusy) "正在保存" else "保存通联日志",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (showPresets) {
        PresetPickerDialog(
            tools = tools,
            onDismiss = { showPresets = false }
        )
    }
}

@Composable
private fun LogbookSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DraftField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text)
    )
}

@Composable
private fun QthDraftField(value: String, onValueChange: (String) -> Unit, label: String, onPick: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            DraarlIconButton(
                icon = Icons.Default.Map,
                label = "在地图上选择$label",
                onClick = onPick
            )
        }
    )
}

private enum class QthPickerTarget { MY, THEIR }

@Composable
private fun PresetPickerDialog(tools: ToolsController, onDismiss: () -> Unit) {
    DraarlDialog(
        title = "选择电台预设",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss)
    ) {
        LazyColumn(Modifier.heightIn(max = 400.dp).padding(horizontal = 8.dp, vertical = 6.dp)) {
            items(tools.presets.size, key = { tools.presets[it].id }) { index ->
                val preset = tools.presets[index]
                TextButton(
                    onClick = {
                        tools.applyPreset(preset)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(preset.name)
                        Text(
                            listOf(preset.radio, preset.antenna).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
