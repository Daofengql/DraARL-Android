package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun UserAvatar(
    url: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "用户头像",
                modifier = Modifier.matchParentSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
