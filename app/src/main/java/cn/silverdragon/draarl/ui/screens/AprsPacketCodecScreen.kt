@file:Suppress(
    "LongMethod",
    "CognitiveComplexMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "MagicNumber",
    "TooGenericExceptionCaught"
)

package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.aprs.AprsConfig
import cn.silverdragon.draarl.aprs.AprsDecodedPacket
import cn.silverdragon.draarl.aprs.AprsIsStreamClient
import cn.silverdragon.draarl.aprs.AprsLiveFilter
import cn.silverdragon.draarl.aprs.AprsLivePacket
import cn.silverdragon.draarl.aprs.AprsPacketCodec
import cn.silverdragon.draarl.aprs.parseAprsLivePacket
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AprsPacketCodecScreen(
    onBack: () -> Unit,
    config: AprsConfig = AprsConfig()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val streamClient = remember { AprsIsStreamClient() }
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    var mode by rememberSaveable { mutableStateOf("encode") }
    var callsign by rememberSaveable { mutableStateOf("N0CALL") }
    var latitude by rememberSaveable { mutableStateOf("39.9042") }
    var longitude by rememberSaveable { mutableStateOf("116.4074") }
    var altitude by rememberSaveable { mutableStateOf("50") }
    var comment by rememberSaveable { mutableStateOf("DraARL") }
    var rawPacket by rememberSaveable {
        mutableStateOf("N0CALL>APDRA1,TCPIP*:!3954.25N/11624.44E>/A=000164 DraARL")
    }
    var centerLatitude by rememberSaveable { mutableStateOf("39.9042") }
    var centerLongitude by rememberSaveable { mutableStateOf("116.4074") }
    var radiusKilometers by rememberSaveable { mutableStateOf("160") }
    var livePackets by remember { mutableStateOf<List<AprsLivePacket>>(emptyList()) }
    var liveState by remember { mutableStateOf("未连接") }
    var liveError by remember { mutableStateOf("") }
    var liveJob by remember { mutableStateOf<Job?>(null) }
    var locating by remember { mutableStateOf(false) }
    val encoded = remember(callsign, latitude, longitude, altitude, comment) {
        runCatching {
            AprsPacketCodec.encode(
                callsign = callsign,
                latitude = latitude.toDoubleOrNull() ?: error("请输入有效纬度"),
                longitude = longitude.toDoubleOrNull() ?: error("请输入有效经度"),
                altitudeMeters = altitude.toDoubleOrNull(),
                comment = comment
            )
        }.getOrNull()
    }
    val decoded = remember(rawPacket) { runCatching { AprsPacketCodec.decode(rawPacket) }.getOrNull() }
    val copyValue = if (mode == "encode") encoded.orEmpty() else decoded?.toCopyText().orEmpty()

    fun stopLiveStream() {
        liveJob?.cancel()
        liveJob = null
        liveState = "未连接"
    }

    fun startLiveStream() {
        val filter = runCatching {
            AprsLiveFilter(
                latitude = centerLatitude.toDoubleOrNull() ?: error("请输入有效中心纬度"),
                longitude = centerLongitude.toDoubleOrNull() ?: error("请输入有效中心经度"),
                radiusKilometers = radiusKilometers.toIntOrNull() ?: error("请输入有效范围")
            )
        }.getOrElse {
            liveError = it.message ?: "范围参数不正确"
            return
        }
        liveError = ""
        livePackets = emptyList()
        liveJob?.cancel()
        liveState = "正在连接"
        liveJob = scope.launch {
            try {
                streamClient.stream(config, filter) { raw ->
                    val packet = parseAprsLivePacket(raw) ?: return@stream
                    withContext(Dispatchers.Main.immediate) {
                        livePackets = (listOf(packet) + livePackets)
                            .distinctBy(AprsLivePacket::stationKey)
                            .take(MAX_LIVE_PACKETS)
                    }
                }
                if (isActive) liveState = "连接已结束"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                liveError = error.message ?: "APRS-IS 接收失败"
                liveState = "连接失败"
            }
        }
    }

    fun useCurrentLocation() {
        if (locating) return
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            return
        }
        locating = true
        liveError = ""
        scope.launch {
            try {
                val location = locationProvider.locate()
                centerLatitude = String.format(Locale.US, "%.5f", location.latitude)
                centerLongitude = String.format(Locale.US, "%.5f", location.longitude)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                liveError = error.message ?: "暂时无法获取当前位置"
            } finally {
                locating = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            useCurrentLocation()
        } else {
            liveError = "需要定位权限才能使用当前位置"
        }
    }

    fun requestCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            useCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(mode) {
        if (mode != "live") stopLiveStream()
    }
    Column(Modifier.fillMaxSize()) {
        ToolHeader(
            title = "APRS 报文编解码",
            onBack = onBack,
            action = {
                if (mode != "live") {
                    ToolCopyButton(context, "复制 APRS 结果", copyValue)
                }
            }
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DraarlSegmentedControl(
                    segments = listOf(
                        DraarlSegment("encode", "生成位置包"),
                        DraarlSegment("decode", "解析位置包"),
                        DraarlSegment("live", "实时接收")
                    ),
                    selectedKey = mode,
                    onSelect = { mode = it }
                )
            }
            if (mode == "encode") {
                item {
                    OutlinedTextField(
                        value = callsign,
                        onValueChange = { callsign = it.uppercase().take(12) },
                        label = { Text("呼号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { NumericField(latitude, { latitude = it.signedDecimal() }, "纬度 °") }
                item { NumericField(longitude, { longitude = it.signedDecimal() }, "经度 °") }
                item { NumericField(altitude, { altitude = it.signedDecimal() }, "海拔 m（可选）") }
                item {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it.take(43) },
                        label = { Text("注释") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { ResultSurface(listOf("生成报文" to (encoded ?: "-"))) }
                item {
                    if (encoded == null) {
                        Text(
                            "请输入有效的呼号、纬度、经度和海拔",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (mode == "decode") {
                item {
                    OutlinedTextField(
                        value = rawPacket,
                        onValueChange = { rawPacket = it.take(512) },
                        label = { Text("原始 APRS 报文") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    ResultSurface(
                        listOf(
                            "来源" to (decoded?.source ?: "-"),
                            "目标" to (decoded?.destination ?: "-"),
                            "纬度" to (decoded?.latitude.formatFixed("°")),
                            "经度" to (decoded?.longitude.formatFixed("°")),
                            "符号" to if (decoded == null) "-" else "${decoded.symbolTable}${decoded.symbolCode}",
                            "海拔" to (decoded?.altitudeFeet?.let { "$it ft" } ?: "-"),
                            "注释" to (decoded?.comment ?: "-")
                        )
                    )
                }
                item {
                    if (decoded == null) {
                        Text(
                            "无法识别位置包，请检查 APRS 头部、坐标和符号字段",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                item {
                    LiveAprsControls(
                        centerLatitude = centerLatitude,
                        centerLongitude = centerLongitude,
                        radiusKilometers = radiusKilometers,
                        connected = liveJob?.isActive == true,
                        liveState = liveState,
                        server = config.server,
                        port = config.port,
                        onCenterLatitudeChange = { centerLatitude = it.signedDecimal() },
                        onCenterLongitudeChange = { centerLongitude = it.signedDecimal() },
                        onRadiusChange = { radiusKilometers = it.filter(Char::isDigit).take(4) },
                        locating = locating,
                        onUseLocation = ::requestCurrentLocation,
                        onStart = ::startLiveStream,
                        onStop = ::stopLiveStream
                    )
                }
                if (liveError.isNotBlank()) {
                    item { Text(liveError, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
                if (livePackets.isEmpty()) {
                    item {
                        Text(
                            if (liveJob?.isActive == true) "正在等待范围内的位置包……" else "输入中心坐标和范围后开始接收",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(livePackets, key = { "${it.stationKey}:${it.receivedAtMillis}" }) { packet ->
                        LiveAprsPacketCard(packet)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAprsControls(
    centerLatitude: String,
    centerLongitude: String,
    radiusKilometers: String,
    connected: Boolean,
    liveState: String,
    server: String,
    port: Int,
    locating: Boolean,
    onCenterLatitudeChange: (String) -> Unit,
    onCenterLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onUseLocation: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("范围过滤", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(
            "只接收 APRS-IS 中带位置的包；不使用高德地图，也不会联动之前的热点/标点功能。",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        NumericField(centerLatitude, onCenterLatitudeChange, "中心纬度 °")
        NumericField(centerLongitude, onCenterLongitudeChange, "中心经度 °")
        NumericField(radiusKilometers, onRadiusChange, "接收范围 km（5～500）")
        Button(onClick = onUseLocation, enabled = !locating) {
            Text(if (locating) "定位中…" else "使用当前位置")
        }
        Text(
            "服务器：$server:$port · 当前状态：$liveState",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = if (connected) onStop else onStart) {
            Text(if (connected) "停止接收" else "开始接收")
        }
    }
}

@Composable
private fun LiveAprsPacketCard(packet: AprsLivePacket) {
    ResultSurface(
        listOf(
            "台站" to packet.source,
            "坐标" to String.format(Locale.US, "%.5f, %.5f", packet.latitude, packet.longitude),
            "海拔" to (packet.altitudeFeet?.let { "$it ft" } ?: "未提供"),
            "符号" to "${packet.symbolTable}${packet.symbolCode}",
            "注释" to packet.comment.ifBlank { "—" },
            "原始报文" to packet.rawPacket
        )
    )
}

@Composable
private fun NumericField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun String.signedDecimal(): String = filterIndexed { index, character ->
    character.isDigit() || (character == '-' && index == 0) || (character == '.' && '.' !in take(index))
}.take(24)

private fun AprsDecodedPacket.toCopyText(): String = buildString {
    appendLine("来源 $source")
    appendLine("目标 $destination")
    appendLine("纬度 ${latitude.formatFixed("°")}")
    appendLine("经度 ${longitude.formatFixed("°")}")
    appendLine("符号 $symbolTable$symbolCode")
    altitudeFeet?.let { appendLine("海拔 $it ft") }
    append("注释 $comment")
}

private const val MAX_LIVE_PACKETS = 100
