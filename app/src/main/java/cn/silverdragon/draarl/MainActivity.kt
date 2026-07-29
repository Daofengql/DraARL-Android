package cn.silverdragon.draarl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import cn.silverdragon.draarl.ui.DraarlApp
import cn.silverdragon.draarl.ui.theme.DraarlTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(220L)
                .withEndAction(provider::remove)
                .start()
        }
        enableEdgeToEdge()
        controller = ViewModelProvider(this)[AppController::class.java]
        setContent {
            val darkTheme = controller.appThemeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            DraarlTheme(darkTheme = darkTheme) {
                DraarlApp(controller)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.onAppForegroundChanged(true)
    }

    override fun onStop() {
        controller.onAppForegroundChanged(false)
        super.onStop()
    }
}
