package cn.silverdragon.draarl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.MAIN_NAVIGATION_PAGES
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.data.appDensityFor
import cn.silverdragon.draarl.data.encodeLocationMessage
import cn.silverdragon.draarl.update.AppUpdateInfo
import cn.silverdragon.draarl.update.AppUpdateStatus
import cn.silverdragon.draarl.ui.screens.AccountSecurityScreen
import cn.silverdragon.draarl.ui.screens.AprsSettingsScreen
import cn.silverdragon.draarl.ui.screens.DevicesScreen
import cn.silverdragon.draarl.ui.screens.EditProfileScreen
import cn.silverdragon.draarl.ui.screens.GroupsScreen
import cn.silverdragon.draarl.ui.screens.LoginScreen
import cn.silverdragon.draarl.ui.screens.LocationMapScreen
import cn.silverdragon.draarl.ui.screens.ProfileScreen
import cn.silverdragon.draarl.ui.screens.RadioScreen
import cn.silverdragon.draarl.ui.screens.RadioPresetsScreen
import cn.silverdragon.draarl.ui.screens.SettingsScreen
import cn.silverdragon.draarl.ui.screens.StorageSettingsScreen
import cn.silverdragon.draarl.ui.screens.SystemSettingsScreen
import cn.silverdragon.draarl.ui.screens.ToolsScreen
import cn.silverdragon.draarl.ui.components.DraarlBottomBar
import cn.silverdragon.draarl.ui.components.DraarlBottomBarItem
import cn.silverdragon.draarl.ui.theme.appMotion

