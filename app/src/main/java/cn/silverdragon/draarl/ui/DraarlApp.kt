package cn.silverdragon.draarl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val approved = controller.user?.isApproved == true
    val tabs = buildList {
        add(NavigationItem(AppPage.DASHBOARD, "概览", { Icon(Icons.Default.Dashboard, null) }))
        if (approved) {
            add(NavigationItem(AppPage.RADIO, "电台", { Icon(Icons.Default.Radio, null) }))
            add(NavigationItem(AppPage.DEVICES, "设备", { Icon(Icons.Default.Devices, null) }))
            add(NavigationItem(AppPage.GROUPS, "群组", { Icon(Icons.Default.Groups, null) }))
        }
        add(NavigationItem(AppPage.PROFILE, "我的", { Icon(Icons.Default.Person, null) }))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle(controller.page)) },
                navigationIcon = {
                    if (controller.page == AppPage.RECORDS) {
                        IconButton(onClick = controller::goBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (controller.page != AppPage.PROFILE && controller.page != AppPage.RADIO) {
                        IconButton(onClick = controller::refreshAll) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (controller.page != AppPage.RECORDS) {
                NavigationBar {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = controller.page == item.page,
                            onClick = { controller.navigate(item.page) },
                            icon = item.icon,
                            label = { Text(item.label) },
                        )
                    }
                }
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

private data class NavigationItem(
    val page: AppPage,
    val label: String,
    val icon: @Composable () -> Unit,
)

private fun pageTitle(page: AppPage): String = when (page) {
    AppPage.RADIO -> "在线收发"
    AppPage.DASHBOARD -> "仪表盘"
    AppPage.DEVICES -> "设备管理"
    AppPage.GROUPS -> "群组管理"
    AppPage.PROFILE -> "个人中心"
    AppPage.RECORDS -> "通信记录"
}
