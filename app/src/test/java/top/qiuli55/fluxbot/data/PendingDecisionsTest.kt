package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B4：子任务上的待批准决策也必须能被聊天里的审批卡收集到。 */
class PendingDecisionsTest {

    private fun task(id: String, pending: JSONObject?): JSONObject = JSONObject().put("id", id).apply {
        if (pending != null) put("pending_decision", pending)
    }

    private fun pending(id: String, kind: String = "delete_data") = JSONObject()
        .put("id", id)
        .put("question", "删除 victim-a？")
        .put("kind", kind)
        .put("options", JSONArray().put(JSONObject().put("label", "允许")).put("拒绝"))

    @Test
    fun collectsDecisionsFromParentAndSubtaskTasks() {
        val tasks = JSONArray()
            .put(task("chat-task", pending("d-parent")))
            .put(task("plain-task", null))
        val subtasks = JSONArray()
            .put(task("subtask-a", pending("d-sub-a")))
            .put(task("subtask-b", pending("d-sub-b", kind = "high_risk_command")))
            .put(task("subtask-done", null))

        val found = parsePendingDecisionTasks(tasks) + parsePendingDecisionTasks(subtasks)

        assertEquals(
            listOf("d-parent", "d-sub-a", "d-sub-b"),
            found.map { it.decisionId },
        )
        // 子任务决策保留自己的 task id：审批必须回到真正在跑的工作上。
        assertEquals("subtask-a", found[1].taskId)
        assertEquals("high_risk_command", found[2].kind)
        assertEquals(listOf("允许", "拒绝"), found[0].options)
    }

    @Test
    fun ignoresMalformedEntriesAndMissingDecision() {
        val tasks = JSONArray()
            .put(JSONObject().put("pending_decision", pending("no-task-id")))
            .put(task("no-decision", null))
            .put(task("empty-decision", JSONObject()))
            .put("not-an-object")

        assertTrue(parsePendingDecisionTasks(tasks).isEmpty())
    }

    @Test
    fun parsesRealTimeoutForNormalAndSubtaskDecisions() {
        val decision = pending("d-timeout")
            .put("timeout_seconds", 300)
            .put("expires_at", "2026-11-16T10:05:00+00:00")
        val normal = parsePendingDecisionTasks(JSONArray().put(task("chat-task", decision))).single()
        val subtask = parsePendingDecisionTasks(JSONArray().put(task("subtask-1", decision))).single()

        assertEquals(300, normal.timeoutSeconds)
        assertEquals("2026-11-16T10:05:00+00:00", normal.expiresAt)
        assertEquals(normal.timeoutSeconds, subtask.timeoutSeconds)
        assertEquals(normal.expiresAt, subtask.expiresAt)
    }

    @Test
    fun oldBackendWithoutTimeoutStaysCompatible() {
        val parsed = parsePendingDecisionTasks(
            JSONArray().put(task("chat-task", pending("d-old"))),
        ).single()

        assertEquals(null, parsed.timeoutSeconds)
        assertEquals("", parsed.expiresAt)
        // 原有字段仍按老契约解析。
        assertEquals("删除 victim-a？", parsed.question)
        assertEquals("delete_data", parsed.kind)
    }

    @Test
    fun parsesPlainSummaryAndAbnormalReasonForNormalAndSubtask() {
        val decision = pending("d-summary", kind = "payment_abnormal")
            .put("summary", "付款 350 元给 张三")
            .put("summary_reason", "陌生收款方、10 分钟内第 3 次付款")
        val normal = parsePendingDecisionTasks(JSONArray().put(task("chat-task", decision))).single()
        val subtask = parsePendingDecisionTasks(JSONArray().put(task("subtask-1", decision))).single()

        assertEquals("付款 350 元给 张三", normal.summary)
        assertEquals("陌生收款方、10 分钟内第 3 次付款", normal.summaryReason)
        assertEquals(normal.summary, subtask.summary)
        assertEquals(normal.summaryReason, subtask.summaryReason)
    }

    @Test
    fun oldBackendWithoutSummaryLeavesItEmptyForFallbackTitle() {
        val parsed = parsePendingDecisionTasks(
            JSONArray().put(task("chat-task", pending("d-old-summary"))),
        ).single()

        assertEquals("", parsed.summary)
        assertEquals("", parsed.summaryReason)
    }

    @Test
    fun parsesAllPendingDecisionsFromListField() {
        // 新后端：一条任务上同时挂多张卡，列表字段优先于旧的单值字段。
        val task = JSONObject()
            .put("id", "task-many")
            .put(
                "pending_decisions",
                JSONArray().put(pending("d-1")).put(pending("d-2", kind = "high_risk_command")),
            )
            .put("pending_decision", pending("d-1"))

        val found = parsePendingDecisionTasks(JSONArray().put(task))

        assertEquals(listOf("d-1", "d-2"), found.map { it.decisionId })
        assertEquals("high_risk_command", found[1].kind)
        assertEquals(listOf("task-many", "task-many"), found.map { it.taskId })
    }

    @Test
    fun emptyPendingDecisionsListDoesNotFallBackToSingleField() {
        // 列表字段存在即为权威：空列表表示这张任务当前没有待决策。
        val task = JSONObject()
            .put("id", "task-empty")
            .put("pending_decisions", JSONArray())
            .put("pending_decision", pending("d-stale"))

        assertTrue(parsePendingDecisionTasks(JSONArray().put(task)).isEmpty())
    }

