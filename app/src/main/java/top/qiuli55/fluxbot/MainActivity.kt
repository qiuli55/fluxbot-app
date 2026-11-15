package top.qiuli55.fluxbot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import top.qiuli55.fluxbot.data.Store
import top.qiuli55.fluxbot.nav.FluxApp
import top.qiuli55.fluxbot.ui.FluxTheme

class MainActivity : ComponentActivity() {

    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF050608.toInt()
        window.navigationBarColor = 0xFF050608.toInt()
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.isAppearanceLightStatusBars = false
        insets.isAppearanceLightNavigationBars = false

        store = Store(applicationContext)
        // 冷启动时系统会把 flux://auth 回跳作为启动 Intent 交进来。
        handleOAuthIntent(intent)
        setContent {
            FluxTheme {
                FluxApp(store)
            }
        }
    }

    /** singleTask 下浏览器回跳不会新建 Activity，而是在既有实例上触发这里。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) store.onHostResumed()
    }

    private fun handleOAuthIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        store.handleOAuthRedirect(intent.dataString)
    }
}