@Composable
fun DraarlApp(controller: AppController) {
    val windowSize = LocalWindowInfo.current.containerSize
    val shortestWindowPixels = minOf(windowSize.width, windowSize.height).toFloat()
    val systemFontScale = LocalDensity.current.fontScale
    val appDensity = remember(shortestWindowPixels, controller.appDisplayScale, systemFontScale) {
        Density(
            density = appDensityFor(shortestWindowPixels, controller.appDisplayScale),
            fontScale = systemFontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides appDensity) {
        DraarlAppContent(controller)
    }
}

@Composable
private fun DraarlAppContent(controller: AppController) {
    when {
        controller.initializing -> LoadingScreen()
        !controller.authenticated -> LoginScreen(controller)
        else -> AuthenticatedApp(controller)
    }
}

@Composable
private fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "startup")
    val logoScale = transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoScale",
    )
    val glowAlpha = transition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .scale(logoScale.value)
                .alpha(glowAlpha.value)
                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.draarl_splash_logo),
                contentDescription = null,
                modifier = Modifier.size(108.dp).scale(logoScale.value),
            )
            Text(
                text = "DraARL 麟链",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "正在接入通信网络",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.65f + glowAlpha.value),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AuthenticatedApp(controller: AppController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()
    var radioExtrasExpanded by rememberSaveable { mutableStateOf(false) }
    var mapLocation by remember { mutableStateOf<Wgs84LocationMessage?>(null) }
    val notice = controller.notice
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) {
            snackbarHostState.showSnackbar(notice)
            controller.clearNotice()
        }
    }
    LaunchedEffect(controller.page) {
        if (controller.page != AppPage.RADIO) radioExtrasExpanded = false
    }

    var dismissedAppUpdateVersion by rememberSaveable { mutableStateOf("") }
    val appUpdateInfo = controller.appUpdateInfo
    val appUpdateStatus = controller.appUpdateStatus
    val updateDialogStickyStatuses = setOf(
        AppUpdateStatus.DOWNLOADING,
        AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
        AppUpdateStatus.READY_TO_INSTALL,
    )
    val showAppUpdateDialog = appUpdateInfo != null &&
        appUpdateStatus in setOf(
            AppUpdateStatus.AVAILABLE,
            AppUpdateStatus.DOWNLOADING,
            AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
            AppUpdateStatus.READY_TO_INSTALL,
            AppUpdateStatus.ERROR,
        ) &&
        (
            appUpdateInfo.forceUpdate ||
                dismissedAppUpdateVersion != appUpdateInfo.version ||
                appUpdateStatus in updateDialogStickyStatuses
            )
    appUpdateInfo?.takeIf { showAppUpdateDialog }?.let { update ->
        AppUpdateDialog(
            update = update,
            status = appUpdateStatus,
            message = controller.appUpdateMessage,
            progress = controller.appUpdateProgress,
            onUpdate = controller::downloadAndInstallAppUpdate,
            onDismiss = { dismissedAppUpdateVersion = update.version },
        )
    }

    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = !imeVisible && !radioExtrasExpanded && controller.page !in setOf(
        AppPage.EDIT_PROFILE,
        AppPage.RADIO_PRESETS,
        AppPage.SETTINGS,
        AppPage.SYSTEM_SETTINGS,
        AppPage.ACCOUNT_SECURITY,
        AppPage.STORAGE_SETTINGS,
        AppPage.APRS_SETTINGS,
        AppPage.LOCATION_MAP,
    )

    // 处理系统返回操作
    BackHandler(
        enabled = controller.page in setOf(
            AppPage.EDIT_PROFILE,
            AppPage.RADIO_PRESETS,
            AppPage.SETTINGS,
            AppPage.SYSTEM_SETTINGS,
            AppPage.ACCOUNT_SECURITY,
            AppPage.STORAGE_SETTINGS,
            AppPage.APRS_SETTINGS,
            AppPage.LOCATION_MAP,
        ),
    ) {
        when (controller.page) {
            AppPage.LOCATION_MAP -> controller.navigate(AppPage.RADIO)
            AppPage.ACCOUNT_SECURITY, AppPage.SYSTEM_SETTINGS -> controller.navigate(AppPage.SETTINGS)
            AppPage.STORAGE_SETTINGS -> controller.navigate(AppPage.SETTINGS)
            AppPage.APRS_SETTINGS -> controller.navigate(AppPage.SETTINGS)
            AppPage.EDIT_PROFILE, AppPage.RADIO_PRESETS, AppPage.SETTINGS -> controller.navigate(AppPage.PROFILE)
            else -> {}
        }
    }

    val pagesWithOwnScaffold = setOf(
        AppPage.EDIT_PROFILE,
        AppPage.RADIO_PRESETS,
        AppPage.SETTINGS,
        AppPage.SYSTEM_SETTINGS,
        AppPage.ACCOUNT_SECURITY,
        AppPage.STORAGE_SETTINGS,
        AppPage.APRS_SETTINGS,
        AppPage.LOCATION_MAP,
    )
    val motion = MaterialTheme.appMotion

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                MainBottomBar(selectedPage = controller.page, onNavigate = controller::navigate)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(
                if (controller.page in pagesWithOwnScaffold) {
                    PaddingValues(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
                } else {
                    innerPadding
                }
            )
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = controller.page,
                transitionSpec = {
                    fadeIn(animationSpec = tween(motion.medium)) togetherWith
                        fadeOut(animationSpec = tween(motion.short))
                },
                contentKey = { it },
            ) { page ->
                pageStateHolder.SaveableStateProvider(page.name) {
                    when (page) {
                        AppPage.RADIO -> RadioScreen(
                            controller = controller,
                            extrasExpanded = radioExtrasExpanded,
                            onExtrasExpandedChange = { radioExtrasExpanded = it },
                            onPickLocation = {
                                mapLocation = null
                                controller.navigate(AppPage.LOCATION_MAP)
                            },
                            onOpenLocation = { location ->
                                mapLocation = location
                                controller.navigate(AppPage.LOCATION_MAP)
                            },
                        )
                        AppPage.DEVICES -> DevicesScreen(controller)
                        AppPage.GROUPS -> GroupsScreen(controller)
                        AppPage.TOOLS -> ToolsScreen(controller)
                        AppPage.PROFILE -> ProfileScreen(controller)
                        AppPage.EDIT_PROFILE -> EditProfileScreen(controller)
                        AppPage.RADIO_PRESETS -> RadioPresetsScreen(
                            tools = controller.tools,
                            onBack = { controller.navigate(AppPage.PROFILE) },
                        )
                        AppPage.SETTINGS -> SettingsScreen(controller)
                        AppPage.SYSTEM_SETTINGS -> SystemSettingsScreen(controller)
                        AppPage.ACCOUNT_SECURITY -> AccountSecurityScreen(controller)
                        AppPage.STORAGE_SETTINGS -> StorageSettingsScreen(controller)
                        AppPage.APRS_SETTINGS -> AprsSettingsScreen(controller)
                        AppPage.LOCATION_MAP -> LocationMapScreen(
                            initialLocation = mapLocation,
                            onBack = { controller.navigate(AppPage.RADIO) },
                            onSend = { location ->
                                controller.sendText(encodeLocationMessage(location)).also { sent ->
                                    if (sent) controller.navigate(AppPage.RADIO)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateDialog(
    update: AppUpdateInfo,
    status: AppUpdateStatus,
    message: String,
    progress: Float,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = status == AppUpdateStatus.DOWNLOADING
    val canDismiss = !update.forceUpdate && !busy
    AlertDialog(
        onDismissRequest = {
            if (canDismiss) onDismiss()
        },
        title = {
            Text(if (update.forceUpdate) "必须更新到 ${update.version}" else "发现新版本 ${update.version}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = update.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "当前版本 ${update.currentVersionName}，新版本 ${update.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update.changelog.isNotBlank()) {
                    Text(
                        text = update.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (status) {
                            AppUpdateStatus.ERROR -> MaterialTheme.colorScheme.error
                            AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED) {
                    Text(
                        text = "请在系统页面允许 DraARL 安装未知应用，返回后会继续更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !busy) {
                Text(
                    when (status) {
                        AppUpdateStatus.READY_TO_INSTALL -> "重新打开安装器"
                        AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> "继续更新"
                        AppUpdateStatus.ERROR -> "重试"
                        AppUpdateStatus.DOWNLOADING -> "下载中"
                        else -> "立即更新"
                    },
                )
            }
        },
        dismissButton = if (canDismiss) {
            {
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun MainBottomBar(selectedPage: AppPage, onNavigate: (AppPage) -> Unit) {
    val items = MAIN_NAVIGATION_PAGES.map(::navigationItem)
    DraarlBottomBar(
        items = items.map { item ->
            DraarlBottomBarItem(
                key = item.page.name,
                label = item.label,
                icon = item.icon,
                prominent = item.page == AppPage.RADIO,
            )
        },
        selectedKey = selectedPage.name,
        onSelect = { key -> items.firstOrNull { it.page.name == key }?.page?.let(onNavigate) },
    )
}

private data class NavigationItem(
    val page: AppPage,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun navigationItem(page: AppPage): NavigationItem = when (page) {
    AppPage.DEVICES -> NavigationItem(page, "设备", Icons.Default.Devices)
    AppPage.GROUPS -> NavigationItem(page, "群组", Icons.Default.Groups)
    AppPage.RADIO -> NavigationItem(page, "PTT", Icons.Default.Mic)
    AppPage.TOOLS -> NavigationItem(page, "工具", Icons.Default.Build)
    AppPage.PROFILE -> NavigationItem(page, "我的", Icons.Default.Person)
    AppPage.EDIT_PROFILE,
    AppPage.RADIO_PRESETS,
    AppPage.SETTINGS,
    AppPage.SYSTEM_SETTINGS,
    AppPage.ACCOUNT_SECURITY,
    AppPage.STORAGE_SETTINGS,
    AppPage.APRS_SETTINGS,
    AppPage.LOCATION_MAP,
    -> error("Secondary pages are not bottom navigation items")
}
