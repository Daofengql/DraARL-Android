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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.tools.ble.BleConnectionPhase
import cn.silverdragon.draarl.tools.ble.BleDeviceInfo
import cn.silverdragon.draarl.tools.ble.BleDeviceProfile
import cn.silverdragon.draarl.tools.ble.BleDeviceProfiles
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.DraarlSettings
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsRow
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.PageFeedback
import cn.silverdragon.draarl.ui.components.PageFeedbackKind
import cn.silverdragon.draarl.ui.components.StatusIndicator
import cn.silverdragon.draarl.ui.components.StatusTone

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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (permissions.all {
                results[it] == true ||
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            showDevices = true
            ble.startScan()
        } else {
            permissionError = "需要蓝牙权限才能扫描和配置设备"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ToolHeader("蓝牙配置", onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                DraarlSettingsSectionTitle("设备类型")
                DraarlSettingsGroup {
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            icon = Icons.Default.SettingsInputAntenna,
                            title = ble.selectedProfile.label,
                            detail = ble.selectedProfile.description,
                            onClick = { showProfiles = true }
                        ),
                        enabled = !ble.busy && ble.status.phase !in setOf(
                            BleConnectionPhase.CONNECTING,
                            BleConnectionPhase.DISCOVERING
                        )
                    )
                }
            }
        }
        if (permissionError.isNotBlank()) {
            item { ToolError(permissionError, onDismiss = { permissionError = "" }) }
        }
        if (ble.error.isNotBlank()) item { ToolError(ble.error, ble::clearFeedback) }
        if (ble.message.isNotBlank()) {
            item {
                InlineNotice(
                    text = ble.message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    tone = StatusTone.CONNECTED
                )
            }
        }
        item {
            BleStatusPanel(
                phase = ble.status.phase,
                deviceName = ble.status.deviceName.ifBlank { ble.selectedProfile.label },
                wifiState = ble.status.wifiState,
                authenticated = ble.status.authenticated,
                rssi = ble.status.rssi
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CommandButton(
                    label = if (ble.status.phase == BleConnectionPhase.SCANNING) "正在扫描" else "扫描设备",
                    onClick = {
                        if (permissions.all {
                                ContextCompat.checkSelfPermission(context, it) ==
                                    PackageManager.PERMISSION_GRANTED
                            }
                        ) {
                            showDevices = true
                            ble.startScan()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    enabled = !ble.busy,
                    modifier = Modifier.weight(1f),
                    style = CommandStyle.PRIMARY,
                    leadingIcon = Icons.AutoMirrored.Filled.BluetoothSearching
                )
                CommandButton(
                    label = "断开设备",
                    onClick = ble::disconnect,
                    enabled =
                        ble.status.phase in
                            setOf(
                                BleConnectionPhase.CONNECTING,
                                BleConnectionPhase.DISCOVERING,
                                BleConnectionPhase.READY
                            ),
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.BluetoothDisabled
                )
            }
        }
        if (ble.status.phase == BleConnectionPhase.READY && !ble.status.authenticated) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    DraarlSettingsSectionTitle("设备认证", detail = "验证后读取当前配置")
                    DraarlSettingsGroup {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = dynamicCode,
                                onValueChange = { dynamicCode = it.filter(Char::isDigit).take(6) },
                                label = { Text("6 位动态码") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            CommandButton(
                                label = if (ble.busy) "正在认证并读取配置" else "认证并读取配置",
                                onClick = { ble.authenticate(dynamicCode) },
                                enabled = !ble.busy && dynamicCode.length == 6,
                                modifier = Modifier.fillMaxWidth(),
                                style = CommandStyle.PRIMARY,
                                leadingIcon = Icons.Default.LockOpen
                            )
                        }
                    }
                }
            }
        }
        if (ble.status.authenticated) {
            if (ble.selectedProfile.supportsWifi) {
                item {
                    BleWifiForm(
                        config = ble.config.wifi,
                        busy = ble.busy,
                        onChange = ble::updateWifi,
                        onSave = ble::saveWifi
                    )
                }
            }
            if (ble.selectedProfile.supportsDraarl) {
                item {
                    BleServerForm(
                        config = ble.config.server,
                        busy = ble.busy,
                        onChange = ble::updateServer,
                        onSave = ble::saveServer
                    )
                }
            }
        }
    }

    if (showDevices) {
        DevicePickerDialog(
            devices = ble.devices,
            scanning = ble.status.phase == BleConnectionPhase.SCANNING,
            onDismiss = {
                showDevices = false
                ble.stopScan()
            },
            onSelect = { device ->
                showDevices = false
                ble.connect(device)
            }
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
            }
        )
    }
}

