package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.ble.BleServerConfig
import cn.silverdragon.draarl.tools.ble.BleWifiConfig
import cn.silverdragon.draarl.tools.ble.BleWifiNetwork
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle

@Composable
@Suppress("LongParameterList") // Hoisted form state and its scan/save actions.
internal fun BleWifiForm(
    config: BleWifiConfig,
    busy: Boolean,
    onChange: (BleWifiConfig) -> Unit,
    onSave: () -> Unit,
    networks: List<BleWifiNetwork>,
    onScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DraarlSettingsSectionTitle("Wi-Fi 网络", detail = "设备接入网络所需参数")
        DraarlSettingsGroup {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CommandButton(label = "扫描附近 Wi-Fi", onClick = onScan, enabled = !busy)
                if (networks.isNotEmpty()) {
                    BleChoice(
                        "附近网络",
                        config.ssid,
                        networks.associate { it.ssid to "${it.ssid} · ${it.rssi} dBm" }
                    ) { ssid ->
                        onChange(config.copy(ssid = ssid, password = if (ssid == config.ssid) config.password else ""))
                    }
                }
                ProvisionField(
                    value = config.ssid,
                    label = "SSID",
                    onValueChange = { onChange(config.copy(ssid = it)) }
                )
                ProvisionField(
                    value = config.password,
                    label = "密码",
                    password = true,
                    onValueChange = { onChange(config.copy(password = it)) }
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("自动获取地址", modifier = Modifier.weight(1f))
                    Switch(checked = config.dhcp, onCheckedChange = { onChange(config.copy(dhcp = it)) })
                }
                if (!config.dhcp) {
                    StaticAddressFields(config, onChange)
                }
                CommandButton(
                    label = if (busy) "正在写入 Wi-Fi 配置" else "写入 Wi-Fi 配置",
                    onClick = onSave,
                    enabled = !busy,
                    style = CommandStyle.PRIMARY,
                    leadingIcon = Icons.Default.Save
                )
            }
        }
    }
}

@Composable
internal fun BleServerForm(
    config: BleServerConfig,
    busy: Boolean,
    onChange: (BleServerConfig) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DraarlSettingsSectionTitle("DraARL 链路", detail = "只需 HTTP API 地址；账号与设备密码可留空，使用设备绑定码关联账号")
        DraarlSettingsGroup {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProvisionField(
                    value = config.account,
                    label = "账号",
                    onValueChange = { onChange(config.copy(account = it)) }
                )
                ProvisionField(
                    value = config.deviceAuthPassword,
                    label = "设备认证密码",
                    password = true,
                    onValueChange = { onChange(config.copy(deviceAuthPassword = it)) }
                )
                ProvisionField(
                    value = config.callsign,
                    label = "呼号",
                    onValueChange = { onChange(config.copy(callsign = it.uppercase())) }
                )
                ProvisionField(
                    value = config.nodeSsid.toString(),
                    label = "SSID",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { onChange(config.copy(nodeSsid = it.toIntOrNull() ?: 0)) }
                )
                ProvisionField(
                    value = config.dmrId.toString(),
                    label = "DMR ID（0 为未设置）",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { onChange(config.copy(dmrId = it.toIntOrNull() ?: 0)) }
                )
                Text("SSID：0 自动，或 1–99 / 106–254；UDP 地址由 API 获取。")
                Text("当前接入点：${config.activeEndpoint.ifBlank { "联网后自动获取" }}")
                ProvisionField(
                    value = config.httpApiBaseUrl,
                    label = "HTTPS API",
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { onChange(config.copy(httpApiBaseUrl = it)) }
                )
                CommandButton(
                    label = if (busy) "正在写入 DraARL 配置" else "写入 DraARL 配置",
                    onClick = onSave,
                    enabled = !busy,
                    style = CommandStyle.PRIMARY,
                    leadingIcon = Icons.Default.Save
                )
            }
        }
    }
}

@Composable
private fun StaticAddressFields(config: BleWifiConfig, onChange: (BleWifiConfig) -> Unit) {
    ProvisionField(config.ip, "IP 地址", { onChange(config.copy(ip = it)) }, KeyboardType.Decimal)
    ProvisionField(config.gateway, "网关", { onChange(config.copy(gateway = it)) }, KeyboardType.Decimal)
    ProvisionField(config.subnet, "子网掩码", { onChange(config.copy(subnet = it)) }, KeyboardType.Decimal)
    ProvisionField(config.dns1, "主 DNS", { onChange(config.copy(dns1 = it)) }, KeyboardType.Decimal)
    ProvisionField(config.dns2, "备用 DNS", { onChange(config.copy(dns2 = it)) }, KeyboardType.Decimal)
}

@Composable
internal fun ProvisionField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
    )
}
