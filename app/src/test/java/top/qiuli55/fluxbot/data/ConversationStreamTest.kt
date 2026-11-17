package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话事件流的纯逻辑：SSE 解析 / 事件应用 / 去重续传 / 子任务归并。
 *
 * 全部在 JVM 上跑，不需要 Android、Compose 或真实服务器。
 */
class ConversationStreamTest {

    private fun event(seq: Long, type: String, data: JSONObject = JSONObject()): TaskStreamEvent =
        TaskStreamEvent(seq, type, data)

    private fun run(
        events: List<TaskStreamEvent>,
        state: ChatStreamState = createChatStreamState(),
    ): ChatStreamState = events.fold(state) { acc, item -> applyConversationEvent(acc, item) }

    private fun jsonMessage(
        id: String,
        role: String = "assistant",
        content: String = "",
        kind: String = "text",
        payload: JSONObject? = null,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("role", role)
        .put("kind", kind)
        .put("content", content)
        .apply { payload?.let { put("payload", it) } }

    private fun subtaskMessage(
        id: String,
        subtaskId: String,
        status: String,
        content: String,
        payload: JSONObject = JSONObject(),
    ): RemoteMessage = RemoteMessage(
        id = id,
        role = "assistant",
        kind = "subtask",
        content = content,
        payload = mapOf(
            "subtask_id" to subtaskId,
            "status" to status,
            "title" to "整理发布记录",
        ) + payload.keys().asSequence().associateWith { payload.optString(it) },
    )

    // ---------- SSE ----------

    @Test
    fun parsesIdEventAndData() {
        val decoder = SseDecoder()
        val frames = decoder.push(
            "id: 12\nevent: conversation.event\ndata: {\"seq\":12,\"type\":\"assistant_delta\"}\n\n",
        )
        assertEquals(1, frames.size)
        assertEquals("12", frames[0].id)
        assertEquals("conversation.event", frames[0].event)
        assertEquals("{\"seq\":12,\"type\":\"assistant_delta\"}", frames[0].data)
    }

    @Test
    fun ignoresKeepAliveComments() {
        val decoder = SseDecoder()
        assertTrue(decoder.push(": keep-alive\n\n").isEmpty())
        val frames = decoder.push("event: turn.event\ndata: {\"seq\":1}\n\n")
        assertEquals(1, frames.size)
        assertEquals("turn.event", frames[0].event)
        assertNull(frames[0].id)
    }

    @Test
    fun joinsMultiLineDataAndStripsOneLeadingSpace() {
        val decoder = SseDecoder()
        val frames = decoder.push("data: a\ndata:b\n\n")
        assertEquals(1, frames.size)
        assertEquals("a\nb", frames[0].data)
    }

    @Test
    fun handlesChunksSplitAcrossCrlfBoundaries() {
        val decoder = SseDecoder()
        assertTrue(decoder.push("id: 1\r\nevent: conversation.event\r\n").isEmpty())
        val frames = decoder.push("data: {}\r\n\r\n")
        assertEquals(1, frames.size)
        assertEquals("1", frames[0].id)
        assertEquals("conversation.event", frames[0].event)
        assertEquals("{}", frames[0].data)
    }

    @Test
    fun flushesTrailingFrameWithoutBlankLine() {
        val decoder = SseDecoder()
        assertTrue(decoder.push("data: {\"seq\":2}").isEmpty())
        val frame = decoder.flush()
        assertEquals("{\"seq\":2}", frame?.data)
        assertNull(decoder.flush())
    }

    // ---------- 事件解析 ----------

    @Test
    fun parsesServerEventShapeAndRejectsIncompleteFrames() {
        val parsed = parseTaskStreamEvent("{\"seq\":3,\"type\":\"assistant_delta\",\"data\":{\"text\":\"你\"}}")
        assertEquals(3L, parsed?.seq)
        assertEquals("assistant_delta", parsed?.type)
        assertEquals("你", parsed?.data?.optString("text"))

        assertNull(parseTaskStreamEvent("{\"type\":\"assistant_delta\",\"data\":{}}"))
        assertNull(parseTaskStreamEvent("{\"seq\":1,\"data\":{}}"))
        assertNull(parseTaskStreamEvent("not json"))
    }

    // ---------- 助手流式输出 ----------

    @Test
    fun appendsDeltasAndFinalisesWithoutDoublingText() {
        var state = run(
            listOf(
                event(1, "assistant_start", JSONObject().put("turn_id", "turn-1")),
                event(2, "assistant_delta", JSONObject().put("turn_id", "turn-1").put("text", "你")),
                event(3, "assistant_delta", JSONObject().put("turn_id", "turn-1").put("text", "好")),
            ),
        )
        assertEquals(1, state.messages.size)
        assertEquals(STREAMING_MESSAGE_ID, state.messages[0].id)
        assertEquals("你好", state.messages[0].content)

        state = applyConversationEvent(
            state,
            event(
                4,
                "assistant_message",
                JSONObject()
                    .put("turn_id", "turn-1")
                    .put("message", jsonMessage("server-reply", content = "你好")),
            ),
        )
        assertEquals(1, state.messages.size)
        assertEquals("server-reply", state.messages[0].id)
        assertEquals("你好", state.messages[0].content)
        assertFalse(state.messages.any { it.id == STREAMING_MESSAGE_ID })
        assertFalse(state.cursor.streaming)
    }

    @Test
    fun acceptsDeltaEvenIfAssistantStartWasMissed() {
        val state = applyConversationEvent(
            createChatStreamState(),
            event(1, "assistant_delta", JSONObject().put("turn_id", "turn-1").put("text", "进行中")),
        )
        assertEquals("进行中", state.messages[0].content)
    }

    @Test
    fun ignoresDeltasFromAnotherTurn() {
        var state = beginLocalTurn(createChatStreamState(), "turn-2")
        state = applyConversationEvent(
            state,
            event(1, "assistant_delta", JSONObject().put("turn_id", "turn-1").put("text", "旧")),
        )
        assertTrue(state.messages.isEmpty())
        state = applyConversationEvent(
            state,
            event(2, "assistant_delta", JSONObject().put("turn_id", "turn-2").put("text", "新")),
        )
        assertEquals("新", state.messages[0].content)
    }

    @Test
    fun dropsStaleStreamingBubbleWhenNewLocalTurnStarts() {
        var state = run(
            listOf(
                event(1, "assistant_start", JSONObject().put("turn_id", "old")),
                event(2, "assistant_delta", JSONObject().put("turn_id", "old").put("text", "旧")),
            ),
        )
        state = beginLocalTurn(state, "new")
        assertTrue(state.messages.isEmpty())
    }

    // ---------- assistant_message 兼容性 ----------

    @Test
    fun rendersAssistantMessageWithoutQuestionsStepsOrConfirmation() {
        val state = run(
            listOf(
                event(
                    1,
                    "assistant_message",
                    JSONObject().put(
                        "message",
                        jsonMessage(
                            "r1",
                            content = "已经安排好了",
                            payload = JSONObject().put("model", JSONObject()),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("已经安排好了", state.messages[0].content)
        assertNull(state.messages[0].component)
    }

    @Test
    fun keepsOldPayloadsWithLegacyFieldsWorking() {
        val payload = JSONObject()
            .put("questions", JSONArray().put("要现在执行吗？"))
            .put("steps", JSONArray().put("读取仓库").put("汇总"))
            .put("confirmation", JSONArray().put(JSONObject().put("title", "执行")))
        val state = run(
            listOf(
                event(
                    1,
                    "assistant_message",
                    JSONObject().put(
                        "message",
                        jsonMessage("r2", content = "给你两个选择", payload = payload),
                    ),
                ),
            ),
        )
        assertEquals("给你两个选择", state.messages[0].content)
        assertTrue(state.messages[0].payload.containsKey("questions"))
    }

    @Test
    fun keepsComponentEnvelopeFromAssistantMessage() {
        val envelope = JSONObject()
            .put("schema_version", 1)
            .put(
                "component",
                JSONObject().put("type", "text").put("text", "请选择").put("markdown", true),
            )
            .put("fallback_text", "请选择")
        val state = run(
            listOf(
                event(
                    1,
                    "assistant_message",
                    JSONObject().put(
                        "message",
                        jsonMessage("r3", content = "请选择", payload = envelope),
                    ),
                ),
            ),
        )
        assertEquals("请选择", state.messages[0].content)
        assertTrue(state.messages[0].component != null)
    }

    // ---------- 去重 / 续传 ----------

    @Test
    fun ignoresDuplicateAndOlderSeqValues() {
        val first = jsonMessage("u1", role = "user", content = "一")
        var state = applyConversationEvent(
            createChatStreamState(),
            event(5, "user_message", JSONObject().put("message", first)),
        )
        assertEquals(5L, state.cursor.lastSeq)
        assertEquals(1, state.messages.size)

        val duplicate = applyConversationEvent(
            state,
            event(5, "user_message", JSONObject().put("message", first)),
        )
        assertSame(state, duplicate)

        val older = applyConversationEvent(
            state,
            event(4, "user_message", JSONObject().put("message", jsonMessage("u0", "user", "零"))),
        )
        assertSame(state, older)

        state = applyConversationEvent(
            state,
            event(6, "user_message", JSONObject().put("message", jsonMessage("u2", "user", "二"))),
        )
        assertEquals(2, state.messages.size)
        assertEquals(6L, state.cursor.lastSeq)
    }

    @Test
    fun deduplicatesReplayedAssistantMessageById() {
        val reply = jsonMessage("reply-9", content = "收到")
        var state = run(
            listOf(
                event(1, "assistant_message", JSONObject().put("message", reply)),
                event(2, "assistant_message", JSONObject().put("message", reply)),
            ),
        )
        assertEquals(1, state.messages.size)

        state = applyConversationEvent(
            state,
            event(
                7,
                "assistant_message",
                JSONObject().put("message", jsonMessage("reply-9", content = "收到！")),
            ),
        )
        assertEquals(1, state.messages.size)
        assertEquals("收到！", state.messages[0].content)
        assertEquals(7L, state.cursor.lastSeq)
    }

    // ---------- 用户消息 ----------

    @Test
    fun replacesLocalOptimisticUserMessageByContent() {
        var state = appendLocalMessage(
            createChatStreamState(),
            RemoteMessage(id = "local-1", role = "user", content = "派个活"),
        )
        state = applyConversationEvent(
            state,
            event(
                1,
                "user_message",
                JSONObject().put("message", jsonMessage("u1", role = "user", content = "派个活")),
            ),
        )
        assertEquals(1, state.messages.size)
        assertEquals("u1", state.messages[0].id)
    }

    // ---------- 子任务 ----------

    @Test
    fun mergesSubtaskEventsBySubtaskId() {
        val state = run(
            listOf(
                event(
                    1,
                    "subtask_update",
                    JSONObject()
                        .put("subtask_id", "st-1")
                        .put("status", "running")
                        .put(
                            "message",
                            jsonMessage(
                                "m1",
                                content = "后台任务「整理」已开始。",
                                kind = "subtask",
                                payload = JSONObject().put("subtask_id", "st-1"),
                            ),
                        ),
                ),
                event(
                    2,
                    "subtask_update",
                    JSONObject()
                        .put("subtask_id", "st-1")
                        .put("status", "completed")
                        .put(
                            "message",
                            jsonMessage(
                                "m3",
                                content = "后台任务「整理」已完成：\n发布了 3 条",
                                kind = "subtask",
                                payload = JSONObject().put("subtask_id", "st-1"),
                            ),
                        ),
                ),
                event(
                    3,
                    "subtask_update",
                    JSONObject()
                        .put("subtask_id", "st-2")
                        .put("status", "running")
                        .put(
                            "message",
                            jsonMessage(
                                "m4",
                                content = "另一条子任务",
                                kind = "subtask",
                                payload = JSONObject().put("subtask_id", "st-2"),
                            ),
                        ),
                ),
            ),
        )
        assertEquals(2, state.messages.size)
        assertEquals("m3", state.messages[0].id)
        assertEquals("后台任务「整理」已完成：\n发布了 3 条", state.messages[0].content)
        assertEquals("completed", state.messages[0].payload["status"])
        assertEquals("st-2", state.messages[1].payload["subtask_id"])
    }

    @Test
    fun toleratesSubtaskUpdateWithoutMessageObject() {
        val state = run(
            listOf(
                event(
                    1,
                    "user_message",
                    JSONObject().put("message", jsonMessage("u1", role = "user", content = "你好")),
                ),
                event(
                    2,
                    "subtask_update",
                    JSONObject()
                        .put("subtask_id", "st-9")
                        .put("status", "failed")
                        .put("summary", "启动失败"),
                ),
            ),
        )
        assertEquals(listOf("u1", "subtask-st-9"), state.messages.map { it.id })
        assertEquals("启动失败", state.messages[1].content)
        assertEquals("failed", state.messages[1].payload["status"])
    }

    @Test
    fun prefersEventMessageTextForSubtaskCard() {
        val state = run(
            listOf(
                event(
                    1,
                    "subtask_update",
                    JSONObject()
                        .put("subtask_id", "st-3")
                        .put("status", "completed")
                        .put("message", "从事件里带回来的结果")
                        .put("summary", "旧摘要"),
                ),
            ),
        )
        assertEquals("从事件里带回来的结果", state.messages[0].content)
        assertEquals("从事件里带回来的结果", state.messages[0].payload["message"])
    }

    @Test
    fun collapsesRepeatedSubtaskHistoryMessagesAtLoadTime() {
        val collapsed = mergeSubtaskMessages(
            listOf(
                RemoteMessage(id = "u1", role = "user", content = "派个活"),
                subtaskMessage("m1", "st-1", "running", "已开始"),
                subtaskMessage("m2", "st-1", "completed", "已完成"),
            ),
        )
        assertEquals(2, collapsed.size)
        assertEquals("m2", collapsed[1].id)
        assertEquals("已完成", collapsed[1].content)
    }

    @Test
    fun setConversationMessagesAlsoMergesSubtasks() {
        val state = setConversationMessages(
            createChatStreamState(),
            listOf(
                subtaskMessage("m1", "st-1", "running", "已开始"),
                subtaskMessage("m2", "st-1", "completed", "已完成"),
            ),
        )
        assertEquals(1, state.messages.size)
        assertEquals("已完成", state.messages[0].content)
    }

    // ---------- 与 App 消息模型互换 ----------

    @Test
    fun convertsBetweenAppAndStreamMessages() {
        val app = Message(
            id = "h-1",
            role = "bot",
            text = "你好",
            time = "09:30",
            kind = "subtask",
            payload = mapOf("subtask_id" to "st-1"),
        )
        val remote = app.toRemoteMessage()
        assertEquals("assistant", remote.role)
        assertEquals("你好", remote.content)
        val back = remote.toAppMessage("10:00")
        assertEquals("bot", back.role)
        assertEquals("09:30", app.time)
        assertEquals("10:00", back.time)
        assertEquals("st-1", back.payload["subtask_id"])
    }
}
