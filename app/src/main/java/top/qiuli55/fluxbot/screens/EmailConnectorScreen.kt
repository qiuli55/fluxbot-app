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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import top.qiuli55.fluxbot.data.RemoteConnector
import top.qiuli55.fluxbot.data.RemoteEmailTest
import top.qiuli55.fluxbot.data.installEmailConnector
import top.qiuli55.fluxbot.data.testEmailConnection
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.PrimaryButton
import top.qiuli55.fluxbot.ui.SecondaryButton
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun EmailConnectorScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var connector by remember { mutableStateOf<RemoteConnector?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var imapSsl by remember { mutableStateOf(true) }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var smtpSsl by remember { mutableStateOf(true) }
    var smtpStarttls by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fromAddress by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<RemoteEmailTest?>(null) }

    LaunchedEffect(Unit) {
        try {
            connector = store.api.listConnectors().firstOrNull { it.id == "email" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "load email connector failed", e)
            loadError = e.message ?: "读取邮箱连接状态失败"
        } finally {
            loading = false
        }
    }

    fun save() {
        if (busy) return
        val imapP = imapPort.trim().toIntOrNull()
        val smtpP = smtpPort.trim().toIntOrNull()
        if (imapHost.isBlank() && smtpHost.isBlank()) {
            ui.showToast("至少填一个 IMAP 或 SMTP 服务器")
            return
        }
        if (imapHost.isNotBlank() && (imapP == null || imapP !in 1..65535)) {
            ui.showToast("IMAP 端口需要 1–65535")
            return
        }
        if (smtpHost.isNotBlank() && (smtpP == null || smtpP !in 1..65535)) {
            ui.showToast("SMTP 端口需要 1–65535")
            return
        }
        if (username.isBlank() || password.isBlank()) {
            ui.showToast("用户名和应用专用密码都要填")
            return
        }
        busy = true
        ui.scope.launch {
            try {
                connector = store.api.installEmailConnector(
                    imapHost = imapHost,
                    imapPort = imapP ?: 993,
                    imapSsl = imapSsl,
                    smtpHost = smtpHost,
                    smtpPort = smtpP ?: 465,
                    smtpSsl = smtpSsl,
                    smtpStarttls = smtpStarttls,
                    username = username,
                    password = password,
                    fromAddress = fromAddress,
                )
                password = ""
                ui.showToast("邮箱已连接")
                ui.refreshData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "install email connector failed", e)
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
                test = store.api.testEmailConnection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "test email failed", e)
                ui.showToast(e.message ?: "测试失败")
            } finally {
                testing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("邮箱", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            when {
                loading -> V040Loading("正在读取邮箱配置…")
                else -> {
                    if (loadError.isNotBlank()) V040Error(loadError) { loadError = "" }
                    FluxCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "邮箱 IMAP / SMTP",
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
                                if (connector?.connected == true) {
                                    "账号：${connector?.account.orEmpty().ifBlank { "已保存" }}"
                                } else {
                                    "不经第三方授权，直接连你自己的邮箱。"
                                },
                                fontSize = 11.5.sp,
                                color = Flux.text3,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    Text(
                        "收信 · IMAP",
                        fontSize = 12.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
                    FluxCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Label("服务器")
                            FluxInput(imapHost, { imapHost = it }, placeholder = "imap.example.com")
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Label("端口")
                                    FluxInput(
                                        imapPort, { imapPort = it },
                                        placeholder = "993", center = true,
                                        keyboardType = KeyboardType.Number,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Label("SSL")
                                    Row(
                                        Modifier.fillMaxWidth().height(50.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        FluxSwitch(imapSsl) { imapSsl = it }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "发信 · SMTP",
                        fontSize = 12.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
                    FluxCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Label("服务器")
                            FluxInput(smtpHost, { smtpHost = it }, placeholder = "smtp.example.com")
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Label("端口")
                                    FluxInput(
                                        smtpPort, { smtpPort = it },
                                        placeholder = "465", center = true,
                                        keyboardType = KeyboardType.Number,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Label("SSL")
                                    Row(
                                        Modifier.fillMaxWidth().height(50.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        FluxSwitch(smtpSsl) {
                                            smtpSsl = it
                                            if (it) smtpStarttls = false
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("STARTTLS", fontSize = 12.sp, color = Flux.text2)
                                    Text(
                                        "SSL 与 STARTTLS 只能开一个",
                                        fontSize = 10.5.sp,
                                        color = Flux.text4,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                FluxSwitch(smtpStarttls) {
                                    smtpStarttls = it
                                    if (it) smtpSsl = false
                                }
                            }
                        }
                    }

                    Text(
                        "登录",
                        fontSize = 12.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
                    FluxCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Label("用户名")
                            FluxInput(username, { username = it }, placeholder = "you@example.com")
                            Spacer(Modifier.height(10.dp))
                            Label("应用专用密码")
                            FluxInput(
                                password, { password = it },
                                placeholder = if (connector?.connected == true) "已加密保存，重填可覆盖" else "应用专用密码",
                                password = true,
                            )
                            Spacer(Modifier.height(10.dp))
                            Label("发件地址（SMTP 用）")
                            FluxInput(
                                fromAddress, { fromAddress = it },
                                placeholder = "you@example.com（用户名不是邮箱时必填）",
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

                    TabHint("新邮件每 60 秒轮询一次，命中 email.received 的例程会被触发。密码只进服务器加密存储，App 不会读回。")

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (busy) "保存中…" else "保存并连接", enabled = !busy) { save() }
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(if (testing) "测试中…" else "测试连接") { runTest() }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = Flux.text3,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
