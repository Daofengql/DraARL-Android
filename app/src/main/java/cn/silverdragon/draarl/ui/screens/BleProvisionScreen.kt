package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.tools.ble.BleConnectionPhase
import cn.silverdragon.draarl.tools.ble.BleDeviceInfo
import cn.silverdragon.draarl.tools.ble.BleDeviceProfile
import cn.silverdragon.draarl.tools.ble.BleDeviceProfiles

@Composable
internal fun BleProvisionScreen(tools: ToolsController, onBack: () -> Unit) {
    val ble = tools.ble
    val context = LocalContext.current
    var dynamicCode by remember { mutableStateOf("") }
    var showDevices by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf("") }
    val permissions = remember { requiredBlePermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (permissions.all { results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            showDevices = true
            ble.startScan()
        } else {
            permissionError = "需要蓝牙权限才能扫描和配置设备"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ToolHeader("蓝牙配置", onBack) }
        item {
            OutlinedButton(
                onClick = { showProfiles = true },
                enabled = !ble.busy && ble.status.phase !in setOf(
                    BleConnectionPhase.CONNECTING,
                    BleConnectionPhase.DISCOVERING,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(ble.selectedProfile.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        ble.selectedProfile.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Default.ExpandMore, contentDescription = "选择设备类型")
            }
        }
        if (permissionError.isNotBlank()) item { ToolError(permissionError) { permissionError = "" } }
        if (ble.error.isNotBlank()) item { ToolError(ble.error, ble::clearFeedback) }
        if (ble.message.isNotBlank()) {
            item {
                Text(
                    ble.message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            BleStatusCard(
                phase = ble.status.phase,
                deviceName = ble.status.deviceName.ifBlank { ble.selectedProfile.label },
                wifiState = ble.status.wifiState,
                authenticated = ble.status.authenticated,
                rssi = ble.status.rssi,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                            showDevices = true
                            ble.startScan()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    enabled = !ble.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                    Text("扫描", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = ble::disconnect,
                    enabled = ble.status.phase in setOf(BleConnectionPhase.CONNECTING, BleConnectionPhase.DISCOVERING, BleConnectionPhase.READY),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.BluetoothDisabled, contentDescription = null)
                    Text("断开", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (ble.status.phase == BleConnectionPhase.READY && !ble.status.authenticated) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = dynamicCode,
                        onValueChange = { dynamicCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("6 位动态码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = { ble.authenticate(dynamicCode) },
                        enabled = !ble.busy && dynamicCode.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (ble.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Text("认证并读取配置", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        if (ble.status.authenticated) {
            if (ble.selectedProfile.supportsWifi) {
                item { BleWifiForm(config = ble.config.wifi, busy = ble.busy, onChange = ble::updateWifi, onSave = ble::saveWifi) }
            }
            if (ble.selectedProfile.supportsDraarl) {
                item { BleServerForm(config = ble.config.server, busy = ble.busy, onChange = ble::updateServer, onSave = ble::saveServer) }
            }
        }
    }

    if (showDevices) {
        DevicePickerDialog(
            devices = ble.devices,
            scanning = ble.status.phase == BleConnectionPhase.SCANNING,
            onDismiss = { showDevices = false; ble.stopScan() },
            onSelect = { device -> showDevices = false; ble.connect(device) },
        )
    }
    if (showProfiles) {
        DeviceProfileDialog(
            profiles = BleDeviceProfiles.all,
            selectedKey = ble.selectedProfileKey,
            onDismiss = { showProfiles = false },
            onSelect = { profile ->
                ble.selectProfile(profile.key)
                showProfiles = false
            },
        )
    }
}

@Composable
private fun BleStatusCard(
    phase: BleConnectionPhase,
    deviceName: String,
    wifiState: String,
    authenticated: Boolean,
    rssi: Int?,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(deviceName, fontWeight = FontWeight.SemiBold)
            Text(
                when (phase) {
                    BleConnectionPhase.IDLE -> "未连接"
                    BleConnectionPhase.SCANNING -> "正在扫描"
                    BleConnectionPhase.CONNECTING -> "正在连接"
                    BleConnectionPhase.DISCOVERING -> "正在初始化"
                    BleConnectionPhase.READY -> "已连接"
                    BleConnectionPhase.DISCONNECTED -> "已断开"
                    BleConnectionPhase.ERROR -> "连接异常"
                },
                color = if (phase == BleConnectionPhase.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (phase == BleConnectionPhase.READY) {
                Text("Wi-Fi $wifiState · ${if (authenticated) "已认证" else "未认证"}${rssi?.let { " · $it dBm" }.orEmpty()}")
            }
        }
    }
}

@Composable
private fun DeviceProfileDialog(
    profiles: List<BleDeviceProfile>,
    selectedKey: String,
    onDismiss: () -> Unit,
    onSelect: (BleDeviceProfile) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择设备类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                profiles.forEach { profile ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(profile) },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                profile.label,
                                fontWeight = if (profile.key == selectedKey) FontWeight.Bold else FontWeight.Medium,
                                color = if (profile.key == selectedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DevicePickerDialog(
    devices: List<BleDeviceInfo>,
    scanning: Boolean,
    onDismiss: () -> Unit,
    onSelect: (BleDeviceInfo) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择设备") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)) {
                if (devices.isEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(if (scanning) "正在查找 DraARL 设备" else "没有发现设备", Modifier.padding(start = 12.dp))
                        }
                    }
                }
                items(devices.size, key = { devices[it].address }) { index ->
                    val device = devices[index]
                    Column(
                        Modifier.fillMaxWidth().clickable { onSelect(device) }.padding(vertical = 12.dp),
                    ) {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun requiredBlePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
