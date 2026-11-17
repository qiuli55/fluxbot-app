package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.4 新页面的后端模型。
 *
 * 所有解析都按服务端 `ok()` 包里的 data 形状写，字段缺失时给空串 / 空列表，
 * 让界面能显示空状态而不是崩在解析上。
 */

// ---------- 后台子任务 ----------

data class RemoteSubtask(
    val id: String,
    val title: String,
    val instruction: String,
    val status: String,
    val createdAt: String,
    val finishedAt: String,
    val error: String,
    val result: String,
) {
    val running: Boolean get() = status == "running" || status == "pending" ||
        status == "queued" || status == "waiting_for_user_decision"
}

internal fun parseSubtask(o: JSONObject): RemoteSubtask? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    val description = o.optStringOrEmpty("description")
    return RemoteSubtask(
        id = id,
        title = o.optStringOrEmpty("title").ifBlank { description.take(40) }.ifBlank { "子任务" },
        instruction = o.optStringOrEmpty("instruction").ifBlank { description },
        status = o.optStringOrEmpty("status"),
        createdAt = v040Time(o.optStringOrEmpty("created_at")),
        finishedAt = v040Time(
            o.optStringOrEmpty("finished_at").ifBlank { o.optStringOrEmpty("updated_at") },
        ),
        error = o.optStringOrEmpty("error"),
        result = o.optStringOrEmpty("result"),
    )
}

// ---------- 出站草稿 ----------

data class RemoteOutboundDraft(
    val id: String,
    val channel: String,
    val status: String,
    val body: String,
    val target: String,
    val botId: String,
    val taskId: String,
    val decisionId: String,
    val error: String,
    val sentAt: String,
    val resolvedAt: String,
    val createdAt: String,
)

internal fun parseOutboundDraft(o: JSONObject): RemoteOutboundDraft? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    val dest = o.optJSONObject("dest") ?: JSONObject()
    val target = listOf(
        dest.optStringOrEmpty("chat_id"),
        dest.optStringOrEmpty("conversation_id"),
        dest.optStringOrEmpty("touser"),
        dest.optStringOrEmpty("to"),
        dest.optStringOrEmpty("name"),
    ).firstOrNull { it.isNotBlank() }.orEmpty()
    return RemoteOutboundDraft(
        id = id,
        channel = o.optStringOrEmpty("channel"),
        status = o.optStringOrEmpty("status"),
        body = o.optStringOrEmpty("body"),
        target = target,
        botId = o.optStringOrEmpty("bot_id"),
        taskId = o.optStringOrEmpty("task_id"),
        decisionId = o.optStringOrEmpty("decision_id"),
        error = o.optStringOrEmpty("error"),
        sentAt = v040Time(o.optStringOrEmpty("sent_at")),
        resolvedAt = v040Time(o.optStringOrEmpty("resolved_at")),
        createdAt = v040Time(o.optStringOrEmpty("created_at")),
    )
}

fun draftStatusText(status: String): String = when (status) {
    "pending" -> "待审批"
    "sending" -> "发送中"
    "sent" -> "已发送"
    "denied" -> "已拒绝"
    "cancelled" -> "已撤回"
    "failed" -> "发送失败"
    else -> status.ifBlank { "未知" }
}

// ---------- 技能库 ----------

data class RemoteSkill(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val body: String,
    val bodyChars: Int,
    val enabled: Boolean,
    val updatedAt: String,
) {
    /** "v1.2 · 2,340 字"；版本为空时只显示字数。 */
    fun metaLine(): String {
        val parts = ArrayList<String>()
        if (version.isNotBlank()) parts.add(version)
        if (bodyChars > 0) parts.add("${formatCount(bodyChars)} 字")
        return parts.joinToString(" · ")
    }
}

internal fun parseSkill(o: JSONObject): RemoteSkill? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    val body = o.optStringOrEmpty("body")
    return RemoteSkill(
        id = id,
        name = o.optStringOrEmpty("name").ifBlank { "未命名技能" },
        description = o.optStringOrEmpty("description"),
        version = o.optStringOrEmpty("version"),
        body = body,
        bodyChars = o.optInt("body_chars", body.length),
        enabled = o.optBoolean("enabled", true),
        updatedAt = v040Time(o.optStringOrEmpty("updated_at")),
    )
}

