@file:Suppress("LongMethod", "MaxLineLength", "MagicNumber")

package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.AntennaLengthCalculator
import cn.silverdragon.draarl.tools.AntennaType

@Composable
internal fun AntennaLengthCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var frequency by rememberSaveable { mutableStateOf("145") }
    var typeKey by rememberSaveable { mutableStateOf(AntennaType.HALF_WAVE_DIPOLE.name) }
    var shorteningFactor by rememberSaveable { mutableStateOf("0.95") }
    val antennaType = AntennaType.entries.firstOrNull { it.name == typeKey } ?: AntennaType.HALF_WAVE_DIPOLE
    val result = remember(frequency, antennaType, shorteningFactor) {
        runCatching {
            AntennaLengthCalculator.calculate(
                frequencyMHz = frequency.toDoubleOrNull() ?: error("请输入有效频率"),
                antennaType = antennaType,
                shorteningFactor = shorteningFactor.toDoubleOrNull() ?: error("请输入有效缩短系数")
            )
        }.getOrNull()
    }
    val copyValue = result?.let {
        "频率 ${frequency} MHz\n类型 ${antennaType.label}\n总长度 ${it.totalLengthMeters.formatFixed("m")}\n单边长度 ${it.elementLengthMeters.formatFixed("m")}"
    }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        ToolHeader(
            title = "天线长度",
            onBack = onBack,
            action = {
                ToolCopyButton(context, "复制天线计算结果", copyValue)
            }
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                NumericField(frequency, { frequency = it.decimalInput() }, "中心频率 MHz")
            }
            item {
                Text("天线类型")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(AntennaType.entries, key = { it.name }) { item ->
                        FilterChip(
                            selected = item == antennaType,
                            onClick = { typeKey = item.name },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = shorteningFactor,
                    onValueChange = { shorteningFactor = it.decimalInput() },
                    label = { Text("缩短系数") },
                    supportingText = { Text("导体直径、绝缘层和端部效应的近似修正，通常 0.90～0.98") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                ResultSurface(
                    listOf(
                        "波长" to result?.wavelengthMeters.formatFixed("m"),
                        "总有效长度" to result?.totalLengthMeters.formatFixed("m"),
                        "单边 / 振子长度" to result?.elementLengthMeters.formatFixed("m")
                    )
                )
            }
            item {
                CalculationErrorText(
                    result == null,
                    if (frequency.isBlank() || shorteningFactor.isBlank()) null else "请输入有效频率和缩短系数"
                )
            }
            item {
                Text(
                    "结果是起始尺寸，实际长度还需结合驻波表、周围环境和安装高度微调。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun CalculationErrorText(show: Boolean, message: String?) {
    if (show && message != null) {
        Text(message, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
    }
}
