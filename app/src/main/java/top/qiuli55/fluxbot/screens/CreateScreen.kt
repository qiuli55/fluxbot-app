package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.ip.AvatarData
import top.qiuli55.fluxbot.ip.FloAvatar
import top.qiuli55.fluxbot.ip.FloAvatarStill
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.ui.BottomCta
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.PrimaryButton
import top.qiuli55.fluxbot.ui.SectionLabel

@Composable
fun CreateScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var name by remember { mutableStateOf("") }
    var shape by remember { mutableStateOf(AvatarData.shapeKeys[4]) }
    var palette by remember { mutableStateOf(AvatarData.paletteKeys[0]) }
    var pop by remember { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("创建 Bot", onBack = { ui.back() })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FloAvatar(shape = shape, palette = palette, size = 116.dp, popSignal = pop)
            }

            FluxInput(
                value = name,
                onValueChange = { name = it.take(18) },
                placeholder = "给它起个名字",
                height = 56.dp,
                fontSize = 16.sp,
                center = true,
            )

            SectionLabel("选一个形体")
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarData.shapeKeys.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            val on = key == shape
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (on) Flux.accentSoft else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (on) Flux.accentLine else Color.Transparent,
                                        RoundedCornerShape(16.dp),
                                    )
                                    .clickable {
                                        if (!on) {
                                            shape = key
                                            ui.haptic(6)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                FloAvatarStill(key, "sage", 48.dp)
                            }
                        }
                    }
                }
            }

            SectionLabel("配色")
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarData.paletteKeys.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                PaletteSwatch(
                                    palette = AvatarData.palette(key),
                                    selected = key == palette,
                                    onClick = {
                                        if (key != palette) {
                                            palette = key
                                            ui.haptic(6)
                                        }
                                    },
                                )
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Text(
                "每个 Bot 有自己的记忆、定时任务和权限。\n形体和配色来自 Flo IP，创建之后可以随时改名。",
                fontSize = 12.sp,
                color = Flux.text4,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        BottomCta {
            PrimaryButton("创建 Bot", enabled = name.trim().isNotEmpty()) {
                val botName = name.trim()
                pop += 1
                ui.haptic(10)
                ui.scope.launch {
                    delay(280)
                    val remote = runCatching { store.api.createBot(botName, shape, palette) }.getOrNull()
                    val id = if (remote != null) {
                        store.mergeRemoteBots(listOf(remote))
                        remote.id
                    } else {
                        store.addBot(botName, shape, palette)
                    }
                    ui.back()
                    ui.push(Route.Chat(id))
                    ui.showToast("已创建 $botName")
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: AvatarData.Palette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, Flux.accentLine, CircleShape) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color(palette.a),
                                0.48f to Color(palette.b),
                                1f to Color(palette.c),
                            ),
                            center = Offset(size.width * 0.3f, size.height * 0.22f),
                            radius = size.minDimension * 1.2f,
                        ),
                        radius = size.minDimension / 2f,
                    )
                }
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        )
    }
}
