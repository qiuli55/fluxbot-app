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
import org.json.JSONObject
import top.qiuli55.fluxbot.data.ComponentAction
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.data.PendingDecision
import top.qiuli55.fluxbot.data.RemoteMessage
import top.qiuli55.fluxbot.data.listRoutineDetails
import top.qiuli55.fluxbot.data.routineTriggerSummary
import top.qiuli55.fluxbot.data.updateRoutineDetail
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme
import top.qiuli55.fluxbot.ui.IconBtn
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
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
    var decisionStatus by remember(botId) {
        mutableStateOf<ApprovalCardStatus>(ApprovalCardStatus.Pending)
    }
    var approving by remember(botId) { mutableStateOf(false) }
    var awaitingApproval by remember(botId) { mutableStateOf(false) }
    var activeTurnId by remember(botId) { mutableStateOf("") }
    var turnInFlight by remember(botId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(messages.size) { store.markRead(botId) }

    /**
     * 子任务在聊天轮次结束后才收尾：把终态消息补进本地列表。
     *
     * 服务端已把 completion/failure 写成普通 text 回复（payload 仍带 subtask_id），
     * 所以这里只追加这类消息，不影响用户自己刚发出的本地气泡。
     */
    suspend fun syncSubtaskResults() {
        val b = store.bot(botId) ?: return
        if (!b.remote || b.group || b.id.isBlank()) return
        try {
            val remote = store.api.conversation(b.id)
            val seen = b.messages.map(::subtaskResultKey).toMutableSet()
            remote.filter(::isVisibleSubtaskResult).forEach { item ->
                val key = subtaskResultKey(item)
                if (!seen.add(key)) return@forEach
                store.addMessage(
                    botId = botId,
                    role = "bot",
                    text = item.displayText(),
                    kind = item.kind,
                    payload = item.payload,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 结果轮询失败保持安静；下一轮再试。
        }
    }

    // 远端 Bot：可见期间持续轮询待批准决策；离开页面后自动取消。
    suspend fun pollPendingDecisions() {
        val b = store.bot(botId) ?: return
        if (!b.remote || b.group || b.id.isBlank()) return
        try {
            val list = store.api.listPendingDecisions(b.id, b.chatTaskId)
            val first = list.firstOrNull()
            val current = pendingDecision
            if (first != null) {
                val newDecision = current == null ||
                    current.decisionId != first.decisionId ||
                    decisionStatus is ApprovalCardStatus.Answered ||
                    decisionStatus is ApprovalCardStatus.TimedOut
                pendingDecision = first
                if (newDecision) {
                    decisionStatus = ApprovalCardStatus.Pending
                } else if (decisionStatus !is ApprovalCardStatus.Submitting &&
                    decisionStatus !is ApprovalCardStatus.Failed
                ) {
                    // 同一张卡继续等：保留待批准，只刷新后端带来的最新超时字段。
                    decisionStatus = ApprovalCardStatus.Pending
                }
                awaitingApproval = true
            } else {
                if (current != null && decisionStatus !is ApprovalCardStatus.Submitting) {
                    // 卡片还在等，但后端已不再列出：按超时自动拒绝处理；本地已答复的保持原样。
                    if (decisionStatus is ApprovalCardStatus.Pending ||
                        decisionStatus is ApprovalCardStatus.Failed
                    ) {
                        decisionStatus = ApprovalCardStatus.TimedOut
                    }
                }
                awaitingApproval = false
            }
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

    // 后台子任务收尾晚于本轮聊天：单独轮询对话，把终态当成普通 Bot 回复补进来。
    LaunchedEffect(botId, bot.remote) {
        if (!bot.remote || bot.group) return@LaunchedEffect
        while (true) {
            syncSubtaskResults()
            delay(5000)
        }
    }

    /** 一轮结束后对比例程列表：Bot 新建的例程在对话里补一张本地卡片。 */
    suspend fun announceNewRoutines(before: Set<String>) {
        try {
            store.api.listRoutineDetails(botId)
                .filter { it.id !in before }
                .forEach { routine ->
                    store.addMessage(
                        botId = botId,
                        role = "bot",
                        text = "Bot 已为你创建例程 ${routine.name}",
                        kind = "routine_created",
                        payload = mapOf(
                            "routine_id" to routine.id,
                            "name" to routine.name,
                            "schedule" to routineTriggerSummary(routine),
                            "next" to routine.nextRunAt,
                        ),
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 只是补卡片，失败不打扰对话。
        }
    }

    fun pauseRoutine(routineId: String) {
        ui.scope.launch {
            try {
                store.api.updateRoutineDetail(botId, routineId, enabled = false)
                ui.showToast("例程已暂停")
                ui.refreshData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.showToast(e.message ?: "暂停失败")
            }
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
        if (decisionStatus is ApprovalCardStatus.Answered ||
            decisionStatus is ApprovalCardStatus.TimedOut
        ) {
            return
        }
        approving = true
        decisionStatus = ApprovalCardStatus.Submitting
        val turnId = activeTurnId
        ui.scope.launch {
            try {
                store.api.chooseDecision(pending.taskId, pending.decisionId, option)
                // 保留卡片并进入已答复态：选中的行打勾，其余行不再可点。
                pendingDecision = pending
                decisionStatus = ApprovalCardStatus.Answered(
                    chosen = option,
                    allowed = option != "拒绝",
                )
                awaitingApproval = false
                ui.haptic(8)
                ui.showToast(if (option == "拒绝") "已拒绝" else "已允许")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 网络失败不能吞掉卡片：保持待批准，卡片内提示并允许重试。
                decisionStatus = ApprovalCardStatus.Failed("提交失败，请重试")
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
                payload = remote.payload,
            )
        }
        store.replaceMessages(botId, history)
    }

    val itemCount = 1 + messages.size +
        (if (typing) 1 else 0) +
        (if (workingBots.isNotEmpty()) 1 else 0) +
        (if (pendingDecision != null) 1 else 0)
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
                val routinesBefore = runCatching {
                    store.api.listRoutineDetails(bot.id).map { it.id }.toSet()
                }.getOrNull()
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
                    routinesBefore?.let { announceNewRoutines(it) }
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
            items(visibleMessages(messages), key = { it.id }) { msg ->
                MessageRow(
                    msg = msg,
                    senderBot = groupMembers.firstOrNull { it.name == msg.sender },
                    onLongPress = { ui.sheet = Sheet.MessageMenu(botId, msg.id, msg.displayText()) },
                    onComponentAction = ::handleComponentAction,
                    onOpenRoutine = { routineId ->
                        ui.sheet = Sheet.RoutineDetail(botId, routineId)
                    },
                    onPauseRoutine = ::pauseRoutine,
                )
            }
            if (typing) {
                item(key = "typing") { TypingRow() }
            }
            if (workingBots.isNotEmpty()) {
                item(key = "working") { GroupWorkingRow(workingBots) }
            }
            // 审批卡跟在对话流末尾，按 Bot 气泡的左对齐与最大宽度展示，不再贴着输入栏。
            pendingDecision?.let { pending ->
                item(key = "approval-${pending.decisionId}") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardMaxWidth = maxWidth * 0.79f
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            ApprovalCard(
                                decision = pending,
                                status = decisionStatus,
                                onChoose = { respondDecision(it) },
                                modifier = Modifier.widthIn(max = cardMaxWidth),
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
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

/**
 * 子任务消息只在后台运行，不再进入对话列表。
 *
 * 数据与接口仍然保留，这里只负责决定哪些 kind 不渲染；
 * 纯函数便于 JVM 测试直接覆盖。
 */
internal fun isHiddenMessageKind(kind: String): Boolean = kind == "subtask"

/** 对话列表真正渲染的消息：过滤掉后台子任务。 */
internal fun visibleMessages(messages: List<Message>): List<Message> =
    messages.filterNot { isHiddenMessageKind(it.kind) }

/** 子任务终态：服务端写成普通 text 回复，聊天里要直接展示。 */
private val TERMINAL_SUBTASK_STATUSES = setOf("completed", "failed", "cancelled")

/**
 * 是否是应在聊天里补显示的子任务终态回复。
 *
 * 开始/进度仍是 kind=subtask（隐藏）；只有终态的普通 text 回复带 subtask_id。
 */
internal fun isVisibleSubtaskResult(message: RemoteMessage): Boolean =
    message.kind == "text" &&
        message.payload["subtask_id"].orEmpty().isNotBlank() &&
        message.payload["status"].orEmpty() in TERMINAL_SUBTASK_STATUSES

private fun subtaskResultKey(message: RemoteMessage): String =
    "${message.payload["subtask_id"].orEmpty()}|" +
        "${message.payload["status"].orEmpty()}|${message.displayText()}"

private fun subtaskResultKey(message: Message): String =
    "${message.payload["subtask_id"].orEmpty()}|" +
        "${message.payload["status"].orEmpty()}|${message.displayText()}"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(
    msg: Message,
    senderBot: Bot?,
    onLongPress: () -> Unit,
    /** 生产路径传入真实触感；截图脚手架保持空回调。 */
    onLikeHaptic: () -> Unit = {},
    onComponentAction: (ComponentAction) -> Unit = {},
    onOpenRoutine: ((String) -> Unit)? = null,
    onPauseRoutine: ((String) -> Unit)? = null,
) {
    // 子任务已经不再出现在界面上：数据与接口保留，只是不渲染。
    if (isHiddenMessageKind(msg.kind)) return
    if (msg.kind == "routine_created" || msg.kind == "decision_result") {
        InlineEventCard(msg, onOpenRoutine, onPauseRoutine)
        return
    }
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

/** 对话里的系统事件卡：Bot 创建的例程 / 已处理审批。 */
@Composable
internal fun InlineEventCard(
    msg: Message,
    onOpenRoutine: ((String) -> Unit)?,
    onPauseRoutine: ((String) -> Unit)?,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Flux.panel2)
            .border(1.dp, Flux.line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when (msg.kind) {
            "routine_created" -> {
                val routineId = msg.payload["routine_id"].orEmpty()
                    .ifBlank { msg.payload["id"].orEmpty() }
                val name = msg.payload["name"].orEmpty().ifBlank { "新例程" }
                val schedule = msg.payload["schedule"].orEmpty()
                val next = msg.payload["next"].orEmpty()
                    .ifBlank { msg.payload["next_run_at"].orEmpty() }
                Text("Bot 已为你创建例程", fontSize = 11.sp, color = Flux.accent)
                Spacer(Modifier.height(5.dp))
                Text(
                    name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight(560),
                    color = Flux.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        schedule.takeIf { it.isNotBlank() },
                        next.takeIf { it.isNotBlank() }?.let { "下次 $it" },
                    ).joinToString(" · ").ifBlank { "已排期" },
                    fontSize = 11.5.sp,
                    color = Flux.text3,
                )
                Row(
                    Modifier.padding(top = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onOpenRoutine != null && routineId.isNotBlank()) {
                        InlineAction("查看") { onOpenRoutine(routineId) }
                    }
                    if (onPauseRoutine != null && routineId.isNotBlank()) {
                        InlineAction("暂停") { onPauseRoutine(routineId) }
                    }
                }
            }
            else -> {
                val decision = runCatching {
                    msg.payload["decision"]?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                }.getOrNull()
                val kind = decision?.optString("kind").orEmpty()
                val meta = decision?.optJSONObject("meta")
                val channel = meta?.optString("channel").orEmpty()
                val tool = meta?.optString("tool_name").orEmpty()
                val label = when {
                    channel.isNotBlank() -> when (channel) {
                        "feishu" -> "飞书"
                        "wecom" -> "企业微信"
                        "dingtalk" -> "钉钉"
                        else -> channel
                    }
                    tool.isNotBlank() -> tool
                    kind == "approval_rule_change" -> "审批规则"
                    else -> "审批"
                }
                val question = decision?.optString("question").orEmpty().ifBlank { msg.displayText() }
                val chosen = decision?.optString("chosen").orEmpty()
                val expired = msg.payload["expired"] == "true"
                val status = when {
                    expired -> "已过期"
                    chosen.isNotBlank() -> "已$chosen"
                    else -> "已处理"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$label · $question",
                        fontSize = 12.5.sp,
                        color = Flux.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Pill(
                        status,
                        if (expired) Flux.text3 else Flux.accent,
                        if (expired) Flux.panel3 else Flux.accentSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Flux.panel3)
            .border(1.dp, Flux.line, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 11.5.sp, color = Flux.text)
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
    pendingStatus: ApprovalCardStatus = ApprovalCardStatus.Pending,
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
                    items(visibleMessages(messages), key = { it.id }) { msg ->
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
                    pending?.let { decision ->
                        item(key = "approval-${decision.decisionId}") {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val cardMaxWidth = maxWidth * 0.79f
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    ApprovalCard(
                                        decision = decision,
                                        status = pendingStatus,
                                        onChoose = onAction,
                                        modifier = Modifier.widthIn(max = cardMaxWidth),
                                    )
                                }
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
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