/** 1234 -> "1,234"（只用于展示字数，不引入格式化库）。 */
fun formatCount(value: Int): String {
    val raw = value.toString()
    val out = StringBuilder()
    raw.forEachIndexed { index, ch ->
        if (index > 0 && (raw.length - index) % 3 == 0) out.append(',')
        out.append(ch)
    }
    return out.toString()
}

// ---------- 连接器目录 / 测试 ----------

data class RemoteConnectorCatalog(
    val id: String,
    val name: String,
    val description: String,
    val authTypes: List<String>,
    val scopes: List<String>,
)

internal fun parseConnectorCatalog(o: JSONObject): RemoteConnectorCatalog? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    val auth = o.optJSONArray("auth_types") ?: JSONArray()
    val scopes = o.optJSONArray("scopes") ?: JSONArray()
    return RemoteConnectorCatalog(
        id = id,
        name = o.optStringOrEmpty("name").ifBlank { id },
        description = o.optStringOrEmpty("description"),
        authTypes = (0 until auth.length()).map { auth.optString(it) },
        scopes = (0 until scopes.length()).map { scopes.optString(it) },
    )
}

data class RemoteSideTest(
    val configured: Boolean,
    val ok: Boolean,
    val error: String,
)

data class RemoteEmailTest(
    val ok: Boolean,
    val imap: RemoteSideTest,
    val smtp: RemoteSideTest,
) {
    fun summary(): String = when {
        imap.configured && !imap.ok -> imap.error.ifBlank { "IMAP 连接失败" }
        smtp.configured && !smtp.ok -> smtp.error.ifBlank { "SMTP 连接失败" }
        ok -> "IMAP 与 SMTP 均已连通"
        else -> "尚未配置收信或发信服务"
    }
}

internal fun parseEmailTest(o: JSONObject): RemoteEmailTest {
    fun side(key: String): RemoteSideTest {
        val value = o.optJSONObject(key) ?: JSONObject()
        return RemoteSideTest(
            configured = value.optBoolean("configured"),
            ok = value.optBoolean("ok"),
            error = value.optStringOrEmpty("error"),
        )
    }
    return RemoteEmailTest(o.optBoolean("ok"), side("imap"), side("smtp"))
}

data class RemoteCalendarTest(
    val ok: Boolean,
    val mode: String,
    val events: Int,
    val status: Int,
) {
    fun summary(): String = when {
        !ok -> "连接失败"
        mode == "ics" -> "订阅可读 · $events 个事件"
        status > 0 -> "CalDAV 已连通 · HTTP $status"
        else -> "日历已连通"
    }
}

internal fun parseCalendarTest(o: JSONObject): RemoteCalendarTest = RemoteCalendarTest(
    ok = o.optBoolean("ok"),
    mode = o.optStringOrEmpty("mode"),
    events = o.optInt("events"),
    status = o.optInt("status"),
)

// ---------- 通道状态 ----------

data class RemoteChannelError(
    val time: String,
    val message: String,
    val source: String,
)

data class RemoteChannelStatus(
    val channel: String,
    val label: String,
    val configStatus: String,
    val inboundConfigured: Boolean,
    val outboundConfigured: Boolean,
    val missingEnv: List<String>,
    val lastSendAt: String,
    val lastReceiveAt: String,
    val recentErrors: List<RemoteChannelError>,
) {
    val connected: Boolean get() = configStatus == "configured"
}

internal fun parseChannelStatus(o: JSONObject): RemoteChannelStatus? {
    val channel = o.optStringOrEmpty("channel")
    if (channel.isEmpty()) return null
    fun env(key: String): List<String> {
        val array = o.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }
    val errors = o.optJSONArray("recent_errors") ?: JSONArray()
    return RemoteChannelStatus(
        channel = channel,
        label = o.optStringOrEmpty("label").ifBlank { channel },
        configStatus = o.optStringOrEmpty("config_status"),
        inboundConfigured = o.optBoolean("inbound_configured"),
        outboundConfigured = o.optBoolean("outbound_configured"),
        missingEnv = env("missing_env"),
        lastSendAt = v040Time(o.optStringOrEmpty("last_send_at")),
        lastReceiveAt = v040Time(o.optStringOrEmpty("last_receive_at")),
        recentErrors = (0 until errors.length()).mapNotNull { index ->
            val item = errors.optJSONObject(index) ?: return@mapNotNull null
            RemoteChannelError(
                time = v040Time(item.optStringOrEmpty("time")),
                message = item.optStringOrEmpty("message"),
                source = item.optStringOrEmpty("source"),
            )
        },
    )
}

