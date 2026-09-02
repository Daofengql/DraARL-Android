@file:Suppress("LongMethod", "CognitiveComplexMethod", "MaxLineLength", "MagicNumber")

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
import cn.silverdragon.draarl.tools.SwrCalculator
import cn.silverdragon.draarl.tools.SwrResult
import cn.silverdragon.draarl.tools.RadioNumberFormatter
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl

@Composable
internal fun SwrCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("swr") }
    var swr by rememberSaveable { mutableStateOf("1.5") }
    var forwardPower by rememberSaveable { mutableStateOf("50") }
    var reflectedPower by rememberSaveable { mutableStateOf("2") }
    val result = remember(mode, swr, forwardPower, reflectedPower) {
        runCatching {
            if (mode == "swr") {
                SwrCalculator.fromSwr(
                    swr.toDoubleOrNull() ?: error("请输入有效 SWR"),
                    forwardPower.toDoubleOrNull()
                )
            } else {
                SwrCalculator.fromPower(
                    forwardPower.toDoubleOrNull() ?: error("请输入有效前向功率"),
                    reflectedPower.toDoubleOrNull() ?: error("请输入有效反射功率")
                )
            }
        }.getOrNull()
    }
    val copyValue = result?.toCopyText().orEmpty()
    Column(Modifier.fillMaxSize()) {
        ToolHeader(
            title = "SWR / 回波损耗",
            onBack = onBack,
            action = { ToolCopyButton(context, "复制驻波计算结果", copyValue) }
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DraarlSegmentedControl(
                    segments = listOf(
                        DraarlSegment("swr", "由 SWR 计算"),
                        DraarlSegment("power", "由功率计算")
                    ),
                    selectedKey = mode,
                    onSelect = { mode = it }
                )
            }
            item {
                if (mode == "swr") {
                    NumericField(swr, { swr = it.decimalInput() }, "SWR")
                } else {
                    NumericField(forwardPower, { forwardPower = it.decimalInput() }, "前向功率 W")
                }
            }
            item {
                NumericField(
                    if (mode == "swr") forwardPower else reflectedPower,
                    {
                        if (mode == "swr") forwardPower = it.decimalInput() else reflectedPower = it.decimalInput()
                    },
                    if (mode == "swr") "前向功率 W（可选）" else "反射功率 W"
                )
            }
            item {
                ResultSurface(
                    listOf(
                        "SWR" to result?.swr.formatRatio(),
                        "反射系数" to result?.reflectionCoefficient.formatFixed(""),
                        "反射比例" to result?.reflectionPercent.formatFixed("%"),
                        "回波损耗" to result?.returnLossDb.formatFixed("dB"),
                        "反射功率" to result?.reflectedPowerPercent.formatFixed("%"),
                        "反射功率值" to result?.reflectedPowerWatts.formatEngineering("W")
                    )
                )
            }
            item {
                if (result == null) {
                    Text("请输入有效参数（SWR ≥ 1，反射功率不大于前向功率）", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        "SWR 越接近 1 越好；回波损耗越大表示匹配越好。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

private fun SwrResult.toCopyText(): String = buildString {
    appendLine("SWR ${swr.formatRatio()}")
    appendLine("反射系数 ${reflectionCoefficient.formatFixed("")}")
    appendLine("反射比例 ${reflectionPercent.formatFixed("%")}")
    appendLine("回波损耗 ${returnLossDb.formatFixed("dB")}")
    append("反射功率 ${reflectedPowerWatts.formatEngineering("W")}")
}

private fun Double?.formatRatio(): String =
    if (this == null) "-" else if (isFinite()) RadioNumberFormatter.format(this) else "∞"
