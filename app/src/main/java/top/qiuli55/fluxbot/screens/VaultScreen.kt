package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.VaultItem
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.PageHead
import kotlin.math.abs

private data class VaultCat(val key: String, val label: String)

private val CATS = listOf(
    VaultCat("all", "全部"),
    VaultCat("login", "登录信息"),
    VaultCat("payment", "支付"),
    VaultCat("contact", "联系信息"),
)

private fun emptyCopy(cat: String): String = when (cat) {
    "login" -> "还没有保存登录信息。"
    "payment" -> "还没有保存支付信息。"
    "contact" -> "还没有保存联系信息。"
    else -> "还没有保存任何内容。"
}

private fun iconOf(cat: String): FIcon = when (cat) {
    "payment" -> FIcon.Wallet
    "contact" -> FIcon.Contact
    else -> FIcon.Key
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val all = store.current().vault
    var cat by remember { mutableStateOf("all") }

    val items = if (cat == "all") all else all.filter { it.cat == cat }
    val counts = CATS.associate { it.key to (if (it.key == "all") all.size else all.count { v -> v.cat == it.key }) }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("保管库", onBack = { ui.back() }) {
            RoundIconBtn(FIcon.Plus, accent = true) {
                ui.sheet = Sheet.VaultAdd(if (cat == "all") "login" else cat)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CATS.forEach { c ->
                val on = c.key == cat
                Row(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (on) Flux.panel3 else Color(0xFF0C0F12))
                        .border(1.dp, if (on) Color(0xFF2A3036) else Flux.line, RoundedCornerShape(17.dp))
                        .clickable { cat = c.key }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        c.label,
                        fontSize = 12.5.sp,
                        color = if (on) Flux.text else Flux.text3,
                    )
                    Text(
                        counts[c.key].toString(),
                        fontSize = 11.sp,
                        color = if (on) Flux.accent else Flux.text4,
                    )
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .pointerInput(cat) {
                    val threshold = 48.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var last = start
                        while (true) {
                            val ev = awaitPointerEvent()
                            ev.changes.firstOrNull { it.id == down.id }?.let { last = it.position }
                            if (ev.changes.all { !it.pressed }) break
                        }
                        val dx = last.x - start.x
                        val dy = last.y - start.y
                        if (abs(dx) < threshold || abs(dx) < abs(dy) * 1.4f) return@awaitEachGesture
                        val i = CATS.indexOfFirst { it.key == cat }
                        val next = if (dx < 0) i + 1 else i - 1
                        if (next in CATS.indices) {
                            ui.haptic(6)
                            cat = CATS[next].key
                        }
                    }
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            ) {
                if (items.isEmpty()) {
                    VaultEmpty(cat)
                } else {
                    items.forEach { v ->
                        VaultRow(
                            v,
                            onClick = {
                                ui.haptic(10)
                                ui.sheet = Sheet.VaultDetail(v.id)
                            },
                            onLongClick = {
                                ui.haptic(10)
                                ui.sheet = Sheet.VaultMenu(v.id)
                            },
                        )
                    }
                }
            }
        }

        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Flux.accent2))
            Spacer(Modifier.width(8.dp))
            Text("由系统密钥库加密 · Bot 仅在任务中按需读取", fontSize = 11.5.sp, color = Flux.text4)
        }
    }
}

@Composable
private fun VaultEmpty(cat: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(emptyCopy(cat), fontSize = 14.sp, color = Flux.text2, lineHeight = 21.7.sp)
    }
    Text(
        "Flux Bot 永远看不到已保存卡片的卡号或安全码。它可以看见登录信息和联系信息，以便替你填写。",
        fontSize = 12.sp,
        color = Flux.text3,
        lineHeight = 19.2.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultRow(item: VaultItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Flux.panel3),
            contentAlignment = Alignment.Center,
        ) {
            FIcon(iconOf(item.cat), size = 18.dp, color = Flux.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight(460), color = Flux.text)
            Spacer(Modifier.height(3.dp))
            Text(item.sub, fontSize = 11.5.sp, color = Flux.text3)
        }
    }
}
