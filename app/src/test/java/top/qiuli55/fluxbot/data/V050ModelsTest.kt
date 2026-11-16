package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.5 新增接口的解析、校验与错误文案。 */
class V050ModelsTest {

    // ---------- 通道 ----------

    @Test
    fun parsesChannelLink() {
        val link = parseChannelLink(
            JSONObject()
                .put("channel", "wecom")
                .put("label", "企业微信")
                .put("configured", true)
                .put("missing_env", JSONArray())
                .put("connected", true)
                .put("bot_id", "bot-1")
                .put("bot_name", "汇报和统帅")
                .put("source", "app"),
        )!!
        assertEquals("wecom", link.channel)
        assertEquals("企业微信", link.label)
        assertTrue(link.configured)
        assertTrue(link.connected)
        assertEquals("bot-1", link.botId)
        assertEquals("已连接 · 汇报和统帅", link.statusText())
        assertEquals("app", link.source)
    }

    @Test
    fun channelLinkToleratesMissingOptionalFields() {
        val link = parseChannelLink(
            JSONObject()
                .put("channel", "wechat")
                .put("configured", false)
                .put("missing_env", JSONArray().put("WECHAT_TOKEN").put("  ")),
        )!!
        assertEquals("微信", link.label)
        assertEquals("服务器未配置", link.statusText())
        assertEquals(listOf("WECHAT_TOKEN"), link.missingEnv)
        assertEquals("", link.botId)
    }

    @Test
    fun channelLinkWithoutChannelIsDropped() {
        assertNull(parseChannelLink(JSONObject().put("label", "飞书")))
        assertNull(parseChannelLink(JSONObject()))
    }

    @Test
    fun botChannelsAreTrimmedAndDeduplicated() {
        val channels = parseBotChannels(
            JSONObject().put(
                "channels",
                JSONArray().put("feishu").put(" feishu ").put("wecom").put(JSONObject.NULL),
            ),
        )
        assertEquals(listOf("feishu", "wecom"), channels)
    }

    @Test
    fun missingBotChannelsMeansEmpty() {
        assertEquals(emptyList<String>(), parseBotChannels(JSONObject()))
        assertEquals(emptyList<String>(), parseBotChannels(JSONObject().put("channels", JSONObject.NULL)))
    }

    // ---------- 审批设置 ----------