fun channelStatusText(status: String): String = when (status) {
    "configured" -> "已接入"
    "partial" -> "部分配置"
    "missing" -> "未配置"
    else -> status.ifBlank { "未知" }
}

// ---------- 浏览器 MCP ----------

data class RemoteBrowserStatus(
    val configured: Boolean,
    val serverName: String,
    val type: String,
    val url: String,
    val ok: Boolean?,
    val statusCode: Int,
    val latencyMs: Int,
    val error: String,
    val lastProbeAt: String,
    val timeoutSeconds: Int,
) {
    val reachable: Boolean get() = ok == true
}

internal fun parseBrowserStatus(o: JSONObject): RemoteBrowserStatus = RemoteBrowserStatus(
    configured = o.optBoolean("configured"),
    serverName = o.optStringOrEmpty("server_name").ifBlank { "browser" },
    type = o.optStringOrEmpty("type"),
    url = o.optStringOrEmpty("url"),
    ok = if (o.isNull("ok")) null else o.optBoolean("ok"),
    statusCode = o.optInt("status_code"),
    latencyMs = o.optInt("latency_ms"),
    error = o.optStringOrEmpty("error"),
    lastProbeAt = v040Time(o.optStringOrEmpty("last_probe_at")),
    timeoutSeconds = o.optInt("timeout_seconds"),
)

// ---------- 共享用户记忆 ----------

data class RemoteJournalEntry(
    val id: String,
    val content: String,
    val source: String,
    val createdAt: String,
)

data class RemoteSharedMemory(
    val profile: Map<String, String>,
    val journal: List<RemoteJournalEntry>,
)

internal fun parseSharedMemory(o: JSONObject): RemoteSharedMemory {
    val profileObj = o.optJSONObject("profile") ?: JSONObject()
    val profile = LinkedHashMap<String, String>()
    profileObj.keys().forEach { key ->
        val value = profileObj.optStringOrEmpty(key)
        if (value.isNotBlank()) profile[key] = value
    }
    val journal = o.optJSONArray("journal") ?: JSONArray()
    return RemoteSharedMemory(
        profile = profile,
        journal = (0 until journal.length()).mapNotNull { index ->
            val item = journal.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optStringOrEmpty("id")
            if (id.isEmpty()) return@mapNotNull null
            RemoteJournalEntry(
                id = id,
                content = item.optStringOrEmpty("content"),
                source = item.optStringOrEmpty("source"),
                createdAt = v040Time(item.optStringOrEmpty("created_at")),
            )
        },
    )
}

fun profileLabel(key: String): String = when (key) {
    "name" -> "称呼"
    "locale" -> "地区"
    "timezone" -> "时区"
    "language" -> "语言"
    "occupation" -> "职业"
    "notes" -> "备注"
    else -> key
}

// ---------- 制品页面 ----------

data class RemotePage(
    val id: String,
    val botId: String,
    val title: String,
    val slug: String,
    val shareToken: String,
    val publicPath: String,
    val status: String,
    val currentRevision: Int,
    val contentType: String,
    val updatedAt: String,
    val body: String,
    val bodyBytes: Int,
) {
    val public: Boolean get() = status == "published"
}

internal fun parsePage(o: JSONObject): RemotePage? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    return RemotePage(
        id = id,
        botId = o.optStringOrEmpty("bot_id"),
        title = o.optStringOrEmpty("title").ifBlank { "未命名页面" },
        slug = o.optStringOrEmpty("slug"),
        shareToken = o.optStringOrEmpty("share_token"),
        publicPath = o.optStringOrEmpty("public_path"),
        status = o.optStringOrEmpty("status"),
        currentRevision = o.optInt("current_revision", o.optInt("revision")),
        contentType = o.optStringOrEmpty("content_type"),
        updatedAt = v040Time(o.optStringOrEmpty("updated_at")),
        body = o.optStringOrEmpty("body"),
        bodyBytes = o.optInt("body_bytes"),
    )
}

