package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.User

@Composable
internal fun ProfileInfoSection(user: User) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("个人信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ProfileInfoItem(Icons.Default.Person, "用户名", user.username)
        HorizontalDivider()
        ProfileInfoItem(Icons.Default.Badge, "呼号", user.callsign.ifBlank { "未设置" })
        HorizontalDivider()
        ProfileInfoItem(Icons.Default.Email, "邮箱", user.email.ifBlank { "未设置" })
        HorizontalDivider()
        ProfileInfoItem(Icons.Default.Phone, "手机", user.phone.ifBlank { "未设置" })
        HorizontalDivider()
        ProfileInfoItem(Icons.Default.LocationOn, "地址", user.address.ifBlank { "未设置" })
        HorizontalDivider()
        ProfileInfoItem(Icons.Default.Person, "简介", user.introduction.ifBlank { "未设置" })
    }
}

@Composable
private fun ProfileInfoItem(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
