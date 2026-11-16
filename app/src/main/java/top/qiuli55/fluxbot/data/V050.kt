package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.5 的接口模型与纯函数。
 *
 * 解析器只依赖 org.json，字段缺失或类型异常时返回空值 / 兜底文案，
 * 让页面能显示空状态而不是在解析阶段崩溃。所有可见文案都是中文。
 */

// ---------- 通道归属 ----------

/** 四个通道的固定顺序；服务端返回顺序优先，这里只做兜底。 */
val CHANNEL_KEYS = listOf("feishu", "wechat", "wecom", "dingtalk")

data class RemoteChannelLink(
    val channel: String,
    val label: String,
    val configured: Boolean,
    val missingEnv: List<String>,
    val connected: Boolean,
    val botId: String,
    val botName: String,
    val source: String,
) {
    /** 设置页第二行文案；未配置时优先说明服务器缺少凭据。 */
    fun statusText(): String = when {
        !configured -> "服务器未配置"
        connected -> "已连接 · ${botName.ifBlank { "未命名 Bot" }}"
        else -> "未连接"
    }
}

internal fun parseChannelLink(o: JSONObject): RemoteChannelLink? {
    val channel = o.optStringOrEmpty("channel").trim()
    if (channel.isEmpty()) return null
    val env = o.optJSONArray("missing_env")?.let { array ->
        (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotEmpty() }
    }.orEmpty()
    return RemoteChannelLink(
        channel = channel,
        label = o.optStringOrEmpty("label").trim().ifBlank { channelLabel(channel) },
        configured = o.optBoolean("configured"),
        missingEnv = env,
        connected = o.optBoolean("connected"),
        botId = o.optStringOrEmpty("bot_id").trim().takeUnless { it == "null" }.orEmpty(),
        botName = o.optStringOrEmpty("bot_name").trim().takeUnless { it == "null" }.orEmpty(),
        source = o.optStringOrEmpty("source").trim(),
    )
}

/**
 * Bot 对象里的 channels 字段。
 *
 * 服务端可能省略该字段（旧版本）或返回 null；两种情况都按「没有通道」处理。
 */
internal fun parseBotChannels(o: JSONObject): List<String> {
    val array = o.optJSONArray("channels") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val key = array.optString(index).trim()
        key.takeIf { it.isNotEmpty() && it != "null" }
    }.distinct()
}

/** Bot 详情「设置 → 权限」的四项；与服务端 bots.permissions 的 key 一一对应。 */
internal val BOT_PERMISSION_KEYS = listOf("files", "shell", "network", "apps")

/**
 * 解析服务端持久化的权限开关。
 *
 * 只保留四项已知 key；字段缺失（旧服务端 / 从未保存）返回空表，由界面按「开启」兜底。
 */
internal fun parseBotPermissions(o: JSONObject): Map<String, Boolean> {
    val raw = o.optJSONObject("permissions") ?: return emptyMap()
    return BOT_PERMISSION_KEYS.mapNotNull { key ->
        if (!raw.has(key) || raw.isNull(key)) null else key to raw.optBoolean(key, true)
    }.toMap()
}

// ---------- 审批设置 ----------

data class BuiltinApprovalRule(
    val key: String,
    val title: String,
    val description: String,
)

/** 默认需要确认的三类内置规则 + 大额付款阈值。 */
data class ApprovalSettings(
    val largePaymentThreshold: Double = DEFAULT_LARGE_PAYMENT_THRESHOLD,
    val currency: String = "CNY",
    val builtins: List<BuiltinApprovalRule> = emptyList(),
) {
    fun builtin(key: String): BuiltinApprovalRule? = builtins.firstOrNull { it.key == key }
}

/** 大额付款的出厂阈值；设置接口读取失败时用它兜底。 */
const val DEFAULT_LARGE_PAYMENT_THRESHOLD: Double = 200.0

/**
 * 大额付款的说明由阈值生成：命中条件是「单笔 >= 阈值」，所以只能说「及以上」。
 * 文案与服务端 GET /api/v1/approval-rules/settings 的默认值保持一致。
 */
fun largePaymentDescription(threshold: Double): String =
    "单笔 ${formatThreshold(threshold)} 元及以上"

/** 三个内置场景的本地兜底文案；设置接口不可用时也能完整展示。 */
fun builtinApprovalRules(threshold: Double): List<BuiltinApprovalRule> = listOf(
    BuiltinApprovalRule(
        key = "large_payment",
        title = "大额付款",
        description = largePaymentDescription(threshold),
    ),
    BuiltinApprovalRule(
        key = "abnormal_payment",
        title = "异常支付",
        description = "陌生收款方、短时间重复付款、金额远超近期平均",
    ),
    BuiltinApprovalRule(
        key = "delete_data",
        title = "删除资料",
        description = "删文件、删数据库记录、删聊天记录和记忆、删 Bot、rm -rf 这类命令",
    ),
)

/** 设置读取失败时的本地兜底：默认阈值或上一次成功读取到的阈值。 */
fun fallbackApprovalSettings(
    threshold: Double = DEFAULT_LARGE_PAYMENT_THRESHOLD,
): ApprovalSettings = ApprovalSettings(
    largePaymentThreshold = threshold,
    currency = "CNY",
    builtins = builtinApprovalRules(threshold),
)

