package top.qiuli55.fluxbot.data

import org.json.JSONObject

/**
 * 一条全局审批规则。
 *
 * 规则按 Flux 云用户隔离，对该用户名下**所有 Bot** 生效；`createdByKind` /
 * `createdByBotId` 只表示「这条规则是谁加的」，不是作用域。
 *
 * 产品默认是零审批：没有规则时 Bot 的操作不会弹审批卡。Bot 可以新增更严格的
 * 规则，但停用 / 删除 / 放宽只能由用户在本页操作。
 */
data class ApprovalRule(
    val id: String,
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val description: String = "",
    val enabled: Boolean = true,
    val createdByKind: String = "user",
    val createdByBotId: String = "",
    /** 仅本地展示用：由 App 用 Bot 列表把 id 换成名字。 */
    val createdByBotName: String = "",
)

/** 新增 / 编辑规则时提交的内容。 */
data class ApprovalRuleDraft(
    val type: String,
    val params: Map<String, Any>,
    val description: String,
)

object ApprovalRuleType {
    const val PAYMENT = "payment_amount_over"
    const val COMMAND = "command_pattern"
    const val TASK = "task_match"
    const val TOOL = "tool_match"
    const val OUTBOUND = "outbound_message"
    const val CUSTOM = "custom"

    val all = listOf(PAYMENT, COMMAND, TASK, TOOL, OUTBOUND, CUSTOM)

    fun label(type: String): String = when (type) {
        PAYMENT -> "支付金额"
        COMMAND -> "命令匹配"
        TASK -> "任务匹配"
        TOOL -> "工具匹配"
        OUTBOUND -> "出站消息"
        CUSTOM -> "自定义"
        else -> "其他规则"
    }

    fun isKnown(type: String): Boolean = type in all
}

/** 把 REST 返回的一条规则解析成 App 模型；缺 id 时返回 null。 */
fun approvalRuleFromJson(o: JSONObject): ApprovalRule? {
    val id = o.optString("id").trim()
    if (id.isEmpty()) return null
    val params = LinkedHashMap<String, String>()
    o.optJSONObject("params")?.let { p ->
        p.keys().asSequence().forEach { key ->
            val value = p.opt(key)
            if (value != null && value != JSONObject.NULL) params[key] = value.toString()
        }
    }
    return ApprovalRule(
        id = id,
        type = o.optString("type").trim(),
        params = params,
        description = o.optString("description").trim(),
        enabled = o.optBoolean("enabled", true),
        createdByKind = o.optString("created_by_kind").trim().ifEmpty { "user" },
        createdByBotId = o.optString("created_by_bot_id").trim()
            .takeUnless { it == "null" }.orEmpty(),
        createdByBotName = o.optString("created_by_bot_name").trim(),
    )
}

/** 按规则类型与参数生成一句中文说明（与后端 default_description 对齐）。 */
fun approvalRuleDescription(type: String, params: Map<String, String>): String {
    fun p(key: String): String = params[key].orEmpty()
    return when (type) {
        ApprovalRuleType.PAYMENT -> "支付金额超过 ${p("amount").ifEmpty { "?" }} " +
            "${p("currency").ifEmpty { "CNY" }} 时需要我确认"
        ApprovalRuleType.COMMAND -> "执行匹配${if (p("style") == "regex") "正则" else "通配符"} " +
            "${p("pattern")} 的命令时需要我确认"
        ApprovalRuleType.TASK -> "执行任务类型 ${p("task_kind")} 时需要我确认"
        ApprovalRuleType.TOOL -> "调用工具 ${p("tool_name")} 时需要我确认"
        ApprovalRuleType.OUTBOUND -> p("channel").takeIf { it.isNotEmpty() }?.let {
            "通过${channelLabel(it)}发送出站消息时需要我确认"
        } ?: "发送任何出站消息时需要我确认"
        else -> "命中自定义规则 ${p("key")} 时需要我确认"
    }
}

/** 界面上的中文说明；服务端 description 为空时按参数兜底。 */
fun ApprovalRule.displayDescription(): String {
    if (description.isNotBlank()) return description
    return approvalRuleDescription(type, params)
}

/** 规则参数的第二行摘要，例如「超过 ¥500 · CNY」。 */
fun ApprovalRule.paramsSummary(): String {
    fun p(key: String): String = params[key].orEmpty()
    return when (type) {
        ApprovalRuleType.PAYMENT -> "超过 ¥${p("amount")} · ${p("currency").ifEmpty { "CNY" }}"
        ApprovalRuleType.COMMAND -> "模式 ${p("pattern")} · " +
            if (p("style") == "regex") "正则" else "通配符"
        ApprovalRuleType.TASK -> "任务类型：${p("task_kind")}"
        ApprovalRuleType.TOOL -> "工具：${p("tool_name")}"
        ApprovalRuleType.OUTBOUND -> p("channel").takeIf { it.isNotEmpty() }?.let {
            "频道：${channelLabel(it)}"
        } ?: "所有出站消息"
        ApprovalRuleType.CUSTOM -> "${p("key")} = ${p("value")}"
        else -> ""
    }
}

fun channelLabel(key: String): String = when (key) {
    "feishu" -> "飞书"
    "wecom" -> "企业微信"
    "dingtalk" -> "钉钉"
    else -> key
}

/** 创建者标签：只能看到「我」或「Bot：某某」，不显示内部 id。 */
fun ApprovalRule.creatorLabel(botNameResolver: (String) -> String? = { null }): String {
    if (createdByKind != "bot") return "我"
    val name = createdByBotName.ifBlank { botNameResolver(createdByBotId).orEmpty() }
    return "Bot：${name.ifBlank { "未知 Bot" }}"
}
