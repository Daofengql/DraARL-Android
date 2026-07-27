package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.silverdragon.draarl.AppController

@Composable
internal fun EditProfileDialog(controller: AppController, onDismiss: () -> Unit) {
    val user = controller.user ?: return
    var nickname by remember(user.id) { mutableStateOf(user.nickname) }
    var phone by remember(user.id) { mutableStateOf(user.phone) }
    var address by remember(user.id) { mutableStateOf(user.address) }
    var introduction by remember(user.id) { mutableStateOf(user.introduction) }
    var birthday by remember(user.id) { mutableStateOf(user.birthday) }
    var dmrId by remember(user.id) { mutableStateOf(user.dmrId.toString()) }
    var mdcId by remember(user.id) { mutableStateOf(user.mdcId) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("编辑个人资料", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { EditField(nickname, { nickname = it }, "昵称") }
                    item { EditField(phone, { phone = it }, "手机号") }
                    item { EditField(address, { address = it }, "地址") }
                    item { EditField(birthday, { birthday = it }, "生日 (YYYY-MM-DD)") }
                    item { EditField(dmrId, { dmrId = it.filter(Char::isDigit) }, "DMR ID") }
                    item { EditField(mdcId, { mdcId = it }, "MDC ID") }
                    item {
                        OutlinedTextField(
                            value = introduction,
                            onValueChange = { introduction = it },
                            label = { Text("个人简介") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            controller.profile.updateProfile(
                                nickname = nickname,
                                phone = phone,
                                address = address,
                                introduction = introduction,
                                birthday = birthday,
                                sex = user.sex,
                                dmrid = dmrId.toIntOrNull() ?: 0,
                                mdcid = mdcId,
                                alarmMsg = user.alarmMsg,
                            )
                            onDismiss()
                        },
                        enabled = !controller.profile.busy,
                    ) {
                        if (controller.profile.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
