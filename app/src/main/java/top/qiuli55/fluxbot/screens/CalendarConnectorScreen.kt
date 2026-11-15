package top.qiuli55.fluxbot.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.RemoteCalendarTest
import top.qiuli55.fluxbot.data.RemoteConnector
import top.qiuli55.fluxbot.data.installCalendarConnector
import top.qiuli55.fluxbot.data.testCalendarConnection
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.PrimaryButton
import top.qiuli55.fluxbot.ui.SecondaryButton
import top.qiuli55.fluxbot.ui.SegTabs
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun CalendarConnectorScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var connector by remember { mutableStateOf<RemoteConnector?>(null) }
    var loading by remember { mutableStateOf(true) }
    var modeIndex by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var timeout by remember { mutableIntStateOf(15) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<RemoteCalendarTest?>(null) }
    val modes = listOf("CalDAV", "订阅 ICS")

    LaunchedEffect(Unit) {
        try {
            connector = store.api.listConnectors().firstOrNull { it.id == "calendar" }
            if (connector?.mode == "ics") modeIndex = 1
            url = connector?.host.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "load calendar connector failed", e)
        } finally {
            loading = false
        }
    }

    fun save() {
        if (busy) return
        if (url.isBlank()) {
            ui.showToast("填日历地址")
            return
        }
        if (modeIndex == 0 && (username.isBlank() || password.isBlank())) {
            ui.showToast("CalDAV 需要用户名和密码")
            return
        }
        busy = true
        ui.scope.launch {
            try {
                connector = store.api.installCalendarConnector(
                    mode = if (modeIndex == 0) "caldav" else "ics",
                    url = url,
                    username = username,
                    password = password,
                    timeoutSeconds = timeout,
                )
                password = ""
                ui.showToast("日历已连接")
                ui.refreshData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "install calendar failed", e)
                ui.showToast(e.message ?: "保存失败")
            } finally {
                busy = false
            }
        }
    }

    fun runTest() {
        if (testing) return
        testing = true
        ui.scope.launch {
            try {
                test = store.api.testCalendarConnection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "test calendar failed", e)
                ui.showToast(e.message ?: "测试失败")
            } finally {
                testing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("日历", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            if (loading) {
                V040Loading("正在读取日历配置…")
            } else {
                FluxCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "日历",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight(560),
                                color = Flux.text,
                                modifier = Modifier.weight(1f),
                            )
                            Pill(
                                if (connector?.connected == true) "已连接" else "未连接",
                                if (connector?.connected == true) Flux.accent else Flux.text3,
                                if (connector?.connected == true) Flux.accentSoft else Flux.panel3,
                            )
                        }
                        Text(
                            "不经第三方授权，直接连 CalDAV 或 ICS 订阅。",
                            fontSize = 11.5.sp,
                            color = Flux.text3,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Text(
                    "连接方式",
                    fontSize = 12.sp,
                    color = Flux.text3,
                    modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                )
                SegTabs(modes, modeIndex, { modeIndex = it })

                Spacer(Modifier.height(12.dp))
                FluxCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (modeIndex == 0) "CalDAV 地址" else "ICS 订阅地址",
                            fontSize = 11.5.sp,
                            color = Flux.text3,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        FluxInput(
                            url, { url = it },
                            placeholder = if (modeIndex == 0) {
                                "https://caldav.example.com/dav/"
                            } else {
                                "https://example.com/calendar.ics"
                            },
                        )
                        if (modeIndex == 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "用户名",
                                fontSize = 11.5.sp,
                                color = Flux.text3,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            FluxInput(username, { username = it }, placeholder = "账号")
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "密码",
                                fontSize = 11.5.sp,
                                color = Flux.text3,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            FluxInput(
                                password, { password = it },
                                placeholder = if (connector?.connected == true) "已加密保存，重填可覆盖" else "密码",
                                password = true,
                            )
                        }
                    }
                }

                Text(
                    "请求超时 · $timeout 秒",
                    fontSize = 12.sp,
                    color = Flux.text3,
                    modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp),
                )
                FluxCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Slider(
                            value = timeout.toFloat(),
                            onValueChange = { timeout = it.toInt().coerceIn(1, 60) },
                            valueRange = 1f..60f,
                            steps = 58,
                            colors = SliderDefaults.colors(
                                thumbColor = Flux.accent,
                                activeTrackColor = Flux.accent,
                                inactiveTrackColor = Flux.panel3,
                                activeTickColor = Flux.accent.copy(alpha = 0.3f),
                                inactiveTickColor = Flux.line,
                            ),
                        )
                        Text(
                            "1–60 秒。网络慢的日历服务可以调大；超时后这次请求算失败，不会卡住 Bot。",
                            fontSize = 10.5.sp,
                            color = Flux.text4,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }

                test?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    FluxCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                if (result.ok) "连接测试通过" else "连接测试未通过",
                                fontSize = 13.sp,
                                fontWeight = FontWeight(560),
                                color = if (result.ok) Flux.accent else Flux.danger,
                            )
                            Text(
                                result.summary(),
                                fontSize = 11.5.sp,
                                color = Flux.text3,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }

                TabHint("超时时间只在安装时写入服务器凭据；当前公开状态不返回该值，所以这里不显示上次测试时间。")

                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (busy) "保存中…" else "保存并连接", enabled = !busy) { save() }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(if (testing) "测试中…" else "测试连接") { runTest() }
            }
        }
    }
}
