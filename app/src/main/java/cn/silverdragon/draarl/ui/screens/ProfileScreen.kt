package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.ui.components.SectionTitle
import cn.silverdragon.draarl.ui.components.StatusPill
import cn.silverdragon.draarl.ui.components.UserAvatar

@Composable
fun ProfileScreen(controller: AppController) {
    val user = controller.user ?: return
    var nickname by remember(user.id, user.nickname) { mutableStateOf(user.nickname) }
    var phone by remember(user.id, user.phone) { mutableStateOf(user.phone) }
    var address by remember(user.id, user.address) { mutableStateOf(user.address) }
    var introduction by remember(user.id, user.introduction) { mutableStateOf(user.introduction) }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user.avatarUrl, Modifier.size(64.dp))
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(user.callsign, "@${user.username}").filter(String::isNotBlank).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusPill(
                        if (user.isApproved) "已审核" else if (user.approvalStatus == 2) "未通过" else "待审核",
                        if (user.isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("个人资料")
                    OutlinedTextField(nickname, { nickname = it }, label = { Text("昵称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("电话") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("所在地") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        introduction,
                        { introduction = it },
                        label = { Text("简介") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    Button(
                        onClick = { controller.updateProfile(nickname, phone, address, introduction) },
                        enabled = !controller.contentLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(" 保存")
                    }
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("连接信息")
                    Text(controller.serverUrl, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        controller.selectedAccessPoint?.let { "${it.displayName} · ${it.address}" } ?: "UDP 入口未选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    if (user.isApproved) {
                        OutlinedButton(
                            onClick = { controller.navigate(AppPage.RECORDS) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Text(" 通信记录")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = controller::logout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Text(" 退出登录")
            }
        }
    }
}
