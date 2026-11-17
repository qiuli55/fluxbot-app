package top.qiuli55.fluxbot.data

import org.json.JSONObject

/**
 * 对话事件流的纯逻辑：解析 / 应用事件 / 去重续传 / 子任务归并。
 *
 * 这里的函数不依赖 Android、Compose 或网络，直接对应网页端
 * `conversationStream.ts`，方便在 JVM 单测里覆盖。
 */

/** 正在流式输出的助手气泡使用固定的本地 id，最终消息到达时被替换掉。 */
const val STREAMING_MESSAGE_ID = "flux-streaming-assistant"

/** 整段对话的 SSE 事件名。 */
const val CONVERSATION_STREAM_EVENT = "conversation.event"

/** 单轮对话的 SSE 事件名（对话流不可用时的兜底）。 */
const val TURN_STREAM_EVENT = "turn.event"

/** 服务端 task_stream 的 `{seq, type, data}` 形状。 */
data class TaskStreamEvent(
    val seq: Long,
    val type: String,
    val data: JSONObject,
)

/** 解析一条 SSE data；缺少可用 seq / type 时返回 null（当作心跳忽略）。 */
fun parseTaskStreamEvent(raw: String): TaskStreamEvent? {
    val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val seq = when (val value = parsed.opt("seq")) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    } ?: return null
    val type = parsed.optStringOrEmpty("type")
    if (type.isEmpty()) return null
    val data = parsed.optJSONObject("data") ?: JSONObject()
    return TaskStreamEvent(seq, type, data)
}

/**
 * 续传游标 + 流式状态。
 *
 * [lastSeq] 是已应用的最大事件序号：重连时作为 after_seq，也用于忽略重放。
 */
data class ConversationCursor(
    val lastSeq: Long = 0,
    val streaming: Boolean = false,
    val streamTurnId: String = "",
    val activeTurnId: String = "",
)

data class ChatStreamState(
    val messages: List<RemoteMessage> = emptyList(),
    val cursor: ConversationCursor = ConversationCursor(),
)

fun createChatStreamState(messages: List<RemoteMessage> = emptyList()): ChatStreamState =
    ChatStreamState(messages = mergeSubtaskMessages(messages), cursor = ConversationCursor())

private fun textOf(value: Any?): String = value as? String ?: ""

private fun turnIdOf(data: JSONObject): String = data.optStringOrEmpty("turn_id")

private fun streamingIndex(messages: List<RemoteMessage>): Int =
    messages.indexOfFirst { it.id == STREAMING_MESSAGE_ID }

private fun withoutStreaming(messages: List<RemoteMessage>): List<RemoteMessage> =
    messages.filterNot { it.id == STREAMING_MESSAGE_ID }

/** 同 id 的消息就地合并：事件里的新字段优先，旧组件 / 兜底文本不丢。 */
private fun mergeMessage(old: RemoteMessage, incoming: RemoteMessage): RemoteMessage =
    incoming.copy(
        component = incoming.component ?: old.component,
        fallbackText = incoming.fallbackText.ifBlank { old.fallbackText },
        createdAt = incoming.createdAt.ifBlank { old.createdAt },
    )

/** 消息插入到流式气泡之前；没有流式气泡就追加到末尾。 */
private fun insertMessage(
    messages: List<RemoteMessage>,
    message: RemoteMessage,
): List<RemoteMessage> {
    val existing = messages.indexOfFirst { it.id == message.id }
    if (existing >= 0) {
        val next = messages.toMutableList()
        next[existing] = mergeMessage(next[existing], message)
        return next
    }
    val streamAt = streamingIndex(messages)
    if (streamAt >= 0) {
        val next = messages.toMutableList()
        next.add(streamAt, message)
        return next
    }
    return messages + message
}

private fun appendDelta(messages: List<RemoteMessage>, text: String): List<RemoteMessage> {
    if (text.isEmpty()) return messages
    val index = streamingIndex(messages)
    if (index >= 0) {
        val next = messages.toMutableList()
        next[index] = next[index].copy(content = next[index].content + text)
        return next
    }
    return messages + RemoteMessage(
        id = STREAMING_MESSAGE_ID,
        role = "assistant",
        kind = "text",
        content = text,
    )
}

/** 事件里的 message 必须带 id / role；否则按「没有消息体」处理。 */
private fun streamMessage(raw: JSONObject?): RemoteMessage? {
    if (raw == null) return null
    val id = raw.optStringOrEmpty("id")
    val role = raw.optStringOrEmpty("role")
    if (id.isEmpty() || role.isEmpty()) return null
    return parseRemoteMessage(raw)
}

/** 子任务事件里 `data.message` 的文本（兼容对象与旧版裸字符串）。 */
private fun subtaskTextOf(data: JSONObject): String = when (val raw = data.opt("message")) {
    is String -> raw
    is JSONObject -> raw.optStringOrEmpty("content")
    else -> ""
}

