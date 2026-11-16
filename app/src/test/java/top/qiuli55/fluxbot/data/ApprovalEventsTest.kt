package top.qiuli55.fluxbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bot 级审批事件流：SSE 分帧与 ready / raised 两种载荷的解析。 */
class ApprovalEventsTest {

    @Test
    fun parsesReadyFrameWithPendingSnapshot() {
        val block = """
            event: ready
            data: {"bot_id":"bot-1","pending_count":2,"pending":[
            data: {"task_id":"task-1","decision":{"id":"d-1","question":"批准？","kind":"delete_data"}},
            data: {"task_id":"sub-1","decision":{"id":"d-2","question":"再批准？","kind":"high_risk_command"}}]}
        """.trimIndent()

        val event = parseApprovalEventFrame(block)!!
        val decisions = approvalEventDecisions(event)

        assertEquals("ready", event.type)
        assertEquals(listOf("d-1", "d-2"), decisions.map { it.decisionId })
        assertEquals(listOf("task-1", "sub-1"), decisions.map { it.taskId })
        assertEquals("high_risk_command", decisions[1].kind)
    }

    @Test
    fun parsesRaisedFrameWithFlatCardFields() {
        val block = """
            event: approval.raised
            data: {"task_id":"task-9","decision_id":"d-9","kind":"delete_data",
            data: "summary":"删除整个文件夹 tmp","raised_at":"2026-11-16T10:00:00+00:00",
            data: "timeout_seconds":300,"expires_at":"2026-11-16T10:05:00+00:00",
            data: "options":[{"label":"允许"},{"label":"拒绝"}]}
        """.trimIndent()

        val event = parseApprovalEventFrame(block)!!
        val decision = approvalEventDecisions(event).single()

        assertEquals("approval.raised", event.type)
        assertEquals("d-9", decision.decisionId)
        assertEquals("task-9", decision.taskId)
        assertEquals("删除整个文件夹 tmp", decision.summary)
        assertEquals(300, decision.timeoutSeconds)
        assertEquals(listOf("允许", "拒绝"), decision.options)
    }

    @Test
    fun resolvedFrameCarriesNoDecisions() {
        val event = parseApprovalEventFrame(
            "event: approval.resolved\ndata: {\"task_id\":\"t\",\"decision_id\":\"d\"}",
        )!!

        assertEquals("approval.resolved", event.type)
        assertTrue(approvalEventDecisions(event).isEmpty())
    }

    @Test
    fun ignoresKeepAliveCommentsAndBrokenPayloads() {
        assertNull(parseApprovalEventFrame(""))
        assertNull(parseApprovalEventFrame(": keep-alive"))
        assertNull(parseApprovalEventFrame("event: ready"))
        // data 不是合法 JSON：丢弃这一帧，不要打断整条流。
        assertNull(parseApprovalEventFrame("event: ready\ndata: {not-json"))
        // 缺 event 名同样丢弃。
        assertNull(parseApprovalEventFrame("data: {\"pending\":[]}"))
    }

    @Test
    fun raisedFrameWithoutIdentityIsIgnored() {
        val event = parseApprovalEventFrame(
            "event: approval.raised\ndata: {\"decision_id\":\"d-1\"}",
        )!!

        assertTrue(approvalEventDecisions(event).isEmpty())
    }

    @Test
    fun crlfFramesParseTheSameAsLf() {
        val event = parseApprovalEventFrame(
            "event: approval.raised\r\ndata: {\"task_id\":\"t\",\"decision_id\":\"d\"}\r\n",
        )

        assertEquals("d", approvalEventDecisions(event!!).single().decisionId)
    }
}
