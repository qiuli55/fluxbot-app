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
