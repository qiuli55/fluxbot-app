package top.qiuli55.fluxbot.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 审批规则解析、中文描述与创建者标签。 */
class ApprovalRulesTest {

    @Test
    fun parsesUserRuleFromRest() {
        val rule = approvalRuleFromJson(
            JSONObject()
                .put("id", "r-1")
                .put("type", "payment_amount_over")
                .put("params", JSONObject().put("amount", 500).put("currency", "CNY"))
                .put("description", "支付超过 500 元需要审批")
                .put("enabled", true)
                .put("created_by_kind", "user")
                .put("created_by_bot_id", JSONObject.NULL),
        )!!
        assertEquals("r-1", rule.id)
        assertEquals("payment_amount_over", rule.type)
        assertEquals("500", rule.params["amount"])
        assertEquals("CNY", rule.params["currency"])
        assertTrue(rule.enabled)
        assertEquals("我", rule.creatorLabel())
        assertEquals("支付超过 500 元需要审批", rule.displayDescription())
        assertEquals("超过 ¥500 · CNY", rule.paramsSummary())
    }

    @Test
    fun parsesBotRuleAndResolvesCreatorName() {
        val rule = approvalRuleFromJson(
            JSONObject()
                .put("id", "r-2")
                .put("type", "task_match")
                .put("params", JSONObject().put("task_kind", "行程规划"))
                .put("enabled", true)
                .put("created_by_kind", "bot")
                .put("created_by_bot_id", "bot-trip"),
        )!!
        assertEquals("Bot：行程助手", rule.creatorLabel { id -> if (id == "bot-trip") "行程助手" else null })
        // 没有解析到名字时也不能露出内部 id
        assertEquals("Bot：未知 Bot", rule.creatorLabel())
        assertEquals("任务类型：行程规划", rule.paramsSummary())
    }

    @Test
    fun invalidRuleIsDropped() {
        assertNull(approvalRuleFromJson(JSONObject().put("type", "task_match")))
    }

    @Test
    fun blankDescriptionFallsBackToGeneratedChinese() {
        val rule = ApprovalRule(
            id = "r-3",
            type = ApprovalRuleType.COMMAND,
            params = mapOf("pattern" to "rm", "style" to "glob"),
        )
        assertEquals("执行匹配通配符 rm 的命令时需要我确认", rule.displayDescription())
        assertEquals("模式 rm · 通配符", rule.paramsSummary())
    }

    @Test
    fun generatedDescriptionsMatchServerDefaults() {
        assertEquals(
            "支付金额超过 500 CNY 时需要我确认",
            approvalRuleDescription(
                ApprovalRuleType.PAYMENT,
                mapOf("amount" to "500", "currency" to "CNY"),
            ),
        )
        assertEquals(
            "调用工具 Bash 时需要我确认",
            approvalRuleDescription(ApprovalRuleType.TOOL, mapOf("tool_name" to "Bash")),
        )
        assertEquals(
            "通过飞书发送出站消息时需要我确认",
            approvalRuleDescription(ApprovalRuleType.OUTBOUND, mapOf("channel" to "feishu")),
        )
        assertEquals(
            "发送任何出站消息时需要我确认",
            approvalRuleDescription(ApprovalRuleType.OUTBOUND, emptyMap()),
        )
    }

    @Test
    fun disabledRuleKeepsState() {
        val rule = approvalRuleFromJson(
            JSONObject().put("id", "r-4").put("type", "outbound_message")
                .put("params", JSONObject().put("channel", "feishu")).put("enabled", false),
        )!!
        assertFalse(rule.enabled)
        assertEquals("频道：飞书", rule.paramsSummary())
    }
}
