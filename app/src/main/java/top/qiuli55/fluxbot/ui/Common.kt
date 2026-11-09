package top.qiuli55.fluxbot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ease = tween<Float>(durationMillis = 260)
fun <T> ease(duration: Int = 260) = tween<T>(durationMillis = duration)

@Composable
fun PageHead(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconBtn(FIcon.Back, onClick = onBack)
            Spacer(Modifier.width(6.dp))
        }
        Text(title, style = FluxType.head, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun IconBtn(
    icon: FIcon,
    size: Dp = 40.dp,
    tint: Color = Flux.text,
    stroke: Float = 1.7f,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        FIcon(icon, size = 20.dp, color = tint, stroke = stroke)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = FluxType.section,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun FluxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
fun ItemRow(
    title: String,
    sub: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Flux.text,
    showDivider: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .then(
                    if (onClick != null) Modifier.clickable { onClick() }
                    else Modifier
                )
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, style = FluxType.rowTitle.copy(color = titleColor),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(sub, style = FluxType.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp)
                    .height(1.dp)
                    .background(Flux.lineSoft),
            )
        }
    }
}

@Composable
fun FluxSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val on by animateColorAsState(if (checked) Flux.accent else Flux.panel3, animationSpec = tween(180), label = "sw")
    val knob by animateDpAsState(if (checked) 19.dp else 3.dp, animationSpec = tween(180), label = "knob")
    Box(
        Modifier
            .width(48.dp)
            .height(29.dp)
            .clip(CircleShape)
            .background(on)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(23.dp)
                .clip(CircleShape)
                .background(if (checked) Flux.ink else Flux.text2),
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (enabled) Flux.accent else Flux.panel3)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight(560),
            color = if (enabled) Flux.ink else Flux.text4,
        )
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Flux.panel2)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(25.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight(460), color = Flux.text)
    }
}

@Composable
fun SegTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Flux.panel)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Flux.panel3 else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight(460) else FontWeight(360),
                    color = if (on) Flux.text else Flux.text3,
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Flux.accent, bg: Color = Flux.accentSoft) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight(460), color = color)
    }
}

@Composable
fun TabHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        color = Flux.text3,
        lineHeight = 17.sp,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
    )
}

@Composable
fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 24.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Flux.text2, lineHeight = 19.sp)
    }
}

@Composable
fun AddRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FIcon(FIcon.Plus, size = 16.dp, color = Flux.accent, baseline = true)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = Flux.accent)
    }
}

/** 底部固定操作区（demo 的 bottom-cta）。 */
@Composable
fun BottomCta(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            content = content,
        )
    }
}

/** 圆形字母账户头像（demo 的 account-avatar）。 */
@Composable
fun InitialsAvatar(initials: String, size: Dp = 38.dp, accent: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (accent) Flux.accentSoft else Flux.panel3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontSize = (size.value * 0.32f).sp,
            fontWeight = FontWeight(560),
            color = if (accent) Flux.accent else Flux.text2,
        )
    }
}
