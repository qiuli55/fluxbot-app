package top.qiuli55.fluxbot.nav

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import top.qiuli55.fluxbot.data.MediaLoader
import top.qiuli55.fluxbot.data.Store
import top.qiuli55.fluxbot.screens.AccountScreen
import top.qiuli55.fluxbot.screens.ApprovalRulesScreen
import top.qiuli55.fluxbot.screens.AppsScreen
import top.qiuli55.fluxbot.screens.AuthScreen
import top.qiuli55.fluxbot.screens.BotScreen
import top.qiuli55.fluxbot.screens.BrowserScreen
import top.qiuli55.fluxbot.screens.CalendarConnectorScreen
import top.qiuli55.fluxbot.screens.ChannelsScreen
import top.qiuli55.fluxbot.screens.ChatScreen
import top.qiuli55.fluxbot.screens.CreateScreen
import top.qiuli55.fluxbot.screens.DesktopScreen
import top.qiuli55.fluxbot.screens.EmailConnectorScreen
import top.qiuli55.fluxbot.screens.HomeScreen
import top.qiuli55.fluxbot.screens.LibraryScreen
import top.qiuli55.fluxbot.screens.LocalMediaLoader
import top.qiuli55.fluxbot.screens.MemoryScreen
import top.qiuli55.fluxbot.screens.OutboundDraftsScreen
import top.qiuli55.fluxbot.screens.PageDetailScreen
import top.qiuli55.fluxbot.screens.PagesScreen
import top.qiuli55.fluxbot.screens.SettingsScreen
import top.qiuli55.fluxbot.screens.SharedMemoryScreen
import top.qiuli55.fluxbot.screens.SkillDetailScreen
import top.qiuli55.fluxbot.screens.SkillsScreen
import top.qiuli55.fluxbot.screens.VaultScreen
import top.qiuli55.fluxbot.ui.Flux

sealed interface Route {
    data object Login : Route
    data object Home : Route
    data object Create : Route
    data class Chat(val botId: String) : Route
    data class BotDetail(val botId: String, val tab: Int = 0) : Route
    data class BotDesktop(val botId: String) : Route
    data class Library(val botId: String) : Route
    data object Settings : Route
    data object ApprovalRules : Route
    data object Account : Route
    data object Vault : Route
    data object Apps : Route
    data object MemoryImport : Route
    data object OutboundDrafts : Route
    data object Skills : Route
    data class SkillDetail(val skillId: String) : Route
    data object Channels : Route
    data object Browser : Route
    data object SharedMemory : Route
    data object Pages : Route
    data class PageDetail(val pageId: String) : Route
    data object EmailConnector : Route
    data object CalendarConnector : Route
}

val LocalUi = staticCompositionLocalOf<AppUi> { error("AppUi not provided") }
val LocalStore = staticCompositionLocalOf<Store> { error("Store not provided") }

/**
 * 自建路由栈：login 0 / home 0 / 其余 1–2，
 * 与 fluxbot-demo/js/router.js 的层级保持一致。
 */
class AppUi(val store: Store) {

    /** 页面无关的协程作用域：承担聊天回复等跨页面定时任务。 */
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val stack = mutableStateListOf<Route>()

    /** 最近一次导航方向：true 前进、false 返回。 */
    var forward by mutableStateOf(true)
        private set

    var sheet by mutableStateOf<Sheet?>(null)

    var toast by mutableStateOf<String?>(null)
        private set

    /** 数据版本：底部面板改完数据后自增，页面据此重新拉取。 */
    var dataRevision by mutableStateOf(0)
        private set

    var onHaptic: (() -> Unit)? = null

    init {
        stack.add(if (store.state.loggedIn) Route.Home else Route.Login)
    }

    fun current(): Route = stack.last()

    fun push(route: Route) {
        forward = true
        stack.add(route)
    }

    fun back() {
        if (stack.size > 1) {
            forward = false
            stack.removeAt(stack.lastIndex)
        }
    }

    fun reset(route: Route) {
        forward = true
        stack.clear()
        stack.add(route)
    }

    fun showToast(text: String) {
        toast = text
    }

    fun clearToast() {
        toast = null
    }

    fun haptic(ms: Int = 8) {
        if (store.current().settings.haptics) onHaptic?.invoke()
    }

    fun closeSheet() {
        sheet = null
    }

