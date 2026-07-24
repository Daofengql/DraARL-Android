package cn.silverdragon.draarl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.ui.screens.DashboardScreen
import cn.silverdragon.draarl.ui.screens.DevicesScreen
import cn.silverdragon.draarl.ui.screens.GroupsScreen
import cn.silverdragon.draarl.ui.screens.LoginScreen
import cn.silverdragon.draarl.ui.screens.ProfileScreen
import cn.silverdragon.draarl.ui.screens.RadioScreen
import cn.silverdragon.draarl.ui.screens.RecordsScreen

@Composable
fun DraarlApp(controller: AppController) {
    when {
        controller.initializing -> LoadingScreen()
        !controller.authenticated -> LoginScreen(controller)
        else -> AuthenticatedApp(controller)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthenticatedApp(controller: AppController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = controller.notice
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) {
            snackbarHostState.showSnackbar(notice)
            controller.clearNotice()
        }
    }
    BackHandler(controller.page == AppPage.RECORDS) { controller.goBack() }

    Scaffold(
        bottomBar = {
            if (controller.page != AppPage.RECORDS) {
                MainBottomBar(controller)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (controller.page) {
                AppPage.RADIO -> RadioScreen(controller)
                AppPage.DASHBOARD -> DashboardScreen(controller)
                AppPage.DEVICES -> DevicesScreen(controller)
                AppPage.GROUPS -> GroupsScreen(controller)
                AppPage.PROFILE -> ProfileScreen(controller)
                AppPage.RECORDS -> RecordsScreen(controller)
            }
        }
    }
}

@Composable
private fun MainBottomBar(controller: AppController) {
    val items = listOf(
        NavigationItem(AppPage.DASHBOARD, "概览", Icons.Default.Dashboard),
        NavigationItem(AppPage.DEVICES, "设备", Icons.Default.Devices),
        NavigationItem(AppPage.RADIO, "PTT", Icons.Default.Mic),
        NavigationItem(AppPage.GROUPS, "群组", Icons.Default.Groups),
        NavigationItem(AppPage.PROFILE, "我的", Icons.Default.Person),
    )
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = controller.page == item.page,
                onClick = { controller.navigate(item.page) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

private data class NavigationItem(
    val page: AppPage,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
