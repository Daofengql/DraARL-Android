package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.aprs.AprsConfig
import cn.silverdragon.draarl.aprs.AprsConnectionState
import cn.silverdragon.draarl.aprs.AprsPosition
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AprsSettingsScreen(controller: AppController) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    val initial = controller.aprsConfig
    var enabled by rememberSaveable(initial) { mutableStateOf(initial.enabled) }
    var server by rememberSaveable(initial) { mutableStateOf(initial.server) }
    var port by rememberSaveable(initial) { mutableStateOf(initial.port.toString()) }
    var callsign by rememberSaveable(initial, controller.user?.callsign) {
        mutableStateOf(initial.callsign.ifBlank { controller.user?.callsign.orEmpty() })
    }
    var passcode by rememberSaveable(initial) { mutableStateOf(initial.passcode) }
    var comment by rememberSaveable(initial) { mutableStateOf(initial.comment) }
    var autoReport by rememberSaveable(initial) { mutableStateOf(initial.autoReport) }
    var stationaryInterval by rememberSaveable(initial) {
        mutableFloatStateOf(initial.stationaryIntervalSeconds.toFloat())
    }
    var saving by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            scope.launch {
                locating = true
                runCatching {
                    val location = locationProvider.locate()
                    controller.sendAprsPosition(
                        AprsPosition(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        ),
                    )
                }.onFailure { controller.showNotice(it.message ?: "当前位置获取失败") }
                locating = false
            }
        } else {
            controller.showNotice("需要定位权限才能发送 APRS 位置")
        }
    }

    fun sendNow() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            scope.launch {
                locating = true
                runCatching {
                    val location = locationProvider.locate()
                    controller.sendAprsPosition(
                        AprsPosition(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        ),
                    )
                }.onFailure { controller.showNotice(it.message ?: "当前位置获取失败") }
                locating = false
            }
        } else {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun save() {
        val parsedPort = port.toIntOrNull()
        if (callsign.isBlank()) {
            controller.showNotice("请填写 APRS 呼号")
            return
        }
        if (parsedPort == null || parsedPort !in 1..65535) {
            controller.showNotice("请输入有效的 APRS 端口")
            return
        }
        saving = true
        controller.updateAprsConfig(
            AprsConfig(
                enabled = enabled,
                server = server,
                port = parsedPort,
                callsign = callsign,
                passcode = passcode,
                comment = comment,
                autoReport = enabled && autoReport,
                stationaryIntervalSeconds = stationaryInterval.toInt().coerceIn(60, 3_600),
            ),
        )
        saving = false
        controller.showNotice("APRS 设置已保存")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APRS 设置") },
                navigationIcon = {
                    IconButton(onClick = { controller.navigate(AppPage.SETTINGS) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = ::save, enabled = !saving) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("启用 APRS", style = MaterialTheme.typography.titleMedium)
                            Text("位置包直接发送到 APRS-IS，不经过 DraARL 服务端", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }
            item {
                SettingsSectionHeader("服务器")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("APRS-IS 地址") }, singleLine = true, enabled = enabled)
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true, enabled = enabled)
                        OutlinedTextField(callsign, { callsign = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("呼号（不含 SSID 也可以）") }, singleLine = true, enabled = enabled)
                        OutlinedTextField(passcode, { passcode = it.filter { char -> char.isDigit() }.take(6) }, Modifier.fillMaxWidth(), label = { Text("验证密码（留空自动计算）") }, singleLine = true, enabled = enabled)
                        OutlinedTextField(comment, { comment = it.take(43) }, Modifier.fillMaxWidth(), label = { Text("位置包注释") }, singleLine = true, enabled = enabled)
                    }
                }
            }
            item {
                SettingsSectionHeader("自动上报")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("后台自动上报", style = MaterialTheme.typography.bodyLarge)
                                Text("需要定位权限，并会显示系统常驻通知", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoReport, enabled = enabled, onCheckedChange = { autoReport = it })
                        }
                        Text("静止间隔：${stationaryInterval.toInt()} 秒", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = stationaryInterval,
                            onValueChange = { stationaryInterval = it },
                            valueRange = 60f..3_600f,
                            steps = 35,
                            enabled = enabled && autoReport,
                        )
                    }
                }
            }
            item {
                SettingsSectionHeader("测试")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = ::sendNow, enabled = enabled && !locating && controller.aprsStatus.state !in setOf(AprsConnectionState.CONNECTING, AprsConnectionState.SENDING), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (locating) "正在获取位置" else "立即上报当前位置")
                        }
                        if (controller.aprsStatus.message.isNotBlank()) {
                            Text(controller.aprsStatus.message, style = MaterialTheme.typography.bodySmall, color = if (controller.aprsStatus.state == AprsConnectionState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
