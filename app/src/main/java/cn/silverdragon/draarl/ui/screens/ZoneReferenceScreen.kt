package cn.silverdragon.draarl.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl

@Composable
internal fun ZoneReferenceScreen(onBack: () -> Unit, initialTypeKey: String = "cq") {
    var typeKey by rememberSaveable { mutableStateOf(initialTypeKey) }
    Column(Modifier.fillMaxSize()) {
        ToolHeader("CQ / ITU 分区图", onBack)
        DraarlSegmentedControl(
            segments = listOf(DraarlSegment("cq", "CQ 40 区"), DraarlSegment("itu", "ITU 90 区")),
            selectedKey = typeKey,
            onSelect = { typeKey = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        key(typeKey) {
            ZoomableZoneReference(
                imageResource = if (typeKey == "cq") R.drawable.cq_zone_reference else R.drawable.itu_zone_reference,
                description = if (typeKey == "cq") "CQ 分区参考图" else "ITU 分区参考图",
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("双指缩放，拖动查看", style = MaterialTheme.typography.labelLarge)
            Text(
                "离线参考图，依据 HB9HIL 分区数据生成，非 CQ Magazine 或 ITU 官方制图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ZoomableZoneReference(@DrawableRes imageResource: Int, description: String, modifier: Modifier = Modifier) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(imageResource),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ).pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val limitX = size.width * (scale - 1f) / 2f + size.width * EXTRA_PAN_FRACTION
                    val limitY = size.height * (scale - 1f) / 2f + size.height * EXTRA_PAN_FRACTION
                    offset = Offset(
                        x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                        y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                    )
                }
            }
        )
        IconButton(
            onClick = {
                scale = 1f
                offset = Offset.Zero
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
        ) {
            Icon(Icons.Default.CenterFocusStrong, contentDescription = "复位分区图")
        }
    }
}

private val OffsetSaver = androidx.compose.runtime.saveable.Saver<Offset, List<Float>>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val EXTRA_PAN_FRACTION = 0.08f