internal fun parseApprovalSettings(o: JSONObject): ApprovalSettings {
    val array = o.optJSONArray("builtins") ?: JSONArray()
    val builtins = (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val key = item.optStringOrEmpty("key").trim()
        if (key.isEmpty()) return@mapNotNull null
        BuiltinApprovalRule(
            key = key,
            title = item.optStringOrEmpty("title").trim().ifBlank { key },
            description = item.optStringOrEmpty("description").trim(),
        )
    }
    val threshold = when (val raw = o.opt("large_payment_threshold")) {
        is Number -> raw.toDouble()
        is String -> raw.trim().toDoubleOrNull()
        else -> null
    } ?: DEFAULT_LARGE_PAYMENT_THRESHOLD
    return ApprovalSettings(
        largePaymentThreshold = threshold,
        currency = o.optStringOrEmpty("currency").trim().ifBlank { "CNY" },
        builtins = builtins,
    )
}

/** 阈值显示：整数不补小数点，和服务器文案保持一致。 */
fun formatThreshold(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** 客户端预校验；通过返回 null，否则返回中文原因。 */
fun validateThresholdInput(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return "请输入金额"
    val value = text.toDoubleOrNull() ?: return "请输入数字"
    if (value < 1.0 || value > 1_000_000.0) return "金额需在 1 到 1000000 元之间"
    return null
}

// ---------- 自定义 MCP 连接 ----------

data class RemoteMcpConnection(
    val id: String,
    val name: String,
    val url: String,
    val transport: String,
    val toolCount: Int,
    val headerName: String,
    val headerValueMasked: String,
    /** 创建成功时服务端附带的前 50 个工具名；列表接口没有该字段。 */
    val tools: List<String> = emptyList(),
) {
    fun toolCountText(): String = "$toolCount 个工具"

    fun hostText(): String = mcpUrlHost(url)
}

internal fun parseMcpConnection(o: JSONObject): RemoteMcpConnection? {
    val id = o.optStringOrEmpty("id").trim()
    if (id.isEmpty()) return null
    val toolsArray = o.optJSONArray("tools") ?: JSONArray()
    val tools = (0 until minOf(toolsArray.length(), 50))
        .map { toolsArray.optString(it).trim() }
        .filter { it.isNotEmpty() }
    val declaredCount = o.optInt("tool_count", -1)
    return RemoteMcpConnection(
        id = id,
        name = o.optStringOrEmpty("name").trim().ifBlank { "未命名连接" },
        url = o.optStringOrEmpty("url").trim(),
        transport = o.optStringOrEmpty("transport").trim(),
        toolCount = if (declaredCount >= 0) declaredCount else tools.size,
        // 只读取脱敏字段；即使响应里混入真实值也不会进入界面。
        headerName = o.optStringOrEmpty("header_name").trim(),
        headerValueMasked = o.optStringOrEmpty("header_value_masked").trim(),
        tools = tools,
    )
}

/** 传输类型的中文展示名；未知值原样返回，避免把服务端新值藏掉。 */
fun mcpTransportLabel(transport: String): String = when (transport.trim().lowercase()) {
    "" -> "未知"
    "streamable_http" -> "Streamable HTTP"
    "sse" -> "SSE"
    "stdio" -> "stdio"
    else -> transport.trim()
}

/** 鉴权头值只展示服务端脱敏结果；没有配置头时返回 null。 */
fun mcpMaskedHeaderValue(connection: RemoteMcpConnection): String? {
    if (connection.headerName.isBlank() && connection.headerValueMasked.isBlank()) return null
    return connection.headerValueMasked.ifBlank { "已保存（未返回脱敏值）" }
}

/** 列表展示用主机名：去掉协议、路径、查询和用户信息。 */
fun mcpUrlHost(url: String): String {
    val raw = url.trim()
    if (raw.isEmpty()) return ""
    val rest = if (raw.contains("://")) raw.substringAfter("://") else raw
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfter('@', authority)
    return host.substringBefore(':').ifBlank { raw }
}

/** 表单预校验；通过返回 null，否则返回中文原因。 */
fun validateMcpForm(
    name: String,
    url: String,
    headerName: String,
    headerValue: String,
): String? {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return "请填写名称"
    if (trimmedName.length > 40) return "名称最多 40 个字符"
    val trimmedUrl = url.trim()
    if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
        return "地址需要以 http:// 或 https:// 开头"
    }
    val hasHeaderName = headerName.trim().isNotEmpty()
    val hasHeaderValue = headerValue.trim().isNotEmpty()
    if (hasHeaderName && !hasHeaderValue) return "请填写头的值"
    if (!hasHeaderName && hasHeaderValue) return "请填写头名称"
    return null
}

// ---------- 错误文案 ----------

/**
 * 从错误响应里取中文提示：优先服务端 message，缺失时按状态码兜底。
 * 422 用于自定义 MCP：连接失败的原因由服务端给出，兜底文案只覆盖极端情况。
 */
internal fun apiErrorMessage(root: JSONObject, status: Int = 0): String {
    val message = root.optStringOrEmpty("message").trim()
    if (message.isNotEmpty()) return message
    return when (status) {
        422 -> "连接失败，请检查地址和鉴权头"
        400 -> "填写的信息有误"
        else -> "请求失败（HTTP $status）"
    }
}