    fun refreshData() {
        dataRevision++
    }
}

@Composable
fun FluxApp(store: Store) {
    val ui = remember { AppUi(store) }
    val view = LocalView.current
    val context = LocalContext.current
    // 鉴权媒体加载器只在真实 App 里注入；截图脚手架不提供，因而不会联网。
    val mediaLoader = remember(store, context) {
        MediaLoader(context.applicationContext, store.api)
    }
    SideEffect {
        ui.onHaptic = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }

    // 底部面板打开时对页面内容做真实模糊（Android 12+ 的 RenderEffect）。
    val blurRadius by animateDpAsState(
        targetValue = if (ui.sheet != null) 18.dp else 0.dp,
        animationSpec = tween(220),
        label = "sheetBlur",
    )
    val contentBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }

    // 冷启动恢复云账号后按需续期一次；401 会让管理器清空登录态并回到登录页。
    LaunchedEffect(Unit) { store.ensureCloudSession() }

    // 第三方登录成功是异步回跳，可能在任意页面到达；统一在这里弹一次提示。
    LaunchedEffect(store.oauthNotice) {
        store.consumeOAuthNotice()?.let { ui.showToast(it) }
    }

    val loggedIn = store.state.loggedIn
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            if (ui.current() !is Route.Login) ui.reset(Route.Login)
        } else if (ui.current() is Route.Login) {
            ui.reset(Route.Home)
        }
    }

    LaunchedEffect(ui.toast) {
        if (ui.toast != null) {
            delay(1700)
            ui.clearToast()
        }
    }

    BackHandler(enabled = true) {
        when {
            ui.sheet != null -> ui.closeSheet()
            ui.stack.size > 1 -> ui.back()
        }
    }

    CompositionLocalProvider(
        LocalUi provides ui,
        LocalStore provides store,
        LocalMediaLoader provides mediaLoader,
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF020304)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = Flux.Dim.screenMax)
                    .background(Flux.bg),
            ) {
                AnimatedContent(
                    targetState = ui.current(),
                    transitionSpec = {
                        val fwd = ui.forward
                        val enter = slideInHorizontally(tween(280)) { w ->
                            if (fwd) w else (-0.22f * w).toInt()
                        }
                        val exit = slideOutHorizontally(tween(280)) { w ->
                            if (fwd) (-0.22f * w).toInt() else w
                        }
                        (enter + fadeIn(tween(160))) togetherWith (exit + fadeOut(tween(160)))
                    },
                    label = "route",
                    modifier = Modifier.fillMaxSize().then(contentBlur),
                ) { route ->
                    when (route) {
                        Route.Login -> AuthScreen()
                        Route.Home -> HomeScreen()
                        Route.Create -> CreateScreen()
                        is Route.Chat -> ChatScreen(route.botId)
                        is Route.BotDetail -> BotScreen(route.botId, route.tab)
                        is Route.BotDesktop -> DesktopScreen(route.botId)
                        is Route.Library -> LibraryScreen(route.botId)
                        Route.Settings -> SettingsScreen()
                        Route.ApprovalRules -> ApprovalRulesScreen()
                        Route.Account -> AccountScreen()
                        Route.Vault -> VaultScreen()
                        Route.Apps -> AppsScreen()
                        Route.MemoryImport -> MemoryScreen()
                        Route.OutboundDrafts -> OutboundDraftsScreen()
                        Route.Skills -> SkillsScreen()
                        is Route.SkillDetail -> SkillDetailScreen(route.skillId)
                        Route.Channels -> ChannelsScreen()
                        Route.Browser -> BrowserScreen()
                        Route.SharedMemory -> SharedMemoryScreen()
                        Route.Pages -> PagesScreen()
                        is Route.PageDetail -> PageDetailScreen(route.pageId)
                        Route.EmailConnector -> EmailConnectorScreen()
                        Route.CalendarConnector -> CalendarConnectorScreen()
                    }
                }

                ToastBar()
                SheetHost()
            }
        }
    }
}

@Composable
private fun BoxScope.ToastBar() {
    val ui = LocalUi.current
    val text = ui.toast ?: return
    Box(Modifier.align(Alignment.TopCenter).padding(top = 60.dp)) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Flux.panel2)
                .border(1.dp, Color(0xFF2A3036), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(text, fontSize = 12.5.sp, color = Flux.text, lineHeight = 18.sp)
        }
    }
}
