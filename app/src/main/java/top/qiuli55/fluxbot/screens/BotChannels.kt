package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.qiuli55.fluxbot.data.CHANNEL_KEYS
import top.qiuli55.fluxbot.data.channelLabel
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.Pill

/**
 * Bot 的通道标记：每个已连接通道一枚小型胶囊。
 *
 * 只用现有的次级文字色与面板色，保持安静；没有通道时不占位。
 */
@Composable
fun BotChannelMarkers(
    channels: List<String>,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        channels
            .distinct()
            .sortedBy { key -> CHANNEL_KEYS.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } }
            .forEach { key ->
                Pill(
                    channelLabel(key),
                    color = Flux.text2,
                    bg = Flux.panel3,
                )
            }
    }
}
