package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.DistanceUnit
import cn.silverdragon.draarl.tools.RadioConversions
import cn.silverdragon.draarl.tools.RadioNumberFormatter
import cn.silverdragon.draarl.tools.SignalUnit
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl

@Composable
internal fun RadioConverterScreen(onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("distance") }
    Column(Modifier.fillMaxSize()) {
        ToolHeader("单位与信号换算", onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DraarlSegmentedControl(
                    segments = listOf(DraarlSegment("distance", "距离"), DraarlSegment("signal", "信号")),
                    selectedKey = mode,
                    onSelect = { mode = it }
                )
            }
            item {
                key(mode) {
                    if (mode == "distance") DistanceConverterContent() else SignalConverterContent()
                }
            }
        }
    }
}

@Composable
private fun DistanceConverterContent() {
    var value by rememberSaveable { mutableStateOf("1") }
    var unitKey by rememberSaveable { mutableStateOf("km") }
    var edited by rememberSaveable { mutableStateOf(false) }
    val unit = when (unitKey) {
        "mi" -> DistanceUnit.MILES
        "nmi" -> DistanceUnit.NAUTICAL_MILES
        else -> DistanceUnit.KILOMETERS
    }
    val result = remember(value, unit) {
        value.toDoubleOrNull()?.let { runCatching { RadioConversions.distance(it, unit) }.getOrNull() }
    }
    val error = remember(value, unit) {
        value.toDoubleOrNull()?.let { parsed ->
            runCatching { RadioConversions.distance(parsed, unit) }.exceptionOrNull()?.message
        } ?: if (value.isBlank()) null else "请输入有效数字"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it.scientificNumberInput()
                edited = true
            },
            label = { Text("距离") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = edited && error != null,
            supportingText = if (edited) error?.let { message -> ({ Text(message) }) } else null,
            modifier = Modifier.fillMaxWidth()
        )
        DraarlSegmentedControl(
            segments = listOf(
                DraarlSegment("km", "公里"),
                DraarlSegment("mi", "英里"),
                DraarlSegment("nmi", "海里")
            ),
            selectedKey = unitKey,
            onSelect = { unitKey = it }
        )
        ResultSurface(
            listOf(
                "公里" to result?.kilometers.formatNumber(),
                "英里" to result?.miles.formatNumber(),
                "海里" to result?.nauticalMiles.formatNumber()
            )
        )
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun SignalConverterContent() {
    var value by rememberSaveable { mutableStateOf("0") }
    var unitKey by rememberSaveable { mutableStateOf("dbm") }
    var impedance by rememberSaveable { mutableStateOf("50") }
    var valueEdited by rememberSaveable { mutableStateOf(false) }
    var impedanceEdited by rememberSaveable { mutableStateOf(false) }
    val unit = when (unitKey) {
        "w" -> SignalUnit.WATTS
        "mw" -> SignalUnit.MILLIWATTS
        "uv" -> SignalUnit.MICROVOLTS
        "dbuv" -> SignalUnit.DBUV
        else -> SignalUnit.DBM
    }
    val result = remember(value, unit, impedance) {
        val parsedValue = value.toDoubleOrNull()
        val parsedImpedance = impedance.toDoubleOrNull()
        if (parsedValue == null || parsedImpedance == null) {
            null
        } else {
            runCatching { RadioConversions.signal(parsedValue, unit, parsedImpedance) }.getOrNull()
        }
    }
    val error = remember(value, unit, impedance) {
        val parsedValue = value.toDoubleOrNull()
        val parsedImpedance = impedance.toDoubleOrNull()
        when {
            value.isNotBlank() && parsedValue == null -> "请输入有效换算数值"

            impedance.isNotBlank() && parsedImpedance == null -> "请输入有效阻抗"

            parsedValue != null && parsedImpedance != null -> runCatching {
                RadioConversions.signal(parsedValue, unit, parsedImpedance)
            }.exceptionOrNull()?.message

            else -> null
        }
    }
    val showError = error != null && (valueEdited || impedanceEdited)
    fun selectUnit(newUnitKey: String) {
        if (newUnitKey == unitKey) return
        val converted = result?.valueFor(newUnitKey)
        value = RadioNumberFormatter.format(converted ?: defaultSignalValue(newUnitKey))
        unitKey = newUnitKey
        valueEdited = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DraarlSegmentedControl(
            segments = listOf(
                DraarlSegment("dbm", "dBm"),
                DraarlSegment("w", "W"),
                DraarlSegment("mw", "mW")
            ),
            selectedKey = unitKey,
            onSelect = ::selectUnit
        )
        DraarlSegmentedControl(
            segments = listOf(DraarlSegment("uv", "µV"), DraarlSegment("dbuv", "dBµV")),
            selectedKey = unitKey,
            onSelect = ::selectUnit
        )
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it.scientificNumberInput()
                valueEdited = true
            },
            label = { Text("输入值") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = showError,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = impedance,
            onValueChange = {
                impedance = it.scientificNumberInput()
                impedanceEdited = true
            },
            label = { Text("阻抗 Ω") },
            supportingText = { Text("电压与功率换算依赖阻抗，电台系统通常为 50 Ω") },
            isError = showError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        ResultSurface(
            listOf(
                "功率" to result?.watts.formatEngineering("W"),
                "毫瓦" to result?.milliwatts.formatEngineering("mW"),
                "电平" to result?.dbm.formatFixed("dBm"),
                "电压" to result?.microvolts.formatEngineering("µV"),
                "电压电平" to result?.dbuv.formatFixed("dBµV")
            )
        )
        if (showError) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ResultSurface(rows: List<Pair<String, String>>) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (label, value) ->
                Text("$label  $value", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

internal fun String.decimalInput(): String = filterIndexed { index, character ->
    character.isDigit() || (character == '.' && '.' !in take(index))
}.take(MAX_NUMERIC_INPUT_LENGTH)

private fun String.scientificNumberInput(): String = filter { character ->
    character.isDigit() || character == '.' || character == '-' || character == '+' || character.lowercaseChar() == 'e'
}.take(MAX_NUMERIC_INPUT_LENGTH)

private fun cn.silverdragon.draarl.tools.SignalConversion.valueFor(unitKey: String): Double = when (unitKey) {
    "w" -> watts
    "mw" -> milliwatts
    "uv" -> microvolts
    "dbuv" -> dbuv
    else -> dbm
}

private fun defaultSignalValue(unitKey: String): Double = when (unitKey) {
    "w", "mw", "uv" -> 1.0
    else -> 0.0
}

internal fun Double?.formatNumber(): String = RadioNumberFormatter.format(this)
internal fun Double?.formatFixed(unit: String): String = RadioNumberFormatter.withUnit(
    value = this,
    unit = unit,
    maxFractionDigits = 2
)
internal fun Double?.formatEngineering(unit: String): String = RadioNumberFormatter.withUnit(this, unit)

private const val MAX_NUMERIC_INPUT_LENGTH = 24
