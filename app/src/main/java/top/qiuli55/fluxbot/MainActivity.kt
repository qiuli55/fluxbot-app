package top.qiuli55.fluxbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import top.qiuli55.fluxbot.data.Store
import top.qiuli55.fluxbot.nav.FluxApp
import top.qiuli55.fluxbot.ui.FluxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF050608.toInt()
        window.navigationBarColor = 0xFF050608.toInt()
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.isAppearanceLightStatusBars = false
        insets.isAppearanceLightNavigationBars = false

        val store = Store(applicationContext)
        setContent {
            FluxTheme {
                FluxApp(store)
            }
        }
    }
}
