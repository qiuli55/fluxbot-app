package top.qiuli55.fluxbot.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.qiuli55.fluxbot.data.PendingDecision

/** 审批卡纯状态映射：标题、操作行、字母徽标、超时文案与可点性。 */
class ApprovalCardStateTest {

    private fun decision(
        kind: String = "delete_data",
        detail: String = "",
        options: List<String> = listOf("允许", "拒绝"),
        meta: Map<String, String> = emptyMap(),
        timeoutSeconds: Int? = 300,
    ) = PendingDecision(
        taskId = "task-1",
        decisionId = "dec-1",
        question = "任意问题",
        detail = detail,
        options = options,
        kind = kind,
        meta = meta,
        timeoutSeconds = timeoutSeconds,
    )

    // ---------- 状态 ----------

    @Test
    fun pendingStateIsTappableWithRealTimeoutFooter() {
        val ui = approvalCardUi(decision(), ApprovalCardStatus.Pending)
        assertFalse(ui.muted)
        assertTrue(ui.tappable)
        assertNull(ui.chosenLabel)
        assertNull(ui.error)
        assertEquals("仅本次有效 · 5 分钟无响应自动拒绝", ui.footer)
    }

    @Test
    fun approvedStateIsMutedAndNotTappable() {
        val ui = approvalCardUi(
            decision(),
            ApprovalCardStatus.Answered(chosen = "允许", allowed = true),
        )
        assertTrue(ui.muted)
        assertFalse(ui.tappable)
        assertEquals("允许", ui.chosenLabel)
        assertEquals("已允许", ui.footer)
    }

    @Test
    fun deniedStateIsMutedAndNotTappable() {
        val ui = approvalCardUi(
            decision(),
            ApprovalCardStatus.Answered(chosen = "拒绝", allowed = false),
        )
        assertTrue(ui.muted)
        assertFalse(ui.tappable)
        assertEquals("拒绝", ui.chosenLabel)
        assertEquals("已拒绝", ui.footer)
    }

    @Test
    fun timedOutStateShowsAutoDeniedFooter() {
        val ui = approvalCardUi(decision(), ApprovalCardStatus.TimedOut)
        assertTrue(ui.muted)
        assertFalse(ui.tappable)
        assertNull(ui.chosenLabel)
        assertEquals("已超时，已自动拒绝", ui.footer)
    }

    @Test
    fun failedSubmissionStaysPendingWithRetryableError() {
        val ui = approvalCardUi(decision(), ApprovalCardStatus.Failed("提交失败，请重试"))
        assertFalse(ui.muted)
        assertTrue(ui.tappable)
        assertEquals("提交失败，请重试", ui.error)
        assertEquals("仅本次有效 · 5 分钟无响应自动拒绝", ui.footer)
    }

    @Test
    fun submittingStateIsNotTappable() {
        assertFalse(approvalCardUi(decision(), ApprovalCardStatus.Submitting).tappable)
    }

    // ---------- 标题 ----------

    @Test
    fun titleFollowsApprovalKind() {
        assertEquals("删除资料需要你确认", approvalTitle("delete_data", emptyMap()))
        assertEquals("大额付款需要你确认", approvalTitle("payment_amount", emptyMap()))
        assertEquals("大额付款需要你确认", approvalTitle("large_payment", emptyMap()))
        assertEquals("异常支付需要你确认", approvalTitle("payment_abnormal", emptyMap()))
        assertEquals("异常支付需要你确认", approvalTitle("abnormal_payment", emptyMap()))
        assertEquals("用户规则需要你确认", approvalTitle("user_rule", emptyMap()))
        assertEquals("Bot 规则需要你确认", approvalTitle("bot_rule", emptyMap()))
    }

    @Test
    fun ruleMatchTitleUsesRuleCreator() {
        assertEquals(
            "用户规则需要你确认",
            approvalTitle("rule_match", mapOf("approval_rule_created_by_kind" to "user")),
        )
        assertEquals(
            "Bot 规则需要你确认",
            approvalTitle("rule_match", mapOf("approval_rule_created_by_kind" to "bot")),
        )
        // 旧后端没带 creator 时按用户规则展示。
        assertEquals("用户规则需要你确认", approvalTitle("rule_match", emptyMap()))
    }

    @Test
    fun unknownKindFallsBackToNeutralTitle() {
        assertEquals("需要你确认", approvalTitle("", emptyMap()))
        assertEquals("需要你确认", approvalTitle("something_new", emptyMap()))
        assertEquals(
            "发送消息需要你确认",
            approvalTitle("feishu_outbound", mapOf("channel" to "feishu")),
        )
    }

    // ---------- 时长 ----------

    @Test
    fun formatsTimeoutSensibly() {
        assertEquals("5 分钟", formatApprovalDuration(300))
        assertEquals("1 分 30 秒", formatApprovalDuration(90))
        assertEquals("59 秒", formatApprovalDuration(59))
        assertEquals("45 秒", formatApprovalDuration(45))
        assertNull(formatApprovalDuration(null))
        assertNull(formatApprovalDuration(0))
        assertNull(formatApprovalDuration(-1))
    }

    @Test
    fun missingTimeoutOmitsDurationInsteadOfHardCodingTenMinutes() {
        val ui = approvalCardUi(decision(timeoutSeconds = null), ApprovalCardStatus.Pending)
        assertEquals("仅本次有效", ui.footer)
        assertFalse(ui.footer.contains("10 分钟"))
    }

    // ---------- 选项与操作行 ----------

    @Test
    fun lettersFollowOptionOrder() {
        val ui = approvalCardUi(
            decision(options = listOf("允许", "拒绝", "仅本次")),
            ApprovalCardStatus.Pending,
        )
        assertEquals(
            listOf("A" to "允许", "B" to "拒绝", "C" to "仅本次"),
            ui.options.map { it.letter to it.label },
        )
    }

    @Test
    fun emptyOptionsFallBackToAllowDeny() {
        val ui = approvalCardUi(decision(options = emptyList()), ApprovalCardStatus.Pending)
        assertEquals(listOf("A" to "允许", "B" to "拒绝"), ui.options.map { it.letter to it.label })
    }

    @Test
    fun operationPrefersCommandMetaThenDetail() {
        assertEquals(
            "rm -rf e2e-victim-b",
            approvalOperation(
                decision(meta = mapOf("command" to "  rm -rf e2e-victim-b  ")),
            ),
        )
        assertEquals(
            "rm -rf victim-a",
            approvalOperation(decision(detail = "Bot 准备执行\n命令：rm -rf victim-a\n风险：删除")),
        )
        assertEquals(
            "明天 10:00 的评审改到 14:00。",
            approvalOperation(
                decision(detail = "准备发送到飞书会话 c-1：\n明天 10:00 的评审改到 14:00。\n触发规则：出站"),
            ),
        )
    }
}
