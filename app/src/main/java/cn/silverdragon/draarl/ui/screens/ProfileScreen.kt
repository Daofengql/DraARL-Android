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
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.data.User

@Composable
fun ProfileScreen(controller: AppController) {
    val user = controller.session.uiState.user ?: return
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
    }

    ProfileContent(
        user = user,
        dashboard = controller.dashboard,
        onAvatarClick = { avatarLauncher.launch("image/*") },
        onNavigate = controller::navigate
    )
    selectedImageUri?.let { uri ->
        AvatarCropDialog(
            imageUri = uri,
            onDismiss = { selectedImageUri = null },
            onConfirm = { croppedBytes ->
                selectedImageUri = null
                controller.profile.uploadAvatar(croppedBytes, "avatar_${System.currentTimeMillis()}.jpg")
            }
        )
    }
}

@Composable
internal fun ProfileContent(
    user: User,
    dashboard: DashboardData,
    onAvatarClick: () -> Unit,
    onNavigate: (AppPage) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            ProfileHeader(
                user = user,
                onAvatarClick = onAvatarClick,
                onEditClick = { onNavigate(AppPage.EDIT_PROFILE) },
                onSettingsClick = { onNavigate(AppPage.SETTINGS) },
                onPresetsClick = { onNavigate(AppPage.RADIO_PRESETS) }
            )
        }
        item {
            ProfileInfoSection(user = user)
        }
        item { ProfileOverview(dashboard) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
