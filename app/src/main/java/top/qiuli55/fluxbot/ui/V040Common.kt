package top.qiuli55.fluxbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** v0.4 页面共用的加载 / 空 / 错误三态。 */

@Composable
fun V040Loading(text: String = "加载中…") {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Flux.text3)
    }
}

@Composable
fun V040Error(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.dangerSoft)
            .border(1.dp, Flux.danger.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            message.ifBlank { "加载失败，请稍后重试。" },
            fontSize = 13.sp,
            color = Flux.danger,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Flux.panel2)
                .border(1.dp, Flux.line, RoundedCornerShape(16.dp))
                .clickable { onRetry() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("重试", fontSize = 12.sp, color = Flux.text)
        }
    }
}

@Composable
fun V040Empty(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Flux.text2, lineHeight = 20.sp)
    }
}

@Composable
fun SmallAction(
    text: String,
    color: Color = Flux.accent,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (color == Flux.danger) Flux.dangerSoft else Flux.accentSoft)
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight(460), color = color)
    }
}

@Composable
fun GhostAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Flux.panel2)
            .border(1.dp, Flux.line, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, color = Flux.text2)
    }
}

@Composable
fun InfoLine(label: String, value: String, valueColor: Color = Flux.text2) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, fontSize = 11.5.sp, color = Flux.text4, modifier = Modifier.width(70.dp))
        Text(
            value.ifBlank { "—" },
            fontSize = 12.5.sp,
            color = valueColor,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun CardTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight(460),
            color = Flux.text,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
