package top.qiuli55.fluxbot.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.data.ComponentAction
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.data.PendingDecision
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme
import top.qiuli55.fluxbot.ui.IconBtn
import top.qiuli55.fluxbot.ui.PageHead
import java.util.UUID

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
    var pendingDecision by remember(botId) { mutableStateOf<PendingDecision?>(null) }
    var approving by remember(botId) { mutableStateOf(false) }
    var awaitingApproval by remember(botId) { mutableStateOf(false) }
    var activeTurnId by remember(botId) { mutableStateOf("") }
    var turnInFlight by remember(botId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(messages.size) { store.markRead(botId) }

    // 远端 Bot：可见期间持续轮询待批准决策；离开页面后自动取消。
    suspend fun pollPendingDecisions() {
        val b = store.bot(botId) ?: return
        if (!b.remote || b.group || b.id.isBlank()) return
        try {
            val list = store.api.listPendingDecisions(b.id, b.chatTaskId)
            pendingDecision = list.firstOrNull()
            if (list.isNotEmpty()) awaitingApproval = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 轮询失败保持安静，不打断正在进行的聊天。
        }
    }

    LaunchedEffect(botId, bot.remote) {
        if (!bot.remote || bot.group) return@LaunchedEffect
        while (true) {
            pollPendingDecisions()
            delay(2000)
        }
    }

    // 等这一轮结束；若超时但服务端仍在等用户批准，保留卡片而不是报硬失败。
    suspend fun waitTurn(turnId: String) {
        val b = store.bot(botId) ?: return
        turnInFlight = true
        store.setWorking(b.id, true)
        try {
            val turn = store.api.awaitTurn(b.id, turnId) { status ->
                if (status == "running") typing = true
                if (status == "waiting_for_user_decision" || status == "waiting_for_approval") {
                    typing = false
                    awaitingApproval = true
                    ui.scope.launch { pollPendingDecisions() }
                }
            }
            activeTurnId = ""
            awaitingApproval = false
            typing = false
            store.addMessage(botId, "bot", turn.replyText.ifBlank { "模型没有返回内容" })
            store.markRead(botId)
            pollPendingDecisions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val stillPending = try {
                store.api.listPendingDecisions(b.id, store.bot(botId)?.chatTaskId.orEmpty())
            } catch (inner: CancellationException) {
                throw inner
            } catch (inner: Exception) {
                emptyList()
            }
            if (stillPending.isNotEmpty()) {
                pendingDecision = stillPending.first()
                awaitingApproval = true
                typing = false
                return
            }
            activeTurnId = ""
            awaitingApproval = false
            typing = false
            store.addMessage(botId, "bot", e.message ?: "模型返回异常")
        } finally {
            turnInFlight = false
            typing = false
            store.setWorking(b.id, false)
        }
    }

    fun respondDecision(option: String) {
        val pending = pendingDecision ?: return
        if (approving) return
        approving = true
        val turnId = activeTurnId
        ui.scope.launch {
            try {
                store.api.chooseDecision(pending.taskId, pending.decisionId, option)
                pendingDecision = null
                awaitingApproval = false
                ui.haptic(8)
                ui.showToast(if (option == "拒绝") "已拒绝" else "已允许")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.showToast(e.message ?: "操作失败")
                return@launch
            } finally {
                approving = false
            }
            pollPendingDecisions()
            if (turnId.isNotBlank() && activeTurnId == turnId && !turnInFlight) waitTurn(turnId)
        }
    }

    /** 组件按钮：客户端动作就地处理，服务端动作交给当前决策流程。 */
    fun handleComponentAction(action: ComponentAction) {
        when {
            action.type == "client" && action.name == "open_desktop" -> {
                ui.push(Route.BotDesktop(botId))
            }
            action.type == "client" && action.name == "open_url" && action.url.isNotBlank() -> {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))
                }.onFailure { ui.showToast("无法打开链接") }
            }
            action.type == "server" && action.decisionId.isNotBlank() -> {
                val option = action.option.ifBlank { "允许" }
                respondDecision(option)
            }
            action.type == "server" -> {
                ui.showToast("已提交：${action.name}")
            }
        }
    }

    // 远端 Bot：首次进入时拉取这个会话的历史消息。
    LaunchedEffect(botId) {
        val b = store.bot(botId) ?: return@LaunchedEffect
        if (!b.remote || b.group || b.id.isBlank() || b.messages.isNotEmpty()) return@LaunchedEffect
        val list = try {
            store.api.conversation(b.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (list.isEmpty()) return@LaunchedEffect
        val now = store.clock()
        val history = list.mapIndexed { i, remote ->
            Message(
                id = "h-$botId-$i",
                role = if (remote.role == "assistant") "bot" else "user",
                text = remote.displayText(),
                time = now,
                component = remote.component,
                kind = remote.kind,
                fallbackText = remote.fallbackText,
            )
        }
        store.replaceMessages(botId, history)
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

        // 远端 Bot 的单聊走异步会话接口：发起一轮 → 长轮询到结束。
        if (!bot.group && bot.remote && bot.id.isNotBlank()) {
            ui.scope.launch {
                typing = true
                try {
                    val clientTurnId = UUID.randomUUID().toString()
                    val started = try {
                        store.api.startTurn(bot.id, text, clientTurnId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        store.addMessage(botId, "bot", "发送失败：${e.message ?: "未知错误"}")
                        return@launch
                    }
                    activeTurnId = started.id
                    awaitingApproval = false
                    // 回合刚开始就立即查一次，别让批准卡片等下一个 2 秒轮询。
                    pollPendingDecisions()
                    waitTurn(started.id)
                } finally {
                    typing = false
                    store.setWorking(botId, false)
                }
            }
            return
        }

        // 本地 Bot 的单聊仍交给旧任务接口，用真实模型回复（不再本地模拟）。
        if (!bot.group) {
            ui.scope.launch {
                typing = true
                try {
                    var taskId = store.bot(botId)?.taskId?.takeUnless { it == "null" }.orEmpty()
                    if (taskId.isEmpty()) {
                        taskId = store.api.createTask(
                            text, bot.name, bot.shape, bot.palette, bot.model, bot.modelKeyId,
                        )
                        store.setTaskId(botId, taskId)
                    }
                    // 触发后端回答；回复从增量事件轮询取回（公网入口 20 秒就会断开空闲连接）
                    val seq = store.api.lastSeq(taskId)
                    val send = ui.scope.async<Throwable?> {
                        try {
                            store.api.sendMessage(taskId, text)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            e
                        }
                    }
                    val reply = store.api.waitForReply(taskId, seq, send = send)
                    typing = false
                    store.addMessage(botId, "bot", reply)
                    store.markRead(botId)
                } catch (e: CancellationException) {
                    throw e
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
                            var taskId = store.bot(member.id)?.taskId?.takeUnless { it == "null" }.orEmpty()
                            if (taskId.isEmpty()) {
                                taskId = store.api.createTask(
                                    text, member.name, member.shape, member.palette,
                                    member.model, member.modelKeyId,
                                )
                                store.setTaskId(member.id, taskId)
                            }
                            val seq = store.api.lastSeq(taskId)
                            val send = ui.scope.async<Throwable?> {
                                try {
                                    store.api.sendMessage(taskId, text)
                                    null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    e
                                }
                            }
                            val reply = store.api.waitForReply(taskId, seq, send = send)
                            store.addMessage(botId, "bot", reply, sender = member.name)
                            store.markRead(botId)
                        } catch (e: CancellationException) {
                            throw e
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
        ChatHead(
            bot = bot,
            onOpen = {
                if (bot.group) ui.sheet = Sheet.GroupMembers(botId) else ui.push(Route.BotDetail(botId))
            },
        )

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
                MessageRow(
                    msg = msg,
                    senderBot = groupMembers.firstOrNull { it.name == msg.sender },
                    onLongPress = { ui.sheet = Sheet.MessageMenu(botId, msg.id, msg.displayText()) },
                    onComponentAction = ::handleComponentAction,
                )
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
            pendingDecision?.let { pending ->
                ApprovalCard(
                    decision = pending,
                    busy = approving,
                    waiting = awaitingApproval,
                    onChoose = { respondDecision(it) },
                )
            }
            ChatChips { submit(it) }
            ChatComposer(
                hint = if (bot.group) "在群里发消息" else "发消息给 ${bot.name}",
                input = input,
                onInputChange = { input = it },
                onSend = { submit(input) },
                onAttach = { ui.sheet = Sheet.Attach(botId) },
            )
        }
    }
}

@Composable
internal fun ChatChips(onPick: (String) -> Unit) {
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
                    .clickable { onPick(chip) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun ChatComposer(
    hint: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
) {
    val canSend = input.trim().isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        RoundPlus(onAttach)
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
                        hint,
                        fontSize = 14.sp,
                        color = Flux.text4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
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
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Flux.accent)
                .alpha(if (canSend) 1f else 0.35f)
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            FIcon(FIcon.Send, size = 18.dp, color = Flux.ink)
        }
    }
}

@Composable
private fun ChatHead(
    bot: Bot,
    onOpen: () -> Unit,
    /** 截图快照关闭交互与动画；生产路径保持默认值。 */
    interactive: Boolean = true,
    subtitleOverride: String? = null,
) {
    val ui = if (interactive) LocalUi.current else null
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBtn(FIcon.Back, onClick = { ui?.back() })
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onOpen() },                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BotAvatar(bot, 34.dp, animated = interactive)
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
                            subtitleOverride ?: if (bot.group) {
                                "${LocalStore.current.groupMembers(bot).size} 个成员 · 群聊"
                            } else {
                                "在线 · ${bot.computer}"
                            },
                            fontSize = 11.sp,
                            color = Flux.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconBtn(FIcon.More, onClick = { ui?.sheet = Sheet.BotMenu(bot.id) })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(
    msg: Message,
    senderBot: Bot?,
    onLongPress: () -> Unit,
    /** 生产路径传入真实触感；截图脚手架保持空回调。 */
    onLikeHaptic: () -> Unit = {},
    onComponentAction: (ComponentAction) -> Unit = {},
) {
    val user = msg.role == "user"
    var liked by remember { mutableStateOf(false) }
    val likeScale = remember { Animatable(1f) }
    val likeScope = rememberCoroutineScope()
    val hasComponent = msg.component != null
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth * 0.79f
        val sender = if (user) null else senderBot
        val withAvatar = sender != null
        // 组件卡片比普通气泡宽一些，否则表格 / 按钮会被挤成竖条。
        val rowMax = if (hasComponent) maxWidth * 0.93f else maxW
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
                    .widthIn(max = if (withAvatar) rowMax - 36.dp else rowMax),
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
                        .padding(
                            horizontal = if (hasComponent) 9.dp else 14.dp,
                            vertical = if (hasComponent) 8.dp else 11.dp,
                        ),
                ) {
                    if (hasComponent) {
                        ComponentView(msg.component, onComponentAction)
                    } else {
                        Text(
                            msg.displayText(),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = if (user) Color(0xFFD9FFF1) else Color(0xFFDBE2DF),
                        )
                    }
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
                                    onLikeHaptic()
                                    likeScope.launch {
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
private fun TypingRow(animated: Boolean = true) {
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
                val a = if (!animated) {
                    // 截图固定帧：三点同时可见，避免无限动画被拍到空白。
                    listOf(1f, 0.68f, 0.42f)[i]
                } else {
                    val t = rememberInfiniteTransition(label = "typing$i")
                    val animatedAlpha by t.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(600, delayMillis = i * 150, easing = LinearEasing),
                            RepeatMode.Reverse,
                        ),
                        label = "typingA$i",
                    )
                    animatedAlpha
                }
                Box(Modifier.size(6.dp).clip(CircleShape).background(Flux.text3.copy(alpha = a)))
            }
        }
    }
}

