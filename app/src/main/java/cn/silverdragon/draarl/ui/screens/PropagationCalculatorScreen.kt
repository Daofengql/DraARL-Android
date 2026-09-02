package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.AntennaGainUnit
import cn.silverdragon.draarl.tools.EffectiveRadiatedPowerCalculator
import cn.silverdragon.draarl.tools.FreeSpacePathLossCalculator
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PropagationCalculatorScreen(onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("fspl") }
    Column(Modifier.fillMaxSize()) {
        ToolHeader("传播与功率计算", onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DraarlSegmentedControl(
                    segments = listOf(
                        DraarlSegment("fspl", "自由空间"),
                        DraarlSegment("power", "ERP / EIRP"),
                        DraarlSegment("conditions", "传播态势")
                    ),
                    selectedKey = mode,
                    onSelect = { mode = it }
                )
            }
            item {
                when (mode) {
                    "power" -> EffectiveRadiatedPowerContent()
                    "conditions" -> PropagationConditionsContent()
                    else -> FreeSpacePathLossContent()
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun PropagationConditionsContent() {
    var mapKey by rememberSaveable { mutableStateOf("muf") }
    var refreshToken by remember {
        mutableLongStateOf(System.currentTimeMillis() / PROPAGATION_CACHE_BUCKET_MILLIS)
    }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val baseUrl = if (mapKey == "fof2") FOF2_MAP_URL else MUF_MAP_URL
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DraarlSegmentedControl(
            segments = listOf(DraarlSegment("muf", "MUF 3000 km"), DraarlSegment("fof2", "foF2")),
            selectedKey = mapKey,
            onSelect = {
                mapKey = it
                loading = true
                loadFailed = false
                loadedAtMillis = null
            }
        )
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            LivePropagationImage(
                url = "$baseUrl?draarl_refresh=$refreshToken",
                description = if (mapKey == "fof2") "当前 foF2 全球图" else "当前 MUF 3000 公里全球图",
                onStateChange = { state ->
                    when (state) {
                        PropagationImageState.LOADING -> {
                            loading = true
                            loadFailed = false
                        }

                        PropagationImageState.SUCCESS -> {
                            loading = false
                            loadFailed = false
                            loadedAtMillis = System.currentTimeMillis()
                        }

                        PropagationImageState.ERROR -> {
                            loading = false
                            loadFailed = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)
            )
            if (loading) CircularProgressIndicator()
            if (loadFailed) {
                Text(
                    "传播图加载失败",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(
                onClick = {
                    refreshToken = maxOf(refreshToken + 1, System.currentTimeMillis())
                    loading = true
                    loadFailed = false
                    loadedAtMillis = null
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新传播图")
            }
        }
        Text(
            if (mapKey == "fof2") {
                "foF2 表示垂直入射临界频率，可辅助判断 NVIS 和短距高仰角传播。"
            } else {
                "MUF 表示约 3000 km 跳距的最高可用频率；多跳路径取沿途较低值。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            buildString {
                append("数据源：prop.kc2g.com，基于 GIRO/INGV 电离层测高仪数据；约每 15 分钟更新。")
                loadedAtMillis?.let {
                    append(" 本次获取：${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}。")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LivePropagationImage(
    url: String,
    description: String,
    onStateChange: (PropagationImageState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        onLoading = { onStateChange(PropagationImageState.LOADING) },
        onSuccess = { onStateChange(PropagationImageState.SUCCESS) },
        onError = { onStateChange(PropagationImageState.ERROR) },
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
        ).pointerInput(url) {
            detectTransformGestures { _, pan, zoom, _ ->
                val nextScale = (scale * zoom).coerceIn(MIN_PROPAGATION_SCALE, MAX_PROPAGATION_SCALE)
                val limitX = size.width * (nextScale - 1f) / 2f
                val limitY = size.height * (nextScale - 1f) / 2f
                scale = nextScale
                offset = Offset(
                    x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                    y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                )
            }
        }
    )
}

private enum class PropagationImageState {
    LOADING,
    SUCCESS,
    ERROR
}

@Composable
private fun FreeSpacePathLossContent() {
    var distance by rememberSaveable { mutableStateOf("10") }
    var frequency by rememberSaveable { mutableStateOf("145") }
    val calculation = remember(distance, frequency) {
        calculateOrError {
            FreeSpacePathLossCalculator.calculate(
                distanceKilometers = distance.requireNumber("距离"),
                frequencyMHz = frequency.requireNumber("频率")
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NumericField(distance, { distance = it.decimalInput() }, "距离 km")
        NumericField(frequency, { frequency = it.decimalInput() }, "频率 MHz")
        ResultSurface(
            listOf(
                "自由空间路径损耗" to calculation.value?.pathLossDb.formatFixed("dB"),
                "波长" to calculation.value?.wavelengthMeters.formatEngineering("m")
            )
        )
        CalculationError(calculation.error)
        Text(
            "按理想自由空间、无遮挡且远场条件计算，不含地形、绕射和多径影响。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EffectiveRadiatedPowerContent() {
    var power by rememberSaveable { mutableStateOf("5") }
    var feedlineLoss by rememberSaveable { mutableStateOf("1.5") }
    var antennaGain by rememberSaveable { mutableStateOf("3") }
    var gainUnitKey by rememberSaveable { mutableStateOf("dbi") }
    val gainUnit = if (gainUnitKey == "dbd") AntennaGainUnit.DBD else AntennaGainUnit.DBI
    val calculation = remember(power, feedlineLoss, antennaGain, gainUnit) {
        calculateOrError {
            EffectiveRadiatedPowerCalculator.calculate(
                transmitterPowerWatts = power.requireNumber("发射功率"),
                feedlineLossDb = feedlineLoss.requireNumber("馈线损耗"),
                antennaGainDb = antennaGain.requireNumber("天线增益"),
                gainUnit = gainUnit
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NumericField(power, { power = it.decimalInput() }, "发射机输出功率 W")
        NumericField(feedlineLoss, { feedlineLoss = it.decimalInput() }, "馈线及接头总损耗 dB")
        SignedNumericField(antennaGain, { antennaGain = it }, "天线增益")
        DraarlSegmentedControl(
            segments = listOf(DraarlSegment("dbi", "dBi"), DraarlSegment("dbd", "dBd")),
            selectedKey = gainUnitKey,
            onSelect = { gainUnitKey = it }
        )
        ResultSurface(
            listOf(
                "发射机功率" to calculation.value?.transmitterPowerDbm.formatFixed("dBm"),
                "折算天线增益" to calculation.value?.antennaGainDbi.formatFixed("dBi"),
                "EIRP" to calculation.value?.eirpWatts.formatEngineering("W"),
                "EIRP 电平" to calculation.value?.eirpDbm.formatFixed("dBm"),
                "ERP" to calculation.value?.erpWatts.formatEngineering("W"),
                "ERP 电平" to calculation.value?.erpDbm.formatFixed("dBm")
            )
        )
        CalculationError(calculation.error)
        Text(
            "EIRP 以各向同性天线为基准；ERP 以半波偶极子为基准，0 dBd = 2.15 dBi。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NumericField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SignedNumericField(value: String, onValueChange: (String) -> Unit, label: String) {
    NumericField(
        value = value,
        onValueChange = { candidate -> onValueChange(candidate.signedDecimal()) },
        label = label
    )
}

@Composable
private fun CalculationError(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private data class CalculationState<T>(val value: T?, val error: String?)

private inline fun <T> calculateOrError(block: () -> T): CalculationState<T> = runCatching(block).fold(
    onSuccess = { CalculationState(it, null) },
    onFailure = { CalculationState(null, it.message ?: "无法计算") }
)

private fun String.requireNumber(label: String): Double = toDoubleOrNull() ?: error("请输入有效$label")

private fun String.signedDecimal(): String = filterIndexed { index, character ->
    character.isDigit() || (character == '-' && index == 0) || (character == '.' && '.' !in take(index))
}.take(MAX_NUMERIC_INPUT_LENGTH)

private const val MAX_NUMERIC_INPUT_LENGTH = 24
private const val MIN_PROPAGATION_SCALE = 1f
private const val MAX_PROPAGATION_SCALE = 6f
private const val PROPAGATION_CACHE_BUCKET_MILLIS = 15L * 60L * 1_000L
private const val MUF_MAP_URL = "https://prop.kc2g.com/renders/current/mufd-normal-now.svg"
private const val FOF2_MAP_URL = "https://prop.kc2g.com/renders/current/fof2-normal-now.svg"
