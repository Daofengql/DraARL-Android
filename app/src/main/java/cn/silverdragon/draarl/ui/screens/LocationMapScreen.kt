package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.system.OsConstants
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.silverdragon.draarl.data.LocationMessageKind
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.maps.CoordinateConverter
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.maps.GeoCoordinate
import cn.silverdragon.draarl.maps.MapDistance
import cn.silverdragon.draarl.maps.MapViewLifecycleController
import cn.silverdragon.draarl.ui.components.DraarlConfirmation
import cn.silverdragon.draarl.ui.components.DraarlConfirmationDialog
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlTooltip
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.PageFeedback
import cn.silverdragon.draarl.ui.components.PageFeedbackKind
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.theme.isDarkTheme
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.ServiceSettings
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapScreen(
    initialLocation: Wgs84LocationMessage?,
    onBack: () -> Unit,
    onSend: (Wgs84LocationMessage) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { CurrentLocationProvider(context) }
    val mapType = if (MaterialTheme.isDarkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
    val preferences = remember(context) {
        context.getSharedPreferences(MAP_PREFERENCES, Context.MODE_PRIVATE)
    }
    var privacyAccepted by rememberSaveable {
        mutableStateOf(preferences.getBoolean(MAP_PRIVACY_ACCEPTED, false))
    }

    if (!privacyAccepted) {
        DraarlConfirmationDialog(
            confirmation = DraarlConfirmation(
                title = "启用地图服务",
                message = "标点和位置预览由高德地图提供。继续使用即同意为地图展示初始化高德地图 SDK。",
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
    if (!hasAmapApiKey(context)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("位置") },
                    navigationIcon = {
                        DraarlIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "返回",
                            onClick = onBack
                        )
                    }
                )
            }
        ) { padding ->
            PageFeedback(
                kind = PageFeedbackKind.ERROR,
                title = "地图暂不可用",
                detail = "未配置地图 Key，无法标点或预览位置",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
        return
    }

    val previewMode = initialLocation != null
    val initialGcj = remember(initialLocation) {
        initialLocation?.let {
            CoordinateConverter.wgs84ToGcj02(GeoCoordinate(it.latitude, it.longitude))
        }
    }
    var selectedGcj by remember(initialLocation) {
        mutableStateOf(initialGcj?.let { LatLng(it.latitude, it.longitude) })
    }
    var altitudeText by rememberSaveable(initialLocation) {
        mutableStateOf(initialLocation?.altitudeMeters?.let { String.format(Locale.US, "%.1f", it) }.orEmpty())
    }
    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }
    var currentWgs84 by remember(initialLocation) { mutableStateOf<GeoCoordinate?>(null) }
    var distanceMeters by remember(initialLocation) { mutableStateOf<Double?>(null) }
    var distanceLoading by remember(initialLocation) { mutableStateOf(false) }
    var distanceError by remember(initialLocation) { mutableStateOf("") }
    var fitMeasurementRequest by remember(initialLocation) { mutableIntStateOf(0) }

    fun locateForDistance() {
        val target = initialLocation ?: return
        if (distanceLoading) return
        distanceLoading = true
        distanceError = ""
        scope.launch {
            try {
                val location = locationProvider.locate()
                val current = GeoCoordinate(location.latitude, location.longitude)
                currentWgs84 = current
                distanceMeters = MapDistance.metersBetween(
                    current,
                    GeoCoordinate(target.latitude, target.longitude)
                )
                fitMeasurementRequest++
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                distanceError = error.message ?: "暂时无法获取当前位置"
            } finally {
                distanceLoading = false
            }
        }
    }

    val distancePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            locateForDistance()
        } else {
            distanceError = "需要定位权限才能查询距离"
        }
    }

    fun requestDistance() {
        val fine =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            locateForDistance()
        } else {
            distancePermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val currentGcj = currentWgs84?.let(CoordinateConverter::wgs84ToGcj02)
        ?.let { LatLng(it.latitude, it.longitude) }
    val distancePath = if (currentGcj != null && selectedGcj != null) {
        listOf(currentGcj, selectedGcj!!)
    } else {
        emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (initialLocation?.kind) {
                            LocationMessageKind.CURRENT -> "当前位置"
                            LocationMessageKind.PINNED -> "标点位置"
                            null -> "选择位置"
                        }
                    )
                },
                navigationIcon = {
                    DraarlIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = onBack
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ManagedAmapView(
                coordinate = selectedGcj,
                allowSelection = !previewMode,
                gesturesEnabled = true,
                showCompass = true,
                zoom = 15f,
                modifier = Modifier.fillMaxSize(),
                recenterRequest = recenterRequest,
                mapType = mapType,
                measurementPoints = currentGcj?.let(::listOf).orEmpty(),
                measurementPath = distancePath,
                measurementPointLabel = "我的位置",
                fitMeasurementRequest = fitMeasurementRequest,
                onCoordinateSelected = { selectedGcj = it }
            )
            if (selectedGcj != null) {
                DraarlTooltip(
                    label = "居中标点",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { recenterRequest++ },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "居中标点")
                    }
                }
            }
            selectedGcj?.let { gcj ->
                val wgs84 = CoordinateConverter.gcj02ToWgs84(GeoCoordinate(gcj.latitude, gcj.longitude))
                LocationDetailsPanel(
                    latitude = wgs84.latitude,
                    longitude = wgs84.longitude,
                    altitudeText = altitudeText,
                    previewMode = previewMode,
                    distanceMeters = distanceMeters,
                    distanceLoading = distanceLoading,
                    distanceError = distanceError,
                    onMeasureDistance = ::requestDistance,
                    onAltitudeChange = { value ->
                        if (value.length <= 12 && value.all { it.isDigit() || it == '.' || it == '-' }) {
                            altitudeText = value
                        }
                    },
                    onSend = {
                        onSend(
                            Wgs84LocationMessage(
                                kind = LocationMessageKind.PINNED,
                                latitude = wgs84.latitude,
                                longitude = wgs84.longitude,
                                altitudeMeters = altitudeText.toDoubleOrNull()
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } ?: Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Text(
                    "点击地图选择标点位置",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
internal fun AmapLocationPreview(location: Wgs84LocationMessage, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val privacyAccepted = context.getSharedPreferences(MAP_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(MAP_PRIVACY_ACCEPTED, false)
    val interactionSource = remember { MutableInteractionSource() }
    val mapType = if (MaterialTheme.isDarkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
    val gcj = remember(location.latitude, location.longitude) {
        CoordinateConverter.wgs84ToGcj02(GeoCoordinate(location.latitude, location.longitude))
    }
    val previewKey = remember(location.latitude, location.longitude, mapType) {
        String.format(Locale.US, "%.5f:%.5f:%d", location.latitude, location.longitude, mapType)
    }
    var mapBitmap by remember(previewKey) {
        mutableStateOf(AmapPreviewCache.get(previewKey))
    }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (mapBitmap != null) {
            Image(
                bitmap = mapBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (privacyAccepted && hasAmapApiKey(context)) {
            remember(context) { initializeAmapServices(context) }
            ManagedAmapView(
                coordinate = LatLng(gcj.latitude, gcj.longitude),
                allowSelection = false,
                gesturesEnabled = false,
                showCompass = false,
                zoom = 15f,
                mapType = mapType,
                modifier = Modifier.fillMaxSize(),
                onMapLoaded = { aMap ->
                    var delivered = false
                    aMap.getMapScreenShot(object : AMap.OnMapScreenShotListener {
                        private fun deliver(bitmap: Bitmap?) {
                            if (!delivered && bitmap != null) {
                                delivered = true
                                AmapPreviewCache.put(previewKey, bitmap)
                                mapBitmap = bitmap
                            }
                        }

                        override fun onMapScreenShot(bitmap: Bitmap?) = deliver(bitmap)

                        override fun onMapScreenShot(bitmap: Bitmap?, status: Int) = deliver(bitmap)
                    })
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "点击查看地图",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onOpen
                )
        )
    }
}

@Composable
internal fun ManagedAmapView(
    coordinate: LatLng?,
    allowSelection: Boolean,
    gesturesEnabled: Boolean,
    active: Boolean = true,
    showCompass: Boolean,
    zoom: Float,
    modifier: Modifier = Modifier,
    recenterRequest: Int = 0,
    zoomInRequest: Int = 0,
    zoomOutRequest: Int = 0,
    mapType: Int = AMap.MAP_TYPE_NORMAL,
    markerIcon: BitmapDescriptor? = null,
    polygonPoints: List<LatLng> = emptyList(),
    measurementPoints: List<LatLng> = emptyList(),
    measurementPath: List<LatLng> = emptyList(),
    measurementPointLabel: String = "测点",
    fitBoundsRequest: Int = 0,
    fitMeasurementRequest: Int = 0,
    onCoordinateSelected: (LatLng) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    onMapLoaded: ((AMap) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (!isAmapNativeSupported()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(
                "当前设备无法安全加载高德地图 native 库",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val mapView = remember(context) { runCatching { MapView(context) }.getOrNull() }
    if (mapView == null) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(
                "地图组件无法在当前系统加载，请使用支持 16K 页的地图 SDK",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val lifecycleController = remember(mapView) {
        MapViewLifecycleController(
            resumeView = mapView::onResume,
            pauseView = mapView::onPause
        )
    }
    var map by remember { mutableStateOf<AMap?>(null) }

    SideEffect {
        lifecycleController.setActive(active)
    }

    DisposableEffect(mapView, lifecycle, lifecycleController) {
        mapView.onCreate(Bundle())
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleController.onHostResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> lifecycleController.onHostResume()
                Lifecycle.Event.ON_PAUSE -> lifecycleController.onHostPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            lifecycleController.close()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val aMap = view.map
            if (map !== aMap) {
                map = aMap
                aMap.uiSettings.isZoomControlsEnabled = false
                if (onMapLoaded != null) {
                    aMap.setOnMapLoadedListener { onMapLoaded(aMap) }
                }
                val camera = coordinate?.let { CameraUpdateFactory.newLatLngZoom(it, zoom) }
                    ?: CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 4.5f)
                aMap.moveCamera(camera)
            }
            aMap.uiSettings.isCompassEnabled = showCompass
            aMap.uiSettings.setAllGesturesEnabled(gesturesEnabled)
            aMap.setOnMapClickListener { selected ->
                if (allowSelection) onCoordinateSelected(selected)
                onMapClick(selected)
            }
        }
    )

    LaunchedEffect(
        map,
        coordinate,
        markerIcon,
        polygonPoints,
        measurementPoints,
        measurementPath,
        measurementPointLabel
    ) {
        val aMap = map ?: return@LaunchedEffect
        aMap.clear()
        coordinate?.let {
            aMap.addMarker(
                MarkerOptions()
                    .position(it)
                    .anchor(0.5f, 1f)
                    .zIndex(20f)
                    .draggable(false)
                    .apply { markerIcon?.let(::icon) }
            )
        }
        if (polygonPoints.size >= 3) {
            aMap.addPolygon(
                PolygonOptions()
                    .addAll(polygonPoints)
                    .strokeColor(MAP_GRID_STROKE_COLOR)
                    .fillColor(MAP_GRID_FILL_COLOR)
                    .strokeWidth(4f)
                    .zIndex(10f)
            )
        }
        if (measurementPath.size >= 2) {
            aMap.addPolyline(
                PolylineOptions()
                    .addAll(measurementPath)
                    .color(MAP_MEASUREMENT_COLOR)
                    .width(8f)
                    .zIndex(15f)
            )
        }
        measurementPoints.forEachIndexed { index, point ->
            aMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .title(
                        if (measurementPoints.size ==
                            1
                        ) {
                            measurementPointLabel
                        } else {
                            "$measurementPointLabel ${index + 1}"
                        }
                    )
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .anchor(0.5f, 1f)
                    .zIndex(18f)
                    .draggable(false)
            )
        }
    }

    LaunchedEffect(map, coordinate, zoom, polygonPoints.isEmpty()) {
        val aMap = map ?: return@LaunchedEffect
        if (polygonPoints.isEmpty()) {
            coordinate?.let { aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom)) }
        }
    }

    LaunchedEffect(map, polygonPoints, fitBoundsRequest) {
        val aMap = map ?: return@LaunchedEffect
        if (polygonPoints.size >= 3) {
            val bounds = LatLngBounds.Builder().apply { polygonPoints.forEach(::include) }.build()
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_GRID_BOUNDS_PADDING_PX))
        }
    }

    LaunchedEffect(map, measurementPath, fitMeasurementRequest) {
        val aMap = map ?: return@LaunchedEffect
        if (fitMeasurementRequest > 0 && measurementPath.size >= 2) {
            val bounds = LatLngBounds.Builder().apply { measurementPath.forEach(::include) }.build()
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_MEASUREMENT_BOUNDS_PADDING_PX))
        }
    }

    LaunchedEffect(map, mapType) {
        map?.mapType = mapType
    }

    LaunchedEffect(map, recenterRequest) {
        if (recenterRequest > 0) {
            val aMap = map ?: return@LaunchedEffect
            coordinate?.let { aMap.animateCamera(CameraUpdateFactory.newLatLng(it)) }
        }
    }

    LaunchedEffect(map, zoomInRequest) {
        if (zoomInRequest > 0) map?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    LaunchedEffect(map, zoomOutRequest) {
        if (zoomOutRequest > 0) map?.animateCamera(CameraUpdateFactory.zoomOut())
    }
}

@Composable
private fun LocationDetailsPanel(
    latitude: Double,
    longitude: Double,
    altitudeText: String,
    previewMode: Boolean,
    distanceMeters: Double?,
    distanceLoading: Boolean,
    distanceError: String,
    onMeasureDistance: () -> Unit,
    onAltitudeChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val altitudeValid = altitudeText.isBlank() || altitudeText.toDoubleOrNull() != null
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PaddingValues(horizontal = 16.dp, vertical = 14.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("WGS-84", style = MaterialTheme.typography.labelMedium)
                    Text(
                        String.format(Locale.US, "%.6f, %.6f", latitude, longitude),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (previewMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            altitudeText.toDoubleOrNull()?.let { "海拔 ${String.format(Locale.US, "%.1f", it)} 米" }
                                ?: "未提供海拔",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        distanceMeters?.let { distance ->
                            Text(
                                "距当前位置 ${MapDistance.format(distance)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (distanceError.isNotBlank()) {
                            InlineNotice(
                                text = distanceError,
                                tone = StatusTone.ERROR
                            )
                        }
                    }
                    Button(onClick = onMeasureDistance, enabled = !distanceLoading) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Text(if (distanceLoading) "定位中" else "距我")
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = altitudeText,
                        onValueChange = onAltitudeChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("海拔（米，可选）") },
                        singleLine = true,
                        isError = !altitudeValid,
                        supportingText = if (altitudeValid) null else ({ Text("请输入有效数字") })
                    )
                    Button(onClick = onSend, enabled = altitudeValid) { Text("发送") }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun hasAmapApiKey(context: Context): Boolean = runCatching {
    val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    applicationInfo.metaData?.getString("com.amap.api.v2.apikey").orEmpty().isNotBlank()
}.getOrDefault(false)

internal fun initializeAmapServices(context: Context): Boolean {
    if (!isAmapNativeSupported() || !hasAmapApiKey(context)) return false
    return runCatching {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        ServiceSettings.updatePrivacyShow(context, true, true)
        ServiceSettings.updatePrivacyAgree(context, true)
        ServiceSettings.getInstance().setProtocol(ServiceSettings.HTTPS)
    }.isSuccess
}

internal fun isAmapNativeSupported(): Boolean = runCatching {
    Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } &&
        Os.sysconf(OsConstants._SC_PAGESIZE) <= AMAP_MAX_PAGE_SIZE_BYTES
}.getOrDefault(false)

internal const val MAP_PREFERENCES = "map_preferences"
internal const val MAP_PRIVACY_ACCEPTED = "amap_privacy_accepted"
private val DEFAULT_MAP_CENTER = LatLng(34.3416, 108.9398)
private const val AMAP_MAX_PAGE_SIZE_BYTES = 4_096L
private const val MAP_GRID_BOUNDS_PADDING_PX = 96
private const val MAP_MEASUREMENT_BOUNDS_PADDING_PX = 144
private const val MAP_GRID_STROKE_COLOR = 0xFF2856D7.toInt()
private const val MAP_GRID_FILL_COLOR = 0x332856D7
private const val MAP_MEASUREMENT_COLOR = 0xFFE54B4B.toInt()

private object AmapPreviewCache : LruCache<String, Bitmap>(12 * 1_024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1_024
}
