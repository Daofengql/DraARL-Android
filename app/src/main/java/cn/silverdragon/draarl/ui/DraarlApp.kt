package cn.silverdragon.draarl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.MAIN_NAVIGATION_PAGES
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.data.appDensityFor
import cn.silverdragon.draarl.data.encodeLocationMessage
import cn.silverdragon.draarl.pagePosition
import cn.silverdragon.draarl.session.SessionUiState
import cn.silverdragon.draarl.ui.components.AppUpdateFeedback
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlBottomBar
import cn.silverdragon.draarl.ui.components.DraarlBottomBarItem
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.StatusIndicator
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.screens.AccountSecurityScreen
import cn.silverdragon.draarl.ui.screens.AprsSettingsScreen
import cn.silverdragon.draarl.ui.screens.DevicesScreen
import cn.silverdragon.draarl.ui.screens.EditProfileScreen
import cn.silverdragon.draarl.ui.screens.GroupsScreen
import cn.silverdragon.draarl.ui.screens.LocationMapScreen
import cn.silverdragon.draarl.ui.screens.LoginScreen
import cn.silverdragon.draarl.ui.screens.ProfileScreen
import cn.silverdragon.draarl.ui.screens.RadioPresetsScreen
import cn.silverdragon.draarl.ui.screens.RadioScreen
import cn.silverdragon.draarl.ui.screens.SettingsMenuAction
import cn.silverdragon.draarl.ui.screens.SettingsScreen
import cn.silverdragon.draarl.ui.screens.StorageSettingsScreen
import cn.silverdragon.draarl.ui.screens.SystemSettingsAction
import cn.silverdragon.draarl.ui.screens.SystemSettingsScreen
import cn.silverdragon.draarl.ui.screens.SystemSettingsUpdateState
import cn.silverdragon.draarl.ui.screens.ToolsScreen
import cn.silverdragon.draarl.ui.theme.appMotion
import cn.silverdragon.draarl.update.AppUpdateInfo
import cn.silverdragon.draarl.update.AppUpdateStatus

@Composable
fun DraarlApp(controller: AppController) {
    val windowSize = LocalWindowInfo.current.containerSize
    val shortestWindowPixels = minOf(windowSize.width, windowSize.height).toFloat()
    val systemFontScale = LocalDensity.current.fontScale
    val displayScale by remember { derivedStateOf { controller.settings.uiState.appDisplayScale } }
    val appDensity = remember(shortestWindowPixels, displayScale, systemFontScale) {
        Density(
            density = appDensityFor(shortestWindowPixels, displayScale),
            fontScale = systemFontScale
        )
    }
    CompositionLocalProvider(LocalDensity provides appDensity) {
        DraarlAppContent(controller)
    }
}

@Composable
private fun DraarlAppContent(controller: AppController) {
    val session = controller.session
    val sessionGate by remember(session) {
        derivedStateOf(structuralEqualityPolicy()) { session.uiState.sessionGateState() }
    }
    when {
        sessionGate.initializing -> LoadingScreen()
        !sessionGate.authenticated -> LoginScreen(controller)
        else -> AuthenticatedApp(controller)
    }
}

@Immutable
internal data class SessionGateState(val initializing: Boolean, val authenticated: Boolean)

internal fun SessionUiState.sessionGateState(): SessionGateState = SessionGateState(
    initializing = initializing,
    authenticated = authenticated
)