data class RemotePageRevision(
    val id: String,
    val revision: Int,
    val title: String,
    val bodyBytes: Int,
    val createdAt: String,
)

internal fun parsePageRevision(o: JSONObject): RemotePageRevision? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    return RemotePageRevision(
        id = id,
        revision = o.optInt("revision"),
        title = o.optStringOrEmpty("title"),
        bodyBytes = o.optInt("body_bytes"),
        createdAt = v040Time(o.optStringOrEmpty("created_at")),
    )
}

fun pageStatusText(status: String): String = when (status) {
    "published" -> "已公开"
    "draft" -> "草稿"
    "unpublished" -> "未公开"
    else -> status.ifBlank { "未知" }
}

// ---------- 保管库（服务端） ----------

data class RemoteVaultLogin(
    val id: String,
    val site: String,
    val username: String,
    val notes: String,
    val hasPassword: Boolean,
    val updatedAt: String,
)

internal fun parseVaultLogin(o: JSONObject): RemoteVaultLogin? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    return RemoteVaultLogin(
        id = id,
        site = o.optStringOrEmpty("site"),
        username = o.optStringOrEmpty("username"),
        notes = o.optStringOrEmpty("notes"),
        hasPassword = o.optBoolean("has_password"),
        updatedAt = v040Time(o.optStringOrEmpty("updated_at")),
    )
}

data class RemoteVaultCard(
    val id: String,
    val label: String,
    val brand: String,
    val last4: String,
    val expMonth: Int,
    val expYear: Int,
    val notes: String,
)

internal fun parseVaultCard(o: JSONObject): RemoteVaultCard? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    return RemoteVaultCard(
        id = id,
        label = o.optStringOrEmpty("label"),
        brand = o.optStringOrEmpty("brand"),
        last4 = o.optStringOrEmpty("last4"),
        expMonth = o.optInt("exp_month"),
        expYear = o.optInt("exp_year"),
        notes = o.optStringOrEmpty("notes"),
    )
}

data class RemoteVaultContact(
    val id: String,
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
    val notes: String,
)

internal fun parseVaultContact(o: JSONObject): RemoteVaultContact? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    fun list(key: String): List<String> {
        val array = o.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }
    return RemoteVaultContact(
        id = id,
        name = o.optStringOrEmpty("name"),
        phones = list("phones"),
        emails = list("emails"),
        notes = o.optStringOrEmpty("notes"),
    )
}

// ---------- 例程（详情形状） ----------

data class RemoteRoutineDetail(
    val id: String,
    val botId: String,
    val name: String,
    val instruction: String,
    val schedule: String,
    val enabled: Boolean,
    val eventTriggers: List<String>,
    val nextRunAt: String,
    val lastRunAt: String,
    val createdAt: String,
    /** 结果通知策略：origin = 发回原处（默认），none = 不发。 */
    val notify: String = ROUTINE_NOTIFY_ORIGIN,
)

internal fun parseRoutineDetail(o: JSONObject): RemoteRoutineDetail? {
    val id = o.optStringOrEmpty("id")
    if (id.isEmpty()) return null
    val triggers = o.optJSONArray("event_triggers") ?: JSONArray()
    return RemoteRoutineDetail(
        id = id,
        botId = o.optStringOrEmpty("bot_id"),
        name = o.optStringOrEmpty("name").ifBlank { o.optStringOrEmpty("title") }
            .ifBlank { "例程" },
        instruction = o.optStringOrEmpty("instruction"),
        schedule = o.optStringOrEmpty("schedule"),
        enabled = o.optBoolean("enabled", true),
        eventTriggers = (0 until triggers.length()).map { triggers.optString(it) }
            .filter { it.isNotBlank() },
        nextRunAt = v040Time(o.optStringOrEmpty("next_run_at")),
        lastRunAt = v040Time(o.optStringOrEmpty("last_run_at")),
        createdAt = v040Time(o.optStringOrEmpty("created_at")),
        notify = normalizeRoutineNotify(o.optStringOrEmpty("notify")),
    )
}

