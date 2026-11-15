package top.qiuli55.fluxbot.data.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import top.qiuli55.fluxbot.data.Api
import top.qiuli55.fluxbot.data.ApiException
import top.qiuli55.fluxbot.data.CloudAuthManager
import top.qiuli55.fluxbot.data.CloudClient
import top.qiuli55.fluxbot.data.KeyValueStore
import top.qiuli55.fluxbot.data.SecureCloudTokenStore
import top.qiuli55.fluxbot.data.SecretStore
import top.qiuli55.fluxbot.data.UrlConnectionHttpTransport
import top.qiuli55.fluxbot.data.appendJournal
import top.qiuli55.fluxbot.data.browserStatus
import top.qiuli55.fluxbot.data.channelStatuses
import top.qiuli55.fluxbot.data.connectorCatalog
import top.qiuli55.fluxbot.data.createSkill
import top.qiuli55.fluxbot.data.deleteJournal
import top.qiuli55.fluxbot.data.deleteRoutineDetail
import top.qiuli55.fluxbot.data.deleteSkill
import top.qiuli55.fluxbot.data.getSkill
import top.qiuli55.fluxbot.data.listOutboundDrafts
import top.qiuli55.fluxbot.data.listPages
import top.qiuli55.fluxbot.data.listRoutineDetails
import top.qiuli55.fluxbot.data.listSkills
import top.qiuli55.fluxbot.data.listSubtasks
import top.qiuli55.fluxbot.data.listVaultCards
import top.qiuli55.fluxbot.data.listVaultContacts
import top.qiuli55.fluxbot.data.listVaultLogins
import top.qiuli55.fluxbot.data.sharedMemory
import top.qiuli55.fluxbot.data.updateRoutineDetail
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * 真机以外的端到端契约测试：直接跑 App 的生产代码（CloudAuthManager / Api /
 * ApiV040 的解析器）打真实本地服务，不用 mock，也不需要 Robolectric。
 *
 * 只有同时设置 FLUXBOT_LIVE_CLOUD 与 FLUXBOT_LIVE_BOT 才执行；CI 默认跳过。
 * 输出的结果表会列出每个端点、状态与解析结果，测试结束后汇总断言。
 */
class LiveE2ETest {

    private data class Row(
        val method: String,
        val path: String,
        val status: String,
        val parseOk: Boolean,
        val note: String = "",
        val expectedFailure: Boolean = false,
    )

    // ---------------------------------------------------------------- 内存存储

    private class MemorySecretStore : SecretStore {
        private val values = LinkedHashMap<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String): Boolean {
            values[key] = value
            return true
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun removePrefix(prefix: String) {
            values.keys.filter { it.startsWith(prefix) }.forEach { values.remove(it) }
        }

        override fun getJson(key: String): JSONObject? =
            get(key)?.let { runCatching { JSONObject(it) }.getOrNull() }

        override fun putJson(key: String, value: JSONObject): Boolean = put(key, value.toString())
    }

    private class MemoryKeyValueStore : KeyValueStore {
        private val values = LinkedHashMap<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private fun manager(
        cloudUrl: String,
        tokenStore: SecureCloudTokenStore,
        logs: MutableList<String>? = null,
        expired: AtomicInteger? = null,
    ): CloudAuthManager = CloudAuthManager(
        client = CloudClient(UrlConnectionHttpTransport()) { cloudUrl },
        tokenStore = tokenStore,
        logger = { line -> logs?.add(line) },
        onSessionExpired = { expired?.incrementAndGet() },
    )

    private fun newApi(botUrl: String, cloud: CloudAuthManager): Api =
        Api(MemoryKeyValueStore(), MemorySecretStore(), cloud).apply {
            baseUrl = botUrl
            useCloudAuth = true
        }

    private suspend fun <T> step(
        rows: MutableList<Row>,
        method: String,
        path: String,
        note: String = "",
        expectedFailure: Boolean = false,
        block: suspend () -> T,
    ): T? = try {
        val value = block()
        rows += Row(method, path, "2xx", parseOk = true, note = note, expectedFailure = expectedFailure)
        value
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val status = (e as? ApiException)?.status?.takeIf { it > 0 }?.toString() ?: "ERR"
        val detail = e.message ?: e.javaClass.simpleName
        rows += Row(
            method = method,
            path = path,
            status = status,
            parseOk = false,
            note = if (note.isBlank()) detail else "$note：$detail",
            expectedFailure = expectedFailure,
        )
        null
    }

