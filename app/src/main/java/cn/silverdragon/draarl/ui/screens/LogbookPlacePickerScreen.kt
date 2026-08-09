package cn.silverdragon.draarl.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import cn.silverdragon.draarl.maps.AmapPlace
import cn.silverdragon.draarl.maps.AmapPlaceService
import cn.silverdragon.draarl.maps.CoordinateConverter
import cn.silverdragon.draarl.maps.GeoCoordinate
import cn.silverdragon.draarl.maps.LastMapLocationStore
import cn.silverdragon.draarl.maps.MaidenheadLocator
import cn.silverdragon.draarl.ui.components.DraarlConfirmation
import cn.silverdragon.draarl.ui.components.DraarlConfirmationDialog
import cn.silverdragon.draarl.ui.theme.isDarkTheme
import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class LogbookPlaceSelection(
    val qth: String,
    val latitude: Double,
    val longitude: Double,
    val locator: String
)

@Composable
internal fun LogbookPlacePickerScreen(title: String, onBack: () -> Unit, onConfirm: (LogbookPlaceSelection) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(MAP_PREFERENCES, Context.MODE_PRIVATE) }
    var privacyAccepted by rememberSaveable {
        mutableStateOf(preferences.getBoolean(MAP_PRIVACY_ACCEPTED, false))
    }
    if (!privacyAccepted) {
        DraarlConfirmationDialog(
            confirmation = DraarlConfirmation(
                title = "启用地点搜索",
                message = "地点搜索、地址解析和地图选点由高德地图提供。",
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
    val placeService = remember(context) { AmapPlaceService(context) }
    val cachedWgs84 = remember(context) { LastMapLocationStore(context).load() }
    val cachedGcj02 = remember(cachedWgs84) {
        cachedWgs84?.let {
            CoordinateConverter.wgs84ToGcj02(GeoCoordinate(it.latitude, it.longitude))
                .let { point -> LatLng(point.latitude, point.longitude) }
        }
    }
    var selectedGcj02 by remember { mutableStateOf(cachedGcj02) }
    var selectedPlace by remember { mutableStateOf<AmapPlace?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AmapPlace>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var recenterRequest by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var reverseJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var reverseGeneration by remember { mutableIntStateOf(0) }
    val mapType = if (MaterialTheme.isDarkTheme) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL

    fun resolve(coordinate: LatLng) {
        val generation = ++reverseGeneration
        selectedGcj02 = coordinate
        selectedPlace = null
        results = emptyList()
        error = ""
        reverseJob?.cancel()
        reverseJob = scope.launch {
            resolving = true
            try {
                val place = placeService.reverse(coordinate.latitude, coordinate.longitude)
                if (generation == reverseGeneration) selectedPlace = place
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == reverseGeneration) {
                    error = failure.message ?: "地址解析失败，可重新选点"
                }
            } finally {
                if (generation == reverseGeneration) resolving = false
            }
        }
    }

    fun search() {
        if (query.isBlank()) return
        val generation = ++searchGeneration
        searchJob?.cancel()
        searchJob = scope.launch {
            searchBusy = true
            error = ""
            try {
                val places = placeService.search(query, selectedGcj02?.latitude, selectedGcj02?.longitude)
                if (generation == searchGeneration) {
                    results = places
                    if (places.isEmpty()) error = "没有找到相关地点"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (generation == searchGeneration) error = failure.message ?: "地点搜索失败"
            } finally {
                if (generation == searchGeneration) searchBusy = false
            }
        }
    }

    LaunchedEffect(cachedGcj02) {
        if (cachedGcj02 != null && selectedPlace == null) resolve(cachedGcj02)
    }

    Column(Modifier.fillMaxSize()) {
        ToolHeader(title, onBack)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ManagedAmapView(
                coordinate = selectedGcj02,
                allowSelection = true,
                gesturesEnabled = true,
                showCompass = true,
                zoom = 16f,
                recenterRequest = recenterRequest,
                mapType = mapType,
                onCoordinateSelected = ::resolve,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索地点、道路或地标") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                searchGeneration++
                                searchJob?.cancel()
                                searchBusy = false
                                query = ""
                                results = emptyList()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "清除搜索")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() })
                )
                if (searchBusy) {
                    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 4.dp) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                            Text("正在搜索地点")
                        }
                    }
                } else if (results.isNotEmpty()) {
                    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 5.dp) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                            items(results, key = { "${it.latitude},${it.longitude}" }) { place ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        reverseGeneration++
                                        reverseJob?.cancel()
                                        resolving = false
                                        selectedPlace = place
                                        selectedGcj02 = LatLng(place.latitude, place.longitude)
                                        query = place.name
                                        results = emptyList()
                                        recenterRequest++
                                    }.padding(horizontal = 14.dp, vertical = 11.dp)
                                ) {
                                    Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                    if (place.address.isNotBlank()) {
                                        Text(
                                            place.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            val coordinate = selectedGcj02
            if (coordinate != null) {
                val wgs84 = CoordinateConverter.gcj02ToWgs84(GeoCoordinate(coordinate.latitude, coordinate.longitude))
                val locator = MaidenheadLocator.encode(wgs84.latitude, wgs84.longitude)
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    tonalElevation = 7.dp
                ) {
                    Column(
                        modifier = Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            selectedPlace?.name.orEmpty().ifBlank { if (resolving) "正在解析地点" else "已选择地图位置" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            String.format(
                                Locale.US,
                                "WGS-84  %.6f, %.6f  ·  %s",
                                wgs84.latitude,
                                wgs84.longitude,
                                locator
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = {
                                val placeName = selectedPlace?.name.orEmpty().ifBlank {
                                    String.format(Locale.US, "%.6f, %.6f", wgs84.latitude, wgs84.longitude)
                                }
                                onConfirm(
                                    LogbookPlaceSelection(
                                        qth = "$placeName · $locator",
                                        latitude = wgs84.latitude,
                                        longitude = wgs84.longitude,
                                        locator = locator
                                    )
                                )
                            },
                            enabled = !resolving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("确认此地点") }
                    }
                }
            } else if (error.isNotBlank()) {
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 6.dp) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
