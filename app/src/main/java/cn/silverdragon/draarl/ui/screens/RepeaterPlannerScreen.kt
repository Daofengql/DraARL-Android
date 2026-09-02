@file:Suppress("LongMethod", "MaxLineLength", "MagicNumber")

package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.RepeaterOffsetDirection
import cn.silverdragon.draarl.tools.RepeaterPlanner
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl

@Composable
internal fun RepeaterPlannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var receiveFrequency by rememberSaveable { mutableStateOf("145.000") }
    var offset by rememberSaveable { mutableStateOf("0.600") }
    var directionKey by rememberSaveable { mutableStateOf(RepeaterOffsetDirection.NEGATIVE.name) }
    var tone by rememberSaveable { mutableStateOf("88.5") }
    var channelName by rememberSaveable { mutableStateOf("2m 中继") }
    val direction = RepeaterOffsetDirection.entries.firstOrNull { it.name == directionKey }
        ?: RepeaterOffsetDirection.NEGATIVE
    val result = remember(receiveFrequency, offset, direction, tone, channelName) {
        runCatching {
            RepeaterPlanner.calculate(
                receiveFrequencyMHz = receiveFrequency.toDoubleOrNull() ?: error("请输入有效接收频率"),
                offsetMHz = offset.toDoubleOrNull() ?: error("请输入有效下差"),
                direction = direction,
                tone = tone,
                channelName = channelName
            )
        }.getOrNull()
    }
    val copyValue = result?.let {
        "${it.channelName}\n接收 ${it.receiveFrequencyMHz.formatFixed("MHz")}\n发射 ${it.transmitFrequencyMHz.formatFixed("MHz")}\n下差 ${it.offsetMHz.formatFixed("MHz")}\n哑音 ${it.tone.ifBlank { "无" }}"
    }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        ToolHeader(
            title = "中继频点规划",
            onBack = onBack,
            action = { ToolCopyButton(context, "复制中继频点", copyValue) }
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { NumericField(receiveFrequency, { receiveFrequency = it.decimalInput() }, "接收频率 MHz") }
            item { NumericField(offset, { offset = it.decimalInput() }, "下差 MHz") }
            item {
                DraarlSegmentedControl(
                    segments = listOf(
                        DraarlSegment(RepeaterOffsetDirection.POSITIVE.name, "正下差"),
                        DraarlSegment(RepeaterOffsetDirection.NEGATIVE.name, "负下差"),
                        DraarlSegment(RepeaterOffsetDirection.SIMPLEX.name, "同频")
                    ),
                    selectedKey = direction.name,
                    onSelect = { directionKey = it }
                )
            }
            item {
                OutlinedTextField(
                    value = tone,
                    onValueChange = { tone = it.take(12) },
                    label = { Text("CTCSS / DCS 哑音（可选）") },
                    placeholder = { Text("88.5 或 D023") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it.take(40) },
                    label = { Text("频道名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                ResultSurface(
                    listOf(
                        "频道" to (result?.channelName ?: "-"),
                        "接收频率" to result?.receiveFrequencyMHz.formatFixed("MHz"),
                        "发射频率" to result?.transmitFrequencyMHz.formatFixed("MHz"),
                        "实际下差" to result?.offsetMHz.formatFixed("MHz"),
                        "哑音" to (result?.tone?.ifBlank { "无" } ?: "-")
                    )
                )
            }
            item {
                if (result == null) {
                    Text("请输入有效的频率和下差", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun NumericField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.decimalInput()) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
