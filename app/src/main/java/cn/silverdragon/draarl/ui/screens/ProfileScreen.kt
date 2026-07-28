package cn.silverdragon.draarl.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage

@Composable
fun ProfileScreen(controller: AppController) {
    val user = controller.user ?: return
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            ProfileHeader(
                user = user,
                onAvatarClick = { avatarLauncher.launch("image/*") },
                onEditClick = { controller.navigate(AppPage.EDIT_PROFILE) },
                onSettingsClick = { controller.navigate(AppPage.SETTINGS) },
                onPresetsClick = { controller.navigate(AppPage.RADIO_PRESETS) },
            )
        }
        item {
            ProfileInfoSection(user = user)
        }
        item { ProfileOverview(controller.dashboard) }
        item { Spacer(Modifier.height(8.dp)) }
    }
    selectedImageUri?.let { uri ->
        AvatarCropDialog(
            imageUri = uri,
            onDismiss = { selectedImageUri = null },
            onConfirm = { croppedBytes ->
                selectedImageUri = null
                controller.profile.uploadAvatar(croppedBytes, "avatar_${System.currentTimeMillis()}.jpg")
            },
        )
    }
}