fun subtaskIdOf(message: RemoteMessage): String = message.payload["subtask_id"].orEmpty()

/**
 * 同一子任务的多条状态消息合并成一张卡：保留首次出现的位置，内容取最新一条。
 *
 * 历史接口一次返回的是多条独立消息（开始 / 完成 / 失败），进入会话时按
 * subtask_id 归并；事件流里的 subtask_update 也走同一套规则。
 */
fun mergeSubtaskMessages(messages: List<RemoteMessage>): List<RemoteMessage> {
    val result = ArrayList<RemoteMessage>(messages.size)
    val positions = HashMap<String, Int>()
    for (message in messages) {
        val subtaskId = if (message.kind == "subtask") subtaskIdOf(message) else ""
        if (subtaskId.isEmpty()) {
            result.add(message)
            continue
        }
        val known = positions[subtaskId]
        if (known != null) {
            result[known] = message
            continue
        }
        positions[subtaskId] = result.size
        result.add(message)
    }
    return result
}

private fun applyAssistantStart(state: ChatStreamState, event: TaskStreamEvent): ChatStreamState =
    state.copy(
        messages = withoutStreaming(state.messages),
        cursor = state.cursor.copy(streaming = true, streamTurnId = turnIdOf(event.data)),
    )

private fun applyAssistantDelta(state: ChatStreamState, event: TaskStreamEvent): ChatStreamState {
    val turnId = turnIdOf(event.data)
    // 增量属于另一条已知回合时忽略：既防止重放旧回合，也避免串台。
    if (turnId.isNotEmpty() && state.cursor.streamTurnId.isNotEmpty() &&
        turnId != state.cursor.streamTurnId
    ) {
        return state
    }
    if (turnId.isNotEmpty() && !state.cursor.streaming &&
        state.cursor.activeTurnId.isNotEmpty() && turnId != state.cursor.activeTurnId
    ) {
        return state
    }
    val text = textOf(event.data.opt("text"))
    if (text.isEmpty()) return state
    return state.copy(
        messages = appendDelta(state.messages, text),
        cursor = state.cursor.copy(
            streaming = true,
            streamTurnId = turnId.ifEmpty { state.cursor.streamTurnId },
        ),
    )
}

private fun applyAssistantMessage(
    state: ChatStreamState,
    event: TaskStreamEvent,
): ChatStreamState {
    val message = streamMessage(event.data.optJSONObject("message"))
    if (message == null) {
        return state.copy(
            messages = withoutStreaming(state.messages),
            cursor = state.cursor.copy(streaming = false, streamTurnId = ""),
        )
    }
    val streamAt = streamingIndex(state.messages)
    val messages = withoutStreaming(state.messages)
    val existing = messages.indexOfFirst { it.id == message.id }
    val next = when {
        existing >= 0 -> messages.toMutableList().also { it[existing] = mergeMessage(it[existing], message) }
        streamAt >= 0 -> messages.toMutableList().also { it.add(minOf(streamAt, it.size), message) }
        else -> messages + message
    }
    return state.copy(
        messages = next,
        cursor = state.cursor.copy(streaming = false, streamTurnId = ""),
    )
}

private fun applyUserMessage(state: ChatStreamState, event: TaskStreamEvent): ChatStreamState {
    val message = streamMessage(event.data.optJSONObject("message")) ?: return state
    // 本机乐观插入的那条用户消息还没有服务端 id：同内容的本地消息就地替换，避免重影。
    val localAt = state.messages.indexOfFirst { item ->
        item.id.startsWith("local-") && item.role == "user" &&
            item.content.trim() == message.content.trim()
    }
    if (localAt >= 0) {
        val messages = state.messages.toMutableList()
        messages[localAt] = message
        return state.copy(messages = messages)
    }
    return state.copy(messages = insertMessage(state.messages, message))
}

