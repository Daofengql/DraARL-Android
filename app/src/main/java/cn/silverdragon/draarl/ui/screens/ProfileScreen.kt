package cn.silverdragon.draarl.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.ui.components.StatusPill
import coil3.compose.AsyncImage

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(controller: AppController) {
    val user = controller.user ?: return
    var showEditDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showCropDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = { controller.navigate(AppPage.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 头部卡片 - 头像和基本信息
            item {
                Card(shape = MaterialTheme.shapes.medium) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 头像 - 点击整个头像触发选择
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { avatarLauncher.launch("image/*") },
                        ) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            // 按下时显示半透明遮罩和相机图标
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isPressed,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "更换头像",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 用户名和呼号
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "@${user.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(8.dp))

                        // 审核状态
                        StatusPill(
                            text = when (user.approvalStatus) {
                                1 -> "已审核"
                                2 -> "未通过"
                                else -> "待审核"
                            },
                            color = when (user.approvalStatus) {
                                1 -> MaterialTheme.colorScheme.primary
                                2 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )

                        Spacer(Modifier.height(12.dp))

                        // 编辑资料按钮
                        Button(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("编辑资料")
                        }
                    }
                }
            }

            // 个人信息卡片
            item {
                Card(shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "个人信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        ProfileInfoItem(Icons.Default.Person, "用户名", user.username)
                        ProfileInfoItem(Icons.Default.Badge, "呼号", user.callsign.ifBlank { "未设置" })
                        ProfileInfoItem(Icons.Default.Email, "邮箱", user.email.ifBlank { "未设置" })
                        ProfileInfoItem(Icons.Default.Phone, "手机", user.phone.ifBlank { "未设置" })
                        ProfileInfoItem(Icons.Default.LocationOn, "地址", user.address.ifBlank { "未设置" })
                        ProfileInfoItem(Icons.Default.Person, "简介", user.introduction.ifBlank { "未设置" })
                    }
                }
            }
        }
    }

    // 编辑资料对话框
    if (showEditDialog) {
        EditProfileDialog(
            controller = controller,
            onDismiss = { showEditDialog = false },
        )
    }

    // 头像裁切对话框
    if (showCropDialog && selectedImageUri != null) {
        AvatarCropDialog(
            imageUri = selectedImageUri!!,
            onDismiss = {
                showCropDialog = false
                selectedImageUri = null
            },
            onConfirm = { croppedBytes ->
                showCropDialog = false
                val fileName = "avatar_${System.currentTimeMillis()}.jpg"
                controller.uploadAvatar(croppedBytes, fileName)
                selectedImageUri = null
            },
        )
    }
}

@Composable
private fun ProfileInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    controller: AppController,
    onDismiss: () -> Unit,
) {
    val user = controller.user ?: return
    var nickname by remember { mutableStateOf(user.nickname) }
    var phone by remember { mutableStateOf(user.phone) }
    var address by remember { mutableStateOf(user.address) }
    var introduction by remember { mutableStateOf(user.introduction) }
    var birthday by remember { mutableStateOf(user.birthday) }
    var sex by remember { mutableStateOf(user.sex) }
    var dmrid by remember { mutableStateOf(user.dmrId.toString()) }
    var mdcid by remember { mutableStateOf(user.mdcId) }
    var alarmMsg by remember { mutableStateOf(user.alarmMsg) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "编辑个人资料",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, false).height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("昵称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("手机号") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("地址") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = birthday,
                            onValueChange = { birthday = it },
                            label = { Text("生日 (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = dmrid,
                            onValueChange = { dmrid = it },
                            label = { Text("DMR ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = mdcid,
                            onValueChange = { mdcid = it },
                            label = { Text("MDC ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            controller.updateProfile(
                                nickname = nickname,
                                phone = phone,
                                address = address,
                                introduction = introduction,
                                birthday = birthday,
                                sex = sex,
                                dmrid = dmrid.toIntOrNull() ?: 0,
                                mdcid = mdcid,
                                alarmMsg = alarmMsg,
                            )
                            onDismiss()
                        },
                        enabled = !controller.contentLoading,
                    ) {
                        if (controller.contentLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
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