/** 结果通知：发回创建来源（默认）。 */
const val ROUTINE_NOTIFY_ORIGIN = "origin"

/** 结果通知：不发。 */
const val ROUTINE_NOTIFY_NONE = "none"

/** 旧载荷没有 notify 字段时按 origin 处理；未知值也归到 origin。 */
fun normalizeRoutineNotify(raw: String): String =
    if (raw.trim().lowercase() == ROUTINE_NOTIFY_NONE) ROUTINE_NOTIFY_NONE else ROUTINE_NOTIFY_ORIGIN

/** 「结果通知：发回原处 / 不发」里展示的文案。 */
fun routineNotifyLabel(notify: String): String =
    if (normalizeRoutineNotify(notify) == ROUTINE_NOTIFY_NONE) "不发" else "发回原处"

/** 例程触发摘要：cron 优先，其次事件，最后「手动」。 */
fun routineTriggerSummary(routine: RemoteRoutineDetail): String {
    val parts = ArrayList<String>()
    if (routine.schedule.isNotBlank()) parts.add(cronSummary(routine.schedule))
    routine.eventTriggers.forEach { parts.add(eventLabel(it)) }
    return parts.joinToString(" · ").ifBlank { "手动触发" }
}

fun eventLabel(event: String): String = when {
    event == "email.received" -> "邮件"
    event == "github.push" -> "GitHub push"
    event == "github.issues" -> "GitHub Issue"
    event == "github.issue_comment" -> "GitHub 评论"
    event == "github.pull_request" -> "GitHub PR"
    event == "github.release" -> "GitHub Release"
    event == "github.workflow_run" -> "GitHub Actions"
    event.startsWith("github.") -> "GitHub ${event.removePrefix("github.")}"
    event.startsWith("webhook.") -> "Webhook ${event.removePrefix("webhook.")}"
    event.startsWith("github") -> "GitHub"
    else -> event
}

/** 只解释常见的 5 段 cron，识别不了就原样显示。 */
fun cronSummary(cron: String): String {
    val parts = cron.trim().split(Regex("\\s+"))
    if (parts.size != 5) return cron
    val (minute, hour, dom, month, dow) = parts
    if (minute.startsWith("*/") && hour == "*" && dom == "*" && month == "*" && dow == "*") {
        return "每 ${minute.removePrefix("*/")} 分钟"
    }
    if (hour == "*" && dom == "*" && month == "*" && dow == "*" && minute.toIntOrNull() != null) {
        return "每小时第 $minute 分"
    }
    val h = hour.toIntOrNull() ?: return cron
    val m = minute.toIntOrNull() ?: return cron
    if (dom != "*" || month != "*") return cron
    val time = "%02d:%02d".format(h, m)
    return when (dow) {
        "*" -> "每天 $time"
        "1-5" -> "工作日 $time"
        "1" -> "每周一 $time"
        "2" -> "每周二 $time"
        "3" -> "每周三 $time"
        "4" -> "每周四 $time"
        "5" -> "每周五 $time"
        "6" -> "每周六 $time"
        "0", "7" -> "每周日 $time"
        else -> "每周 $dow · $time"
    }
}

// ---------- 展示工具 ----------

/** 与 Api.displayTime 同口径：去掉 T / Z，只留到分钟。 */
fun v040Time(raw: String): String {
    if (raw.isBlank()) return ""
    return raw.replace('T', ' ').removeSuffix("Z").take(16)
}

/** ISO 时间转「刚刚 / N 分钟前 / N 小时前 / 日期」。 */
fun relativeTime(raw: String, nowMillis: Long = System.currentTimeMillis()): String {
    if (raw.isBlank()) return ""
    val normalized = raw.replace(' ', 'T').removeSuffix("Z")
    val parsed = runCatching {
        java.time.LocalDateTime.parse(normalized).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }.getOrNull() ?: return v040Time(raw)
    val delta = (nowMillis - parsed).coerceAtLeast(0)
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        minutes < 48 * 60 -> "昨天"
        else -> v040Time(raw).take(10)
    }
}