    @Test
    fun raisedAtIsParsedForOrdering() {
        val decision = pending("d-raised").put("raised_at", "2026-11-16T10:00:00+00:00")
        val parsed = parsePendingDecisionTasks(JSONArray().put(task("t", decision))).single()

        assertEquals("2026-11-16T10:00:00+00:00", parsed.raisedAt)
        assertEquals(
            approvalTimeMillis("2026-11-16T10:00:00+00:00"),
            approvalRaisedAtMillis(parsed),
        )
    }

    @Test
    fun mergeDedupesByIdAndOrdersOldestFirst() {
        val recent = pending("d-recent").toDecision("t", raisedAt = "2026-11-16T10:05:00+00:00")
        val old = pending("d-old").toDecision("t", raisedAt = "2026-11-16T09:55:00+00:00")
        val same = recent.copy(question = "刷新后的同一张卡")

        val merged = mergePendingDecisions(
            listOf(listOf(recent, old), listOf(same)),
        )

        assertEquals(listOf("d-old", "d-recent"), merged.map { it.decisionId })
        // 后出现的同 id 记录覆盖先出现的（轮询 / 事件带来的新字段生效）。
        assertEquals("刷新后的同一张卡", merged[1].question)
    }

    @Test
    fun decisionsWithoutRaisedTimeSortLastAndKeepOriginalOrder() {
        val unknownA = pending("d-unknown-a").toDecision("t")
        val known = pending("d-known").toDecision("t", raisedAt = "2026-11-16T10:00:00+00:00")
        val unknownB = pending("d-unknown-b").toDecision("t")

        val ordered = orderPendingDecisions(listOf(unknownA, known, unknownB))

        assertEquals(listOf("d-known", "d-unknown-a", "d-unknown-b"), ordered.map { it.decisionId })
    }

    @Test
    fun stablePageFollowsAnchorAndAdvancesAfterResolve() {
        val a = pending("a").toDecision("t")
        val b = pending("b").toDecision("t")
        val c = pending("c").toDecision("t")
        val inserted = pending("new").toDecision("t")

        // 新卡插到最前面：用户正在看 b，页码跟着 b 从 1 变 2，而不是停在 1。
        assertEquals(2, stablePendingPage(listOf(inserted, a, b, c), "b", 1))
        // b 被解决：列表里只剩 a、c，原来第 1 页由 c 顶上。
        assertEquals(1, stablePendingPage(listOf(a, c), "b", 1))
        // 最后一页的卡被解决：收敛到新的最后一页。
        assertEquals(1, stablePendingPage(listOf(a, c), "d", 2))
        // 空列表：回到 0。
        assertEquals(0, stablePendingPage(emptyList(), "x", 3))
    }

    @Test
    fun positionLabelOnlyShowsWhenMultipleCards() {
        assertEquals(null, approvalPositionLabel(0, 1))
        assertEquals("1/2", approvalPositionLabel(0, 2))
        assertEquals("2/2", approvalPositionLabel(1, 2))
        // 越界页号不会写出 3/2 这种文案。
        assertEquals("2/2", approvalPositionLabel(5, 2))
    }

    @Test
    fun firstShownLogCarriesSourceAndLatency() {
        val raised = pending("d-log").toDecision(
            "t",
            raisedAt = "2026-11-16T10:00:00+00:00",
        )
        val line = firstShownApprovalLog(
            raised,
            "sse.raised",
            approvalTimeMillis("2026-11-16T10:00:01.500+00:00")!!,
        )

        assertTrue(line.contains("decision=d-log"))
        assertTrue(line.contains("source=sse.raised"))
        assertTrue(line.contains("latency_ms=1500"))
    }

    @Test
    fun firstShownLogFallsBackToExpiresMinusTimeout() {
        // 旧后端没有 raised_at：用 expires_at - timeout_seconds 反推发起时刻。
        val decision = pending("d-exp").toDecision(
            "t",
            raisedAt = "",
            expiresAt = "2026-11-16T10:05:00+00:00",
            timeoutSeconds = 300,
        )
        val line = firstShownApprovalLog(
            decision,
            "poll",
            approvalTimeMillis("2026-11-16T10:00:02+00:00")!!,
        )

        assertTrue(line.contains("source=poll"))
        assertTrue(line.contains("latency_ms=2000"))
    }

    @Test
    fun firstShownLogWithoutTimingUsesMinusOne() {
        val line = firstShownApprovalLog(
            pending("d-unknown").toDecision("t"),
            "poll",
            1_700_000_000_000L,
        )

        assertTrue(line.contains("latency_ms=-1"))
    }

    private fun JSONObject.toDecision(
        taskId: String,
        raisedAt: String = "",
        expiresAt: String = "",
        timeoutSeconds: Int? = null,
    ): PendingDecision {
        val parsed = parsePendingDecision(taskId, this)!!
        return parsed.copy(
            raisedAt = raisedAt,
            expiresAt = expiresAt,
            timeoutSeconds = timeoutSeconds,
        )
    }

    @Test
    fun subtaskDecisionKeepsDetailAndMeta() {
        val decision = JSONObject()
            .put("id", "d-1")
            .put("question", "执行 rm -rf victim-a？")
            .put("context", "命令：rm -rf victim-a")
            .put("kind", "delete_data")
            .put("meta", JSONObject().put("tool_name", "Bash"))

        val parsed = parsePendingDecisionTasks(
            JSONArray().put(task("subtask-1", decision)),
        ).single()

        assertEquals("subtask-1", parsed.taskId)
        assertEquals("命令：rm -rf victim-a", parsed.detail)
        assertEquals("Bash", parsed.meta["tool_name"])
    }
}
