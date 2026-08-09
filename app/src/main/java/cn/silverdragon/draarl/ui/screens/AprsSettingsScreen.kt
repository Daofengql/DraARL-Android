package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.aprs.AprsConfig
import cn.silverdragon.draarl.aprs.AprsConnectionState
import cn.silverdragon.draarl.aprs.AprsEvent
import cn.silverdragon.draarl.aprs.AprsPosition
import cn.silverdragon.draarl.aprs.AprsStatus
import cn.silverdragon.draarl.aprs.AprsUiState
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlIconButtonOptions
import cn.silverdragon.draarl.ui.components.DraarlSettings
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsRow
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.StatusTone
import kotlinx.coroutines.launch

@Immutable
internal data class AprsSettingsContentState(
    val enabled: Boolean,
    val server: String,
    val port: String,
    val callsign: String,
    val passcode: String,
    val comment: String,
    val autoReport: Boolean,
    val stationaryIntervalSeconds: Float,
    val locating: Boolean,
    val sending: Boolean,
    val saving: Boolean,
    val status: AprsStatus
)

internal sealed interface AprsSettingsContentAction {
    data object Back : AprsSettingsContentAction

    data object Save : AprsSettingsContentAction

    data object SendNow : AprsSettingsContentAction

    data class SetEnabled(val enabled: Boolean) : AprsSettingsContentAction

    data class SetServer(val server: String) : AprsSettingsContentAction

    data class SetPort(val port: String) : AprsSettingsContentAction

    data class SetCallsign(val callsign: String) : AprsSettingsContentAction

    data class SetPasscode(val passcode: String) : AprsSettingsContentAction

    data class SetComment(val comment: String) : AprsSettingsContentAction

    data class SetAutoReport(val enabled: Boolean) : AprsSettingsContentAction