@Composable
private fun BleStatusPanel(
    phase: BleConnectionPhase,
    deviceName: String,
    wifiState: String,
    authenticated: Boolean,
    rssi: Int?
) {
    DraarlSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                StatusIndicator(phase.displayName(), phase.statusTone())
            }
            if (phase == BleConnectionPhase.READY) {
                Text(
                    "Wi-Fi $wifiState · ${if (authenticated) "已认证" else "未认证"}" +
                        rssi?.let { " · $it dBm" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceProfileDialog(
    profiles: List<BleDeviceProfile>,
    selectedKey: String,
    onDismiss: () -> Unit,
    onSelect: (BleDeviceProfile) -> Unit
) {
    DraarlDialog(
        title = "选择设备类型",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            profiles.forEachIndexed { index, profile ->
                DeviceProfileRow(
                    profile = profile,
                    selected = profile.key == selectedKey,
                    showDivider = index < profiles.lastIndex,
                    onSelect = onSelect
                )
            }
        }
    }
}

@Composable
private fun DeviceProfileRow(
    profile: BleDeviceProfile,
    selected: Boolean,
    showDivider: Boolean,
    onSelect: (BleDeviceProfile) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(profile) }
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                profile.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                profile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "当前设备类型")
    }
    if (showDivider) HorizontalDivider()
}

@Composable
private fun DevicePickerDialog(
    devices: List<BleDeviceInfo>,
    scanning: Boolean,
    onDismiss: () -> Unit,
    onSelect: (BleDeviceInfo) -> Unit
) {
    DraarlDialog(
        title = "选择设备",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss)
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp).padding(horizontal = 18.dp)
        ) {
            if (devices.isEmpty()) {
                item {
                    BleDevicePickerFeedback(scanning)
                }
            }
            items(devices.size, key = { devices[it].address }) { index ->
                val device = devices[index]
                Column(
                    Modifier.fillMaxWidth().clickable { onSelect(device) }.padding(vertical = 12.dp)
                ) {
                    Text(device.name, fontWeight = FontWeight.SemiBold)
                    Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BleDevicePickerFeedback(scanning: Boolean) {
    PageFeedback(
        kind = if (scanning) PageFeedbackKind.LOADING else PageFeedbackKind.EMPTY,
        title = if (scanning) "正在查找设备" else "没有发现设备",
        detail = if (scanning) "正在扫描附近的 DraARL 设备" else "请确认设备已开机并处于配网模式"
    )
}

private fun requiredBlePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun BleConnectionPhase.displayName(): String = when (this) {
    BleConnectionPhase.IDLE -> "未连接"
    BleConnectionPhase.SCANNING -> "正在扫描"
    BleConnectionPhase.CONNECTING -> "正在连接"
    BleConnectionPhase.DISCOVERING -> "正在初始化"
    BleConnectionPhase.READY -> "已连接"
    BleConnectionPhase.DISCONNECTED -> "已断开"
    BleConnectionPhase.ERROR -> "连接异常"
}

private fun BleConnectionPhase.statusTone(): StatusTone = when (this) {
    BleConnectionPhase.SCANNING,
    BleConnectionPhase.CONNECTING,
    BleConnectionPhase.DISCOVERING -> StatusTone.CONNECTING

    BleConnectionPhase.READY -> StatusTone.CONNECTED

    BleConnectionPhase.ERROR -> StatusTone.ERROR

    BleConnectionPhase.IDLE,
    BleConnectionPhase.DISCONNECTED -> StatusTone.NEUTRAL
}
