package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.FeedlineCalculator
import cn.silverdragon.draarl.tools.FeedlinePresets

@Composable
internal fun FeedlineCalculatorScreen(onBack: () -> Unit) {
    var frequency by rememberSaveable { mutableStateOf("145") }
    var length by rememberSaveable { mutableStateOf("20") }
    var inputPower by rememberSaveable { mutableStateOf("50") }
    var connectorLoss by rememberSaveable { mutableStateOf("0.5") }
    var selectedPreset by rememberSaveable { mutableStateOf(FeedlinePresets.items[2].name) }
    var manualLoss by rememberSaveable { mutableStateOf("") }
    val frequencyValue = frequency.toDoubleOrNull()
    val preset = FeedlinePresets.items.firstOrNull { it.name == selectedPreset }
    val estimatedLoss = frequencyValue?.let { value ->
        if (value > 0.0) preset?.lossDbPer100Meters(value) else null
    }
    val effectiveLoss = manualLoss.toDoubleOrNull() ?: estimatedLoss
    val result = remember(length, inputPower, connectorLoss, effectiveLoss) {
        val parsedLength = length.toDoubleOrNull()
        val parsedPower = inputPower.toDoubleOrNull()
        val parsedConnectorLoss = connectorLoss.toDoubleOrNull()
        calculateFeedlineResult(parsedLength, parsedPower, parsedConnectorLoss, effectiveLoss)
    }

    Column(Modifier.fillMaxSize()) {
        ToolHeader("馈线计算", onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("线缆类型")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(FeedlinePresets.items, key = { it.name }) { item ->
                        FilterChip(
                            selected = selectedPreset == item.name,
                            onClick = {
                                selectedPreset = item.name
                                manualLoss = ""
                            },
                            label = { Text(item.name) }
                        )
                    }
                }
            }
            item { DecimalField(frequency, { frequency = it }, "频率 MHz") }
            item {
                OutlinedTextField(
                    value = manualLoss,
                    onValueChange = { manualLoss = it.decimalInput() },
                    label = { Text("百米损耗 dB") },
                    placeholder = { Text(estimatedLoss.formatFixed("dB / 100 m")) },
                    supportingText = { Text("预设值为典型估算；厂商数据优先，可手动覆盖") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { DecimalField(length, { length = it }, "馈线长度 m") }
            item { DecimalField(inputPower, { inputPower = it }, "输入功率 W") }
            item { DecimalField(connectorLoss, { connectorLoss = it }, "接头及转接损耗 dB") }
            item {
                ResultSurface(
                    listOf(
                        "线缆损耗" to result?.cableLossDb.formatFixed("dB"),
                        "总损耗" to result?.totalLossDb.formatFixed("dB"),
                        "天线端功率" to result?.outputPowerWatts.formatEngineering("W"),
                        "传输效率" to result?.efficiencyPercent.formatFixed("%")
                    )
                )
            }
        }
    }
}

private fun calculateFeedlineResult(length: Double?, power: Double?, connectorLoss: Double?, cableLoss: Double?) =
    listOf(length, power, connectorLoss, cableLoss).takeIf { values -> values.none { it == null } }?.let {
        runCatching {
            FeedlineCalculator.calculate(
                lengthMeters = checkNotNull(length),
                lossDbPer100Meters = checkNotNull(cableLoss),
                inputPowerWatts = checkNotNull(power),
                connectorLossDb = checkNotNull(connectorLoss)
            )
        }.getOrNull()
    }

@Composable
private fun DecimalField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.decimalInput()) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
