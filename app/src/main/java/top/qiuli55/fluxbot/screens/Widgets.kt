package top.qiuli55.fluxbot.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.ip.FloAvatar
import top.qiuli55.fluxbot.ip.FloAvatarStill
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux

/** Bot 头像：列表里用静态版 + 手绘绿点，对话头/详情用带动画版。 */
@Composable
fun BotAvatar(
    bot: Bot,
    size: Dp,
    animated: Boolean = false,
    popSignal: Int = 0,
) {
    if (bot.group) {
        GroupAvatar(bot, size)
        return
    }
    if (animated) {
        FloAvatar(
            shape = bot.shape,
            palette = bot.palette,
            size = size,
            working = bot.working,
            popSignal = popSignal,
            showDot = bot.working,
        )
        return
    }
    Box(Modifier.size(size)) {
        FloAvatarStill(bot.shape, bot.palette, size)
        if (bot.working) {
            WorkingDot(size, Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** 群聊头像：成员头像拼成 2×2（2 位横排，3 位下排居中）。 */
@Composable
fun GroupAvatar(bot: Bot, size: Dp) {
    val members = LocalStore.current.groupMembers(bot).take(4)
    val tile = size * 0.46f
    val gap = size * 0.08f
    Box(Modifier.size(size)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                members.size <= 1 -> GroupRow(members.firstOrNull() ?: bot, null, tile)
                else -> {
                    GroupRow(members[0], members[1], tile)
                    if (members.size > 2) GroupRow(members[2], members.getOrNull(3), tile)
                }
            }
        }
        if (bot.working) WorkingDot(size, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun GroupRow(a: Bot, b: Bot?, tile: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(tile * 0.17f)) {
        GroupTile(a, tile)
        b?.let { GroupTile(it, tile) }
    }
}

@Composable
private fun GroupTile(bot: Bot, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Flux.panel3),
        contentAlignment = Alignment.Center,
    ) {
        FloAvatarStill(bot.shape, bot.palette, size)
    }
}

/** 工作绿点：2.2s 呼吸。 */
@Composable
fun WorkingDot(size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dot")
    val p by t.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotP",
    )
    val d = (size * 0.2f).coerceAtLeast(7.dp)
    Box(
        modifier
            .offset(x = d * 0.16f, y = d * 0.16f)
            .size(d)
            .graphicsLayer { scaleX = p; scaleY = p }
            .clip(CircleShape)
            .background(Flux.accent)
            .border(2.dp, Flux.bg, CircleShape),
    )
}

/** 首页 / 保管库 / 连接应用页头右上角的圆形按钮（demo 的 icon-btn）。 */
@Composable
fun RoundIconBtn(
    icon: FIcon,
    accent: Boolean = false,
    size: Dp = 40.dp,
    onClick: () -> Unit,
) {
    val bg = if (accent) Flux.accentSoft else Color(0xFF0D1013)
    val line = if (accent) Flux.accentLine else Flux.line
    val tint = if (accent) Flux.accent else Flux.text
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, line, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        FIcon(icon, size = 20.dp, color = tint)
    }
}

/** 在线小圆点。 */
@Composable
fun OnlineDot(size: Dp = 6.dp, color: Color = Flux.accent2) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** 列表行右侧的箭头。 */
@Composable
fun RowChevron() {
    FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.text4)
}

/** 列表行右侧的次要文案。 */
@Composable
fun RowValue(text: String) {
    Text(text, fontSize = 13.sp, color = Flux.text3, maxLines = 1)
}

/** 1dp 分隔线。 */
@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
}
