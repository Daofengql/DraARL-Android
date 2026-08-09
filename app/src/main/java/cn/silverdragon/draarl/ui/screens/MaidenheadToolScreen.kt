package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import cn.silverdragon.draarl.maps.CoordinateConverter
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.maps.GeoCoordinate
import cn.silverdragon.draarl.maps.MaidenheadCell
import cn.silverdragon.draarl.maps.MaidenheadLocator
import cn.silverdragon.draarl.ui.components.DraarlConfirmation
import cn.silverdragon.draarl.ui.components.DraarlConfirmationDialog
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.theme.isDarkTheme
import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MaidenheadToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(MAP_PREFERENCES, Context.MODE_PRIVATE) }
    var privacyAccepted by rememberSaveable {
        mutableStateOf(preferences.getBoolean(MAP_PRIVACY_ACCEPTED, false))
    }
    if (!privacyAccepted) {
        DraarlConfirmationDialog(
            confirmation = DraarlConfirmation(
                title = "启用地图服务",
                message = "梅登海德网格正反查使用高德地图展示位置和边界。",
                confirmLabel = "同意并继续"
            ),
            onDismissRequest = onBack,
            onConfirm = {
                preferences.edit { putBoolean(MAP_PRIVACY_ACCEPTED, true) }
                privacyAccepted = true
            }
        )
        return
    }

    remember(context) { initializeAmapServices(context) }
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    var input by rememberSaveable { mutableStateOf("") }
    var precisionPairs by rememberSaveable { mutableIntStateOf(3) }
    var selectedCell by remember { mutableStateOf<MaidenheadCell?>(null) }
    var selectedWgs84 by remember { mutableStateOf<GeoCoordinate?>(null) }
    var error by remember { mutableStateOf("") }
    var fitBoundsRequest by remember { mutableIntStateOf(0) }
    var locatingCurrent by remember { mutableStateOf(false) }
    var defaultLocateAttempted by rememberSaveable { mutableStateOf(false) }
    val mapType = if (MaterialTheme.isDarkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL

    fun showCell(cell: MaidenheadCell, coordinate: GeoCoordinate = cell.center) {
        selectedCell = cell
        selectedWgs84 = coordinate
        input = cell.locator
        error = ""
        fitBoundsRequest++
    }

    fun locateInput() {
        runCatching { MaidenheadLocator.decode(input) }
            .onSuccess { showCell(it) }
            .onFailure { error = it.message ?: "网格格式不正确" }
    }

    fun locateCurrentGrid() {
        if (locatingCurrent) return
        locatingCurrent = true
        scope.launch {
            try {
                val location = locationProvider.locate()
                val coordinate = GeoCoordinate(location.latitude, location.longitude)
                val locator = MaidenheadLocator.encode(coordinate.latitude, coordinate.longitude, precisionPairs)
                showCell(MaidenheadLocator.decode(locator), coordinate)
            } catch (error: CancellationException) {
                throw error
            } catch (cause: Throwable) {
                if (input.isBlank()) error = cause.message ?: "暂时无法获取当前位置"
            } finally {
                locatingCurrent = false
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            locateCurrentGrid()
        } else if (input.isBlank()) {
            error = "需要定位权限才能显示当前设备所在网格"
        }
    }

    LaunchedEffect(Unit) {
        if (defaultLocateAttempted) return@LaunchedEffect
        defaultLocateAttempted = true
        val fine =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            locateCurrentGrid()
        } else {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val selectedGcj02 = selectedWgs84?.toGcj02LatLng()
    val polygonPoints = selectedCell?.let(::cellPolygonGcj02).orEmpty()

    Column(Modifier.fillMaxSize()) {
        ToolHeader("梅登海德网格", onBack)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ManagedAmapView(
                coordinate = selectedGcj02,
                allowSelection = true,
                gesturesEnabled = true,
                showCompass = true,
                zoom = 12f,
                mapType = mapType,
                polygonPoints = polygonPoints,
                fitBoundsRequest = fitBoundsRequest,
                onCoordinateSelected = { gcj02 ->
                    val wgs84 = CoordinateConverter.gcj02ToWgs84(GeoCoordinate(gcj02.latitude, gcj02.longitude))
                    val locator = MaidenheadLocator.encode(wgs84.latitude, wgs84.longitude, precisionPairs)
                    showCell(MaidenheadLocator.decode(locator), wgs84)
                },
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 5.dp
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { value ->
                            input = value.uppercase().filter { it.isLetterOrDigit() }.take(8)
                            error = ""
                        },
                        label = { Text("输入网格反查") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            DraarlIconButton(
                                icon = Icons.Default.Search,
                                label = "定位网格",
                                onClick = ::locateInput
                            )
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("点击精度", style = MaterialTheme.typography.bodySmall)
                        listOf(2 to "4 位", 3 to "6 位", 4 to "8 位").forEach { (pairs, label) ->
                            FilterChip(
                                selected = precisionPairs == pairs,
                                onClick = { precisionPairs = pairs },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (error.isNotBlank()) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            selectedCell?.let { cell ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    tonalElevation = 7.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            cell.locator,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        selectedWgs84?.let { point ->
                            Text(
                                String.format(Locale.US, "坐标  %.6f, %.6f", point.latitude, point.longitude),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            String.format(
                                Locale.US,
                                "范围  纬度 %.6f 至 %.6f  ·  经度 %.6f 至 %.6f",
                                cell.south,
                                cell.north,
                                cell.west,
                                cell.east
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } ?: Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                tonalElevation = 6.dp
            ) {
                Text(
                    if (locatingCurrent) "正在定位当前设备" else "输入网格定位，或点击地图正向计算",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private fun GeoCoordinate.toGcj02LatLng(): LatLng = CoordinateConverter.wgs84ToGcj02(this)
    .let { LatLng(it.latitude, it.longitude) }

private fun cellPolygonGcj02(cell: MaidenheadCell): List<LatLng> = listOf(
    GeoCoordinate(cell.south, cell.west),
    GeoCoordinate(cell.south, cell.east),
    GeoCoordinate(cell.north, cell.east),
    GeoCoordinate(cell.north, cell.west)
).map(GeoCoordinate::toGcj02LatLng)
