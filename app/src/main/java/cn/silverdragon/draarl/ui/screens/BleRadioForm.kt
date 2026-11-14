package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.ble.BleRadioConfig
import cn.silverdragon.draarl.tools.ble.BleToneValues
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle
import kotlin.math.roundToInt

private const val MAX_SQUELCH = 8
private const val MAX_GAIN_DB = 24
private const val GAIN_STEP_DB = 3
private const val MAX_VOLUME = 100

@Composable
internal fun BleRadioForm(
    model: Int,
    config: BleRadioConfig,
    busy: Boolean,
    onChange: (BleRadioConfig) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DraarlSettingsSectionTitle(if (model == 1) "SA818 与音频" else "音频与外接射频 IO", detail = "按设备实际检测结果显示")
        DraarlSettingsGroup {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (model == 1) {
                    ProvisionField(config.txFrequencyMhz, "TX 频率 MHz", {
                        onChange(config.copy(txFrequencyMhz = it))
                    }, KeyboardType.Decimal)
                    ProvisionField(config.rxFrequencyMhz, "RX 频率 MHz", {
                        onChange(config.copy(rxFrequencyMhz = it))
                    }, KeyboardType.Decimal)
                    BleToneInput("TX 亚音", config.txToneType, config.txToneIndex) { type, index ->
                        onChange(config.copy(txToneType = type, txToneIndex = index))
                    }
                    BleToneInput("RX 亚音", config.rxToneType, config.rxToneIndex) { type, index ->
                        onChange(config.copy(rxToneType = type, rxToneIndex = index))
                    }
                    BleLevel("静噪", config.squelch, MAX_SQUELCH, busy) { onChange(config.copy(squelch = it)) }
                    BleToggle("宽带 25 kHz", config.wideBand, busy) { onChange(config.copy(wideBand = it)) }
                    BleToggle("高功率", config.powerHigh, busy) { onChange(config.copy(powerHigh = it)) }
                } else {
                    BleToggle("SQL 高电平有效", config.sqlActiveHigh, busy) { onChange(config.copy(sqlActiveHigh = it)) }
                    BleToggle("PTT 高电平有效", config.pttActiveHigh, busy) { onChange(config.copy(pttActiveHigh = it)) }
                }
                BleLevel("ADC 增益 dB", config.adcGainDb, MAX_GAIN_DB, busy, GAIN_STEP_DB) {
                    onChange(config.copy(adcGainDb = it))
                }
                BleLevel("ADC 音量", config.adcVolume, MAX_VOLUME, busy) { onChange(config.copy(adcVolume = it)) }
                BleLevel("DAC 音量", config.dacVolume, MAX_VOLUME, busy) { onChange(config.copy(dacVolume = it)) }
                CommandButton(label = "保存设备参数", onClick = onSave, enabled = !busy)
            }
        }
    }
}

@Composable
internal fun BleChoice(label: String, selected: String, options: Map<String, String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：${options[selected] ?: selected}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = {
            expanded = false
        }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (key, value) ->
                DropdownMenuItem(text = { Text(value) }, onClick = {
                    expanded = false
                    onChange(key)
                })
            }
        }
    }
}

@Composable
private fun BleToneInput(label: String, type: String, index: Int, onChange: (String, Int) -> Unit) {
    BleChoice("$label 类型", type, BleToneValues.types.associateWith { it }) { onChange(it, 0) }
    if (type != "OFF") {
        BleChoice(
            label,
            index.toString(),
            BleToneValues.labels(type).mapIndexed { i, value ->
                i.toString() to value
            }.toMap()
        ) {
            onChange(type, it.toInt())
        }
    }
}

@Composable
private fun BleToggle(label: String, checked: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = !busy)
    }
}

@Composable
@Suppress("LongParameterList") // Slider label, range, state and change callback.
private fun BleLevel(label: String, value: Int, max: Int, busy: Boolean, step: Int = 1, onChange: (Int) -> Unit) {
    Text("$label：$value")
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange((it / step).roundToInt() * step) },
        valueRange = 0f..max.toFloat(),
        steps = max / step - 1,
        enabled = !busy
    )
}
