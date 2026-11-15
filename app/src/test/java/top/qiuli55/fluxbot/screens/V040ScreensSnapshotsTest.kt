package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import top.qiuli55.fluxbot.R
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.data.PendingDecision
import top.qiuli55.fluxbot.data.RemoteBrowserStatus
import top.qiuli55.fluxbot.data.RemoteJournalEntry
import top.qiuli55.fluxbot.data.RemoteOutboundDraft
import top.qiuli55.fluxbot.data.RemotePage
import top.qiuli55.fluxbot.data.RemoteRoutineDetail
import top.qiuli55.fluxbot.data.RemoteSharedMemory
import top.qiuli55.fluxbot.data.RemoteSkill
import top.qiuli55.fluxbot.data.RemoteSubtask
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme

/**
 * v0.4 新页面的 JVM 整页截图。
 *
 * 全部使用与真实接口同形状的假数据，不访问网络；只渲染纯内容层，
 * 因此能稳定覆盖列表、待审批卡片、空状态与服务器字段。
 */
class V040ScreensSnapshotsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 824,
            screenHeight = 1830,
            xdpi = 320,
            ydpi = 320,
            orientation = ScreenOrientation.PORTRAIT,
            density = Density.XHIGH,
            nightMode = NightMode.NIGHT,
            softButtons = false,
            locale = "zh-rCN",
        ),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
        useDeviceResolution = true,
    )

    private val chinese = FontFamily(Font(R.font.noto_sans_sc))

    private val drafts = listOf(
        RemoteOutboundDraft(
            id = "d-1",
            channel = "feishu",
            status = "pending",
            body = "明天 10:00 的产品评审改到 14:00，会议室还是 A301。",
            target = "产品评审群",
            botId = "b-1",
            taskId = "t-1",
            decisionId = "dec-1",
            error = "",
            sentAt = "",
            resolvedAt = "",
            createdAt = "2026-11-16 09:30",
        ),
        RemoteOutboundDraft(
            id = "d-2",
            channel = "wecom",
            status = "sent",
            body = "周报已发到你的邮箱。",
            target = "张三",
            botId = "b-1",
            taskId = "t-2",
            decisionId = "dec-2",
            error = "",
            sentAt = "2026-11-16 09:00",
            resolvedAt = "2026-11-16 09:00",
            createdAt = "2026-11-16 08:58",
        ),
        RemoteOutboundDraft(
            id = "d-3",
            channel = "dingtalk",
            status = "failed",
            body = "今晚的提醒",
            target = "值班群",
            botId = "b-1",
            taskId = "t-3",
            decisionId = "dec-3",
            error = "access token expired",
            sentAt = "",
            resolvedAt = "2026-11-15 20:00",
            createdAt = "2026-11-15 19:58",
        ),
    )

    private val skills = listOf(
        RemoteSkill(
            id = "s-1",
            name = "周报助手",
            description = "按团队模板汇总本周提交与风险",
            version = "v1.2",
            body = "# 步骤\n1. 拉取本周提交\n2. 按项目分组\n3. 标出风险",
            bodyChars = 2340,
            enabled = true,
            updatedAt = "2026-11-16 08:00",
        ),
        RemoteSkill(
            id = "s-2",
            name = "发布检查",
            description = "上线前逐项核对",
            version = "v0.4",
            body = "# 检查项\n- 版本号\n- 灰度范围",
            bodyChars = 860,
            enabled = false,
            updatedAt = "2026-11-12 18:20",
        ),
    )

    private val browser = RemoteBrowserStatus(
        configured = true,
        serverName = "browser",
        type = "playwright",
        url = "http://127.0.0.1:8931/mcp",
        ok = true,
        statusCode = 200,
        latencyMs = 18,
        error = "",
        lastProbeAt = "2026-11-16 09:20",
        timeoutSeconds = 2,
    )

    private val memory = RemoteSharedMemory(
        profile = linkedMapOf(
            "name" to "小林",
            "timezone" to "Asia/Shanghai",
            "occupation" to "产品经理",
        ),
        journal = listOf(
            RemoteJournalEntry(
                id = "j-1",
                content = "周五要交周报，记得附上本季度的留存数据。",
                source = "user",
                createdAt = "2026-11-16 09:00",
            ),
            RemoteJournalEntry(
                id = "j-2",
                content = "用户更关心首次配置是否能在 5 分钟内完成。",
                source = "bot",
                createdAt = "2026-11-15 17:40",
            ),
        ),
    )

    private val pages = listOf(
        RemotePage(
            id = "p-1",
            botId = "b-1",
            title = "本周行情速览",
            slug = "weekly-market",
            shareToken = "tok-1",
            publicPath = "/api/v1/public/pages/tok-1",
            status = "published",
            currentRevision = 3,
            contentType = "text/markdown",
            updatedAt = "2026-11-16 09:00",
            body = "# 行情\n...",
            bodyBytes = 1024,
        ),
        RemotePage(
            id = "p-2",
            botId = "b-1",
            title = "竞品对比草稿",
            slug = "competitor-draft",
            shareToken = "tok-2",
            publicPath = "/api/v1/public/pages/tok-2",
            status = "draft",
            currentRevision = 1,
            contentType = "text/markdown",
            updatedAt = "2026-11-14 16:20",
            body = "",
            bodyBytes = 320,
        ),
    )

    private val subtasks = listOf(
        RemoteSubtask(
            id = "st-1",
            title = "整理 30 份访谈记录",
            instruction = "按主题聚类并输出摘要",
            status = "running",
            createdAt = "2026-11-16 09:00",
            finishedAt = "",
            error = "",
            result = "",
        ),
        RemoteSubtask(
            id = "st-2",
            title = "导出上月账单",
            instruction = "导出 CSV",
            status = "done",
            createdAt = "2026-11-15 10:00",
            finishedAt = "2026-11-15 10:04",
            error = "",
            result = "已生成 1 个文件",
        ),
    )

    private val routines = listOf(
        RemoteRoutineDetail(
            id = "rt-1",
            botId = "b-1",
            name = "每天晨报",
            instruction = "汇总昨天的重要消息",
            schedule = "0 8 * * *",
            enabled = true,
            eventTriggers = listOf("email.received"),
            nextRunAt = "2026-11-17 08:00",
            lastRunAt = "2026-11-16 08:00",
            createdAt = "2026-11-10 08:00",
        ),
        RemoteRoutineDetail(
            id = "rt-2",
            botId = "b-1",
            name = "GitHub 发布提醒",
            instruction = "有新 release 时通知我",
            schedule = "",
            enabled = false,
            eventTriggers = listOf("github.release"),
            nextRunAt = "",
            lastRunAt = "",
            createdAt = "2026-11-12 12:00",
        ),
    )

    private fun page(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            FluxTheme {
                val base = LocalTextStyle.current
                CompositionLocalProvider(LocalTextStyle provides base.copy(fontFamily = chinese)) {
                    Box(Modifier.fillMaxSize().background(Flux.bg)) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun toolApprovalCard() = page("27-chat-tool-approval") {
        ApprovalCard(
            decision = PendingDecision(
                taskId = "t-1",
                decisionId = "dec-1",
                question = "Claude 请求使用 Bash",
                detail = "命令：rm -rf ./build\n风险：删除目录内全部构建产物",
                options = listOf("允许", "拒绝"),
                kind = "high_risk_command",
                meta = mapOf("tool_name" to "Bash", "working_dir" to "/workspace/app"),
            ),
            busy = false,
            waiting = false,
            onChoose = {},
        )
    }

    @Test
    fun outboundApprovalCard() = page("28-chat-outbound-approval") {
        ApprovalCard(
            decision = PendingDecision(
                taskId = "t-2",
                decisionId = "dec-2",
                question = "发送到飞书",
                detail = "准备发送到 产品评审群\n明天 10:00 的评审改到 14:00。\n触发规则：outbound.feishu",
                options = listOf("允许", "拒绝"),
                kind = "outbound_approval",
                meta = mapOf("channel" to "feishu", "chat_id" to "产品评审群"),
            ),
            busy = false,
            waiting = false,
            onChoose = {},
        )
    }

    @Test
    fun routineCreatedCard() = page("29-chat-routine-created") {
        InlineEventCard(
            msg = Message(
                id = "m-1",
                role = "bot",
                text = "",
                time = "09:30",
                kind = "routine_created",
                payload = mapOf(
                    "routine_id" to "rt-1",
                    "name" to "每周一汇总上周发布",
                    "schedule" to "每周一 09:00",
                    "next" to "2026-11-23 09:00",
                ),
            ),
            onOpenRoutine = {},
            onPauseRoutine = {},
        )
    }

    @Test
    fun outboundDrafts() = page("18-outbound-drafts") {
        OutboundDraftsContent(
            drafts = drafts,
            loading = false,
            error = "",
            busyId = "",
            onRetry = {},
            onAllow = {},
            onDeny = {},
        )
    }

    @Test
    fun skillsList() = page("19-skills") {
        SkillsContent(
            skills = skills,
            loading = false,
            error = "",
            onRetry = {},
            onToggle = {},
            onOpen = {},
        )
    }

    @Test
    fun skillDetail() = page("20-skill-detail") {
        SkillDetailContent(
            skill = skills.first(),
            loading = false,
            error = "",
            onRetry = {},
            onToggle = {},
            onDelete = {},
        )
    }

    @Test
    fun browser() = page("22-browser") {
        BrowserContent(status = browser, loading = false, error = "", onRetry = {})
    }

    @Test
    fun sharedMemory() = page("23-shared-memory") {
        SharedMemoryContent(
            memory = memory,
            loading = false,
            error = "",
            onRetry = {},
            onEditProfile = { _, _ -> },
            onAddJournal = {},
            onDeleteJournal = {},
        )
    }

    @Test
    fun pages() = page("24-pages") {
        PagesContent(
            pages = pages,
            loading = false,
            error = "",
            onRetry = {},
            onOpen = {},
        )
    }

    @Test
    fun subtasks() = page("25-subtasks") {
        SubtaskPaneContent(
            subtasks = subtasks,
            loading = false,
            error = "",
            remote = true,
            onRetry = {},
            onStop = {},
            onOpenChat = {},
            onAdd = {},
        )
    }

    @Test
    fun routines() = page("26-routines") {
        RoutinePaneContent(
            routines = routines,
            loading = false,
            error = "",
            remote = true,
            onRetry = {},
            onToggle = {},
            onOpen = {},
            onAdd = {},
        )
    }
}
