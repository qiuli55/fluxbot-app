package top.qiuli55.fluxbot.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.IconBtn
import top.qiuli55.fluxbot.ui.PageHead

private val CHIPS = listOf("总结未读消息", "安排今天", "查一下资料", "写一条周报")
private val DockBg = Color(0xFF0D1013)

@Composable
fun ChatScreen(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId)

    if (bot == null) {
        Column(Modifier.fillMaxSize().background(Flux.bg)) {
            PageHead("对话", onBack = { ui.back() })
            Text(
                "这个 Bot 已经被删除。\n回首页看看其他的 Bot。",
                fontSize = 13.sp,
                color = Flux.text3,
                lineHeight = 20.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val messages = bot.messages
    var input by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var workingBots by remember { mutableStateOf<List<Bot>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) { store.markRead(botId) }

    // 远端 Bot：首次进入时拉取这个任务的历史消息。
    LaunchedEffect(botId) {
        val b = store.bot(botId) ?: return@LaunchedEffect
        if (!b.remote || b.group || b.taskId.isEmpty() || b.messages.isNotEmpty()) return@LaunchedEffect
        runCatching { store.api.listMessages(b.taskId) }.onSuccess { list ->
            if (list.isEmpty()) return@onSuccess
            val now = store.clock()
            val history = list.mapIndexed { i, (role, content) ->
                Message(
                    id = "h-$botId-$i",
                    role = if (role == "assistant") "bot" else "user",
                    text = content,
                    time = now,
                )
            }
            store.replaceMessages(botId, history)
        }
    }

    val itemCount = 1 + messages.size +
        (if (typing) 1 else 0) +
        (if (workingBots.isNotEmpty()) 1 else 0)
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        input = ""
        store.addMessage(botId, "user", text)
        store.setWorking(botId, true)

        // 单聊一律交给 flux-bot 后端，用真实模型回复（不再本地模拟）。
        if (!bot.group) {
            ui.scope.launch {
                typing = true
                try {
                    var taskId = store.bot(botId)?.taskId.orEmpty()
                    if (taskId.isEmpty()) {
                        taskId = store.api.createTask(
                            text, bot.name, bot.shape, bot.palette, bot.model, bot.modelKeyId,
                        )
                        store.setTaskId(botId, taskId)
                    }
                    val reply = store.api.sendMessage(taskId, text)
                    typing = false
                    store.addMessage(botId, "bot", reply)
                    store.markRead(botId)
                } catch (e: Exception) {
                    typing = false
                    store.addMessage(botId, "bot", "后端没有响应：${e.message ?: "未知错误"}")
                } finally {
                    store.setWorking(botId, false)
                }
            }
            return
        }

        // 群聊：每个成员各用自己的会话并行向后端要回复，谁先返回谁先上屏。
        ui.scope.launch {
            val members = store.groupMembers(bot)
            if (members.isEmpty()) {
                store.setWorking(botId, false)
                return@launch
            }
            workingBots = members
            members.forEach { store.setWorking(it.id, true) }
            coroutineScope {
                members.forEach { member ->
                    launch {
                        try {
                            var taskId = store.bot(member.id)?.taskId.orEmpty()
                            if (taskId.isEmpty()) {
                                taskId = store.api.createTask(
                                    text, member.name, member.shape, member.palette,
                                    member.model, member.modelKeyId,
                                )
                                store.setTaskId(member.id, taskId)
                            }
                            val reply = store.api.sendMessage(taskId, text)
                            store.addMessage(botId, "bot", reply, sender = member.name)
                            store.markRead(botId)
                        } catch (e: Exception) {
                            store.addMessage(
                                botId, "bot", "没有响应：${e.message ?: "未知错误"}", sender = member.name,
                            )
                        } finally {
                            store.setWorking(member.id, false)
                            workingBots = workingBots.filterNot { it.id == member.id }
                        }
                    }
                }
            }
            workingBots = emptyList()
            store.setWorking(botId, false)
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg).imePadding()) {
        ChatHead(bot) {
            if (bot.group) ui.sheet = Sheet.GroupMembers(botId) else ui.push(Route.BotDetail(botId))
        }

        val groupMembers = if (bot.group) store.groupMembers(bot) else emptyList()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "system") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        if (bot.group) "今天 · ${groupMembers.joinToString("、") { it.name }} 的群聊"
                        else "今天 · 与 ${bot.name} 的对话",
                        fontSize = 11.sp,
                        color = Flux.text4,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0C0F12))
                            .border(1.dp, Flux.lineSoft, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            items(messages, key = { it.id }) { msg ->
                MessageRow(msg, groupMembers.firstOrNull { it.name == msg.sender }) {
                    ui.sheet = Sheet.MessageMenu(botId, msg.id, msg.text)
                }
            }
            if (typing) {
                item(key = "typing") { TypingRow() }
            }
            if (workingBots.isNotEmpty()) {
                item(key = "working") { GroupWorkingRow(workingBots) }
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CHIPS.forEach { chip ->
                    Text(
                        chip,
                        fontSize = 12.sp,
                        color = Flux.text2,
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFF0C0F12))
                            .border(1.dp, Flux.line, RoundedCornerShape(15.dp))
                            .clickable { submit(chip) }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                RoundPlus { ui.sheet = Sheet.Attach(botId) }
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DockBg)
                        .border(1.dp, Flux.line, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) {
                            Text(
                                if (bot.group) "在群里发消息" else "发消息给 ${bot.name}",
                                fontSize = 14.sp,
                                color = Flux.text4,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = Flux.text, lineHeight = 20.sp),
                            cursorBrush = SolidColor(Flux.accent),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                val canSend = input.trim().isNotEmpty()
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Flux.accent else Flux.accent)
                        .alpha(if (canSend) 1f else 0.35f)
                        .clickable(enabled = canSend) { submit(input) },
                    contentAlignment = Alignment.Center,
                ) {
                    FIcon(FIcon.Send, size = 18.dp, color = Flux.ink)
                }
            }
        }
    }
}

