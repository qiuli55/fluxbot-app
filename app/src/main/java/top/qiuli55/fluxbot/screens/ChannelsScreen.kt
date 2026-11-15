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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import top.qiuli55.fluxbot.data.RemoteChannelStatus
import top.qiuli55.fluxbot.data.channelStatusText
import top.qiuli55.fluxbot.data.channelStatuses
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.InfoLine
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.V040Empty
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun ChannelsScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var channels by remember { mutableStateOf<List<RemoteChannelStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            channels = store.api.channelStatuses()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "channelStatuses failed", e)
            error = e.message ?: "通道状态加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("通道", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            ChannelsContent(
                channels = channels,
                loading = loading,
                error = error,
                onRetry = { refresh++ },
            )
        }
    }
}

@Composable
internal fun ChannelsContent(
    channels: List<RemoteChannelStatus>,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
) {
    when {
        loading -> V040Loading("正在读取通道状态…")
        error.isNotBlank() -> V040Error(error, onRetry)
        channels.isEmpty() -> V040Empty("服务器没有返回任何通道。\n在服务器 .env 里配置飞书、企业微信或钉钉后，这里会显示接入状态。")
        else -> {
            channels.forEachIndexed { index, channel ->
                ChannelCard(channel)
                if (index != channels.lastIndex) Spacer(Modifier.height(12.dp))
            }
            Text(
                "通道凭据与回调地址只存在于服务器 .env；App 不保存密钥，也无法在这里修改归属 Bot、回调地址或消息加密方式。",
                fontSize = 11.sp,
                color = Flux.text4,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: RemoteChannelStatus) {
    FluxCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight(560),
                    color = Flux.text,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    channelStatusText(channel.configStatus),
                    if (channel.connected) Flux.accent else Flux.text3,
                    if (channel.connected) Flux.accentSoft else Flux.panel3,
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoLine("收消息", if (channel.inboundConfigured) "已配置" else "未配置")
            InfoLine("发消息", if (channel.outboundConfigured) "已配置" else "未配置")
            InfoLine(
                "最近收",
                channel.lastReceiveAt.takeIf { it.isNotBlank() }?.let { relativeTime(it) } ?: "无记录",
            )
            InfoLine(
                "最近发",
                channel.lastSendAt.takeIf { it.isNotBlank() }?.let { relativeTime(it) } ?: "无记录",
            )
            if (channel.missingEnv.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "缺少环境变量：${channel.missingEnv.joinToString("、")}",
                    fontSize = 11.sp,
                    color = Flux.warn,
                    lineHeight = 17.sp,
                )
            }
            if (channel.recentErrors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                channel.recentErrors.take(2).forEach { item ->
                    Text(
                        listOfNotNull(
                            item.time.takeIf { it.isNotBlank() },
                            item.message.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        fontSize = 10.5.sp,
                        color = Flux.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
