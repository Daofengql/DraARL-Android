package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.ble.BleServerConfig
import cn.silverdragon.draarl.tools.ble.BleWifiConfig

@Composable
internal fun BleWifiForm(
    config: BleWifiConfig,
    busy: Boolean,
    onChange: (BleWifiConfig) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Wi-Fi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(config.ssid, { onChange(config.copy(ssid = it)) }, label = { Text("SSID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                config.password,
                { onChange(config.copy(password = it)) },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("自动获取地址", modifier = Modifier.weight(1f))
                Switch(checked = config.dhcp, onCheckedChange = { onChange(config.copy(dhcp = it)) })
            }
            if (!config.dhcp) {
                OutlinedTextField(config.ip, { onChange(config.copy(ip = it)) }, label = { Text("IP 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(config.gateway, { onChange(config.copy(gateway = it)) }, label = { Text("网关") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(config.subnet, { onChange(config.copy(subnet = it)) }, label = { Text("子网掩码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(config.dns1, { onChange(config.copy(dns1 = it)) }, label = { Text("主 DNS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(config.dns2, { onChange(config.copy(dns2 = it)) }, label = { Text("备用 DNS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("写入 Wi-Fi 配置") }
        }
    }
}

@Composable
internal fun BleServerForm(
    config: BleServerConfig,
    busy: Boolean,
    onChange: (BleServerConfig) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DraARL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(config.account, { onChange(config.copy(account = it)) }, label = { Text("账号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                config.deviceAuthPassword,
                { onChange(config.copy(deviceAuthPassword = it)) },
                label = { Text("设备认证密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(config.callsign, { onChange(config.copy(callsign = it.uppercase())) }, label = { Text("呼号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.nodeSsid.toString(), { onChange(config.copy(nodeSsid = it.toIntOrNull() ?: 0)) }, label = { Text("SSID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.udpHost, { onChange(config.copy(udpHost = it)) }, label = { Text("UDP 主机") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.udpPort.toString(), { onChange(config.copy(udpPort = it.toIntOrNull() ?: 0)) }, label = { Text("UDP 端口") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.httpApiBaseUrl, { onChange(config.copy(httpApiBaseUrl = it)) }, label = { Text("HTTPS API") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("写入 DraARL 配置") }
        }
    }
}
