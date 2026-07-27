package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(controller: AppController) {
    val user = controller.user ?: return
    var nickname by remember(user.id) { mutableStateOf(user.nickname) }
    var phone by remember(user.id) { mutableStateOf(user.phone) }
    var address by remember(user.id) { mutableStateOf(user.address) }
    var introduction by remember(user.id) { mutableStateOf(user.introduction) }
    var birthday by remember(user.id) { mutableStateOf(user.birthday) }
    var dmrId by remember(user.id) { mutableStateOf(user.dmrId.takeIf { it > 0 }?.toString().orEmpty()) }
    var mdcId by remember(user.id) { mutableStateOf(user.mdcId) }

    fun save() {
        controller.profile.updateProfile(
            nickname = nickname.trim(),
            phone = phone.trim(),
            address = address.trim(),
            introduction = introduction.trim(),
            birthday = birthday.trim(),
            sex = user.sex,
            dmrid = dmrId.toIntOrNull() ?: 0,
            mdcid = mdcId.trim(),
            alarmMsg = user.alarmMsg,
            onSuccess = { controller.navigate(AppPage.PROFILE) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    IconButton(onClick = { controller.navigate(AppPage.PROFILE) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { EditSectionTitle("基本资料") }
            item { ProfileEditField(nickname, { nickname = it }, "昵称") }
            item {
                OutlinedTextField(
                    value = introduction,
                    onValueChange = { introduction = it },
                    label = { Text("个人简介") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
            }
            item { ProfileEditField(phone, { phone = it }, "手机号", KeyboardType.Phone) }
            item { ProfileEditField(address, { address = it }, "地址") }
            item { ProfileEditField(birthday, { birthday = it }, "生日 (YYYY-MM-DD)") }

            item { EditSectionTitle("电台身份") }
            item { ProfileEditField(dmrId, { dmrId = it.filter(Char::isDigit) }, "DMR ID", KeyboardType.Number) }
            item { ProfileEditField(mdcId, { mdcId = it }, "MDC ID") }

            item {
                Button(
                    onClick = ::save,
                    enabled = !controller.profile.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    if (controller.profile.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Text(
                        if (controller.profile.busy) "正在保存" else "保存资料",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ProfileEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
