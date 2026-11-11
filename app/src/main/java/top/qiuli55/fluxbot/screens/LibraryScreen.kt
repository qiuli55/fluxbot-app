package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.LibraryEntry
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.EmptyCard
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.SectionLabel

/** 资料库：Bot / 群聊在任务中看过的网页，按时间分组。 */
@Composable
fun LibraryScreen(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val entries = store.bot(botId)?.library ?: emptyList()

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("资料库", onBack = { ui.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            if (entries.isEmpty()) {
                EmptyCard("还没有记录。\nBot 和群聊在任务里看过的网页会出现在这里。")
            } else {
                entries.groupBy { it.group }.forEach { (group, list) ->
                    SectionLabel(group)
                    FluxCard {
                        list.forEachIndexed { i, entry ->
                            LibraryRow(entry, divider = i != list.lastIndex)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: LibraryEntry, divider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(entry.bg)),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.mark.isEmpty()) {
                    FIcon(FIcon.Globe, size = 18.dp, color = Color(entry.fg))
                } else {
                    Text(
                        entry.mark,
                        fontSize = if (entry.mark.length > 1) 11.sp else 14.sp,
                        fontWeight = FontWeight(600),
                        color = Color(entry.fg),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontSize = 14.sp,
                    color = Flux.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.host,
                    fontSize = 11.5.sp,
                    color = Flux.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (divider) {
            Box(
                Modifier.fillMaxWidth().padding(start = 66.dp)
                    .height(1.dp).background(Flux.lineSoft),
            )
        }
    }
}