    private fun newSuffix(): String = UUID.randomUUID().toString().replace("-", "").take(10)

    private fun row(rows: List<Row>): String = buildString {
        appendLine("LIVE-E2E ENDPOINT TABLE")
        appendLine("METHOD\tPATH\tSTATUS\tPARSE\tNOTE")
        rows.forEach { r ->
            appendLine(
                "${r.method}\t${r.path}\t${r.status}\t" +
                    (if (r.parseOk) "ok" else "FAIL") + "\t" + r.note,
            )
        }
    }

    // ---------------------------------------------------------------- 测试入口

    @Test
    fun liveEndToEnd() {
        val cloudUrl = System.getenv("FLUXBOT_LIVE_CLOUD")?.trim()?.takeIf { it.isNotEmpty() }
        val botUrl = System.getenv("FLUXBOT_LIVE_BOT")?.trim()?.takeIf { it.isNotEmpty() }
        assumeTrue(
            "FLUXBOT_LIVE_CLOUD / FLUXBOT_LIVE_BOT 未设置，跳过 live e2e",
            cloudUrl != null && botUrl != null,
        )

        val rows = mutableListOf<Row>()
        val notes = mutableListOf<String>()
        try {
            runBlocking { flow(cloudUrl!!, botUrl!!, rows, notes) }
        } catch (e: Throwable) {
            println(row(rows))
            notes.forEach { println("NOTE: $it") }
            throw e
        }
        println(row(rows))
        notes.forEach { println("NOTE: $it") }

        val unexpected = rows.filter { !it.expectedFailure && !it.parseOk }
        if (unexpected.isNotEmpty()) {
            fail(
                buildString {
                    append("live 契约测试有 ${unexpected.size} 个端点失败：")
                    unexpected.forEach {
                        append("\n${it.method} ${it.path} -> ${it.status} ${it.note}")
                    }
                },
            )
        }
    }

