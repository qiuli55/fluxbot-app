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
import top.qiuli55.fluxbot.data.ApprovalRule
import top.qiuli55.fluxbot.data.ApprovalRuleType
import top.qiuli55.fluxbot.data.ApprovalSettings
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.data.BuiltinApprovalRule
import top.qiuli55.fluxbot.data.RemoteChannelLink
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme

/**
 * v0.5 新增界面的 JVM 截图：设置页的通道分区、Bot 列表的通道标记、
 * 自定义 MCP 表单（普通 / 正在连接 / 服务器报错）、审批阈值编辑。
 *
 * 全部数据为本地样例，不发网络请求；文案与真实页面保持一致。
 */
class V050SnapshotsTest {

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

    private val channelLinks = listOf(
        RemoteChannelLink(
            channel = "feishu",
            label = "飞书",
            configured = true,
            missingEnv = emptyList(),
            connected = true,
            botId = "b-main",
            botName = "汇报和统帅",
            source = "app",
        ),
        RemoteChannelLink(
            channel = "wechat",
            label = "微信",
            configured = true,
            missingEnv = emptyList(),
            connected = false,
            botId = "",
            botName = "",
            source = "",
        ),
        RemoteChannelLink(
            channel = "wecom",
            label = "企业微信",
            configured = false,
            missingEnv = listOf("WECOM_CORP_ID", "WECOM_APP_SECRET"),
            connected = false,
            botId = "",
            botName = "",
            source = "",
        ),
        RemoteChannelLink(
            channel = "dingtalk",
            label = "钉钉",
            configured = true,
            missingEnv = emptyList(),
            connected = false,
            botId = "",
            botName = "",
            source = "",
        ),
    )

    private fun bot(
        id: String,
        name: String,
        preview: String,
        channels: List<String>,
    ) = Bot(
        id = id,
        name = name,
        seed = id,
        shape = "cloud",
        palette = "mint",
        preview = preview,
        time = "09:40",
        unread = null,
        working = false,
        hero = false,
        computer = "A 机 · Linux 服务器",
        messages = emptyList(),
        memories = emptyList(),
        routines = emptyList(),
        permissions = emptyMap(),
        autoMemory = true,
        channels = channels,
    )

    private val bots = listOf(
        bot("b-main", "汇报和统帅", "这周的汇报材料我整理好了，你过一眼。", listOf("feishu", "wecom")),
        bot("b-trip", "行程助手", "明天 10:00 的评审改到 14:00。", listOf("dingtalk")),
        bot("b-life", "生活小助手", "周末的机票已经比过价了。", emptyList()),
    )

    private val settings = ApprovalSettings(
        largePaymentThreshold = 500.0,
        currency = "CNY",
        builtins = listOf(
            BuiltinApprovalRule(
                key = "large_payment",
                title = "大额付款",
                description = "单笔 500 元及以上",
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
        ),
    )

    private val rules = listOf(
        ApprovalRule(
            id = "r-payment",
            type = ApprovalRuleType.PAYMENT,
            params = mapOf("amount" to "500", "currency" to "CNY"),
            description = "支付超过 500 元需要确认",
        ),
        ApprovalRule(
            id = "r-command",
            type = ApprovalRuleType.COMMAND,
            params = mapOf("pattern" to "rm", "style" to "glob"),
            description = "执行 rm 开头的命令需要确认",
        ),
        ApprovalRule(
            id = "r-task",
            type = ApprovalRuleType.TASK,
            params = mapOf("task_kind" to "行程规划"),
            description = "「行程规划」任务需要确认",
            createdByKind = "bot",
            createdByBotId = "b-trip",
            createdByBotName = "行程助手",
        ),
    )

    /**
     * 整页截图：页面自己负责滚动和底部按钮，不能再套 verticalScroll。
     *
     * 外层一旦是无界高度的滚动容器，页面里的 weight(1f) 会被压成 0，
     * 只剩标题和底部按钮可见（审批阈值截图曾经的空白问题）。
     */
    private fun fullPage(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            FluxTheme {
                val base = LocalTextStyle.current
                CompositionLocalProvider(LocalTextStyle provides base.copy(fontFamily = chinese)) {
                    Box(Modifier.fillMaxSize().background(Flux.bg)) { content() }
                }
            }
        }
    }

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

    // ------------------------------------------------------------ 通道

    @Test
    fun settingsChannels() = page("30-settings-channels") {
        ChannelSettingsSection(
            links = channelLinks,
            loading = false,
            error = "",
            expanded = "wecom",
            onExpand = {},
            onConnect = {},
            onDisconnect = {},
            onRetry = {},
        )
    }

    @Test
    fun settingsChannelsLoading() = page("31-settings-channels-loading") {
        ChannelSettingsSection(
            links = emptyList(),
            loading = true,
            error = "",
            expanded = "",
            onExpand = {},
            onConnect = {},
            onDisconnect = {},
            onRetry = {},
        )
    }

    @Test
    fun settingsChannelsError() = page("32-settings-channels-error") {
        ChannelSettingsSection(
            links = emptyList(),
            loading = false,
            error = "网络连接超时",
            expanded = "",
            onExpand = {},
            onConnect = {},
            onDisconnect = {},
            onRetry = {},
        )
    }

    @Test
    fun botListChannels() = page("33-home-bot-channels") {
        bots.forEachIndexed { index, item ->
            BotRow(bot = item, onClick = {}, onLongClick = {})
            if (index != bots.lastIndex) Hairline()
        }
    }

    @Test
    fun botDetailHeaderChannels() = page("38-bot-detail-header") {
        BotDetailHeader(bot = bots.first(), avatarAnimated = false)
    }

    // ------------------------------------------------------------ 自定义 MCP

    @Test
    fun mcpAddForm() = page("34-apps-add-mcp") {
        McpAddForm(busy = false, error = "", onSubmit = {})
    }

    @Test
    fun mcpAddFormConnecting() = page("35-apps-add-mcp-connecting") {
        McpAddForm(busy = true, error = "", onSubmit = {})
    }

    @Test
    fun mcpAddFormError() = page("36-apps-add-mcp-error") {
        McpAddForm(
            busy = false,
            error = "无法连接到这个地址：服务器返回 404，请确认路径是 MCP 的 SSE 地址。",
            onSubmit = {},
        )
    }

    // ------------------------------------------------------------ 审批规则

    @Test
    fun approvalThresholdEditor() = fullPage("37-settings-approval-threshold") {
        ApprovalRulesPage(
            rules = rules,
            settings = settings,
            thresholdEditing = true,
            botNameFor = { id -> if (id == "b-trip") "行程助手" else null },
        )
    }
}
