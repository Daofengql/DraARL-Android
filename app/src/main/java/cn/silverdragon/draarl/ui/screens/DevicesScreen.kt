package cn.silverdragon.draarl.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.ReplaceableDevice
import cn.silverdragon.draarl.data.deviceModelName
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlConfirmation
import cn.silverdragon.draarl.ui.components.DraarlConfirmationDialog
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.EmptyState
import cn.silverdragon.draarl.ui.components.PageFeedback
import cn.silverdragon.draarl.ui.components.PageFeedbackKind
import cn.silverdragon.draarl.ui.components.StatusIndicator
import cn.silverdragon.draarl.ui.components.StatusPill
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.state.activeGroups
import cn.silverdragon.draarl.ui.state.filterDevices
import cn.silverdragon.draarl.ui.state.groupNamesById
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.dataTypography
import java.math.BigDecimal
import java.math.RoundingMode

@Immutable
internal data class DevicesContentState(
    val devices: List<Device>,
    val groups: List<Group>,
    val defaultGroupId: Int?,
    val loading: Boolean
)

internal sealed interface DevicesContentAction {
    data class OpenDevice(val device: Device) : DevicesContentAction

    data object OpenDefaultGroup : DevicesContentAction

    data object BindDevice : DevicesContentAction

    data object OpenPassword : DevicesContentAction
}

@Composable
fun DevicesScreen(controller: AppController) {
    var detailDeviceId by remember { mutableStateOf<Int?>(null) }
    var showDefaultGroup by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showBind by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Device?>(null) }
    var groupTarget by remember { mutableStateOf<Device?>(null) }
    var configTarget by remember { mutableStateOf<Device?>(null) }
    var deleteTarget by remember { mutableStateOf<Device?>(null) }

    val devices = controller.devices
    val groups = controller.groups
    val devicesById = remember(devices) { devices.associateBy(Device::id) }
    val groupNames = remember(groups) { groupNamesById(groups) }
    val enabledGroups = remember(groups) { activeGroups(groups) }
    DevicesContent(
        state = DevicesContentState(
            devices = devices,
            groups = groups,
            defaultGroupId = controller.deviceManagement.defaultDeviceGroupId,
            loading = controller.contentLoading
        ),
        onAction = { action ->
            when (action) {
                is DevicesContentAction.OpenDevice -> detailDeviceId = action.device.id

                DevicesContentAction.OpenDefaultGroup -> showDefaultGroup = true

                DevicesContentAction.BindDevice -> {
                    controller.deviceManagement.resetBinding()
                    showBind = true
                }

                DevicesContentAction.OpenPassword -> {
                    controller.deviceManagement.loadPassword()
                    showPassword = true
                }
            }
        }
    )

    val detailDevice = detailDeviceId?.let(devicesById::get)
    detailDevice?.let { device ->
        DeviceDetailDialog(
            device = device,
            groupName = groupNames[device.groupId].orEmpty(),
            busy = controller.deviceManagement.busy,
            onClose = { detailDeviceId = null },
            onRename = { renameTarget = device },
            onSwitchGroup = { groupTarget = device },
            onConfig = {
                configTarget = device
                controller.deviceManagement.loadConfig(device.id)
            },
            onSendChanged = { controller.deviceManagement.updateDevice(device, disableSend = it) },
            onReceiveChanged = { controller.deviceManagement.updateDevice(device, disableReceive = it) },
            onDelete = { deleteTarget = device }
        )
    }

    if (showDefaultGroup) {
        GroupPickerDialog(
            title = "新设备默认群组",
            groups = enabledGroups,
            selectedGroupId = controller.deviceManagement.defaultDeviceGroupId,
            allowNone = true,
            onDismiss = { showDefaultGroup = false },
            onSelect = {
                controller.deviceManagement.setDefaultGroup(it?.id)
                showDefaultGroup = false
            }
        )
    }

    renameTarget?.let { device ->
        RenameDeviceDialog(
            device = device,
            busy = controller.deviceManagement.busy,
            onDismiss = { renameTarget = null },
            onSave = { name -> controller.deviceManagement.updateDevice(device, name = name) { renameTarget = null } }
        )
    }

    groupTarget?.let { device ->
        GroupPickerDialog(
            title = "切换设备群组",
            groups = enabledGroups,
            selectedGroupId = device.groupId,
            allowNone = false,
            onDismiss = { groupTarget = null },
            onSelect = { group ->
                if (group != null) controller.deviceManagement.switchGroup(device, group) { groupTarget = null }
            }
        )
    }

    configTarget?.let { device ->
        DeviceConfigDialog(
            controller = controller,
            device = device,
            onClose = {
                configTarget = null
                controller.deviceManagement.closeConfig()
            }
        )
    }

    deleteTarget?.let { device ->
        ConfirmDeviceActionDialog(
            title = "删除设备",
            message = "确定删除“${device.name.ifBlank { "设备 ${device.id}" }}”吗？删除后需要重新绑定。",
            confirmText = "删除",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                controller.deviceManagement.deleteDevice(device) {
                    deleteTarget = null
                    detailDeviceId = null
                }
            }
        )
    }

    if (showPassword) {
        DevicePasswordDialog(controller, onClose = { showPassword = false })
    }

    if (showBind) {
        DynamicBindDialog(controller, onClose = {
            showBind = false
            controller.deviceManagement.resetBinding()
        })
    }
}

