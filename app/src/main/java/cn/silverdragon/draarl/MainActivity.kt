package cn.silverdragon.draarl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import cn.silverdragon.draarl.ui.DraarlApp
import cn.silverdragon.draarl.ui.theme.DraarlTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = ViewModelProvider(this)[AppController::class.java]
        setContent {
            DraarlTheme {
                DraarlApp(controller)
            }
        }
    }
}
