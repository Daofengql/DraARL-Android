package cn.silverdragon.draarl.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun CaptchaInput(
    value: String,
    onValueChange: (String) -> Unit,
    imageBase64: String,
    loading: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
) {
    val bitmap = remember(imageBase64) {
        if (imageBase64.isBlank()) null else runCatching {
            val encoded = imageBase64.substringAfter("base64,", imageBase64)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(8)) },
            modifier = Modifier.weight(1f),
            label = { Text("图片验证码") },
            singleLine = true,
            enabled = enabled,
        )
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.width(132.dp).height(56.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClickLabel = "刷新图片验证码",
                    onClick = onRefresh,
                ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = "图片验证码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    else -> Icon(Icons.Default.Refresh, contentDescription = "获取图片验证码")
                }
            }
        }
    }
}
