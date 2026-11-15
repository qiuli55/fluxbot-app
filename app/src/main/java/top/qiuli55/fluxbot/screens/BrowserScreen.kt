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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import top.qiuli55.fluxbot.data.RemoteBrowserStatus
import top.qiuli55.fluxbot.data.browserStatus
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.InfoLine
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun BrowserScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var status by remember { mutableStateOf<RemoteBrowserStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            status = store.api.browserStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "browserStatus failed", e)
            error = e.message ?: "浏览器状态加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("浏览器", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            BrowserContent(status, loading, error, onRetry = { refresh++ })
        }
    }
}

@Composable
internal fun BrowserContent(
    status: RemoteBrowserStatus?,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
) {
    when {
        loading -> V040Loading("正在探测浏览器…")
        status == null -> V040Error(error.ifBlank { "浏览器状态为空" }, onRetry)
        else -> {
            FluxCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (status.configured) "已接入" else "未接入",
                            fontSize = 16.sp,
                            fontWeight = FontWeight(560),
                            color = Flux.text,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(
                            if (!status.configured) "未配置"
                            else if (status.reachable) "可访问" else "不可访问",
                            when {
                                !status.configured -> Flux.text3
                                status.reachable -> Flux.accent
                                else -> Flux.danger
                            },
                            when {
                                !status.configured -> Flux.panel3
                                status.reachable -> Flux.accentSoft
                                else -> Flux.dangerSoft
                            },
                        )
                    }
                    Text(
                        if (status.configured) {
                            "Bot 可以在任务里打开网页、读取页面内容。"
                        } else {
                            "服务器还没有配置浏览器 MCP，Bot 暂时不能自己打开网页。"
                        },
                        fontSize = 12.sp,
                        color = Flux.text2,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }

            if (status.configured) {
                Spacer(Modifier.height(12.dp))
                FluxCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        InfoLine("服务名", status.serverName)
                        InfoLine("类型", status.type.ifBlank { "—" })
                        InfoLine("地址", status.url.ifBlank { "—" })
                        InfoLine(
                            "探测",
                            listOfNotNull(
                                status.statusCode.takeIf { it > 0 }?.let { "HTTP $it" },
                                status.latencyMs.takeIf { it > 0 }?.let { "${it}ms" },
                                status.lastProbeAt.takeIf { it.isNotBlank() }
                                    ?.let { relativeTime(it) },
                            ).joinToString(" · ").ifBlank { "还没有探测结果" },
                        )
                        InfoLine(
                            "超时",
                            if (status.timeoutSeconds > 0) "${status.timeoutSeconds} 秒" else "—",
                        )
                    }
                }
            }

            if (status.error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    status.error,
                    fontSize = 12.sp,
                    color = Flux.danger,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }

            Text(
                "接入步骤",
                fontSize = 12.sp,
                color = Flux.text3,
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
            )
            FluxCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Step("1", "在服务器 .env 里填 FLUX_BROWSER_MCP_URL 与可选的鉴权头。")
                    Step("2", "重启服务器，回到这里确认状态变成「已接入」。")
                    Step("3", "在对话里让 Bot 查资料，它会自己调用浏览器工具。")
                }
            }
            TabHint("服务器暂未开放「镜像地址」与「权限模式」字段，这里只展示真实探测到的服务信息。")
        }
    }
}

@Composable
private fun Step(index: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            index,
            fontSize = 11.sp,
            color = Flux.accent,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(text, fontSize = 12.sp, color = Flux.text2, lineHeight = 18.sp)
    }
}
