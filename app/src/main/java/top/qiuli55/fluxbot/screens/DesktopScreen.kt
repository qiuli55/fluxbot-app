package top.qiuli55.fluxbot.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONObject
import top.qiuli55.fluxbot.data.BotDesktop
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.IconBtn
import top.qiuli55.fluxbot.ui.Pill
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 网页通过它把连接状态回吐给原生，并借用原生能力（剪贴板/键盘/横屏/全屏）。 */
private class HostBridge(
    private val handler: Handler,
    private val context: Context,
    private val webView: () -> WebView?,
    private val onUpdate: (text: String, control: Boolean) -> Unit,
    private val onLog: (String) -> Unit,
    private val onFullscreen: (Boolean) -> Unit,
    private val onLandscape: (Boolean) -> Unit,
) {
    @JavascriptInterface
    fun onState(json: String) {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val text = obj.optString("text")
        val control = obj.optBoolean("control")
        handler.post { onUpdate(text, control) }
    }

    @JavascriptInterface
    fun onLog(msg: String) {
        onLog(msg)
    }

    /** 桌面复制 → 手机剪贴板 */
    @JavascriptInterface
    fun setClipboard(text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("fluxbot-desktop", text))
        }
    }

    /** 手机剪贴板 → 桌面（页面拿去做 clipboardPasteFrom） */
    @JavascriptInterface
    fun getClipboard(): String = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount <= 0) "" else clip.getItemAt(0).coerceToText(context).toString()
    }.getOrDefault("")

    /** 页面内 focus 拉不起软键盘时，由原生强制弹一次 */
    @JavascriptInterface
    fun showKeyboard() {
        handler.post {
            val wv = webView() ?: return@post
            wv.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            wv.postDelayed({ imm.showSoftInput(wv, InputMethodManager.SHOW_IMPLICIT) }, 120)
        }
    }

    @JavascriptInterface
    fun setFullscreen(on: Int) {
        handler.post { onFullscreen(on != 0) }
    }

    @JavascriptInterface
    fun setLandscape(on: Int) {
        handler.post { onLandscape(on != 0) }
    }
}

/** 真机排查用：把页面日志由原生线程 POST 到 bridge /log（比页面 fetch 更可靠）。 */
private class RemoteLog(private val base: String, private val key: String, private val tag: String) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "flux-desktop-log").apply { isDaemon = true }
    }

    fun send(msg: String) {
        val line = msg.take(800)
        executor.execute {
            runCatching {
                val conn = URL("$base/log?tag=$tag").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("X-Flux-Key", key)
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.outputStream.use { it.write(line.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}

/** 原生 HTTP 兜底：WebView 里 fetch 被拦时，页面改走这里（仍直连，不经中转）。 */
private class NativeHttp(
    private val handler: Handler,
    private val webView: () -> WebView?,
) {
    private val executor = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "flux-desktop-http").apply { isDaemon = true }
    }

    @JavascriptInterface
    fun request(id: String, method: String, url: String, body: String) {
        executor.execute {
            var ok = "0"
            var text = ""
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = if (method.isBlank()) "GET" else method.uppercase()
                conn.connectTimeout = 12000
                conn.readTimeout = 90000
                if (body.isNotEmpty()) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val payload = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code in 200..299) {
                    ok = "1"
                    text = payload
                } else {
                    text = "HTTP $code " + payload.take(200)
                }
            }.onFailure { text = err(it) }
            val js = "window.Flux && window.Flux.__http(" + JSONObject.quote(id) + "," +
                JSONObject.quote(ok) + "," + JSONObject.quote(text.take(4000)) + ")"
            handler.post { webView()?.evaluateJavascript(js, null) }
        }
    }

    private fun err(t: Throwable): String =
        (t.javaClass.simpleName + ": " + (t.message ?: "")).take(160)
}