/** 高风险工具审批卡：只存在于界面上，不写入消息列表。 */
@Composable
private fun ApprovalCard(
    decision: PendingDecision,
    busy: Boolean,
    waiting: Boolean,
    onChoose: (String) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .clip(shape)
            .background(Flux.panel2)
            .border(1.dp, Flux.line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FIcon(FIcon.Shield, size = 14.dp, color = Flux.warn)
            Text(
                decision.question.ifBlank { "需要你批准" },
                fontSize = 13.5.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (decision.detail.isNotBlank()) {
            Text(
                decision.detail,
                fontSize = 12.sp,
                color = Flux.text3,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (waiting) {
            Text(
                "等待你的批准",
                fontSize = 11.sp,
                color = Flux.text4,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val labels = decision.options.ifEmpty { listOf("允许", "拒绝") }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach { label ->
                val primary = label != "拒绝" && (label == "允许" || labels.size == 1)
                ApprovalButton(
                    label = label,
                    primary = primary,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onChoose(label) },
                )
            }
        }
    }
}

@Composable
private fun ApprovalButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(if (primary) Flux.accent else Flux.panel3)
            .border(1.dp, if (primary) Flux.accent else Flux.line, shape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight(560),
            color = if (primary) Flux.ink else Flux.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 仅用于 JVM 截图（Paparazzi）的聊天页快照。
 *
 * 复用真实 ChatHead / MessageRow / TypingRow / GroupWorkingRow / ApprovalCard /
 * 快捷 chips / 输入栏，但不读取 LocalStore、不轮询远端决策、不发网络请求。
 */
@Composable
internal fun ChatSnapshot(
    bot: Bot,
    messages: List<Message>,
    pending: PendingDecision? = null,
    approving: Boolean = false,
    waiting: Boolean = false,
    typing: Boolean = false,
    workingBots: List<Bot> = emptyList(),
    groupMembers: List<Bot> = emptyList(),
    input: String = "",
    fontFamily: FontFamily? = null,
    onAction: (String) -> Unit = {},
    onComponentAction: (ComponentAction) -> Unit = {},
) {
    FluxTheme {
        val inheritedStyle = LocalTextStyle.current
        CompositionLocalProvider(
            LocalTextStyle provides if (fontFamily != null) {
                inheritedStyle.copy(fontFamily = fontFamily)
            } else {
                inheritedStyle
            },
        ) {
            Column(Modifier.fillMaxSize().background(Flux.bg)) {
                ChatHead(
                    bot = bot,
                    onOpen = {},
                    interactive = false,
                    subtitleOverride = if (bot.group) {
                        "${groupMembers.size} 个成员 · 群聊"
                    } else {
                        "在线 · ${bot.computer}"
                    },
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "system") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text(
                                if (bot.group) {
                                    "今天 · ${groupMembers.joinToString("、") { it.name }} 的群聊"
                                } else {
                                    "今天 · 与 ${bot.name} 的对话"
                                },
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
                        MessageRow(
                            msg = msg,
                            senderBot = groupMembers.firstOrNull { it.name == msg.sender },
                            onLongPress = {},
                            onComponentAction = onComponentAction,
                        )
                    }
                    if (typing) {
                        item(key = "typing") { TypingRow(animated = false) }
                    }
                    if (workingBots.isNotEmpty()) {
                        item(key = "working") { GroupWorkingRow(workingBots) }
                    }
                }

                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
                    pending?.let {
                        ApprovalCard(
                            decision = it,
                            busy = approving,
                            waiting = waiting,
                            onChoose = onAction,
                        )
                    }
                    ChatChips(onPick = onAction)
                    ChatComposer(
                        hint = if (bot.group) "在群里发消息" else "发消息给 ${bot.name}",
                        input = input,
                        onInputChange = {},
                        onSend = {},
                        onAttach = {},
                    )
                }
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
