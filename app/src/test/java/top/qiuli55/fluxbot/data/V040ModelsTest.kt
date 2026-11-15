package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v0.4 新页面模型的解析与展示逻辑。
 *
 * 样例字段按服务端 `to_dict()` / 路由响应构造，覆盖正常值、空值和缺字段，
 * 保证界面拿到真实载荷时不会因为某个可选字段缺失而崩。
 */
class V040ModelsTest {

    // ---------- 子任务 ----------

    @Test
    fun parsesSubtaskWithTitleAndTimes() {
        val item = parseSubtask(
            JSONObject()
                .put("id", "t-1")
                .put("title", "整理周报")
                .put("instruction", "汇总本周提交")
                .put("status", "running")
                .put("created_at", "2026-11-16T09:00:00Z")
                .put("finished_at", JSONObject.NULL)
                .put("error", JSONObject.NULL)
                .put("result", JSONObject.NULL),
        )
        assertNotNull(item)
        assertEquals("整理周报", item!!.title)
        assertEquals("汇总本周提交", item.instruction)
        assertEquals("2026-11-16 09:00", item.createdAt)
        assertEquals("", item.finishedAt)
        assertEquals("", item.error)
        assertTrue(item.running)
    }

    @Test
    fun subtaskFallsBackToDescriptionAndRejectsMissingId() {
        val item = parseSubtask(
            JSONObject()
                .put("id", "t-2")
                .put("description", "从聊天里派生的子任务说明")
                .put("status", "done")
                .put("updated_at", "2026-11-16T10:00:00Z"),
        )!!
        assertEquals("从聊天里派生的子任务说明", item.title)
        assertEquals("从聊天里派生的子任务说明", item.instruction)
        assertEquals("2026-11-16 10:00", item.finishedAt)
        assertFalse(item.running)
        assertNull(parseSubtask(JSONObject().put("status", "running")))
    }

    // ---------- 出站草稿 ----------

    @Test
    fun parsesOutboundDraftTargetFromDest() {
        val item = parseOutboundDraft(
            JSONObject()
                .put("id", "d-1")
                .put("channel", "feishu")
                .put("status", "pending")
                .put("body", "明天 10 点开会")
                .put("dest", JSONObject().put("chat_id", "oc_123").put("name", "项目群"))
                .put("bot_id", "b-1")
                .put("task_id", "task-1")
                .put("decision_id", "dec-1")
                .put("error", JSONObject.NULL)
                .put("sent_at", JSONObject.NULL)
                .put("resolved_at", JSONObject.NULL)
                .put("created_at", "2026-11-16T09:30:00Z"),
        )!!
        assertEquals("oc_123", item.target)
        assertEquals("feishu", item.channel)
        assertEquals("明天 10 点开会", item.body)
        assertEquals("b-1", item.botId)
        assertEquals("dec-1", item.decisionId)
        assertEquals("", item.sentAt)
        assertEquals("2026-11-16 09:30", item.createdAt)
    }

    @Test
    fun outboundTargetFallsBackThroughDestKeys() {
        val item = parseOutboundDraft(
            JSONObject()
                .put("id", "d-2")
                .put("channel", "wecom")
                .put("status", "failed")
                .put("dest", JSONObject().put("touser", "zhangsan"))
                .put("error", "invalid token"),
        )!!
        assertEquals("zhangsan", item.target)
        assertEquals("invalid token", item.error)
        assertEquals("发送失败", draftStatusText(item.status))
        assertEquals("已发送", draftStatusText("sent"))
        assertEquals("待审批", draftStatusText("pending"))
        assertEquals("已撤回", draftStatusText("cancelled"))
        assertEquals("mystery", draftStatusText("mystery"))
        assertNull(parseOutboundDraft(JSONObject().put("channel", "feishu")))
    }

    // ---------- 技能库 ----------

    @Test
    fun parsesSkillDetailAndSummary() {
        val detail = parseSkill(
            JSONObject()
                .put("id", "s-1")
                .put("name", "周报助手")
                .put("description", "按模板汇总")
                .put("version", "v1.2")
                .put("body", "# 指令\n汇总本周提交")
                .put("body_chars", 2340)
                .put("enabled", true)
                .put("updated_at", "2026-11-16T08:00:00Z"),
        )!!
        assertEquals("周报助手", detail.name)
        assertEquals("v1.2 · 2,340 字", detail.metaLine())
        assertEquals("2026-11-16 08:00", detail.updatedAt)

        // 列表形状不带 body：body_chars 由服务端给出，仍能展示字数。
        val summary = parseSkill(
            JSONObject().put("id", "s-2").put("name", "列表技能").put("body_chars", 12),
        )!!
        assertEquals("", summary.body)
        assertEquals("12 字", summary.metaLine())
        assertEquals("未命名技能", parseSkill(JSONObject().put("id", "s-3"))!!.name)
        assertNull(parseSkill(JSONObject().put("name", "无 id")))
    }