/** 真机排查用：原生侧测 DNS / TCP / HTTPS，结果写回页面诊断条。 */
private class NetProbe(
    private val bridge: String,
    private val gateway: String,
    private val key: String,
    private val version: String,
) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "flux-desktop-probe").apply { isDaemon = true }
    }

    fun run(report: (String) -> Unit) {
        executor.execute {
            val bridgeHost = runCatching { URL(bridge).host }.getOrDefault(bridge)
            val gatewayHost = runCatching { URL("https://$gateway").host }.getOrDefault(gateway)
            report(version)
            report("dns 桥: " + dns(bridgeHost))
            report("tcp 桥: " + tcp(bridgeHost))
            report("https 桥: " + health())
            report("dns 网关: " + dns(gatewayHost))
            report("tcp 网关: " + tcp(gatewayHost))
        }
    }

    private fun dns(host: String): String = runCatching {
        val t0 = System.currentTimeMillis()
        val ips = java.net.InetAddress.getAllByName(host).joinToString(",") { it.hostAddress ?: "?" }
        "$ips ${System.currentTimeMillis() - t0}ms"
    }.getOrElse { err(it) }

    private fun tcp(host: String): String = runCatching {
        val t0 = System.currentTimeMillis()
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, 443), 8000) }
        "ok ${System.currentTimeMillis() - t0}ms"
    }.getOrElse { err(it) }

    private fun health(): String = runCatching {
        val t0 = System.currentTimeMillis()
        val conn = URL("$bridge/health").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("X-Flux-Key", key)
        val code = conn.responseCode
        conn.disconnect()
        "$code ${System.currentTimeMillis() - t0}ms"
    }.getOrElse { err(it) }

    private fun err(t: Throwable): String =
        (t.javaClass.simpleName + ": " + (t.message ?: "")).take(140)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DesktopScreen(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId)
    val profile = remember(botId) { BotDesktop.profile(botId) }
    val view = LocalView.current
    val holder = remember { arrayOfNulls<WebView>(1) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val remoteLog = remember { RemoteLog(BotDesktop.BRIDGE, BotDesktop.BRIDGE_KEY, profile) }
    val appCtx = LocalContext.current.applicationContext
    val version = remember {
        runCatching {
            val pi = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            "ver ${pi.versionName}/${pi.versionCode}"
        }.getOrDefault("ver ?")
    }
    val probe = remember { NetProbe(BotDesktop.BRIDGE, BotDesktop.HOST, BotDesktop.BRIDGE_KEY, version) }

    var state by remember { mutableStateOf("连接中…") }
    var control by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    val activity = remember(view) { view.context.findActivity() }

    /* 布局变化（横屏/全屏/键盘）后把 WebView 实际尺寸告诉页面，再让画面重算 */
    fun refitNow(w: Int = 0, h: Int = 0) {
        handler.postDelayed({
            val wv = holder[0] ?: return@postDelayed
            if (w > 0 && h > 0) {
                wv.evaluateJavascript("window.Flux && window.Flux.setViewport($w, $h)", null)
            } else {
                wv.evaluateJavascript("window.Flux && window.Flux.refit()", null)
            }
        }, 350)
    }

    val connectJs = remember(botId) {
        val args = listOf(
            BotDesktop.HOST,
            BotDesktop.TOKEN,
            profile,
            BotDesktop.BRIDGE,
            BotDesktop.BRIDGE_KEY,
            BotDesktop.CONFIG_URL,
        ).joinToString(",") { JSONObject.quote(it) }
        "window.Flux && window.Flux.connect($args)"
    }

    /* 切后台再回前台时 WebView 的视口可能已经变了，通知页面重排浮球/工具面板 */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                holder[0]?.evaluateJavascript("window.Flux && window.Flux.resume()", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        // 真机排查：部分 WebView 前台被系统判成 hidden，合成被暂停，画面不上屏。定期强制重绘。
        val tick = object : Runnable {
            override fun run() {
                holder[0]?.invalidate()
                handler.postDelayed(this, 1500)
            }
        }
        handler.postDelayed(tick, 1500)
        onDispose {
            handler.removeCallbacks(tick)
            view.keepScreenOn = false
            holder[0]?.evaluateJavascript("window.Flux && window.Flux.disconnect()", null)
            // 退出页面时恢复正常方向与系统栏，避免影响其它页面
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /* 横屏：运行时切换方向（Manifest 里 portrait 只是默认值） */
    DisposableEffect(landscape) {
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        refitNow()
        onDispose {}
    }

    /* 全屏：隐藏系统栏，页面里自带「退出全屏」按钮兜底 */
    DisposableEffect(fullscreen) {
        val win = activity?.window
        if (win != null) {
            val insets = WindowCompat.getInsetsController(win, win.decorView)
            insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) {
                insets.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insets.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        refitNow()
        onDispose {}
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).background(Flux.bg)
                    .padding(start = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(FIcon.Back) { ui.back() }
                Column(Modifier.weight(1f)) {
                    Text(
                        bot?.name ?: "Bot 桌面",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight(560),
                        color = Flux.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("$profile · $version", fontSize = 10.5.sp, color = Flux.text4, maxLines = 1)
                }
                Pill(
                    state,
                    color = if (control) Flux.accent else Flux.text2,
                    bg = if (control) Flux.accentSoft else Flux.panel2,
                )
                IconBtn(FIcon.Help) { ui.sheet = Sheet.UseComputer }
                IconBtn(FIcon.Refresh) {
                    state = "重新连接…"
                    holder[0]?.evaluateJavascript("window.Flux && window.Flux.reconnect()", null)
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged { refitNow(it.width, it.height) },
            factory = { ctx ->
                var loaded = false
                WebView(ctx).apply {
                    holder[0] = this
                    setBackgroundColor(android.graphics.Color.BLACK)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowFileAccessFromFileURLs = true
                        // file:// 页面要 fetch / WebSocket 到 https，Android WebView 必须显式放行
                        allowUniversalAccessFromFileURLs = true
                        mediaPlaybackRequiresUserGesture = false
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        textZoom = 100
                    }
                    addJavascriptInterface(
                        HostBridge(
                            handler,
                            appCtx,
                            { holder[0] },
                            onUpdate = { text, ctl -> state = text; control = ctl },
                            onLog = { remoteLog.send("[web] $it") },
                            onFullscreen = { fullscreen = it },
                            onLandscape = { landscape = it },
                        ),
                        "FluxHost",
                    )
                    addJavascriptInterface(NativeHttp(handler) { holder[0] }, "FluxNet")
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            remoteLog.send("[console] ${msg.message()} @${msg.lineNumber()}")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView, url: String) {
                            if (loaded) return
                            loaded = true
                            probe.run { text ->
                                remoteLog.send("[probe] $text")
                                handler.post {
                                    v.evaluateJavascript(
                                        "window.Flux && window.Flux.diag(" + JSONObject.quote(text) + ")",
                                        null,
                                    )
                                }
                            }
                            v.evaluateJavascript(connectJs, null)
                            handler.postDelayed({
                                holder[0]?.let { w ->
                                    remoteLog.send(
                                        "[native] shown=${w.isShown} win=${w.windowVisibility}" +
                                            " vis=${w.visibility} ${w.width}x${w.height}",
                                    )
                                }
                            }, 5000)
                        }

                        override fun onReceivedError(
                            v: WebView,
                            req: WebResourceRequest,
                            err: WebResourceError,
                        ) {
                            if (req.isForMainFrame) {
                                remoteLog.send("[load] ${err.errorCode} ${err.description}")
                            }
                        }
                    }
                    loadUrl("file:///android_asset/desktop/index.html")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