@Composable
private fun ChatHead(bot: Bot, onOpen: () -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBtn(FIcon.Back, onClick = { ui.back() })
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onOpen() },                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BotAvatar(bot, 34.dp, animated = true)
                Column {
                    Text(
                        bot.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight(560),
                        color = Flux.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier.padding(top = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        OnlineDot(5.dp)
                        Text(
                            if (bot.group) "${LocalStore.current.groupMembers(bot).size} 个成员 · 群聊"
                            else "在线 · ${bot.computer}",
                            fontSize = 11.sp,
                            color = Flux.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconBtn(FIcon.More, onClick = { ui.sheet = Sheet.BotMenu(bot.id) })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(msg: Message, senderBot: Bot?, onLongPress: () -> Unit) {
    val ui = LocalUi.current
    val user = msg.role == "user"
    var liked by remember { mutableStateOf(false) }
    val likeScale = remember { Animatable(1f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth * 0.79f
        val sender = if (user) null else senderBot
        val withAvatar = sender != null
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (sender != null) {
                BotAvatar(sender, 28.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(
                Modifier
                    .widthIn(max = if (withAvatar) maxW - 36.dp else maxW),
                horizontalAlignment = if (user) Alignment.End else Alignment.Start,
            ) {
                if (!user && msg.sender != null && !withAvatar) {
                    Text(
                        msg.sender,
                        fontSize = 11.5.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 5.dp),
                    )
                }
                val shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (user) 18.dp else 7.dp,
                    bottomEnd = if (user) 7.dp else 18.dp,
                )
                Column(
                    Modifier
                        .clip(shape)
                        .background(if (user) Flux.accentSoft else Flux.panel2)
                        .border(1.dp, if (user) Flux.accentLine else Flux.line, shape)
                        .combinedClickable(onClick = {}, onLongClick = onLongPress)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Text(
                        msg.text,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = if (user) Color(0xFFD9FFF1) else Color(0xFFDBE2DF),
                    )
                }
                Row(
                    Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(msg.time, fontSize = 10.sp, color = Flux.text4)
                    if (!user) {
                        // 高定规范：点赞 k=380 / c=18 微弹，一次回弹后归位。
                        FIcon(
                            FIcon.Heart,
                            size = 13.dp,
                            color = if (liked) Flux.accent else Flux.text4,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = likeScale.value
                                    scaleY = likeScale.value
                                    transformOrigin = TransformOrigin(0.5f, 0.6f)
                                }
                                .clickable {
                                    liked = !liked
                                    ui.haptic(6)
                                    ui.scope.launch {
                                        likeScale.snapTo(0.86f)
                                        likeScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = 0.46f, stiffness = 380f),
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** 群聊工作中：头像堆叠 + 正在完成 / 多个 bot 正在工作。 */
@Composable
private fun GroupWorkingRow(bots: List<Bot>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AvatarStack(bots)
        Text(
            if (bots.size <= 1) "正在完成..." else "「${bots.first().name}」等 ${bots.size} 个 bot 正在工作",
            fontSize = 12.5.sp,
            color = Flux.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AvatarStack(bots: List<Bot>) {
    val shown = bots.take(4)
    val size = 24.dp
    val step = 16.dp
    Box(
        Modifier
            .height(size)
            .width(size + step * (shown.size - 1).coerceAtLeast(0)),
    ) {
        shown.forEachIndexed { i, b ->
            Box(Modifier.offset(x = step * i)) { BotAvatar(b, size) }
        }
    }
}

@Composable
private fun TypingRow() {
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 7.dp,
        bottomEnd = 18.dp,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .clip(shape)
                .background(Flux.panel2)
                .border(1.dp, Flux.line, shape)
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val t = rememberInfiniteTransition(label = "typing$i")
                val a by t.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, delayMillis = i * 150, easing = LinearEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "typingA$i",
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(Flux.text3.copy(alpha = a)))
            }
        }
    }
}

@Composable
private fun RoundPlus(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(DockBg)
            .border(1.dp, Flux.line, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        FIcon(FIcon.Plus, size = 18.dp, color = Flux.text2)
    }
}
