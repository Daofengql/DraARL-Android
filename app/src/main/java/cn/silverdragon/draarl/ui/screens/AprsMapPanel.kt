package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.maps.CoordinateConverter
import cn.silverdragon.draarl.maps.CurrentLocationProvider
import cn.silverdragon.draarl.maps.GeoCoordinate
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.BitmapDescriptorFactory
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AprsMapPanel(
    controller: AppController,
    onStartPtt: () -> Boolean,
    onStopPtt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember(context) { CurrentLocationProvider(context) }
    var coordinate by remember { mutableStateOf<LatLng?>(null) }
    var loading by remember { mutableStateOf(false) }
    var recenterRequest by remember { mutableIntStateOf(0) }
    val fallbackMarker = remember(context) {
        BitmapDescriptorFactory.fromResource(R.drawable.ic_aprs_self_marker)
    }
    var avatarMarker by remember(controller.user?.avatarUrl) { mutableStateOf<com.amap.api.maps.model.BitmapDescriptor?>(null) }

    LaunchedEffect(controller.user?.avatarUrl) {
        val avatarUrl = controller.user?.avatarUrl.orEmpty()
        if (avatarUrl.isBlank()) {
            avatarMarker = null
        } else {
            avatarMarker = runCatching {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context).data(avatarUrl).size(96).build(),
                )
                val bitmap = (result as? SuccessResult)?.image as? BitmapImage
                bitmap?.bitmap?.let { avatar ->
                    val output = Bitmap.createBitmap(96, 112, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    canvas.drawCircle(48f, 48f, 45f, paint.apply { color = android.graphics.Color.WHITE })
                    canvas.save()
                    canvas.clipPath(android.graphics.Path().apply { addCircle(48f, 48f, 42f, android.graphics.Path.Direction.CW) })
                    canvas.drawBitmap(avatar, null, Rect(6, 6, 90, 90), paint)
                    canvas.restore()
                    val tip = android.graphics.Path().apply {
                        moveTo(48f, 112f)
                        lineTo(39f, 88f)
                        lineTo(57f, 88f)
                        close()
                    }
                    canvas.drawPath(tip, paint.apply { color = android.graphics.Color.rgb(25, 118, 210) })
                    BitmapDescriptorFactory.fromBitmap(output)
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
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) locate() else controller.showNotice("需要定位权限才能显示当前位置")
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) locate()
    }

    Box(modifier.fillMaxSize()) {
        if (hasAmapApiKey(context)) {
            ManagedAmapView(
                coordinate = coordinate,
                allowSelection = false,
                gesturesEnabled = true,
                showCompass = true,
                zoom = 15f,
                recenterRequest = recenterRequest,
                markerIcon = avatarMarker ?: fallbackMarker,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("地图 Key 未配置", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        IconButton(
            onClick = {
                if (coordinate == null) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    recenterRequest++
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp).shadow(4.dp, CircleShape),
        ) {
            Icon(
                if (coordinate == null) Icons.Default.LocationSearching else Icons.Default.CenterFocusStrong,
                contentDescription = "定位当前位置",
            )
        }

        if (loading) {
            Text("正在定位", modifier = Modifier.align(Alignment.Center).padding(top = 54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        MapPttButton(
            transmitting = controller.radioStatus.transmitting,
            enabled = controller.radioStatus.connected && controller.radioStatus.speaker.isBlank(),
            onStart = onStartPtt,
            onStop = onStopPtt,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

@Composable
internal fun RadioModeSwitcher(
    mapSelected: Boolean,
    onMap: () -> Unit,
    onMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = mapSelected,
            onClick = onMap,
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("地图") }
        SegmentedButton(
            selected = !mapSelected,
            onClick = onMessages,
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("通联日志") }
    }
}

@Composable
private fun MapPttButton(
    transmitting: Boolean,
    enabled: Boolean,
    onStart: () -> Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
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
                    },
                )
            },
        shape = CircleShape,
        color = color,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, contentDescription = "按住发射", modifier = Modifier.size(42.dp))
        }
    }
}