private fun applySubtaskUpdate(state: ChatStreamState, event: TaskStreamEvent): ChatStreamState {
    val subtaskId = event.data.optStringOrEmpty("subtask_id")
    val status = event.data.optStringOrEmpty("status")
    val dataMessage = subtaskTextOf(event.data)
    val summary = event.data.optStringOrEmpty("summary")
    var message = streamMessage(event.data.optJSONObject("message"))
    if (message == null) {
        // 兼容旧服务端：message 可能只是字符串，或干脆没带。
        message = RemoteMessage(
            id = if (subtaskId.isNotEmpty()) "subtask-$subtaskId" else "subtask-seq-${event.seq}",
            role = "assistant",
            kind = "subtask",
            content = dataMessage.ifBlank { summary },
        )
    }
    val payload = LinkedHashMap(message.payload)
    if (subtaskId.isNotEmpty()) payload["subtask_id"] = subtaskId
    if (status.isNotEmpty()) payload["status"] = status
    if (!payload.containsKey("summary") && summary.isNotEmpty()) payload["summary"] = summary
    if (dataMessage.isNotEmpty()) payload["message"] = dataMessage
    event.data.optStringOrEmpty("title").takeIf { it.isNotEmpty() && !payload.containsKey("title") }
        ?.let { payload["title"] = it }
    val normalized = message.copy(
        content = message.content.ifBlank { dataMessage }.ifBlank { summary },
        payload = payload,
    )
    val index = if (subtaskId.isNotEmpty()) {
        state.messages.indexOfFirst { it.kind == "subtask" && subtaskIdOf(it) == subtaskId }
    } else {
        state.messages.indexOfFirst { it.id == normalized.id }
    }
    if (index >= 0) {
        val messages = state.messages.toMutableList()
        messages[index] = normalized
        return state.copy(messages = messages)
    }
    return state.copy(messages = insertMessage(state.messages, normalized))
}

/**
 * 应用一条流事件；纯函数，便于单测。
 *
 * - `seq <= lastSeq` 视为重放，直接忽略；
 * - `assistant_delta` 追加到流式气泡，`assistant_message` 用最终消息替换气泡（不重复正文）；
 * - `subtask_update` 按 subtask_id 归并同一张卡。
 */
fun applyConversationEvent(state: ChatStreamState, event: TaskStreamEvent): ChatStreamState {
    if (event.seq <= state.cursor.lastSeq) return state
    val base = state.copy(cursor = state.cursor.copy(lastSeq = event.seq))
    return when (event.type) {
        "assistant_start" -> applyAssistantStart(base, event)
        "assistant_delta" -> applyAssistantDelta(base, event)
        "assistant_message" -> applyAssistantMessage(base, event)
        "user_message" -> applyUserMessage(base, event)
        "subtask_update" -> applySubtaskUpdate(base, event)
        "assistant_error" -> base.copy(
            messages = withoutStreaming(base.messages),
            cursor = base.cursor.copy(streaming = false, streamTurnId = ""),
        )
        else -> base
    }
}

/** 服务端历史是真相源：整体替换消息，但保留续传游标。 */
fun setConversationMessages(
    state: ChatStreamState,
    messages: List<RemoteMessage>,
): ChatStreamState = state.copy(messages = mergeSubtaskMessages(messages))

fun appendLocalMessage(state: ChatStreamState, message: RemoteMessage): ChatStreamState =
    state.copy(messages = state.messages + message)

fun removeMessage(state: ChatStreamState, messageId: String): ChatStreamState =
    state.copy(messages = state.messages.filterNot { it.id == messageId })

/** 本机发起新回合：清掉旧气泡，并允许接收该回合的增量。 */
fun beginLocalTurn(state: ChatStreamState, turnId: String): ChatStreamState = state.copy(
    messages = withoutStreaming(state.messages),
    cursor = state.cursor.copy(
        activeTurnId = turnId,
        streaming = false,
        streamTurnId = "",
    ),
)

fun isStreaming(messages: List<RemoteMessage>): Boolean =
    messages.any { it.id == STREAMING_MESSAGE_ID }

/** 当前流式气泡的文本；没有气泡时为空串。 */
fun streamingText(state: ChatStreamState): String =
    state.messages.firstOrNull { it.id == STREAMING_MESSAGE_ID }?.content.orEmpty()

// ---------- 与 App 消息模型互换 ----------

fun RemoteMessage.toAppMessage(time: String): Message = Message(
    id = id,
    role = if (role == "user") "user" else "bot",
    text = content,
    time = time,
    component = component,
    kind = kind,
    fallbackText = fallbackText,
    payload = payload,
)

fun Message.toRemoteMessage(): RemoteMessage = RemoteMessage(
    id = id,
    role = if (role == "user") "user" else "assistant",
    content = text,
    kind = kind,
    component = component,
    fallbackText = fallbackText,
    payload = payload,
)

/**
 * 用服务端历史替换本地消息时，保留本机作答过的选择卡状态，
 * 以及 App 侧补出来的例程卡片（历史接口里没有它们）。
 */
fun reconcileHistory(fresh: List<Message>, existing: List<Message>): List<Message> {
    if (existing.isEmpty()) return fresh
    val byId = existing.associateBy { it.id }
    val byText = existing.groupBy { it.role to it.displayText().trim() }
    val merged = fresh.map { message ->
        val old = byId[message.id]
            ?: byText[message.role to message.displayText().trim()]?.firstOrNull()
        val local = old?.payload?.filterKeys { it.startsWith("choice_") }.orEmpty()
        if (local.isEmpty()) message else message.copy(payload = local + message.payload)
    }
    val extras = existing.filter { old ->
        old.kind == "routine_created" && merged.none { it.id == old.id }
    }
    return merged + extras
}