    data class SetStationaryInterval(val seconds: Float) : AprsSettingsContentAction
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AprsSettingsScreen(
    state: AprsUiState,
    defaultCallsign: String,
    onBack: () -> Unit,
    onEvent: (AprsEvent) -> Unit,
    onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    val initial = state.config
    var enabled by rememberSaveable(initial) { mutableStateOf(initial.enabled) }
    var server by rememberSaveable(initial) { mutableStateOf(initial.server) }
    var port by rememberSaveable(initial) { mutableStateOf(initial.port.toString()) }
    var callsign by rememberSaveable(initial, defaultCallsign) {
        mutableStateOf(initial.callsign.ifBlank { defaultCallsign })
    }
    var passcode by rememberSaveable(initial) { mutableStateOf(initial.passcode) }
    var comment by rememberSaveable(initial) { mutableStateOf(initial.comment) }
    var autoReport by rememberSaveable(initial) { mutableStateOf(initial.autoReport) }
    var stationaryInterval by rememberSaveable(initial) {
        mutableFloatStateOf(initial.stationaryIntervalSeconds.toFloat())
    }
    var locating by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            scope.launch {
                locating = true
                runCatching {
                    val location = locationProvider.locate()
                    onEvent(
                        AprsEvent.SendPosition(
                            AprsPosition(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                            )
                        )
                    )
                }.onFailure { onNotice(it.message ?: "当前位置获取失败") }
                locating = false
            }
        } else {
            onNotice("需要定位权限才能发送 APRS 位置")
        }
    }

    fun sendNow() {
        val fine =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            scope.launch {
                locating = true
                runCatching {
                    val location = locationProvider.locate()
                    onEvent(
                        AprsEvent.SendPosition(
                            AprsPosition(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                            )
                        )
                    )
                }.onFailure { onNotice(it.message ?: "当前位置获取失败") }
                locating = false
            }
        } else {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun save() {
        val parsedPort = port.toIntOrNull()
        if (callsign.isBlank()) {
            onNotice("请填写 APRS 呼号")
            return
        }
        if (parsedPort == null || parsedPort !in 1..65535) {
            onNotice("请输入有效的 APRS 端口")
            return
        }
        onEvent(
            AprsEvent.SaveConfig(
                AprsConfig(
                    enabled = enabled,
                    server = server,
                    port = parsedPort,
                    callsign = callsign,
                    passcode = passcode,
                    comment = comment,
                    autoReport = enabled && autoReport,
                    stationaryIntervalSeconds = stationaryInterval.toInt().coerceIn(60, 3_600)
                )
            )
        )
    }

    AprsSettingsContent(
        state = AprsSettingsContentState(
            enabled = enabled,
            server = server,
            port = port,
            callsign = callsign,
            passcode = passcode,
            comment = comment,
            autoReport = autoReport,
            stationaryIntervalSeconds = stationaryInterval,
            locating = locating,
            sending = state.sending,
            saving = state.saving,
            status = state.status
        ),
        onAction = { action ->
            when (action) {
                AprsSettingsContentAction.Back -> onBack()

                AprsSettingsContentAction.Save -> save()

                AprsSettingsContentAction.SendNow -> sendNow()

                is AprsSettingsContentAction.SetEnabled -> enabled = action.enabled

                is AprsSettingsContentAction.SetServer -> server = action.server

                is AprsSettingsContentAction.SetPort -> port = action.port.filter(Char::isDigit).take(5)

                is AprsSettingsContentAction.SetCallsign -> callsign = action.callsign.uppercase()

                is AprsSettingsContentAction.SetPasscode -> {
                    passcode = action.passcode.filter(Char::isDigit).take(6)
                }

                is AprsSettingsContentAction.SetComment -> comment = action.comment.take(43)

                is AprsSettingsContentAction.SetAutoReport -> autoReport = action.enabled

                is AprsSettingsContentAction.SetStationaryInterval -> {
                    stationaryInterval = action.seconds
                }
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AprsSettingsContent(state: AprsSettingsContentState, onAction: (AprsSettingsContentAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APRS 设置") },
                navigationIcon = {
                    DraarlIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = { onAction(AprsSettingsContentAction.Back) }
                    )
                },
                actions = {
                    DraarlIconButton(
                        icon = Icons.Default.Save,
                        label = "保存",
                        onClick = { onAction(AprsSettingsContentAction.Save) },
                        options = DraarlIconButtonOptions(enabled = !state.saving)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { AprsLinkSection(state, onAction) }
            item { AprsServerSection(state, onAction) }
            item { AprsAutomaticReportSection(state, onAction) }
            item { AprsLinkTestSection(state, onAction) }
        }
    }
}

@Composable
private fun AprsLinkSection(state: AprsSettingsContentState, onAction: (AprsSettingsContentAction) -> Unit) {
    DraarlSettingsSectionTitle("链路", detail = "APRS-IS 直连配置")
    DraarlSettingsGroup {
        DraarlSettingsRow(
            item = DraarlSettings(
                icon = Icons.Default.SettingsInputAntenna,
                title = "启用 APRS",
                detail = "位置包直接发送到 APRS-IS，不经过 DraARL 服务端"
            ),
            trailing = {
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { onAction(AprsSettingsContentAction.SetEnabled(it)) }
                )
            }
        )
    }
}

@Composable
private fun AprsServerSection(state: AprsSettingsContentState, onAction: (AprsSettingsContentAction) -> Unit) {
    DraarlSettingsSectionTitle("服务器", detail = "呼号与 APRS-IS 登录参数")
    DraarlSettingsGroup {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(state.server, {
                onAction(AprsSettingsContentAction.SetServer(it))
            }, Modifier.fillMaxWidth(), label = {
                Text("APRS-IS 地址")
            }, singleLine = true, enabled = state.enabled)
            OutlinedTextField(state.port, {
                onAction(AprsSettingsContentAction.SetPort(it))
            }, Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true, enabled = state.enabled)
            OutlinedTextField(state.callsign, {
                onAction(AprsSettingsContentAction.SetCallsign(it))
            }, Modifier.fillMaxWidth(), label = {
                Text("呼号（不含 SSID 也可以）")
            }, singleLine = true, enabled = state.enabled)
            OutlinedTextField(state.passcode, {
                onAction(AprsSettingsContentAction.SetPasscode(it))
            }, Modifier.fillMaxWidth(), label = {
                Text("验证密码（留空自动计算）")
            }, singleLine = true, enabled = state.enabled)
            OutlinedTextField(state.comment, {
                onAction(AprsSettingsContentAction.SetComment(it))
            }, Modifier.fillMaxWidth(), label = {
                Text("位置包注释")
            }, singleLine = true, enabled = state.enabled)
        }
    }
}

@Composable
private fun AprsAutomaticReportSection(state: AprsSettingsContentState, onAction: (AprsSettingsContentAction) -> Unit) {
    DraarlSettingsSectionTitle("自动上报", detail = "后台位置上报策略")
    DraarlSettingsGroup {
        DraarlSettingsRow(
            item = DraarlSettings(
                icon = Icons.Default.LocationOn,
                title = "后台自动上报",
                detail = "需要定位权限，并会显示系统常驻通知"
            ),
            showDivider = true,
            enabled = state.enabled,
            trailing = {
                Switch(
                    checked = state.autoReport,
                    enabled = state.enabled,
                    onCheckedChange = { onAction(AprsSettingsContentAction.SetAutoReport(it)) }
                )
            }
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AprsIntervalHeader(state.stationaryIntervalSeconds)
            Slider(
                value = state.stationaryIntervalSeconds,
                onValueChange = { onAction(AprsSettingsContentAction.SetStationaryInterval(it)) },
                valueRange = 60f..3_600f,
                steps = 35,
                enabled = state.enabled && state.autoReport
            )
        }
    }
}

@Composable
private fun AprsIntervalHeader(seconds: Float) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "静止间隔",
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "${seconds.toInt()} 秒",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AprsLinkTestSection(state: AprsSettingsContentState, onAction: (AprsSettingsContentAction) -> Unit) {
    DraarlSettingsSectionTitle("链路测试", detail = "使用设备当前位置验证配置")
    DraarlSettingsGroup {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandButton(
                label = if (state.locating) "正在获取位置" else "立即上报当前位置",
                onClick = { onAction(AprsSettingsContentAction.SendNow) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.enabled && !state.locating && !state.sending,
                style = CommandStyle.PRIMARY,
                supportingText = "发送一次实时位置到 APRS-IS",
                leadingIcon = Icons.Default.LocationOn
            )
            if (state.status.message.isNotBlank()) {
                InlineNotice(text = state.status.message, tone = state.status.state.toStatusTone())
            }
        }
    }
}

private fun AprsConnectionState.toStatusTone(): StatusTone = when (this) {
    AprsConnectionState.CONNECTING,
    AprsConnectionState.SENDING -> StatusTone.CONNECTING

    AprsConnectionState.VERIFIED,
    AprsConnectionState.SENT -> StatusTone.CONNECTED

    AprsConnectionState.ERROR -> StatusTone.ERROR

    AprsConnectionState.IDLE -> StatusTone.NEUTRAL
}