@Composable
internal fun DevicesContent(
    state: DevicesContentState,
    onAction: (DevicesContentAction) -> Unit,
    initialFilter: String = ""
) {
    val devices = state.devices
    val groups = state.groups
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    val query = filter.trim()
    val visibleDevices = remember(devices, query) { filterDevices(devices, query) }
    val groupsById = remember(groups) { groups.associateBy(Group::id) }
    val groupNames = remember(groups) { groupNamesById(groups) }
    val defaultGroup = state.defaultGroupId?.let(groupsById::get)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("搜索名称、呼号或 SSID") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            CommandIconButton(
                onClick = { onAction(DevicesContentAction.BindDevice) },
                contentDescription = "动态码绑定",
                icon = Icons.Default.QrCodeScanner
            )
            CommandIconButton(
                onClick = { onAction(DevicesContentAction.OpenPassword) },
                contentDescription = "设备密码",
                icon = Icons.Default.Key
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(DevicesContentAction.OpenDefaultGroup) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("新设备默认群组", fontWeight = FontWeight.Medium)
                Text(
                    defaultGroup?.let { "${it.name}（${it.id}）" } ?: "未设置，新设备只登记不参与转发",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
        HorizontalDivider()

        DevicesBody(state, visibleDevices, groupNames, onAction)
    }
}

@Composable
private fun DevicesBody(
    state: DevicesContentState,
    visibleDevices: List<Device>,
    groupNames: Map<Int, String>,
    onAction: (DevicesContentAction) -> Unit
) {
    when {
        state.devices.isEmpty() && state.loading -> PageFeedback(
            kind = PageFeedbackKind.LOADING,
            title = "正在加载设备",
            detail = "正在同步设备与默认群组",
            modifier = Modifier.fillMaxSize()
        )

        state.devices.isEmpty() -> EmptyState(
            Icons.Default.Devices,
            "暂无设备",
            "使用动态码绑定，或让设备首次接入后再查看",
            modifier = Modifier.fillMaxSize()
        )

        visibleDevices.isEmpty() -> EmptyState(
            Icons.Default.Search,
            "没有匹配的设备",
            "换一个名称、呼号或 SSID 试试",
            modifier = Modifier.fillMaxSize()
        )

        else -> LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(visibleDevices, key = Device::id) { device ->
                DeviceListRow(
                    device = device,
                    groupName = groupNames[device.groupId].orEmpty(),
                    onClick = { onAction(DevicesContentAction.OpenDevice(device)) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun DeviceListRow(device: Device, groupName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceAvatar(device)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    device.name.ifBlank { "${device.callsign}-${device.ssid}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusIndicator(
                    text = if (device.online) "在线" else "离线",
                    tone = if (device.online) StatusTone.CONNECTED else StatusTone.NEUTRAL
                )
            }
            Text(
                "${device.callsign}-${device.ssid} · ${deviceModelName(device.model)}",
                style = MaterialTheme.dataTypography.compact,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                groupName.ifBlank { if (device.groupId == 0) "未分组" else "群组 ${device.groupId}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            val restrictions = buildList {
                if (device.disableSend) add("禁发")
                if (device.disableReceive) add("禁收")
                if (!device.enabled) add("停用")
            }
            if (restrictions.isNotEmpty()) {
                StatusIndicator(
                    text = restrictions.joinToString(" · "),
                    tone = StatusTone.ERROR
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DeviceAvatar(device: Device, size: Int = 48) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (device.online) {
            MaterialTheme.appColors.receiveContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Devices,
                contentDescription = null,
                tint = if (device.online) {
                    MaterialTheme.appColors.onReceiveContainer
                } else {
                    MaterialTheme.appColors.statusOffline
                },
                modifier = Modifier.size((size * 0.5f).dp)
            )
        }
    }
}

@Composable
private fun DeviceDetailDialog(
    device: Device,
    groupName: String,
    busy: Boolean,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onSwitchGroup: () -> Unit,
    onConfig: () -> Unit,
    onSendChanged: (Boolean) -> Unit,
    onReceiveChanged: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    FullDeviceDialog(onClose) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraarlIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onClose
                    )
                    Text("设备资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                DeviceAvatar(device, 76)
                Spacer(Modifier.height(12.dp))
                Text(
                    device.name.ifBlank { "设备 ${device.id}" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${device.callsign}-${device.ssid} · ${if (device.online) "在线" else "离线"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                DeviceValueRow("设备型号", deviceModelName(device.model))
                DeviceValueRow("所在群组", groupName.ifBlank { if (device.groupId == 0) "未分组" else "群组 ${device.groupId}" })
                DeviceValueRow("入口节点", device.entryName.ifBlank { "-" })
                DeviceValueRow("最新 IP", device.lastOnlineIp.ifBlank { "-" })
                if (device.lastOnlineIpLocation.isNotBlank()) DeviceValueRow("IP 归属", device.lastOnlineIpLocation)
                if (device.qth.isNotBlank()) DeviceValueRow("位置", device.qth)
                if (device.onlineTime.isNotBlank()) DeviceValueRow("最近上线", device.onlineTime)
                if (device.note.isNotBlank()) DeviceValueRow("备注", device.note)

                HorizontalDivider(Modifier.padding(top = 10.dp))
                DeviceActionRow(Icons.Default.Settings, "修改设备名称", "设置便于识别的名称", onRename)
                DeviceActionRow(
                    Icons.Default.Router,
                    "切换群组",
                    "当前：${groupName.ifBlank { device.groupId.toString() }}",
                    onSwitchGroup
                )
                if (device.model in 1..2) {
                    DeviceActionRow(Icons.Default.Settings, "参数配置", "频率、功率、静噪与音频", onConfig)
                }
                DeviceSwitchRow("禁止发送", "阻止此设备向群组发送", device.disableSend, busy, onSendChanged)
                DeviceSwitchRow("禁止接收", "阻止此设备接收群组消息", device.disableReceive, busy, onReceiveChanged)
                TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.padding(vertical = 18.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(" 删除设备")
                }
            }
        }
    }
}

@Composable
private fun DeviceValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(82.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DeviceActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DeviceSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    busy: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = !busy)
    }
}

@Composable
private fun GroupPickerDialog(
    title: String,
    groups: List<Group>,
    selectedGroupId: Int?,
    allowNone: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Group?) -> Unit
) {
    DraarlDialog(
        title = title,
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss)
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            if (allowNone) {
                item(key = "none") {
                    PickerRow("未设置", "只登记，不参与转发", selectedGroupId == null) { onSelect(null) }
                }
            }
            items(groups, key = Group::id) { group ->
                PickerRow(
                    group.name,
                    "ID ${group.id} · ${if (group.isPrivate) "私有" else "公开"}",
                    group.id == selectedGroupId
                ) { onSelect(group) }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.Router else Icons.Default.Devices,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) StatusPill("当前", MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RenameDeviceDialog(device: Device, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(device.id) { mutableStateOf(device.name) }
    DraarlDialog(
        title = "修改设备名称",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss, enabled = !busy),
        confirmAction = DraarlAction(
            "保存",
            { onSave(name.trim()) },
            enabled = name.isNotBlank() && !busy,
            style = CommandStyle.PRIMARY
        )
    ) {
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("设备名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(18.dp)
        )
    }
}

@Composable
private fun DeviceConfigDialog(controller: AppController, device: Device, onClose: () -> Unit) {
    var txFreq by remember(device.id) { mutableStateOf("") }
    var rxFreq by remember(device.id) { mutableStateOf("") }
    var sameFrequency by remember(device.id) { mutableStateOf(true) }
    var squelch by remember(device.id) { mutableFloatStateOf(0f) }
    var highPower by remember(device.id) { mutableStateOf(false) }
    var wideBandwidth by remember(device.id) { mutableStateOf(false) }
    var rfGuard by remember(device.id) { mutableStateOf(false) }
    var singleLimit by remember(device.id) { mutableStateOf("60") }
    var guardWindow by remember(device.id) { mutableStateOf("300") }
    var windowLimit by remember(device.id) { mutableStateOf("180") }
    var adcGain by remember(device.id) { mutableFloatStateOf(0f) }
    var adcVolume by remember(device.id) { mutableFloatStateOf(80f) }
    var dacVolume by remember(device.id) { mutableFloatStateOf(80f) }
    var sqlActiveHigh by remember(device.id) { mutableStateOf(false) }
    var pttActiveHigh by remember(device.id) { mutableStateOf(false) }

    LaunchedEffect(controller.deviceManagement.config, controller.deviceManagement.configDeviceId) {
        if (
            controller.deviceManagement.configDeviceId != device.id ||
            controller.deviceManagement.config.isEmpty()
        ) {
            return@LaunchedEffect
        }
        val config = controller.deviceManagement.config
        txFreq = hzToMhz(config["tx_freq"].orEmpty())
        rxFreq = hzToMhz(config["rx_freq"].orEmpty())
        sameFrequency = rxFreq.isBlank() || txFreq == rxFreq
        squelch = config["sql_level"]?.toFloatOrNull()?.coerceIn(0f, 8f) ?: 0f
        highPower = config["power_level"] == "3"
        wideBandwidth = config["tx_bandwidth"] == "2"
        rfGuard = config["rf_guard_enabled"] == "1"
        singleLimit = config["rf_guard_single_tx_limit_s"] ?: "60"
        guardWindow = config["rf_guard_window_s"] ?: "300"
        windowLimit = config["rf_guard_max_tx_in_window_s"] ?: "180"
        adcGain = config["adc_gain_db"]?.toFloatOrNull()?.coerceIn(0f, 24f) ?: 0f
        adcVolume = config["adc_volume"]?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 80f
        dacVolume = config["dac_volume"]?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 80f
        sqlActiveHigh = config["sql_active_high"] == "1"
        pttActiveHigh = config["ptt_active_high"] == "1"
    }

    FullDeviceDialog(onClose) {
        Scaffold(
            topBar = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraarlIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onClose
                    )
                    Column(Modifier.weight(1f)) {
                        Text("参数配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            device.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (controller.deviceManagement.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!device.online) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("设备当前离线，配置会保存到服务器，并在上线后自动同步。", modifier = Modifier.padding(12.dp))
                    }
                }
                Text("频率与发射", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    txFreq,
                    { txFreq = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("发射频率 MHz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("收发同频", modifier = Modifier.weight(1f))
                    Switch(sameFrequency, { sameFrequency = it })
                }
                if (!sameFrequency) {
                    OutlinedTextField(
                        rxFreq,
                        { rxFreq = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("接收频率 MHz") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ValueSlider("静噪等级", squelch, 0f..8f, 7) { squelch = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!highPower, { highPower = false }, { Text("低功率") })
                    FilterChip(highPower, { highPower = true }, { Text("高功率") })
                    FilterChip(!wideBandwidth, { wideBandwidth = false }, { Text("窄带") })
                    FilterChip(wideBandwidth, { wideBandwidth = true }, { Text("宽带") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("射频保护")
                        Text(
                            "限制连续和窗口内发射时长",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(rfGuard, { rfGuard = it })
                }
                if (rfGuard) {
                    NumericConfigField("单次发射上限（秒）", singleLimit) { singleLimit = it }
                    NumericConfigField("统计窗口（秒）", guardWindow) { guardWindow = it }
                    NumericConfigField("窗口累计上限（秒）", windowLimit) { windowLimit = it }
                }
                HorizontalDivider()
                Text("音频与接口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ValueSlider("ADC 增益 dB", adcGain, 0f..24f, 23) { adcGain = it }
                ValueSlider("输入音量", adcVolume, 0f..100f, 19) { adcVolume = it }
                ValueSlider("输出音量", dacVolume, 0f..100f, 19) { dacVolume = it }
                DeviceSwitchRow("SQL 高电平有效", "接收信号检测极性", sqlActiveHigh, false) { sqlActiveHigh = it }
                DeviceSwitchRow("PTT 高电平有效", "发射控制极性", pttActiveHigh, false) { pttActiveHigh = it }
                Button(
                    onClick = {
                        val updated = controller.deviceManagement.config.toMutableMap().apply {
                            put("tx_freq", mhzToHz(txFreq))
                            put("rx_freq", mhzToHz(if (sameFrequency) txFreq else rxFreq))
                            put("sql_level", squelch.toInt().toString())
                            put("power_level", if (highPower) "3" else "1")
                            put("tx_bandwidth", if (wideBandwidth) "2" else "1")
                            put("rf_guard_enabled", if (rfGuard) "1" else "0")
                            put("rf_guard_single_tx_limit_s", singleLimit)
                            put("rf_guard_window_s", guardWindow)
                            put("rf_guard_max_tx_in_window_s", windowLimit)
                            put("adc_gain_db", adcGain.toInt().toString())
                            put("adc_volume", adcVolume.toInt().toString())
                            put("dac_volume", dacVolume.toInt().toString())
                            put("sql_active_high", if (sqlActiveHigh) "1" else "0")
                            put("ptt_active_high", if (pttActiveHigh) "1" else "0")
                        }
                        controller.deviceManagement.saveConfig(device, updated, onClose)
                    },
                    enabled = !controller.deviceManagement.busy &&
                        txFreq.isNotBlank() &&
                        (sameFrequency || rxFreq.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(if (device.online) "保存并同步" else "保存配置")
                }
            }
        }
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value.toInt().toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun NumericConfigField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DevicePasswordDialog(controller: AppController, onClose: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    DraarlDialog(
        title = "设备密码",
        onDismissRequest = onClose,
        dismissAction = DraarlAction(
            label = "刷新密码",
            onClick = { confirmRegenerate = true },
            enabled = !controller.deviceManagement.busy
        ),
        confirmAction = DraarlAction("关闭", onClose, style = CommandStyle.PRIMARY)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("此密码只用于硬件设备接入，不是账号登录密码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (controller.deviceManagement.busy && controller.deviceManagement.passwordInfo == null) {
                Box(
                    Modifier.fillMaxWidth().height(90.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                Text(
                    "账号",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(controller.session.uiState.user?.username.orEmpty(), fontFamily = FontFamily.Monospace)
                Text(
                    "设备密码",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (visible) controller.deviceManagement.passwordInfo?.password.orEmpty() else "••••••••",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    DraarlIconButton(
                        icon = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        label = if (visible) "隐藏密码" else "显示密码",
                        onClick = { visible = !visible }
                    )
                    DraarlIconButton(
                        icon = Icons.Default.ContentCopy,
                        label = "复制密码",
                        onClick = {
                            copyText(
                                context,
                                "设备密码",
                                controller.deviceManagement.passwordInfo?.password.orEmpty()
                            )
                        }
                    )
                }
            }
        }
    }
    if (confirmRegenerate) {
        ConfirmDeviceActionDialog(
            title = "刷新设备密码",
            message = "刷新后旧密码立即失效，现有设备下次连接时需要使用新密码重新配置。",
            confirmText = "确认刷新",
            onDismiss = { confirmRegenerate = false },
            onConfirm = {
                confirmRegenerate = false
                visible = true
                controller.deviceManagement.regeneratePassword()
            }
        )
    }
}

@Composable
private fun DynamicBindDialog(controller: AppController, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf<ReplaceableDevice?>(null) }
    val context = LocalContext.current
    val preview = controller.deviceManagement.bindPreview
    val result = controller.deviceManagement.bindResult

    LaunchedEffect(preview) {
        if (preview != null) {
            ssid = preview.recommendedSsid.takeIf { it > 0 }?.toString()
                ?: preview.availableSsids.firstOrNull()?.toString().orEmpty()
            replacement = if (preview.availableSsids.isEmpty()) preview.replaceableDevices.firstOrNull() else null
        }
    }

    DraarlDialog(
        title = "动态码绑定",
        onDismissRequest = onClose,
        dismissAction = DraarlAction(if (result == null) "取消" else "完成", onClose),
        confirmAction = when {
            result != null -> null

            preview != null -> DraarlAction(
                label = "提交配置",
                onClick = {
                    controller.deviceManagement.submitBinding(
                        ssid.toIntOrNull(),
                        replacement?.deviceId
                    )
                },
                enabled = !controller.deviceManagement.busy && (ssid.isNotBlank() || replacement != null),
                style = CommandStyle.PRIMARY
            )

            else -> DraarlAction(
                label = "下一步",
                onClick = { controller.deviceManagement.lookupBindCode(code) },
                enabled = !controller.deviceManagement.busy && code.length == 6,
                style = CommandStyle.PRIMARY
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                result != null -> BindingResultContent(result, context)

                preview != null -> BindingPreviewContent(
                    preview = preview,
                    ssid = ssid,
                    onSsidChange = {
                        replacement = null
                        ssid = it.filter(Char::isDigit).take(3)
                    },
                    replacement = replacement,
                    onReplacement = {
                        replacement = it
                        ssid = it.ssid.toString()
                    }
                )

                else -> {
                    Text("输入设备屏幕或串口显示的 6 位动态码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        code,
                        { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("6 位动态码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BindingPreviewContent(
    preview: DeviceBindPreview,
    ssid: String,
    onSsidChange: (String) -> Unit,
    replacement: ReplaceableDevice?,
    onReplacement: (ReplaceableDevice) -> Unit
) {
    Text(
        preview.callsign.ifBlank { "待绑定设备" },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "MAC ${preview.deviceMac}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (preview.availableSsids.isNotEmpty()) {
        OutlinedTextField(
            ssid,
            onSsidChange,
            label = { Text("SSID") },
            supportingText = { Text("可用：${preview.availableSsids.joinToString()}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (preview.replaceableDevices.isNotEmpty()) {
        Text("或让离线设备重新上线", fontWeight = FontWeight.Medium)
        preview.replaceableDevices.forEach { device ->
            FilterChip(
                selected = replacement?.deviceId == device.deviceId,
                onClick = { onReplacement(device) },
                label = { Text("${device.name.ifBlank { device.callsign }} · ${device.callsign}-${device.ssid}") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BindingResultContent(result: cn.silverdragon.draarl.data.DeviceBindResult, context: Context) {
    Text(
        result.message.ifBlank { "设备配置已提交" },
        color = MaterialTheme.appColors.statusConnected,
        fontWeight = FontWeight.SemiBold
    )
    Text("SSID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(result.ssid?.toString().orEmpty(), fontFamily = FontFamily.Monospace)
    Text("UDP 账号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(result.username, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        DraarlIconButton(
            icon = Icons.Default.ContentCopy,
            label = "复制账号",
            onClick = { copyText(context, "UDP 账号", result.username) }
        )
    }
    Text("设备密码", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(result.devicePassword, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        DraarlIconButton(
            icon = Icons.Default.ContentCopy,
            label = "复制密码",
            onClick = { copyText(context, "设备密码", result.devicePassword) }
        )
    }
    if (result.dmrId > 0) Text("DMR ID ${result.dmrId}")
}

private fun copyText(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun hzToMhz(value: String): String = value.toBigDecimalOrNull()
    ?.divide(BigDecimal("1000000"))
    ?.stripTrailingZeros()
    ?.toPlainString()
    .orEmpty()

private fun mhzToHz(value: String): String = value.toBigDecimalOrNull()
    ?.multiply(BigDecimal("1000000"))
    ?.setScale(0, RoundingMode.HALF_UP)
    ?.toPlainString()
    .orEmpty()

@Composable
private fun ConfirmDeviceActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    DraarlConfirmationDialog(
        confirmation = DraarlConfirmation(
            title = title,
            message = message,
            confirmLabel = confirmText,
            confirmStyle = CommandStyle.DANGER
        ),
        onDismissRequest = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun FullDeviceDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}
