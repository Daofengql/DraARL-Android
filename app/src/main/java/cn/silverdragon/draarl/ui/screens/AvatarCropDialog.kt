package cn.silverdragon.draarl.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.StatusTone
import java.io.ByteArrayOutputStream

@Composable
fun AvatarCropDialog(imageUri: Uri, onDismiss: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val originalBitmap = remember(imageUri) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val decoded = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            decoded
        } catch (e: Exception) {
            null
        }
    }

    val imageBitmap = remember(originalBitmap) {
        originalBitmap?.asImageBitmap()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    DraarlDialog(
        title = "裁切头像",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss),
        confirmAction = DraarlAction(
            label = "确认",
            onClick = { originalBitmap?.let { onConfirm(centerCropAvatar(it)) } },
            enabled = originalBitmap != null,
            style = CommandStyle.PRIMARY
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (imageBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "裁切预览",
                        modifier = Modifier
                            .aspectRatio(1f)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "双指缩放和拖动调整头像位置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                InlineNotice(
                    text = "无法加载图片，请重新选择头像文件。",
                    tone = StatusTone.ERROR
                )
            }
        }
    }
}

private fun centerCropAvatar(originalBitmap: Bitmap): ByteArray {
    val size = minOf(originalBitmap.width, originalBitmap.height)
    val croppedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(croppedBitmap)
    val srcX = (originalBitmap.width - size) / 2
    val srcY = (originalBitmap.height - size) / 2

    canvas.drawBitmap(
        originalBitmap,
        android.graphics.Rect(srcX, srcY, srcX + size, srcY + size),
        android.graphics.Rect(0, 0, size, size),
        android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    )

    return ByteArrayOutputStream().use { output ->
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        output.toByteArray()
    }
}
