package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.maps.CoordinateConverter
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.maps.GeoCoordinate
import cn.silverdragon.draarl.maps.LastMapLocationStore
import cn.silverdragon.draarl.maps.MapDistance
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl
import cn.silverdragon.draarl.ui.theme.isDarkTheme
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AprsMapPanel(
    controller: AppController,
    onStartPtt: () -> Boolean,
    onStopPtt: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember(context) { CurrentLocationProvider(context) }
    val locationStore = remember(context) { LastMapLocationStore(context) }
    val cachedCoordinate = remember(locationStore) {
        locationStore.load()?.let { cached ->
            CoordinateConverter.wgs84ToGcj02(GeoCoordinate(cached.latitude, cached.longitude))
                .let { LatLng(it.latitude, it.longitude) }
        }
    }
    var coordinate by remember { mutableStateOf(cachedCoordinate) }
    var loading by remember { mutableStateOf(false) }
    var recenterRequest by remember { mutableIntStateOf(0) }
    var zoomInRequest by remember { mutableIntStateOf(0) }
    var zoomOutRequest by remember { mutableIntStateOf(0) }
    var measuring by rememberSaveable { mutableStateOf(false) }
    var measurementPath by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val darkTheme = MaterialTheme.isDarkTheme
    var mapType by rememberSaveable {
        mutableIntStateOf(if (darkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL)
    }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    val fallbackMarker = remember {
        BitmapDescriptorFactory.fromBitmap(createAvatarMarkerBitmap(null))
    }
    var avatarMarker by remember(controller.user?.avatarUrl) {
        mutableStateOf<com.amap.api.maps.model.BitmapDescriptor?>(null)
    }

    LaunchedEffect(controller.user?.avatarUrl) {
        val avatarUrl = controller.user?.avatarUrl.orEmpty()
        if (avatarUrl.isBlank()) {
            avatarMarker = null
        } else {
            avatarMarker = runCatching {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .size(AVATAR_BITMAP_SIZE)
                        .allowHardware(false)
                        .build()
                )
                (result as? SuccessResult)?.image?.toBitmap(AVATAR_BITMAP_SIZE, AVATAR_BITMAP_SIZE)?.let { avatar ->
                    BitmapDescriptorFactory.fromBitmap(createAvatarMarkerBitmap(avatar))
                }
            }.getOrNull()
        }
    }

    fun locate() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val location = provider.locate()
                locationStore.save(location)
                val gcj = CoordinateConverter.wgs84ToGcj02(GeoCoordinate(location.latitude, location.longitude))
                coordinate = LatLng(gcj.latitude, gcj.longitude)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                controller.showNotice(error.message ?: "暂时无法获取当前位置")
            } finally {
                loading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) locate() else controller.showNotice("需要定位权限才能显示当前位置")
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            locate()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(visible) {
        if (!visible) mapTypeMenuExpanded = false
    }

    val measurementMarkers = remember(measurementPath, coordinate) {
        val current = coordinate
        if (current != null && measurementPath.firstOrNull()?.isSamePoint(current) == true) {
            measurementPath.drop(1)
        } else {
            measurementPath
        }
    }
    val measurementDistance = remember(measurementPath) {
        MapDistance.totalMeters(
            measurementPath.map { point -> GeoCoordinate(point.latitude, point.longitude) }
        )
    }

    LaunchedEffect(darkTheme) {
        if (mapType == AMap.MAP_TYPE_NORMAL || mapType == AMap.MAP_TYPE_NIGHT) {
            mapType = if (darkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
        }
    }

    Box(modifier.fillMaxSize()) {
        if (hasAmapApiKey(context)) {
            ManagedAmapView(
                coordinate = coordinate,
                allowSelection = false,
                gesturesEnabled = visible,
                active = visible,
                showCompass = true,
                zoom = 15f,
                recenterRequest = recenterRequest,
                zoomInRequest = zoomInRequest,
                zoomOutRequest = zoomOutRequest,
                mapType = mapType,
                markerIcon = avatarMarker ?: fallbackMarker,
                measurementPoints = if (measuring) measurementMarkers else emptyList(),
                measurementPath = if (measuring) measurementPath else emptyList(),
                onMapClick = { point ->
                    if (measuring) measurementPath = measurementPath + point
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    "地图 Key 未配置",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (visible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .zIndex(2f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControlButton(
                    onClick = {
                        if (coordinate == null) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            recenterRequest++
                        }
                    },
                    icon = if (coordinate == null) Icons.Default.LocationSearching else Icons.Default.CenterFocusStrong,
                    description = "定位当前位置"
                )
                MapControlButton(
                    onClick = { zoomInRequest++ },
                    icon = Icons.Default.Add,
                    description = "放大地图"
                )
                MapControlButton(
                    onClick = { zoomOutRequest++ },
                    icon = Icons.Default.Remove,
                    description = "缩小地图"
                )
                MapControlButton(
                    onClick = {
                        val enabling = !measuring
                        if (enabling && measurementPath.isEmpty()) {
                            coordinate?.let { measurementPath = listOf(it) }
                        }
                        measuring = enabling
                    },
                    icon = Icons.Default.Straighten,
                    description = if (measuring) "退出测距" else "开始测距",
                    selected = measuring
                )
                if (measuring && measurementPath.isNotEmpty()) {
                    MapControlButton(
                        onClick = { measurementPath = emptyList() },
                        icon = Icons.Default.Delete,
                        description = "清除测距点"
                    )
                }
                Box(Modifier.size(MAP_CONTROL_SIZE)) {
                    MapControlButton(
                        onClick = { mapTypeMenuExpanded = true },
                        icon = Icons.Default.Layers,
                        description = "选择地图类型"
                    )
                    DropdownMenu(
                        expanded = mapTypeMenuExpanded,
                        onDismissRequest = { mapTypeMenuExpanded = false }
                    ) {
                        MAP_TYPES.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label,
                                        color = if (mapType == option.value) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    mapType = option.value
                                    mapTypeMenuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("等高线（暂不可用）", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                mapTypeMenuExpanded = false
                                controller.showNotice("当前高德 SDK 不提供等高线数据；外部地形瓦片在境内存在坐标偏移，暂未启用")
                            }
                        )
                    }
                }
            }
        }

        if (visible && measuring) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).zIndex(2f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text("测距 · ${measurementPath.size} 点", style = MaterialTheme.typography.labelMedium)
                    Text(
                        MapDistance.format(measurementDistance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (visible && loading && coordinate == null) {
            Text(
                "正在定位",
                modifier = Modifier.align(Alignment.Center).padding(top = 54.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (visible) {
            val radioStatus = controller.radioSession.uiState.status
            MapPttButton(
                transmitting = radioStatus.transmitting,
                enabled = radioStatus.connected && radioStatus.speaker.isBlank(),
                onStart = onStartPtt,
                onStop = onStopPtt,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun MapControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean = false
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(MAP_CONTROL_SIZE),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
internal fun RadioModeSwitcher(
    mapSelected: Boolean,
    onMap: () -> Unit,
    onMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        DraarlSegmentedControl(
            segments = RADIO_CONTENT_SEGMENTS,
            selectedKey = if (mapSelected) RADIO_MAP_SEGMENT else RADIO_MESSAGES_SEGMENT,
            onSelect = { selected ->
                if (selected == RADIO_MAP_SEGMENT) onMap() else onMessages()
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private const val RADIO_MAP_SEGMENT = "map"
private const val RADIO_MESSAGES_SEGMENT = "messages"
private val RADIO_CONTENT_SEGMENTS = listOf(
    DraarlSegment(RADIO_MAP_SEGMENT, "地图"),
    DraarlSegment(RADIO_MESSAGES_SEGMENT, "通联日志")
)

private fun createAvatarMarkerBitmap(avatar: Bitmap?): Bitmap {
    val output = Bitmap.createBitmap(MARKER_WIDTH, MARKER_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val tip = Path().apply {
        moveTo(MARKER_CENTER, MARKER_HEIGHT.toFloat())
        lineTo(54f, 130f)
        lineTo(90f, 130f)
        close()
    }
    canvas.drawPath(tip, paint.apply { color = MARKER_BLUE })
    canvas.drawCircle(MARKER_CENTER, MARKER_CENTER, 69f, paint.apply { color = AndroidColor.WHITE })
    canvas.save()
    canvas.clipPath(Path().apply { addCircle(MARKER_CENTER, MARKER_CENTER, 63f, Path.Direction.CW) })
    if (avatar != null) {
        canvas.drawBitmap(avatar, null, Rect(9, 9, 135, 135), paint)
    } else {
        canvas.drawCircle(MARKER_CENTER, MARKER_CENTER, 63f, paint.apply { color = MARKER_BLUE })
        canvas.drawCircle(MARKER_CENTER, 52f, 21f, paint.apply { color = AndroidColor.WHITE })
        canvas.drawOval(RectF(31f, 80f, 113f, 145f), paint)
    }
    canvas.restore()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    paint.color = MARKER_BLUE
    canvas.drawCircle(MARKER_CENTER, MARKER_CENTER, 66f, paint)
    return output
}

@Composable
private fun MapPttButton(
    transmitting: Boolean,
    enabled: Boolean,
    onStart: () -> Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        transmitting -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier
            .size(104.dp)
            .shadow(10.dp, CircleShape)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        if (onStart()) {
                            try {
                                tryAwaitRelease()
                            } finally {
                                onStop()
                            }
                        }
                    }
                )
            },
        shape = CircleShape,
        color = color,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, contentDescription = "按住发射", modifier = Modifier.size(42.dp))
        }
    }
}

private const val AVATAR_BITMAP_SIZE = 144
private val MAP_CONTROL_SIZE = 44.dp
private const val MARKER_WIDTH = 144
private const val MARKER_HEIGHT = 168
private const val MARKER_CENTER = 72f
private val MARKER_BLUE = AndroidColor.rgb(25, 118, 210)
private data class MapTypeOption(val label: String, val value: Int)
private val MAP_TYPES = listOf(
    MapTypeOption("标准地图", AMap.MAP_TYPE_NORMAL),
    MapTypeOption("卫星地图", AMap.MAP_TYPE_SATELLITE),
    MapTypeOption("夜间地图", AMap.MAP_TYPE_NIGHT)
)

private fun LatLng.isSamePoint(other: LatLng): Boolean = kotlin.math.abs(latitude - other.latitude) < 1e-7 &&
    kotlin.math.abs(longitude - other.longitude) < 1e-7