    private suspend fun flow(
        cloudUrl: String,
        botUrl: String,
        rows: MutableList<Row>,
        notes: MutableList<String>,
    ) {
        val suffix = newSuffix()
        val email = "live-e2e-$suffix@example.test"
        val username = "livee2e$suffix"
        val password = "Lv-$suffix-Aa1"
        val secretStore = MemorySecretStore()
        val tokenStore = SecureCloudTokenStore(secretStore)
        val logs = mutableListOf<String>()
        val expired = AtomicInteger()
        val cloud = manager(cloudUrl, tokenStore, logs, expired)
        val api = newApi(botUrl, cloud)

        // ---------- 1. 注册后立即登录 ----------
        val registered = step(rows, "POST", "/api/auth/register", "register + login/token") {
            val session = cloud.register(email, password, username, "Live E2E")
            assertTrue(session.accessToken.isNotBlank())
            assertTrue(session.refreshToken.isNotBlank())
            assertEquals(username, session.user.username)
            assertNotNull(tokenStore.load())
            assertEquals(session.accessToken, tokenStore.load()?.accessToken)
            session
        }
        assertNotNull("注册失败，后续步骤无法继续", registered)

        step(rows, "GET", "/api/auth/me", "Bearer access token") {
            val me = cloud.fetchMe()
            assertEquals(username, me.user.username)
            assertEquals(email, me.user.email)
        }

        // ---------- 2. 错误密码与正确密码 ----------
        step(rows, "POST", "/api/auth/login/token", "错误密码应返回中文错误", expectedFailure = false) {
            val error = runCatching { cloud.login(email, "$password-wrong") }.exceptionOrNull()
            assertTrue("错误密码没有失败", error is ApiException)
            assertEquals(401, (error as ApiException).status)
            assertEquals("账号或密码不正确", error.message)
            assertTrue("失败登录不应清掉已有会话", cloud.isLoggedIn)
        }

        step(rows, "POST", "/api/auth/login/token", "正确密码") {
            val session = cloud.login(email, password)
            assertTrue(session.accessToken.isNotBlank())
            assertNotNull(tokenStore.load())
        }

        // ---------- 3. Bot 读路径 ----------
        step(rows, "GET", "/api/v1/health", "健康检查") {
            assertTrue(api.health())
        }

        val created = step(rows, "POST", "/api/v1/bots", "创建 Bot") {
            val bot = api.createBot("Live E2E $suffix", "circle", "mint")
            assertTrue(bot.id.isNotBlank())
            bot
        }
        val botId = requireNotNull(created) { "创建 Bot 失败，无法继续 Bot 读路径" }.id

        step(rows, "GET", "/api/v1/bots", "列表包含刚建的 Bot") {
            val bots = api.listBots()
            assertTrue("列表里找不到新建的 Bot", bots.any { it.id == botId })
            assertTrue(bots.first { it.id == botId }.name.startsWith("Live E2E"))
        }

        step(rows, "GET", "/api/v1/desktop", "桌面配置") {
            val desktop = api.desktopConfig()
            assertFalse(desktop.configured)
        }

        step(rows, "GET", "/api/v1/connectors", "连接器状态") {
            val connectors = api.listConnectors()
            assertTrue("连接器列表为空", connectors.isNotEmpty())
            assertTrue(connectors.any { it.id == "github" })
        }

        step(rows, "GET", "/api/v1/connectors/catalog", "连接器目录") {
            val catalog = api.connectorCatalog()
            assertTrue("连接器目录为空", catalog.isNotEmpty())
            assertTrue(catalog.any { it.id == "email" && it.authTypes.contains("api_key") })
        }

        step(rows, "GET", "/api/v1/channels/status", "通道状态") {
            val channels = api.channelStatuses()
            assertTrue("通道状态为空", channels.isNotEmpty())
            assertTrue(channels.all { it.channel.isNotBlank() })
        }

        step(rows, "GET", "/api/v1/browser/status", "浏览器 MCP 状态") {
            val browser = api.browserStatus()
            assertFalse(browser.configured)
            assertNull(browser.ok)
            assertEquals("browser", browser.serverName)
        }

        step(rows, "GET", "/api/v1/user-memory?journal_limit=50", "共享用户记忆") {
            val memory = api.sharedMemory()
            assertNotNull(memory.profile)
            assertNotNull(memory.journal)
        }

        step(rows, "GET", "/api/v1/pages?limit=100", "页面列表") {
            api.listPages()
        }

        step(rows, "GET", "/api/v1/vault/logins", "保管库登录项") {
            api.listVaultLogins()
        }

        step(rows, "GET", "/api/v1/vault/cards", "保管库支付卡") {
            api.listVaultCards()
        }

        step(rows, "GET", "/api/v1/vault/contacts", "保管库联系人") {
            api.listVaultContacts()
        }

        step(rows, "GET", "/api/v1/skills?limit=200", "技能列表") {
            api.listSkills()
        }

        step(rows, "GET", "/api/v1/outbound-drafts?limit=100", "出站草稿") {
            api.listOutboundDrafts()
        }

        step(rows, "GET", "/api/v1/approval-rules", "审批规则") {
            api.listApprovalRules()
        }

        step(rows, "GET", "/api/v1/bots/$botId/routines", "例程详情解析器") {
            val routines = api.listRoutineDetails(botId)
            assertTrue(routines.isEmpty())
        }

        step(rows, "GET", "/api/v1/bots/$botId/routines", "例程旧解析器") {
            val routines = api.listRoutines(botId)
            assertTrue(routines.isEmpty())
        }

        step(rows, "GET", "/api/v1/bots/$botId/subtasks?limit=50", "后台子任务") {
            assertTrue(api.listSubtasks(botId).isEmpty())
        }

        step(rows, "GET", "/api/v1/bots/$botId/memories", "Bot 记忆") {
            assertTrue(api.listMemories(botId).isEmpty())
        }

        step(rows, "GET", "/api/v1/bots/$botId/tasks?limit=50", "任务列表") {
            api.listTasks(botId)
        }

        step(rows, "GET", "/api/v1/bots/$botId/conversation?limit=50", "会话历史") {
            api.conversation(botId)
        }

        step(rows, "GET", "/api/v1/bots/$botId/tasks?status=waiting_for_user_decision", "待审批决策") {
            api.listPendingDecisions(botId)
        }

        // 已知服务端缺口：flux-bot main 没有模型目录接口，App 的模型选择器会静默降级。
        step(rows, "GET", "/api/v1/dsh/models", "服务端不存在该路由", expectedFailure = true) {
            api.listModels()
        }

        // ---------- 4. 安全写路径 ----------
        val skillName = "live-e2e-$suffix"
        val skillBody = "live e2e skill body\nsecond line"
        val skill = step(rows, "POST", "/api/v1/skills", "创建技能") {
            val createdSkill = api.createSkill(skillName, skillBody, "live e2e", "v1.0")
            assertEquals(skillName, createdSkill.name)
            assertEquals(skillBody.length, createdSkill.bodyChars)
            assertTrue(createdSkill.enabled)
            createdSkill
        }
        val skillId = skill?.id

        if (skillId != null) {
            step(rows, "GET", "/api/v1/skills/$skillId", "读取技能正文") {
                val fetched = api.getSkill(skillId)
                assertEquals(skillBody, fetched.body)
            }
            step(rows, "GET", "/api/v1/skills?limit=200", "列表包含新技能") {
                assertTrue(api.listSkills().any { it.id == skillId })
            }
        } else {
            notes += "技能创建失败，跳过 get/delete 技能"
        }

        val marker = "live-e2e journal $suffix"
        step(rows, "POST", "/api/v1/user-memory/journal", "追加共享记忆日志") {
            api.appendJournal(marker)
        }
        val journalId = step(rows, "GET", "/api/v1/user-memory?journal_limit=50", "日志出现在列表") {
            val entry = api.sharedMemory().journal.firstOrNull { it.content == marker }
            assertNotNull("共享记忆里找不到刚写入的日志", entry)
            assertEquals("user", entry?.source)
            entry?.id
        }

        val routineName = "live-e2e-routine-$suffix"
        step(rows, "POST", "/api/v1/bots/$botId/routines", "创建例程") {
            api.addRoutine(botId, routineName, "live e2e instruction", "0 9 * * *")
        }
        val routine = step(rows, "GET", "/api/v1/bots/$botId/routines", "例程解析与内容") {
            val found = api.listRoutineDetails(botId).firstOrNull { it.name == routineName }
            assertNotNull("例程列表里找不到刚创建的例程", found)
            found
        }
        val routineId = routine?.id

        if (routineId != null) {
            step(rows, "PATCH", "/api/v1/bots/$botId/routines/$routineId", "暂停例程") {
                api.updateRoutineDetail(botId, routineId, enabled = false)
                val paused = api.listRoutineDetails(botId).first { it.id == routineId }
                assertFalse("例程没有暂停", paused.enabled)
            }
        } else {
            notes += "例程创建失败，跳过暂停/删除例程"
        }

        // ---------- 6. 数据隔离（先做检查，再清理） ----------
        val secondEmail = "live-e2e-b-$suffix@example.test"
        val secondUsername = "livee2eb$suffix"
        val secondSecret = MemorySecretStore()
        val secondStore = SecureCloudTokenStore(secondSecret)
        val second = manager(cloudUrl, secondStore)
        second.register(secondEmail, password, secondUsername, "Live E2E B")
        val secondApi = newApi(botUrl, second)

        val secondRoutines = runCatching { secondApi.listRoutineDetails(botId) }.getOrDefault(emptyList())
        notes += if (routineId != null && secondRoutines.any { it.id == routineId }) {
            "隔离：第二个用户能看到第一个用户的例程（服务端 list_routines 未按云用户过滤）"
        } else {
            "隔离：第二个用户看不到第一个用户的例程"
        }
        val secondSkills = runCatching { secondApi.listSkills() }.getOrDefault(emptyList())
        notes += if (skillId != null && secondSkills.any { it.id == skillId }) {
            "隔离：第二个用户能看到第一个用户的技能（服务端技能库全局共享）"
        } else {
            "隔离：第二个用户看不到第一个用户的技能"
        }
        val secondMemory = runCatching { secondApi.sharedMemory() }.getOrDefault(null)
        expect(
            notes,
            "隔离：第二个用户的共享记忆不包含第一个用户的日志",
        ) { secondMemory != null && secondMemory.journal.none { it.content == marker } }

        // ---------- 清理写入的数据 ----------
        if (journalId != null) {
            step(rows, "DELETE", "/api/v1/user-memory/journal/$journalId", "删除日志") {
                api.deleteJournal(journalId)
                assertTrue(api.sharedMemory().journal.none { it.id == journalId })
            }
        }
        if (routineId != null) {
            step(rows, "DELETE", "/api/v1/bots/$botId/routines/$routineId", "删除例程") {
                api.deleteRoutineDetail(botId, routineId)
                assertTrue(api.listRoutineDetails(botId).none { it.id == routineId })
            }
        }
        if (skillId != null) {
            step(rows, "DELETE", "/api/v1/skills/$skillId", "删除技能") {
                api.deleteSkill(skillId)
                assertTrue(api.listSkills().none { it.id == skillId })
            }
        }
        step(rows, "DELETE", "/api/v1/bots/$botId", "删除 Bot") {
            api.deleteBot(botId)
            assertTrue(api.listBots().none { it.id == botId })
        }

        // ---------- 5. 令牌刷新 ----------
        // (a) 已过期的 access token：下一次请求前主动走 /api/auth/token。
        val stored = requireNotNull(tokenStore.load()) { "登录态没有落盘" }
        tokenStore.save(
            stored.copy(
                accessToken = "invalid-access-token",
                expiresAtMillis = System.currentTimeMillis() - 60_000L,
            ),
        )
        val refreshLogs = mutableListOf<String>()
        val refreshManager = manager(cloudUrl, tokenStore, refreshLogs)
        val refreshApi = newApi(botUrl, refreshManager)
        step(rows, "GET", "/api/v1/health", "过期令牌主动刷新后重试") {
            assertTrue(refreshApi.health())
        }
        assertTrue(
            "主动刷新没有调用 /api/auth/token：$refreshLogs",
            refreshLogs.any { it.startsWith("cloud token refreshed") },
        )
        assertFalse(tokenStore.load()?.accessToken == "invalid-access-token")

        // (b) 未过期但无效的 access token：Bot 返回 401 后单飞刷新并重试一次。
        // 注意 /api/v1/health 是公开端点，不会返回 401，必须用需要鉴权的读接口。
        val afterRefresh = requireNotNull(tokenStore.load())
        tokenStore.save(
            afterRefresh.copy(
                accessToken = "invalid-access-token",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
            ),
        )
        val retryLogs = mutableListOf<String>()
        val retryManager = manager(cloudUrl, tokenStore, retryLogs)
        val retryApi = newApi(botUrl, retryManager)
        step(rows, "GET", "/api/v1/bots", "401 后刷新并重试") {
            retryApi.listBots()
        }
        assertTrue(
            "401 后没有刷新令牌：$retryLogs",
            retryLogs.any { it.startsWith("cloud token refreshed") },
        )

        // (c) refresh token 损坏：刷新被拒，会话被清空并通知调用方。
        val beforeCorrupt = requireNotNull(tokenStore.load())
        tokenStore.save(
            beforeCorrupt.copy(
                accessToken = "invalid-access-token",
                refreshToken = "definitely-invalid-refresh-token",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
            ),
        )
        val expiredCount = AtomicInteger()
        val corruptManager = manager(cloudUrl, tokenStore, mutableListOf(), expiredCount)
        val corruptApi = newApi(botUrl, corruptManager)
        val corruptError = runCatching { corruptApi.listBots() }.exceptionOrNull()
        rows += Row(
            "GET",
            "/api/v1/bots",
            (corruptError as? ApiException)?.status?.toString() ?: "ERR",
            parseOk = corruptError is ApiException && corruptError.status == 401,
            note = "损坏的 refresh token 应清空会话",
        )
        assertFalse("会话没有被清空", corruptManager.isLoggedIn)
        assertNull("本地令牌没有被删除", tokenStore.load())
        assertNull("SecureStore 里仍残留 cloud.session", secretStore.get("cloud.session"))
        assertEquals("没有触发登录过期回调", 1, expiredCount.get())

        // ---------- 7. 退出登录清空本地存储 ----------
        val logoutManager = manager(cloudUrl, tokenStore)
        logoutManager.login(email, password)
        assertNotNull(tokenStore.load())
        logoutManager.logout()
        assertFalse(logoutManager.isLoggedIn)
        assertNull("退出后令牌仍在", tokenStore.load())
        assertNull("退出后 SecureStore 仍残留 cloud.session", secretStore.get("cloud.session"))
        rows += Row("POST", "/api/auth/logout", "2xx", parseOk = true, note = "本地存储已清空")
    }

    /** 记录隔离检查结果，同时作为硬断言。 */
    private fun expect(notes: MutableList<String>, label: String, block: () -> Boolean) {
        val ok = runCatching(block).getOrDefault(false)
        notes += "$label：${if (ok) "符合预期" else "不符合预期"}"
        assertTrue(label, ok)
    }
}
