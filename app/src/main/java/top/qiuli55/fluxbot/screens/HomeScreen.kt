package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.InitialsAvatar
import top.qiuli55.fluxbot.ui.floaty

private val DockBg = Color(0xFF0D1013)

@Composable
fun HomeScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val acc = store.current()
    val bots = acc.bots
    val hero = bots.firstOrNull { it.hero } ?: bots.firstOrNull()
    val rest = bots.filter { it !== hero }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { store.api.listBots() }.onSuccess { store.mergeRemoteBots(it) }
    }

    Box(Modifier.fillMaxSize().background(Flux.bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp)
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).clickable { ui.push(Route.Settings) },
                    contentAlignment = Alignment.Center,
                ) {
                    InitialsAvatar(acc.account.initials, 46.dp)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconBtn(FIcon.Search) { searching = true }
                    RoundIconBtn(FIcon.Plus, accent = true) { ui.sheet = Sheet.CreateMenu }
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                if (hero != null) {
                    item {
                        HomeHero(hero) { ui.push(Route.Chat(hero.id)) }
                    }
                    items(rest, key = { it.id }) { bot ->
                        BotRow(
                            bot = bot,
                            onClick = { ui.push(Route.Chat(bot.id)) },
                            onLongClick = { ui.sheet = Sheet.BotMenu(bot.id) },
                        )
                    }
                    if (rest.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "还没有其他 Bot。\n点右上角的 + 创建一个。",
                                    fontSize = 13.sp,
                                    color = Flux.text3,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "这个账号还没有 Bot。\n先创建一个，把今天的事交出去。",
                                fontSize = 13.sp,
                                color = Flux.text3,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }

            HomeDock(
                onGrid = { ui.sheet = Sheet.Grid },
                onSearch = { searching = true },
            )
        }

        if (searching) {
            SearchLayer(
                bots = bots,
                query = query,
                onQuery = { query = it },
                onClose = { searching = false; query = "" },
                onOpen = { bot ->
                    searching = false
                    query = ""
                    ui.push(Route.Chat(bot.id))
                },
            )
        }
    }
}

@Composable
private fun HomeHero(hero: Bot, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clickable { onClick() }) {
            BotAvatar(
                bot = hero,
                size = 128.dp,
                animated = true,
                popSignal = if (hero.working) 1 else 0,
            )
        }
        Text(
            hero.name,
            fontSize = 17.sp,
            fontWeight = FontWeight(560),
            color = Flux.text,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "在线 · ${hero.computer} · 记忆已同步",
            fontSize = 11.sp,
            color = Flux.text3,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(Modifier.padding(top = 10.dp)) {
            Text("")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BotRow(
    bot: Bot,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotAvatar(bot, 48.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bot.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight(560),
                    color = Flux.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                bot.unread?.let {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (it == "warn") Flux.warn else Flux.accent2),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(bot.time, fontSize = 11.sp, color = Flux.text4)
            }
            Text(
                bot.preview.ifEmpty { "还没有消息" },
                fontSize = 12.5.sp,
                color = Flux.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HomeDock(onGrid: () -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DockBg)
                    .border(1.dp, Flux.line, RoundedCornerShape(14.dp))
                    .clickable { onGrid() },
                contentAlignment = Alignment.Center,
            ) {
                FIcon(FIcon.Grid, size = 19.dp, color = Flux.text2)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DockBg)
                    .border(1.dp, Flux.line, RoundedCornerShape(22.dp))
                    .clickable { onSearch() }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FIcon(FIcon.Search, size = 17.dp, color = Flux.text3)
                Spacer(Modifier.width(9.dp))
                Text("搜索 Bot 和消息", fontSize = 14.sp, color = Flux.text3)
            }
        }
    }
}

@Composable
private fun SearchLayer(
    bots: List<Bot>,
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (Bot) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val rows = remember(bots, query) { searchRows(bots, query) }

    Column(Modifier.fillMaxSize().background(Flux.bg).padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DockBg)
                    .border(1.dp, Flux.line, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FIcon(FIcon.Search, size = 17.dp, color = Flux.text3)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("搜索 Bot 和消息", fontSize = 15.sp, color = Flux.text4)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = Flux.text),
                        cursorBrush = SolidColor(Flux.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Text(
                "取消",
                fontSize = 14.sp,
                color = Flux.text2,
                modifier = Modifier.clickable { onClose() }.padding(vertical = 8.dp),
            )
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp)) {
                Text(
                    "没有找到和「$query」相关的 Bot 或消息。",
                    fontSize = 13.sp,
                    color = Flux.text3,
                    lineHeight = 20.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 6.dp)) {
                items(rows, key = { it.first.id }) { (bot, snippet) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpen(bot) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotAvatar(bot, 42.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                bot.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight(560),
                                color = Flux.text,
                            )
                            Text(
                                snippet.ifEmpty { "还没有消息" },
                                fontSize = 12.5.sp,
                                color = Flux.text3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

private fun searchRows(bots: List<Bot>, query: String): List<Pair<Bot, String>> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return bots.map { it to it.preview }
    val rows = bots.mapNotNull { bot ->
        val msgs = bot.messages
        val hit = msgs.firstOrNull { it.text.lowercase().contains(q) }
        val nameHit = bot.name.lowercase().contains(q)
        if (!nameHit && hit == null) return@mapNotNull null
        Triple(bot, hit?.text ?: bot.preview, nameHit)
    }
    return rows.sortedByDescending { it.third }.map { it.first to it.second }
}
