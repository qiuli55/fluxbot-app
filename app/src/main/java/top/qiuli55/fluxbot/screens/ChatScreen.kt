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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.qiuli55.fluxbot.data.ApiException
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.data.CONVERSATION_STREAM_EVENT
import top.qiuli55.fluxbot.data.ChatStreamState
import top.qiuli55.fluxbot.data.HttpStream
import top.qiuli55.fluxbot.data.STREAMING_MESSAGE_ID
import top.qiuli55.fluxbot.data.SseDecoder
import top.qiuli55.fluxbot.data.TURN_STREAM_EVENT
import top.qiuli55.fluxbot.data.TaskStreamEvent
import top.qiuli55.fluxbot.data.appendLocalMessage
import top.qiuli55.fluxbot.data.applyConversationEvent
import top.qiuli55.fluxbot.data.beginLocalTurn
import top.qiuli55.fluxbot.data.createChatStreamState
import top.qiuli55.fluxbot.data.parseTaskStreamEvent
import top.qiuli55.fluxbot.data.reconcileHistory
import top.qiuli55.fluxbot.data.removeMessage
import top.qiuli55.fluxbot.data.setConversationMessages
import top.qiuli55.fluxbot.data.streamingText
import top.qiuli55.fluxbot.data.toAppMessage
import top.qiuli55.fluxbot.data.toRemoteMessage
import org.json.JSONObject
import top.qiuli55.fluxbot.data.ComponentAction
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.data.PendingDecision
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

    val remoteChat = bot.remote && !bot.group && bot.id.isNotBlank()

    /**
     * 事件流的纯状态：负责 seq 去重、增量拼接、子任务归并。
     * 渲染仍以 Store 为准，流事件在这里算完后按需写回 Store。
     */
    var streamState by remember(botId) {
        mutableStateOf(
            createChatStreamState(
                store.bot(botId)?.messages?.map { it.toRemoteMessage() }.orEmpty(),
            ),
        )
    }
    /** 已应用的最大 seq；重连时作为 after_seq。网络线程只读，主线程推进。 */
    val seenSeq = remember(botId) { AtomicLong(0L) }
    /** 每次发消息后 +1，让对话流立即重连（新建对话任务后不用等退避）。 */
    var streamKick by remember(botId) { mutableIntStateOf(0) }
    /** 兜底回复的 turnId -> 本地消息 id：流里的最终消息到达时替换掉，避免重复正文。 */
    val fallbackReplies = remember(botId) { mutableMapOf<String, String>() }
    val turnStreamHandles = remember(botId) { ConcurrentHashMap.newKeySet<HttpStream>() }
    val turnStreamScope = rememberCoroutineScope()

    DisposableEffect(botId) {
        onDispose {
            turnStreamHandles.toList().forEach { runCatching { it.close() } }
            turnStreamHandles.clear()
        }
    }

    /** 把本次事件真正改变的消息写回 Store；每段增量不落盘，最终消息才持久化。 */
    fun syncStreamMessages(previous: ChatStreamState, next: ChatStreamState) {
        val before = previous.messages.associateBy { it.id }
        next.messages.forEach { message ->
            if (message.id == STREAMING_MESSAGE_ID) return@forEach
            if (before[message.id] != message) store.upsertStreamMessage(botId, message)
        }
    }

    /** 流里的最终消息到达时，撤掉之前补的兜底回复（按 turn_id 或同内容匹配）。 */
    fun dropFallbackReply(event: TaskStreamEvent) {
        if (event.type != "assistant_message" || fallbackReplies.isEmpty()) return
        val turnId = event.data.optString("turn_id")
        val incoming = event.data.optJSONObject("message")
            ?.optString("content")?.trim().orEmpty()
        val match = fallbackReplies.entries.firstOrNull { (id, messageId) ->
            (turnId.isNotEmpty() && id == turnId) ||
                (
                    incoming.isNotEmpty() &&
                        store.bot(botId)?.messages
                            ?.firstOrNull { it.id == messageId }
                            ?.displayText()?.trim() == incoming
                    )
        } ?: return
        fallbackReplies.remove(match.key)
        streamState = removeMessage(streamState, match.value)
        store.removeMessage(botId, match.value)
    }

    /** 在主线程应用一条事件；seq 已在网络线程推进过时这里会直接忽略重放。 */
    fun handleStreamData(raw: String) {
        val event = parseTaskStreamEvent(raw) ?: return
        if (event.seq <= seenSeq.get()) return
        seenSeq.set(event.seq)
        dropFallbackReply(event)
        val previous = streamState
        val next = applyConversationEvent(previous, event)
        if (next === previous) return
        streamState = next
        // 增量只更新界面上的流式气泡；其它事件（最终消息 / 用户消息 / 子任务）才写回 Store。
        if (event.type != "assistant_delta" && event.type != "assistant_start" &&
            event.type != "assistant_error"
        ) {
            syncStreamMessages(previous, next)
        }
    }

    /** 本机刚发起的回合再单独订阅一次：对话流暂不可用时仍能实时看到回复。 */
    fun watchTurnStream(turnId: String) {
        turnStreamScope.launch {
            var handle: HttpStream? = null
            try {
                withContext(Dispatchers.IO) {
                    handle = store.api.turnStream(botId, turnId, seenSeq.get())
                        .also { turnStreamHandles.add(it) }
                    val decoder = SseDecoder()
                    val reader = handle!!.body.bufferedReader(Charsets.UTF_8)
                    val buffer = CharArray(4_096)
                    while (isActive) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        for (frame in decoder.push(String(buffer, 0, read))) {
                            if (frame.event.isNullOrEmpty() || frame.event == TURN_STREAM_EVENT) {
                                withContext(Dispatchers.Main.immediate) {
                                    handleStreamData(frame.data)
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 对话流是主通道；单轮流连不上时保持安静。
            } finally {
                handle?.let {
                    turnStreamHandles.remove(it)
                    runCatching { it.close() }
                }
            }
        }
    }

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

    /**
     * 整段对话的实时流：只要聊天页可见就一直保持，回合结束后不关闭
     * （后台子任务与例程结果随后才回来）。发消息时通过 [streamKick] 立即重连。
     */
    LaunchedEffect(botId, remoteChat, streamKick) {
        if (!remoteChat) return@LaunchedEffect
        val open = AtomicReference<HttpStream?>(null)
        val worker = launch(Dispatchers.IO) {
            var backoff = 800L
            while (isActive) {
                try {
                    val handle = store.api.conversationStream(botId, seenSeq.get())
                    open.set(handle)
                    turnStreamHandles.add(handle)
                    // 连上就重置退避；没有对话任务时服务端会立刻发一个空流结束，稍后重试。
                    backoff = 800L
                    val decoder = SseDecoder()
                    val reader = handle.body.bufferedReader(Charsets.UTF_8)
                    val buffer = CharArray(4_096)
                    while (isActive) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        for (frame in decoder.push(String(buffer, 0, read))) {
                            if (frame.event.isNullOrEmpty() ||
                                frame.event == CONVERSATION_STREAM_EVENT
                            ) {
                                withContext(Dispatchers.Main.immediate) {
                                    handleStreamData(frame.data)
                                }
                            }
                        }
                    }
                    val tail = decoder.flush()
                    if (tail != null &&
                        (tail.event.isNullOrEmpty() || tail.event == CONVERSATION_STREAM_EVENT)
                    ) {
                        withContext(Dispatchers.Main.immediate) { handleStreamData(tail.data) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isActive) break
                    // 登录态已失效：交给全局过期回调，别在这里无限重试。
                    if (e is ApiException && e.status == 401) break
                } finally {
                    open.getAndSet(null)?.let {
                        turnStreamHandles.remove(it)
                        runCatching { it.close() }
                    }
                }
                if (!isActive) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(5_000)
            }
        }
        try {
            worker.join()
        } finally {
            // 离开页面 / 切换 Bot：断开长连接，阻塞中的读会立即返回。
            open.getAndSet(null)?.let {
                turnStreamHandles.remove(it)
                runCatching { it.close() }
            }
            worker.cancel()
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

    /**
     * 提交兜底回复：事件流没送到最终消息时，至少让用户看到这一轮的回复。
     *
     * 用 `reply-<turnId>` 作 id 并记进 [fallbackReplies]；如果流里的
     * assistant_message 稍后到达，会被就地替换掉而不是再追加一遍。
     */
    fun addFallbackReply(turnId: String, text: String) {
        val id = "reply-$turnId"
        if (store.bot(botId)?.messages?.any { it.id == id } == true) return
        fallbackReplies[turnId] = id
        val message = store.addMessage(botId, "bot", text, id = id)
        streamState = appendLocalMessage(streamState, message.toRemoteMessage())
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
            val reply = turn.replyText
            if (reply.isNotBlank()) {
                // 内容以事件流为准：先等它一拍，避免和流里的 assistant_message 重复。
                var rendered = streamState.messages.any {
                    it.role == "assistant" && it.content.trim() == reply.trim()
                }
                var waited = 0
                while (!rendered && waited < 2_000) {
                    delay(150)
                    waited += 150
                    rendered = streamState.messages.any {
                        it.role == "assistant" && it.content.trim() == reply.trim()
                    }
                }
                if (!rendered) addFallbackReply(turnId, reply)
            } else {
                addFallbackReply(turnId, "模型没有返回内容")
            }
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

    // 远端 Bot：进入会话时拉取历史（服务端是真相源；同一子任务的多条状态在这里已归并）。
    LaunchedEffect(botId) {
        val b = store.bot(botId) ?: return@LaunchedEffect
        if (!b.remote || b.group || b.id.isBlank()) return@LaunchedEffect
        val list = try {
            store.api.conversation(b.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (list.isEmpty()) return@LaunchedEffect
        streamState = setConversationMessages(streamState, list)
        val now = store.clock()
        // 历史 id 用服务端 id：事件流重放时据此去重；本机作答状态与例程卡片保留。
        val fresh = list.map { it.toAppMessage(now) }
        store.replaceMessages(botId, reconcileHistory(fresh, store.bot(botId)?.messages.orEmpty()))
    }

    // 流式气泡单独叠加在持久化消息之后，避免每个增量都写一次磁盘。
    val liveAssistantText = streamingText(streamState)
    val displayMessages = remember(messages, liveAssistantText) {
        if (liveAssistantText.isEmpty()) {
            messages
        } else {
            messages + Message(
                id = STREAMING_MESSAGE_ID,
                role = "bot",
                text = liveAssistantText,
                time = store.clock(),
                kind = "text",
            )
        }
    }
    val showTyping = typing && liveAssistantText.isEmpty()

    val itemCount = 1 + displayMessages.size +
        (if (showTyping) 1 else 0) +
        (if (workingBots.isNotEmpty()) 1 else 0)
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        // 用户在输入框直接发言：此前还没作答的选择卡视为跳过，之后不可再提交。
        store.skipPendingChoices(botId)
        input = ""
        // 乐观消息用 local- 前缀：服务端回显同内容时事件流会就地替换它。
        val localId = "local-${UUID.randomUUID()}"
        val localMessage = store.addMessage(botId, "user", text, id = localId)
        streamState = appendLocalMessage(streamState, localMessage.toRemoteMessage())
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
                    // 本机回合：清掉旧气泡，允许接收这一轮的增量。
                    streamState = beginLocalTurn(streamState, started.id)
                    // 首条消息会创建对话任务：立即重连对话流，不必等退避。
                    streamKick++
                    watchTurnStream(started.id)
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

    /**
     * 选择卡提交：把选择结果作为一条普通用户消息发回对话（走与输入框相同的
     * addMessage + startTurn 路径）。本地先落「已答」状态，再处理卡上已有的
     * 服务端动作（决策卡仍走 respondDecision），最后发送回复。
     */
    fun handleChoiceSubmit(
        msg: Message,
        choice: ComponentNode.Choice,
        selectedIds: List<String>,
        custom: String,
    ) {
        val reply = choiceReplyText(choice, selectedIds, custom)
        if (reply.isBlank()) return
        store.setChoiceState(botId, msg.id, "answered", selectedIds, custom.trim())
        val action = choice.action
        if (action != null) {
            when {
                action.type == "client" -> handleComponentAction(action)
                action.type == "server" && action.decisionId.isNotBlank() ->
                    respondDecision(action.option.ifBlank { "允许" })
                // 通用 choice 没有服务端动作：不发明新动作名，只发用户回复。
            }
        }
        submit(reply)
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
            val lastUserIndex = displayMessages.indexOfLast { it.role == "user" }
            itemsIndexed(displayMessages, key = { _, msg -> msg.id }) { index, msg ->
                MessageRow(
                    msg = msg,
                    senderBot = groupMembers.firstOrNull { it.name == msg.sender },
                    onLongPress = { ui.sheet = Sheet.MessageMenu(botId, msg.id, msg.displayText()) },
                    onComponentAction = ::handleComponentAction,
                    choiceState = choiceCardState(msg, hasLaterUserMessage = lastUserIndex > index),
                    onChoiceSubmit = ::handleChoiceSubmit,
                    onOpenSubtask = { ui.push(Route.BotDetail(botId, tab = 2)) },
                    onOpenRoutine = { routineId ->
                        ui.sheet = Sheet.RoutineDetail(botId, routineId)
                    },
                    onPauseRoutine = ::pauseRoutine,
                )
            }
            if (showTyping) {
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
    choiceState: ChoiceCardState = ChoiceCardState.Pending,
    choicePreview: ChoicePreview? = null,
    onChoiceSubmit: (Message, ComponentNode.Choice, List<String>, String) -> Unit = { _, _, _, _ -> },
    onOpenSubtask: (() -> Unit)? = null,
    onOpenRoutine: ((String) -> Unit)? = null,
    onPauseRoutine: ((String) -> Unit)? = null,
) {
    if (msg.kind == "subtask" || msg.kind == "routine_created" || msg.kind == "decision_result") {
        InlineEventCard(msg, onOpenSubtask, onOpenRoutine, onPauseRoutine)
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
                        ComponentView(
                            msg.component,
                            onComponentAction,
                            choiceState = choiceState,
                            choicePreview = choicePreview,
                            onChoiceSubmit = { choice, ids, custom ->
                                onChoiceSubmit(msg, choice, ids, custom)
                            },
                        )
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

/** 对话里的系统事件卡：后台子任务 / Bot 创建的例程 / 已处理审批。 */
@Composable
internal fun InlineEventCard(
    msg: Message,
    onOpenSubtask: (() -> Unit)?,
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
            "subtask" -> {
                val status = msg.payload["status"].orEmpty()
                val running = status in setOf(
                    "running", "executing", "queued", "pending", "waiting_for_user_decision",
                )
                val title = msg.payload["title"].orEmpty().ifBlank { msg.displayText() }
                // 事件里的 data.message 优先，其次历史字段 summary / result。
                val summary = msg.payload["message"].orEmpty()
                    .ifBlank { msg.payload["summary"].orEmpty() }
                    .ifBlank { msg.payload["result"].orEmpty() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (running) Flux.warn else Flux.text4),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title.ifBlank { "后台子任务" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight(560),
                        color = Flux.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        when {
                            running -> "运行中"
                            status == "completed" -> "已完成"
                            status == "cancelled" -> "已取消"
                            status == "failed" -> "失败"
                            else -> "已结束"
                        },
                        if (running) Flux.warn else if (status == "failed") Flux.danger else Flux.text3,
                        if (running) Color(0x1FE8B25E) else Flux.panel3,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (running) "运行中 · 不占用对话" else summary.ifBlank { "结果已回到对话" },
                    fontSize = 11.5.sp,
                    color = Flux.text3,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onOpenSubtask != null) {
                    Text(
                        "查看子任务",
                        fontSize = 11.5.sp,
                        color = Flux.accent,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onOpenSubtask() },
                    )
                }
            }
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

/** 审批卡：工具 / 出站草稿 / 通用决策，只存在于界面上，不写入消息列表。 */
@Composable
internal fun ApprovalCard(
    decision: PendingDecision,
    busy: Boolean,
    waiting: Boolean,
    onChoose: (String) -> Unit,
) {
    val channel = decision.meta["channel"].orEmpty()
    val tool = decision.meta["tool_name"].orEmpty()
    when {
        channel.isNotBlank() || decision.kind.endsWith("_outbound") ->
            OutboundApprovalCard(decision, busy, waiting, onChoose)
        tool.isNotBlank() || decision.kind in setOf(
            "high_risk_command", "protected_access", "rule_match", "tool_permission_prompt",
        ) -> ToolApprovalCard(decision, busy, waiting, onChoose)
        else -> GenericApprovalCard(decision, busy, waiting, onChoose)
    }
}

@Composable
private fun ApprovalShell(
    title: String,
    badgeColor: Color,
    note: String,
    busy: Boolean,
    waiting: Boolean,
    options: List<String>,
    onChoose: (String) -> Unit,
    body: @Composable () -> Unit,
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
            Box(Modifier.size(7.dp).clip(CircleShape).background(badgeColor))
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        body()
        Text(
            if (waiting) "等待你的批准 · $note" else note,
            fontSize = 10.5.sp,
            color = Flux.text4,
            modifier = Modifier.padding(top = 8.dp),
        )
        val labels = options.ifEmpty { listOf("允许", "拒绝") }
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
private fun ToolApprovalCard(
    decision: PendingDecision,
    busy: Boolean,
    waiting: Boolean,
    onChoose: (String) -> Unit,
) {
    val tool = decision.meta["tool_name"].orEmpty()
    val command = decision.detail.lineSequence()
        .firstOrNull { it.startsWith("命令：") }
        ?.removePrefix("命令：")
        ?.trim()
        .orEmpty()
    val risk = decision.detail.lineSequence()
        .firstOrNull { it.startsWith("风险：") }
        ?.removePrefix("风险：")
        ?.trim()
        .orEmpty()
    val dir = decision.meta["working_dir"].orEmpty()
    ApprovalShell(
        title = decision.question.ifBlank {
            if (tool.isBlank()) "需要你批准" else "Claude 请求使用 $tool"
        },
        badgeColor = Flux.warn,
        note = "仅本次有效 · 10 分钟无响应自动拒绝",
        busy = busy,
        waiting = waiting,
        options = decision.options,
        onChoose = onChoose,
    ) {
        if (command.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF090B0D))
                    .border(1.dp, Flux.lineSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Text(
                    command,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFCFE9DE),
                    lineHeight = 17.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dir.isNotBlank()) {
                    Text(
                        dir,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Flux.text4,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        } else if (decision.detail.isNotBlank()) {
            Text(
                decision.detail,
                fontSize = 12.sp,
                color = Flux.text3,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (risk.isNotBlank()) {
            Text(
                "风险：$risk",
                fontSize = 10.5.sp,
                color = Flux.warn,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun OutboundApprovalCard(
    decision: PendingDecision,
    busy: Boolean,
    waiting: Boolean,
    onChoose: (String) -> Unit,
) {
    val channel = decision.meta["channel"].orEmpty()
    val channelText = when (channel) {
        "feishu" -> "飞书"
        "wecom" -> "企业微信"
        "dingtalk" -> "钉钉"
        "email" -> "邮箱"
        else -> channel.ifBlank { "频道" }
    }
    val chat = decision.meta["chat_id"].orEmpty()
    val preview = decision.detail.lineSequence()
        .filterNot { it.startsWith("准备发送到") || it.startsWith("触发规则：") }
        .joinToString("\n")
        .trim()
    ApprovalShell(
        title = listOfNotNull(
            "发送到$channelText",
            chat.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        badgeColor = Flux.accent,
        note = "出站草稿 · 允许后只发送一次",
        busy = busy,
        waiting = waiting,
        options = decision.options,
        onChoose = onChoose,
    ) {
        if (preview.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Flux.panel3)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Text(
                    preview,
                    fontSize = 12.sp,
                    color = Flux.text2,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GenericApprovalCard(
    decision: PendingDecision,
    busy: Boolean,
    waiting: Boolean,
    onChoose: (String) -> Unit,
) {
    ApprovalShell(
        title = decision.question.ifBlank { "需要你批准" },
        badgeColor = Flux.warn,
        note = "仅本次有效",
        busy = busy,
        waiting = waiting,
        options = decision.options,
        onChoose = onChoose,
    ) {
        if (decision.detail.isNotBlank()) {
            Text(
                decision.detail,
                fontSize = 12.sp,
                color = Flux.text3,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
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
    /** 截图脚手架：显式指定选择卡状态 / 初始展开与勾选。 */
    choiceState: ChoiceCardState? = null,
    choicePreview: ChoicePreview? = null,
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
                    val lastUserIndex = messages.indexOfLast { it.role == "user" }
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        MessageRow(
                            msg = msg,
                            senderBot = groupMembers.firstOrNull { it.name == msg.sender },
                            onLongPress = {},
                            onComponentAction = onComponentAction,
                            choiceState = choiceState
                                ?: choiceCardState(msg, hasLaterUserMessage = lastUserIndex > index),
                            choicePreview = choicePreview,
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