    @Test
    fun formatsThousands() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1,000", formatCount(1000))
        assertEquals("12,345", formatCount(12345))
        assertEquals("1,234,567", formatCount(1234567))
    }

    // ---------- 连接器目录与测试 ----------

    @Test
    fun parsesConnectorCatalog() {
        val catalog = parseConnectorCatalog(
            JSONObject()
                .put("id", "github")
                .put("name", "GitHub")
                .put("description", "读取仓库与 Issue")
                .put(
                    "auth_types",
                    JSONArray().put("oauth").put("pat"),
                )
                .put("scopes", JSONArray().put("repo")),
        )!!
        assertEquals("GitHub", catalog.name)
        assertEquals(listOf("oauth", "pat"), catalog.authTypes)
        assertEquals(listOf("repo"), catalog.scopes)

        val fallback = parseConnectorCatalog(JSONObject().put("id", "calendar"))!!
        assertEquals("calendar", fallback.name)
        assertTrue(fallback.authTypes.isEmpty())
    }

    @Test
    fun parsesEmailTestBothSides() {
        val test = parseEmailTest(
            JSONObject()
                .put("ok", false)
                .put("imap", JSONObject().put("configured", true).put("ok", true))
                .put(
                    "smtp",
                    JSONObject().put("configured", true).put("ok", false)
                        .put("error", "535 auth failed"),
                ),
        )
        assertFalse(test.ok)
        assertTrue(test.imap.configured)
        assertEquals("535 auth failed", test.summary())

        val empty = parseEmailTest(JSONObject())
        assertFalse(empty.ok)
        assertEquals("尚未配置收信或发信服务", empty.summary())
    }

    @Test
    fun parsesCalendarTestModes() {
        val ics = parseCalendarTest(
            JSONObject().put("ok", true).put("mode", "ics").put("events", 42),
        )
        assertEquals("订阅可读 · 42 个事件", ics.summary())

        val caldav = parseCalendarTest(
            JSONObject().put("ok", true).put("mode", "caldav").put("status", 207),
        )
        assertEquals("CalDAV 已连通 · HTTP 207", caldav.summary())

        assertEquals("连接失败", parseCalendarTest(JSONObject().put("ok", false)).summary())
    }

    // ---------- 通道状态 ----------

    @Test
    fun parsesChannelStatusAndErrors() {
        val status = parseChannelStatus(
            JSONObject()
                .put("channel", "feishu")
                .put("label", "飞书")
                .put("config_status", "configured")
                .put("inbound_configured", true)
                .put("outbound_configured", true)
                .put("missing_env", JSONArray())
                .put("last_send_at", "2026-11-16T09:00:00Z")
                .put("last_receive_at", "2026-11-16T09:05:00Z")
                .put(
                    "recent_errors",
                    JSONArray().put(
                        JSONObject()
                            .put("time", "2026-11-16T08:00:00Z")
                            .put("message", "webhook 签名不匹配")
                            .put("source", "inbound"),
                    ),
                ),
        )!!
        assertTrue(status.connected)
        assertEquals("飞书", status.label)
        assertEquals("2026-11-16 09:00", status.lastSendAt)
        assertEquals(1, status.recentErrors.size)
        assertEquals("webhook 签名不匹配", status.recentErrors.single().message)

        val missing = parseChannelStatus(
            JSONObject()
                .put("channel", "dingtalk")
                .put("config_status", "missing")
                .put("missing_env", JSONArray().put("DINGTALK_CLIENT_ID").put("")),
        )!!
        assertEquals(listOf("DINGTALK_CLIENT_ID"), missing.missingEnv)
        assertFalse(missing.connected)
        assertEquals("已接入", channelStatusText("configured"))
        assertEquals("部分配置", channelStatusText("partial"))
        assertEquals("未配置", channelStatusText("missing"))
        assertEquals("custom", channelStatusText("custom"))
        assertNull(parseChannelStatus(JSONObject().put("label", "没有 channel")))
    }

    // ---------- 浏览器 ----------

    @Test
    fun parsesBrowserStatusConfiguredAndProbe() {
        val status = parseBrowserStatus(
            JSONObject()
                .put("configured", true)
                .put("server_name", "browser")
                .put("type", "playwright")
                .put("url", "http://127.0.0.1:8931/mcp")
                .put("ok", true)
                .put("status_code", 200)
                .put("latency_ms", 18)
                .put("error", JSONObject.NULL)
                .put("last_probe_at", "2026-11-16T09:20:00Z")
                .put("timeout_seconds", 2),
        )
        assertTrue(status.configured)
        assertTrue(status.reachable)
        assertEquals(200, status.statusCode)
        assertEquals("2026-11-16 09:20", status.lastProbeAt)
        assertEquals(2, status.timeoutSeconds)
    }

    @Test
    fun parsesBrowserStatusUnconfigured() {
        val status = parseBrowserStatus(
            JSONObject()
                .put("configured", false)
                .put("type", JSONObject.NULL)
                .put("url", JSONObject.NULL)
                .put("ok", JSONObject.NULL)
                .put("status_code", JSONObject.NULL)
                .put("latency_ms", JSONObject.NULL)
                .put("timeout_seconds", 2),
        )
        assertFalse(status.configured)
        assertFalse(status.reachable)
        assertEquals("browser", status.serverName)
        assertEquals("", status.type)
        assertEquals("", status.url)
    }

    // ---------- 共享记忆 ----------

    @Test
    fun parsesSharedMemoryProfileAndJournal() {
        val memory = parseSharedMemory(
            JSONObject()
                .put(
                    "profile",
                    JSONObject()
                        .put("name", "小林")
                        .put("timezone", "Asia/Shanghai")
                        .put("empty", ""),
                )
                .put(
                    "journal",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "j-1")
                                .put("content", "周五要交周报")
                                .put("source", "user")
                                .put("created_at", "2026-11-16T09:00:00Z"),
                        )
                        .put(JSONObject().put("content", "没有 id 的记录")),
                ),
        )
        assertEquals(mapOf("name" to "小林", "timezone" to "Asia/Shanghai"), memory.profile)
        assertEquals(1, memory.journal.size)
        assertEquals("user", memory.journal.single().source)
        assertEquals("2026-11-16 09:00", memory.journal.single().createdAt)

        val empty = parseSharedMemory(JSONObject())
        assertTrue(empty.profile.isEmpty())
        assertTrue(empty.journal.isEmpty())
        assertEquals("称呼", profileLabel("name"))
        assertEquals("custom_key", profileLabel("custom_key"))
    }

    // ---------- 制品页面 ----------

    @Test
    fun parsesPageDetailAndRevision() {
        val page = parsePage(
            JSONObject()
                .put("id", "p-1")
                .put("bot_id", "b-1")
                .put("title", "本周行情")
                .put("slug", "weekly-market")
                .put("share_token", "tok-1")
                .put("public_path", "/api/v1/public/pages/tok-1")
                .put("status", "published")
                .put("current_revision", 3)
                .put("content_type", "text/markdown")
                .put("updated_at", "2026-11-16T09:00:00Z")
                .put("body", "# 行情\n...")
                .put("body_bytes", 1024),
        )!!
        assertTrue(page.public)
        assertEquals(3, page.currentRevision)
        assertEquals("/api/v1/public/pages/tok-1", page.publicPath)
        assertEquals("已公开", pageStatusText(page.status))
        assertEquals("草稿", pageStatusText("draft"))

        val revision = parsePageRevision(
            JSONObject()
                .put("id", "r-1")
                .put("revision", 2)
                .put("title", "旧标题")
                .put("body_bytes", 512)
                .put("created_at", "2026-11-15T09:00:00Z"),
        )!!
        assertEquals(2, revision.revision)
        assertEquals(512, revision.bodyBytes)
        assertNull(parsePage(JSONObject().put("title", "缺 id")))
    }

    @Test
    fun pageRevisionFallsBackToRevisionField() {
        val page = parsePage(
            JSONObject().put("id", "p-2").put("title", "草稿").put("status", "draft")
                .put("revision", 4),
        )!!
        assertEquals(4, page.currentRevision)
        assertFalse(page.public)
    }

    // ---------- 服务端保管库 ----------

    @Test
    fun parsesVaultLoginCardAndContact() {
        val login = parseVaultLogin(
            JSONObject()
                .put("id", "l-1")
                .put("site", "https://example.com")
                .put("username", "me@example.com")
                .put("notes", "工作账号")
                .put("has_password", true)
                .put("updated_at", "2026-11-16T09:00:00Z"),
        )!!
        assertEquals("me@example.com", login.username)
        assertTrue(login.hasPassword)
        assertEquals("2026-11-16 09:00", login.updatedAt)

        val card = parseVaultCard(
            JSONObject()
                .put("id", "c-1")
                .put("label", "招行 Visa")
                .put("brand", "visa")
                .put("last4", "4242")
                .put("exp_month", 8)
                .put("exp_year", 2029)
                .put("notes", ""),
        )!!
        assertEquals("4242", card.last4)
        assertEquals(2029, card.expYear)

        val contact = parseVaultContact(
            JSONObject()
                .put("id", "ct-1")
                .put("name", "张三")
                .put("phones", JSONArray().put("13800000000").put(""))
                .put("emails", JSONArray().put("z@example.com")),
        )!!
        assertEquals(listOf("13800000000"), contact.phones)
        assertEquals(listOf("z@example.com"), contact.emails)
        assertNull(parseVaultLogin(JSONObject().put("site", "缺 id")))
    }

    // ---------- 例程 ----------

    @Test
    fun parsesRoutineDetailWithTriggers() {
        val routine = parseRoutineDetail(
            JSONObject()
                .put("id", "rt-1")
                .put("bot_id", "b-1")
                .put("name", "每天晨报")
                .put("instruction", "汇总昨天的重要消息")
                .put("schedule", "0 8 * * *")
                .put("enabled", true)
                .put(
                    "event_triggers",
                    JSONArray().put("email.received").put("github.push"),
                )
                .put("next_run_at", "2026-11-17T08:00:00Z")
                .put("last_run_at", JSONObject.NULL)
                .put("created_at", "2026-11-10T08:00:00Z"),
        )!!
        assertEquals("每天晨报", routine.name)
        assertEquals(listOf("email.received", "github.push"), routine.eventTriggers)
        assertEquals("2026-11-17 08:00", routine.nextRunAt)
        assertEquals("", routine.lastRunAt)
        assertNull(parseRoutineDetail(JSONObject().put("name", "缺 id")))
    }

    @Test
    fun routineTriggerSummaryPrefersScheduleThenEvents() {
        val base = RemoteRoutineDetail(
            id = "rt",
            botId = "b",
            name = "例程",
            instruction = "",
            schedule = "",
            enabled = true,
            eventTriggers = emptyList(),
            nextRunAt = "",
            lastRunAt = "",
            createdAt = "",
        )
        assertEquals("手动触发", routineTriggerSummary(base))
        assertEquals(
            "每天 08:00",
            routineTriggerSummary(base.copy(schedule = "0 8 * * *")),
        )
        assertEquals(
            "每天 08:00 · 邮件",
            routineTriggerSummary(
                base.copy(schedule = "0 8 * * *", eventTriggers = listOf("email.received")),
            ),
        )
        assertEquals("邮件", routineTriggerSummary(base.copy(eventTriggers = listOf("email.received"))))
    }

    @Test
    fun labelsEventsAndExplainsCron() {
        assertEquals("邮件", eventLabel("email.received"))
        assertEquals("GitHub push", eventLabel("github.push"))
        assertEquals("GitHub 评论", eventLabel("github.issue_comment"))
        assertEquals("GitHub comment", eventLabel("github.comment"))
        assertEquals("Webhook deploy", eventLabel("webhook.deploy"))
        assertEquals("custom.event", eventLabel("custom.event"))

        assertEquals("每 15 分钟", cronSummary("*/15 * * * *"))
        assertEquals("每小时第 5 分", cronSummary("5 * * * *"))
        assertEquals("每天 08:30", cronSummary("30 8 * * *"))
        assertEquals("工作日 09:00", cronSummary("0 9 * * 1-5"))
        assertEquals("每周一 07:00", cronSummary("0 7 * * 1"))
        assertEquals("每周日 22:00", cronSummary("0 22 * * 7"))
        assertEquals("每周三 10:00", cronSummary("0 10 * * 3"))
        assertEquals("每周 9 · 10:00", cronSummary("0 10 * * 9"))
        // 识别不了的原样返回，避免误导。
        assertEquals("not a cron", cronSummary("not a cron"))
        assertEquals("0 8 * *", cronSummary("0 8 * *"))
    }

    // ---------- 时间展示 ----------

    @Test
    fun normalizesIsoTime() {
        assertEquals("", v040Time(""))
        assertEquals("2026-11-16 09:30", v040Time("2026-11-16T09:30:45Z"))
        assertEquals("2026-11-16 09:30", v040Time("2026-11-16 09:30:45"))
    }

    @Test
    fun formatsRelativeTimeWithFixedNow() {
        val zone = ZoneId.systemDefault()
        fun at(hour: Int, minute: Int): Long =
            LocalDateTime.of(2026, 11, 16, hour, minute).atZone(zone).toInstant().toEpochMilli()

        val now = at(12, 0)
        assertEquals("刚刚", relativeTime("2026-11-16T11:59:30Z", now))
        assertEquals("10 分钟前", relativeTime("2026-11-16T11:50:00Z", now))
        assertEquals("3 小时前", relativeTime("2026-11-16T09:00:00Z", now))
        assertEquals("昨天", relativeTime("2026-11-15T09:00:00Z", now))
        assertEquals("2026-11-10", relativeTime("2026-11-10T09:00:00Z", now))
        assertEquals("", relativeTime("", now))
        // 无法解析时退回截断后的原始时间。
        assertEquals("not-a-time", relativeTime("not-a-time", now))
    }
}
