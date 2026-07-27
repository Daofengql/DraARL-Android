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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.MAIN_NAVIGATION_PAGES
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.pagePosition
import cn.silverdragon.draarl.ui.screens.AccountSecurityScreen
import cn.silverdragon.draarl.ui.screens.DevicesScreen
import cn.silverdragon.draarl.ui.screens.GroupsScreen
import cn.silverdragon.draarl.ui.screens.LoginScreen
import cn.silverdragon.draarl.ui.screens.ProfileScreen
import cn.silverdragon.draarl.ui.screens.RadioScreen
import cn.silverdragon.draarl.ui.screens.SettingsScreen
import cn.silverdragon.draarl.ui.screens.ToolsScreen

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
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F8FF)),
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
                color = Color(0xFF0D47A1),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "正在接入通信网络",
                color = Color(0xFF52657D),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.65f + glowAlpha.value),
            )
        }
    }
}

@Composable
private fun AuthenticatedApp(controller: AppController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()
    val notice = controller.notice
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) {
            snackbarHostState.showSnackbar(notice)
            controller.clearNotice()
        }
    }

    val showBottomBar = controller.page !in setOf(
        AppPage.SETTINGS,
        AppPage.ACCOUNT_SECURITY,
    )

    // 处理系统返回操作
    BackHandler(enabled = controller.page in setOf(AppPage.SETTINGS, AppPage.ACCOUNT_SECURITY)) {
        when (controller.page) {
            AppPage.ACCOUNT_SECURITY -> controller.navigate(AppPage.SETTINGS)
            AppPage.SETTINGS -> controller.navigate(AppPage.PROFILE)
            else -> {}
        }
    }

    val pagesWithOwnScaffold = setOf(AppPage.SETTINGS, AppPage.ACCOUNT_SECURITY)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        bottomBar = {
            if (showBottomBar) {
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
                        AppPage.RADIO -> RadioScreen(controller)
                        AppPage.DEVICES -> DevicesScreen(controller)
                        AppPage.GROUPS -> GroupsScreen(controller)
                        AppPage.TOOLS -> ToolsScreen(controller)
                        AppPage.PROFILE -> ProfileScreen(controller)
                        AppPage.SETTINGS -> SettingsScreen(controller)
                        AppPage.ACCOUNT_SECURITY -> AccountSecurityScreen(controller)
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
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selectedPage == item.page,
                onClick = { onNavigate(item.page) },
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

private fun navigationItem(page: AppPage): NavigationItem = when (page) {
    AppPage.DEVICES -> NavigationItem(page, "设备", Icons.Default.Devices)
    AppPage.GROUPS -> NavigationItem(page, "群组", Icons.Default.Groups)
    AppPage.RADIO -> NavigationItem(page, "PTT", Icons.Default.Mic)
    AppPage.TOOLS -> NavigationItem(page, "工具", Icons.Default.Build)
    AppPage.PROFILE -> NavigationItem(page, "我的", Icons.Default.Person)
    AppPage.SETTINGS, AppPage.ACCOUNT_SECURITY -> error("Secondary pages are not bottom navigation items")
}
