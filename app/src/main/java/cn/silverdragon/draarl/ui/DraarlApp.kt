package cn.silverdragon.draarl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
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
import cn.silverdragon.draarl.pagePosition
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

@Composable
fun DraarlApp(controller: AppController) {
    val windowSize = LocalWindowInfo.current.containerSize
    val shortestWindowPixels = minOf(windowSize.width, windowSize.height).toFloat()
    val appDensity = remember(shortestWindowPixels, controller.appDisplayScale) {
        Density(
            density = appDensityFor(shortestWindowPixels, controller.appDisplayScale),
            fontScale = 1f,
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
                    val direction = if (pagePosition(targetState) >= pagePosition(initialState)) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    (
                        slideIntoContainer(
                            towards = direction,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        ) + fadeIn(animationSpec = tween(160))
                    ) togetherWith (
                        slideOutOfContainer(
                            towards = direction,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        ) + fadeOut(animationSpec = tween(160))
                    )
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
internal fun MainBottomBar(selectedPage: AppPage, onNavigate: (AppPage) -> Unit) {
    val items = MAIN_NAVIGATION_PAGES.map(::navigationItem)
    NavigationBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selectedPage == item.page,
                onClick = { onNavigate(item.page) },
                icon = {
                    if (item.page == AppPage.RADIO) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = if (selectedPage == AppPage.RADIO) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            contentColor = if (selectedPage == AppPage.RADIO) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            tonalElevation = if (selectedPage == AppPage.RADIO) 4.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(26.dp))
                            }
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
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
