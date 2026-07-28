package cn.silverdragon.draarl.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.silverdragon.draarl.data.LocationMessageKind
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.maps.CoordinateConverter
import cn.silverdragon.draarl.maps.GeoCoordinate
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapScreen(
    initialLocation: Wgs84LocationMessage?,
    onBack: () -> Unit,
    onSend: (Wgs84LocationMessage) -> Boolean,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(MAP_PREFERENCES, Context.MODE_PRIVATE)
    }
    var privacyAccepted by rememberSaveable {
        mutableStateOf(preferences.getBoolean(MAP_PRIVACY_ACCEPTED, false))
    }

    if (!privacyAccepted) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("启用地图服务") },
            text = { Text("标点和位置预览由高德地图提供。继续使用即同意为地图展示初始化高德地图 SDK。") },
            confirmButton = {
                TextButton(onClick = {
                    preferences.edit { putBoolean(MAP_PRIVACY_ACCEPTED, true) }
                    privacyAccepted = true
                }) { Text("同意并继续") }
            },
            dismissButton = { TextButton(onClick = onBack) { Text("取消") } },
        )
        return
    }

    remember(context) {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        true
    }
    if (!hasAmapApiKey(context)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("位置") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("地图 Key 未配置，暂时无法使用标点位置")
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (initialLocation?.kind) {
                            LocationMessageKind.CURRENT -> "当前位置"
                            LocationMessageKind.PINNED -> "标点位置"
                            null -> "选择位置"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
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
                onCoordinateSelected = { selectedGcj = it },
            )
            if (selectedGcj != null) {
                SmallFloatingActionButton(
                    onClick = { recenterRequest++ },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "居中标点")
                }
            }
            selectedGcj?.let { gcj ->
                val wgs84 = CoordinateConverter.gcj02ToWgs84(GeoCoordinate(gcj.latitude, gcj.longitude))
                LocationDetailsPanel(
                    latitude = wgs84.latitude,
                    longitude = wgs84.longitude,
                    altitudeText = altitudeText,
                    previewMode = previewMode,
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
                                altitudeMeters = altitudeText.toDoubleOrNull(),
                            ),
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } ?: Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Text(
                    "点击地图选择标点位置",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun AmapLocationPreview(
    location: Wgs84LocationMessage,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val privacyAccepted = context.getSharedPreferences(MAP_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(MAP_PRIVACY_ACCEPTED, false)
    val interactionSource = remember { MutableInteractionSource() }
    val gcj = remember(location.latitude, location.longitude) {
        CoordinateConverter.wgs84ToGcj02(GeoCoordinate(location.latitude, location.longitude))
    }
    val previewKey = remember(location.latitude, location.longitude) {
        String.format(Locale.US, "%.5f:%.5f", location.latitude, location.longitude)
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (mapBitmap != null) {
            Image(
                bitmap = mapBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (privacyAccepted && hasAmapApiKey(context)) {
            remember(context) {
                MapsInitializer.updatePrivacyShow(context, true, true)
                MapsInitializer.updatePrivacyAgree(context, true)
                true
            }
            ManagedAmapView(
                coordinate = LatLng(gcj.latitude, gcj.longitude),
                allowSelection = false,
                gesturesEnabled = false,
                showCompass = false,
                zoom = 15f,
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
                },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "点击查看地图",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onOpen,
                ),
        )
    }
}

@Composable
private fun ManagedAmapView(
    coordinate: LatLng?,
    allowSelection: Boolean,
    gesturesEnabled: Boolean,
    showCompass: Boolean,
    zoom: Float,
    modifier: Modifier = Modifier,
    recenterRequest: Int = 0,
    onCoordinateSelected: (LatLng) -> Unit = {},
    onMapLoaded: ((AMap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) { MapView(context) }
    var map by remember { mutableStateOf<AMap?>(null) }

    DisposableEffect(mapView, lifecycle) {
        mapView.onCreate(Bundle())
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
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
                aMap.uiSettings.isCompassEnabled = showCompass
                aMap.uiSettings.setAllGesturesEnabled(gesturesEnabled)
                aMap.setOnMapClickListener { coordinate ->
                    if (allowSelection) onCoordinateSelected(coordinate)
                }
                if (onMapLoaded != null) {
                    aMap.setOnMapLoadedListener { onMapLoaded(aMap) }
                }
                val camera = coordinate?.let { CameraUpdateFactory.newLatLngZoom(it, zoom) }
                    ?: CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, 4.5f)
                aMap.moveCamera(camera)
            }
        },
    )

    LaunchedEffect(map, coordinate, zoom, recenterRequest) {
        val aMap = map ?: return@LaunchedEffect
        aMap.clear()
        coordinate?.let {
            aMap.addMarker(MarkerOptions().position(it))
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
        }
    }
}

@Composable
private fun LocationDetailsPanel(
    latitude: Double,
    longitude: Double,
    altitudeText: String,
    previewMode: Boolean,
    onAltitudeChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val altitudeValid = altitudeText.isBlank() || altitudeText.toDoubleOrNull() != null
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PaddingValues(horizontal = 16.dp, vertical = 14.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("WGS-84", style = MaterialTheme.typography.labelMedium)
                    Text(
                        String.format(Locale.US, "%.6f, %.6f", latitude, longitude),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (previewMode) {
                Text(
                    altitudeText.toDoubleOrNull()?.let { "海拔 ${String.format(Locale.US, "%.1f", it)} 米" }
                        ?: "未提供海拔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = altitudeText,
                        onValueChange = onAltitudeChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("海拔（米，可选）") },
                        singleLine = true,
                        isError = !altitudeValid,
                        supportingText = if (altitudeValid) null else ({ Text("请输入有效数字") }),
                    )
                    Button(onClick = onSend, enabled = altitudeValid) { Text("发送") }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun hasAmapApiKey(context: Context): Boolean = runCatching {
    val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    applicationInfo.metaData?.getString("com.amap.api.v2.apikey").orEmpty().isNotBlank()
}.getOrDefault(false)

private const val MAP_PREFERENCES = "map_preferences"
private const val MAP_PRIVACY_ACCEPTED = "amap_privacy_accepted"
private val DEFAULT_MAP_CENTER = LatLng(34.3416, 108.9398)

private object AmapPreviewCache : LruCache<String, Bitmap>(12 * 1_024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1_024
}