    @Test
    fun parsesApprovalSettings() {
        val settings = parseApprovalSettings(
            JSONObject()
                .put("large_payment_threshold", 500)
                .put("currency", "CNY")
                .put(
                    "builtins",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("key", "large_payment")
                                .put("title", "大额付款")
                                .put("description", "单笔 500 元及以上"),
                        )
                        .put(
                            JSONObject()
                                .put("key", "abnormal_payment")
                                .put("title", "异常支付")
                                .put(
                                    "description",
                                    "陌生收款方、短时间重复付款、金额远超近期平均",
                                ),
                        )
                        .put(JSONObject().put("key", "delete_data")),
                ),
        )
        assertEquals(500.0, settings.largePaymentThreshold, 0.001)
        assertEquals(3, settings.builtins.size)
        assertEquals("单笔 500 元及以上", settings.builtin("large_payment")?.description)
        assertEquals("异常支付", settings.builtin("abnormal_payment")?.title)
        // title 缺失时退回 key，至少不会出现空白行。
        assertEquals("delete_data", settings.builtin("delete_data")?.title)
    }

    @Test
    fun builtinFallbackMatchesServerContract() {
        val fallback = fallbackApprovalSettings()

        assertEquals(200.0, fallback.largePaymentThreshold, 0.001)
        assertEquals("CNY", fallback.currency)
        assertEquals(
            listOf("large_payment", "abnormal_payment", "delete_data"),
            fallback.builtins.map { it.key },
        )
        assertEquals("大额付款", fallback.builtin("large_payment")?.title)
        assertEquals("单笔 200 元及以上", fallback.builtin("large_payment")?.description)
        assertEquals("异常支付", fallback.builtin("abnormal_payment")?.title)
        assertEquals(
            "陌生收款方、短时间重复付款、金额远超近期平均",
            fallback.builtin("abnormal_payment")?.description,
        )
        assertEquals("删除资料", fallback.builtin("delete_data")?.title)
        assertEquals(
            "删文件、删数据库记录、删聊天记录和记忆、删 Bot、rm -rf 这类命令",
            fallback.builtin("delete_data")?.description,
        )
        // 内置规则是 >= 阈值，文案不能出现「超过」。
        assertTrue(fallback.builtins.none { it.description.contains("超过") })
    }

    @Test
    fun largePaymentDescriptionUsesThresholdAndInclusiveWording() {
        assertEquals("单笔 200 元及以上", largePaymentDescription(200.0))
        assertEquals("单笔 300.5 元及以上", largePaymentDescription(300.5))
        assertEquals(
            "单笔 500 元及以上",
            fallbackApprovalSettings(500.0).builtin("large_payment")?.description,
        )
    }

    @Test
    fun approvalSettingsAcceptNumericStringAndFallbackOnGarbage() {
        val fromString = parseApprovalSettings(
            JSONObject().put("large_payment_threshold", "300.5"),
        )
        assertEquals(300.5, fromString.largePaymentThreshold, 0.001)

        val fallback = parseApprovalSettings(
            JSONObject().put("large_payment_threshold", "很多").put("builtins", "oops"),
        )
        assertEquals(200.0, fallback.largePaymentThreshold, 0.001)
        assertEquals("CNY", fallback.currency)
        assertEquals(emptyList<BuiltinApprovalRule>(), fallback.builtins)
    }

    @Test
    fun builtinsWithoutKeyAreDropped() {
        val settings = parseApprovalSettings(
            JSONObject().put("builtins", JSONArray().put(JSONObject().put("title", "缺 key"))),
        )
        assertTrue(settings.builtins.isEmpty())
    }

    @Test
    fun thresholdFormattingMatchesServerWording() {
        assertEquals("200", formatThreshold(200.0))
        assertEquals("300.5", formatThreshold(300.5))
        assertEquals("1000000", formatThreshold(1_000_000.0))
    }

    @Test
    fun thresholdValidation() {
        assertEquals("请输入金额", validateThresholdInput("  "))
        assertEquals("请输入数字", validateThresholdInput("abc"))
        assertEquals("金额需在 1 到 1000000 元之间", validateThresholdInput("0"))
        assertEquals("金额需在 1 到 1000000 元之间", validateThresholdInput("1000001"))
        assertNull(validateThresholdInput("1"))
        assertNull(validateThresholdInput("1000000"))
        assertNull(validateThresholdInput(" 200 "))
    }

    @Test
    fun botRuleUsesServerProvidedCreatorName() {
        val rule = approvalRuleFromJson(
            JSONObject()
                .put("id", "r-bot")
                .put("type", "task_match")
                .put("created_by_kind", "bot")
                .put("created_by_bot_id", "bot-trip")
                .put("created_by_bot_name", "行程助手"),
        )!!
        assertEquals("行程助手", rule.createdByBotName)
        assertEquals("Bot：行程助手", rule.creatorLabel())
        assertEquals("行程助手", rule.botCreatorLabel())
    }

    @Test
    fun botRuleWithoutNameFallsBack() {
        val rule = ApprovalRule(id = "r-x", type = "task_match", createdByKind = "bot")
        assertEquals("已删除的 Bot", rule.botCreatorLabel())
        assertEquals("Bot：已删除的 Bot", rule.creatorLabel())
    }

    // ---------- 自定义 MCP ----------

    @Test
    fun parsesMcpConnectionWithMaskedHeader() {
        val connection = parseMcpConnection(
            JSONObject()
                .put("id", "m-1")
                .put("name", "公司知识库")
                .put("url", "https://mcp.example.com/sse")
                .put("transport", "http")
                .put("tool_count", 12)
                .put("header_name", "Authorization")
                .put("header_value_masked", "Bear••••9f2a")
                .put("header_value", "Bearer secret-value")
                .put("tools", JSONArray().put("search").put("read")),
        )!!
        assertEquals("公司知识库", connection.name)
        assertEquals("mcp.example.com", connection.hostText())
        assertEquals("12 个工具", connection.toolCountText())
        assertEquals("Authorization", connection.headerName)
        assertEquals("Bear••••9f2a", connection.headerValueMasked)
        // 只读取脱敏值，响应里的真实 header 不会进入模型。
        assertTrue(!connection.toString().contains("secret-value"))
        assertEquals(listOf("search", "read"), connection.tools)
    }

    @Test
    fun mcpToolListIsCappedAndCountFallsBack() {
        val many = JSONArray()
        repeat(60) { many.put("tool-$it") }
        val connection = parseMcpConnection(
            JSONObject().put("id", "m-2").put("tools", many),
        )!!
        assertEquals(50, connection.tools.size)
        assertEquals(50, connection.toolCount)
        assertEquals("未命名连接", connection.name)
    }

    @Test
    fun mcpConnectionWithoutIdIsDropped() {
        assertNull(parseMcpConnection(JSONObject().put("name", "没有 id")))
    }

    @Test
    fun mcpHostStripsSchemeUserPortAndPath() {
        assertEquals("mcp.example.com", mcpUrlHost("https://mcp.example.com/sse?x=1"))
        assertEquals("mcp.example.com", mcpUrlHost("http://user:pass@mcp.example.com:8080/mcp"))
        assertEquals("mcp.example.com", mcpUrlHost("mcp.example.com/mcp"))
        assertEquals("", mcpUrlHost("  "))
    }

    @Test
    fun mcpFormValidation() {
        assertEquals("请填写名称", validateMcpForm("", "https://a.com", "", ""))
        assertEquals(
            "名称最多 40 个字符",
            validateMcpForm("a".repeat(41), "https://a.com", "", ""),
        )
        assertEquals(
            "地址需要以 http:// 或 https:// 开头",
            validateMcpForm("知识库", "mcp.example.com", "", ""),
        )
        assertEquals("请填写头的值", validateMcpForm("知识库", "https://a.com", "Authorization", ""))
        assertEquals("请填写头名称", validateMcpForm("知识库", "https://a.com", "", "Bearer x"))
        assertNull(validateMcpForm("知识库", "https://a.com", "", ""))
        assertNull(validateMcpForm("知识库", "http://a.com", "Authorization", "Bearer x"))
    }

    // ---------- Bot 权限 ----------

    @Test
    fun parsesBotPermissions() {
        val permissions = parseBotPermissions(
            JSONObject().put(
                "permissions",
                JSONObject()
                    .put("files", true)
                    .put("shell", false)
                    .put("network", true)
                    .put("apps", false),
            ),
        )
        assertEquals(
            mapOf("files" to true, "shell" to false, "network" to true, "apps" to false),
            permissions,
        )
    }

    @Test
    fun botPermissionsTreatMissingKeysAsUnset() {
        // 旧服务端 / 未保存过：整个字段缺失 → 空表，界面按「开启」兜底。
        assertEquals(emptyMap<String, Boolean>(), parseBotPermissions(JSONObject()))
        assertEquals(
            emptyMap<String, Boolean>(),
            parseBotPermissions(JSONObject().put("permissions", JSONObject.NULL)),
        )
        // 只返回一项时只解析这一项；未知 key 忽略，不污染界面状态。
        assertEquals(
            mapOf("shell" to false),
            parseBotPermissions(
                JSONObject().put(
                    "permissions",
                    JSONObject().put("shell", false).put("bogus", true),
                ),
            ),
        )
    }

    // ---------- 错误文案 ----------

    @Test
    fun errorMessagePrefersServerChineseMessage() {
        val root = JSONObject().put("success", false).put("message", "无法连接到这个地址")
        assertEquals("无法连接到这个地址", apiErrorMessage(root, 422))
    }

    @Test
    fun errorMessageFallsBackByStatus() {
        assertEquals("连接失败，请检查地址和鉴权头", apiErrorMessage(JSONObject(), 422))
        assertEquals("填写的信息有误", apiErrorMessage(JSONObject(), 400))
        assertEquals("请求失败（HTTP 500）", apiErrorMessage(JSONObject(), 500))
    }

    @Test
    fun errorMessageSkipsNullMessage() {
        val root = JSONObject().put("message", JSONObject.NULL)
        assertEquals("连接失败，请检查地址和鉴权头", apiErrorMessage(root, 422))
    }
}
