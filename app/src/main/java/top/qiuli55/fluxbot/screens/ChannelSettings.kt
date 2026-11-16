package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.RemoteChannelLink
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.SectionLabel
import top.qiuli55.fluxbot.ui.SecondaryButton

/**
 * 设置页的「通道」分区：四个通道的接入状态都收在这里，不再单独开页。
 *
 * 已连接时显示「已连接 · Bot 名」和「断开」；未连接时显示「连接」，
 * 服务器没配置时只显示「服务器未配置」，点一下行才展开缺失的环境变量。
 */
@Composable
fun ChannelSettingsSection(
    links: List<RemoteChannelLink>,
    loading: Boolean,
    error: String,
    expanded: String,
    onExpand: (String) -> Unit,
    onConnect: (RemoteChannelLink) -> Unit,
    onDisconnect: (RemoteChannelLink) -> Unit,
    onRetry: () -> Unit,
) {
    SectionLabel("通道")
    FluxCard {
        when {
            links.isEmpty() && loading -> {
                Text(
                    "正在读取通道状态…",
                    fontSize = 12.5.sp,
                    color = Flux.text3,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                )
            }
            links.isEmpty() && error.isNotBlank() -> {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        "通道状态读取失败：$error",
                        fontSize = 12.5.sp,
                        color = Flux.text3,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("重试") { onRetry() }
                }
            }
            links.isEmpty() -> {
                Text(
                    "还没有可用的通道。",
                    fontSize = 12.5.sp,
                    color = Flux.text3,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                )
            }
            else -> {
                links.forEachIndexed { index, link ->
                    ChannelLinkRow(
                        link = link,
                        expanded = expanded == link.channel,
                        onExpand = { onExpand(link.channel) },
                        onConnect = { onConnect(link) },
                        onDisconnect = { onDisconnect(link) },
                    )
                    if (index != links.lastIndex) Hairline()
                }
            }
        }
    }
}

@Composable
private fun ChannelLinkRow(
    link: RemoteChannelLink,
    expanded: Boolean,
    onExpand: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(enabled = !link.configured) { onExpand() }
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(link.label, fontSize = 15.sp, color = Flux.text)
                Text(
                    link.statusText(),
                    fontSize = 12.sp,
                    color = when {
                        !link.configured -> Flux.text4
                        link.connected -> Flux.text2
                        else -> Flux.text3
                    },
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            when {
                !link.configured -> {
                    FIcon(
                        if (expanded) FIcon.ChevronDown else FIcon.ChevronRight,
                        size = 14.dp,
                        color = Flux.text4,
                    )
                }
                link.connected -> {
                    ActionText("断开", Flux.text3, onDisconnect)
                }
                else -> {
                    ActionText("连接", Flux.accent, onConnect)
                }
            }
        }
        if (!link.configured && expanded) {
            Text(
                if (link.missingEnv.isEmpty()) {
                    "服务器还没有配置这个通道。"
                } else {
                    "需要先在服务器上配置 " + link.missingEnv.joinToString("、")
                },
                fontSize = 11.5.sp,
                color = Flux.text4,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 13.dp),
            )
        }
    }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 13.sp, color = color)
    }
}