@Composable
internal fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.draarl_splash_logo),
                contentDescription = null,
                modifier = Modifier.size(104.dp)
            )
            Text(
                text = "DraARL 麟链",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            StatusIndicator("正在恢复通信会话", StatusTone.CONNECTING)
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

    AppUpdateHost(controller)

    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = !imeVisible && !radioExtrasExpanded && controller.page !in setOf(
        AppPage.EDIT_PROFILE,
        AppPage.RADIO_PRESETS,
        AppPage.SETTINGS,
        AppPage.SYSTEM_SETTINGS,
        AppPage.ACCOUNT_SECURITY,
        AppPage.STORAGE_SETTINGS,
        AppPage.APRS_SETTINGS,
        AppPage.LOCATION_MAP
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
            AppPage.LOCATION_MAP
        )
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
        AppPage.LOCATION_MAP
    )
    val motion = MaterialTheme.appMotion

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                MainBottomBar(selectedPage = controller.page, onNavigate = controller::navigate)
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    InlineNotice(
                        text = data.visuals.message,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        onDismiss = data::dismiss
                    )
                }
            )
        }
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
                    val direction = if (pagePosition(targetState) >= pagePosition(initialState)) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    (
                        (
                            slideIntoContainer(
                                towards = direction,
                                animationSpec = tween(motion.medium, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(motion.short))
                        ) togetherWith (
                            slideOutOfContainer(
                                towards = direction,
                                animationSpec = tween(motion.medium, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(motion.short))
                        )
                    ).using(
                        SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ -> snap() }
                        )
                    )
                },
                contentKey = { it }
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
                            }
                        )

                        AppPage.DEVICES -> DevicesScreen(controller)

                        AppPage.GROUPS -> GroupsScreen(controller)

                        AppPage.TOOLS -> ToolsScreen(controller)

                        AppPage.PROFILE -> ProfileScreen(controller)

                        AppPage.EDIT_PROFILE -> EditProfileScreen(controller)

                        AppPage.RADIO_PRESETS -> RadioPresetsScreen(
                            tools = controller.tools,
                            onBack = { controller.navigate(AppPage.PROFILE) }
                        )

                        AppPage.SETTINGS -> SettingsScreen { action ->
                            when (action) {
                                SettingsMenuAction.Back -> controller.navigate(AppPage.PROFILE)

                                SettingsMenuAction.OpenAccountSecurity -> {
                                    controller.navigate(AppPage.ACCOUNT_SECURITY)
                                }

                                SettingsMenuAction.OpenSystemSettings -> {
                                    controller.navigate(AppPage.SYSTEM_SETTINGS)
                                }

                                SettingsMenuAction.OpenStorageSettings -> {
                                    controller.navigate(AppPage.STORAGE_SETTINGS)
                                }

                                SettingsMenuAction.OpenAprsSettings -> controller.navigate(AppPage.APRS_SETTINGS)

                                SettingsMenuAction.Logout -> controller.session.logout()
                            }
                        }

                        AppPage.SYSTEM_SETTINGS -> SystemSettingsScreen(
                            settings = controller.settings,
                            userApproved = controller.session.uiState.user?.isApproved == true,
                            update = SystemSettingsUpdateState(
                                currentVersionName = controller.currentAppVersionName,
                                status = controller.appUpdateStatus,
                                info = controller.appUpdateInfo,
                                message = controller.appUpdateMessage,
                                progress = { controller.appUpdateProgress }
                            ),
                            onAction = { action ->
                                when (action) {
                                    SystemSettingsAction.Back -> controller.navigate(AppPage.SETTINGS)

                                    is SystemSettingsAction.ShowNotice -> controller.showNotice(action.message)

                                    SystemSettingsAction.CheckAppUpdate -> controller.checkAppUpdate()

                                    SystemSettingsAction.DownloadAndInstallUpdate -> {
                                        controller.downloadAndInstallAppUpdate()
                                    }
                                }
                            }
                        )

                        AppPage.ACCOUNT_SECURITY -> AccountSecurityScreen(controller)

                        AppPage.STORAGE_SETTINGS -> StorageSettingsScreen(
                            settings = controller.settings,
                            onBack = { controller.navigate(AppPage.SETTINGS) }
                        )

                        AppPage.APRS_SETTINGS -> AprsSettingsScreen(
                            state = controller.aprs.uiState,
                            defaultCallsign = controller.session.uiState.user?.callsign.orEmpty(),
                            onBack = { controller.navigate(AppPage.SETTINGS) },
                            onEvent = controller.aprs::onEvent,
                            onNotice = controller::showNotice
                        )

                        AppPage.LOCATION_MAP -> LocationMapScreen(
                            initialLocation = mapLocation,
                            onBack = { controller.navigate(AppPage.RADIO) },
                            onSend = { location ->
                                controller.sendText(encodeLocationMessage(location)).also { sent ->
                                    if (sent) controller.navigate(AppPage.RADIO)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateHost(controller: AppController) {
    var dismissedVersion by rememberSaveable { mutableStateOf("") }
    val update = controller.appUpdateInfo ?: return
    val status = controller.appUpdateStatus
    val visible = status in setOf(
        AppUpdateStatus.AVAILABLE,
        AppUpdateStatus.DOWNLOADING,
        AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
        AppUpdateStatus.READY_TO_INSTALL,
        AppUpdateStatus.ERROR
    ) && (
        update.forceUpdate ||
            dismissedVersion != update.version ||
            status in setOf(
                AppUpdateStatus.DOWNLOADING,
                AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
                AppUpdateStatus.READY_TO_INSTALL
            )
        )
    if (visible) {
        AppUpdateDialog(
            update = update,
            status = status,
            message = controller.appUpdateMessage,
            progress = { controller.appUpdateProgress },
            onUpdate = controller::downloadAndInstallAppUpdate,
            onDismiss = { dismissedVersion = update.version }
        )
    }
}

@Composable
private fun AppUpdateDialog(
    update: AppUpdateInfo,
    status: AppUpdateStatus,
    message: String,
    progress: () -> Float,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = status == AppUpdateStatus.DOWNLOADING
    val canDismiss = !update.forceUpdate && !busy
    DraarlDialog(
        title = if (update.forceUpdate) "必须更新到 ${update.version}" else "发现新版本 ${update.version}",
        onDismissRequest = {
            if (canDismiss) onDismiss()
        },
        dismissAction = if (canDismiss) DraarlAction("稍后", onDismiss) else null,
        confirmAction = DraarlAction(
            label = when (status) {
                AppUpdateStatus.READY_TO_INSTALL -> "重新打开安装器"
                AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> "继续更新"
                AppUpdateStatus.ERROR -> "重试"
                AppUpdateStatus.DOWNLOADING -> "下载中"
                else -> "立即更新"
            },
            onClick = onUpdate,
            enabled = !busy,
            style = CommandStyle.PRIMARY
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = update.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "当前版本 ${update.currentVersionName}，新版本 ${update.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (update.changelog.isNotBlank()) {
                Text(
                    text = update.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppUpdateFeedback(status = status, message = message)
            if (busy) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun AppUpdateDialogPreview(
    update: AppUpdateInfo,
    status: AppUpdateStatus,
    message: String,
    progress: () -> Float
) {
    AppUpdateDialog(
        update = update,
        status = status,
        message = message,
        progress = progress,
        onUpdate = {},
        onDismiss = {}
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
                prominent = item.page == AppPage.RADIO
            )
        },
        selectedKey = selectedPage.name,
        onSelect = { key -> items.firstOrNull { it.page.name == key }?.page?.let(onNavigate) }
    )
}

private data class NavigationItem(
    val page: AppPage,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
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
    AppPage.LOCATION_MAP
    -> error("Secondary pages are not bottom navigation items")
}
