package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import top.qiuli55.fluxbot.data.PendingDecision
import top.qiuli55.fluxbot.data.approvalPositionLabel
import top.qiuli55.fluxbot.ui.Flux

/**
 * Grok Bot 风格审批卡：待批准 / 已允许 / 已超时 / 提交失败四个界面状态，
 * 暗色（Flux 令牌）与浅色（近白变体）各一套 JVM 截图。
 */
class ApprovalCardSnapshotsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 824,
            screenHeight = 900,
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

    private val deleteDecision = PendingDecision(
        taskId = "task-1",
        decisionId = "dec-1",
        question = "删除资料需要你确认",
        detail = "Bot 准备执行：rm -rf e2e-victim-b（删除目录）",
        options = listOf("允许", "拒绝"),
        kind = "delete_data",
        meta = mapOf("command" to "rm -rf e2e-victim-b", "tool_name" to "Bash"),
        summary = "删除整个文件夹 e2e-victim-b",
        timeoutSeconds = 300,
        expiresAt = "2026-11-16T10:05:00+00:00",
    )

    /** 很长的绝对路径：收起时只留 3 行，点开后完整展示。 */
    private val longPathDecision = deleteDecision.copy(
        decisionId = "dec-long-path",
        meta = mapOf(
            "command" to "rm -rf /opt/projects/v05/mac-test/backend/data/attachments/2026/11/17/" +
                "e2e-victim-b-long-directory-name/nested/deeper/subdir/" +
                "another-very-long-segment-for-wrapping",
            "tool_name" to "Bash",
        ),
        detail = "Bot 准备执行：rm -rf e2e-victim-b（删除目录）",
    )

    /** 旧后端：没有 summary，卡片必须回到按 kind 生成的旧标题。 */
    private val legacyDecision = deleteDecision.copy(
        decisionId = "dec-legacy",
        summary = "",
        summaryReason = "",
    )

    private fun page(
        name: String,
        darkTheme: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val appBackground = if (darkTheme) Flux.bg else Color(0xFFF3F4F2)
        paparazzi.snapshot(name = name) {
            // 卡面配色由 MaterialTheme.colorScheme.background 的亮度推导：
            // 暗色用 Flux.panel2/panel3，浅色自动切到 lightApprovalCardColors()。
            val scheme = if (darkTheme) {
                darkColorScheme(
                    background = Flux.bg,
                    surface = Flux.panel,
                    onBackground = Flux.text,
                    onSurface = Flux.text,
                )
            } else {
                lightColorScheme(
                    background = appBackground,
                    surface = Color.White,
                    onBackground = Color(0xFF1B2320),
                    onSurface = Color(0xFF1B2320),
                )
            }
            MaterialTheme(colorScheme = scheme) {
                val base = LocalTextStyle.current
                CompositionLocalProvider(LocalTextStyle provides base.copy(fontFamily = chinese)) {
                    Box(Modifier.fillMaxSize().background(appBackground)) {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Box(Modifier.width(360.dp)) { content() }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun pending() = page("30-approval-card-pending") {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Pending,
            onChoose = {},
        )
    }

    @Test
    fun pendingLight() = page("30-approval-card-pending-light", darkTheme = false) {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Pending,
            onChoose = {},
        )
    }

    @Test
    fun answered() = page("31-approval-card-answered") {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Answered(chosen = "允许", allowed = true),
            onChoose = {},
        )
    }

    @Test
    fun answeredLight() = page("31-approval-card-answered-light", darkTheme = false) {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Answered(chosen = "允许", allowed = true),
            onChoose = {},
        )
    }

    @Test
    fun timedOut() = page("32-approval-card-timed-out") {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.TimedOut,
            onChoose = {},
        )
    }

    @Test
    fun timedOutLight() = page("32-approval-card-timed-out-light", darkTheme = false) {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.TimedOut,
            onChoose = {},
        )
    }

    @Test
    fun networkError() = page("33-approval-card-error") {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Failed("提交失败，请重试"),
            onChoose = {},
        )
    }

    @Test
    fun networkErrorLight() = page("33-approval-card-error-light", darkTheme = false) {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Failed("提交失败，请重试"),
            onChoose = {},
        )
    }

    private fun withSummary(
        name: String,
        summary: String,
        summaryReason: String = "",
        kind: String = "delete_data",
        command: String = "rm -rf e2e-victim-b",
        detail: String = "Bot 准备执行：$command（删除目录）",
        meta: Map<String, String> = mapOf("command" to command, "tool_name" to "Bash"),
    ): Unit = page(name) {
        ApprovalCard(
            decision = deleteDecision.copy(
                kind = kind,
                summary = summary,
                summaryReason = summaryReason,
                detail = detail,
                meta = meta,
            ),
            status = ApprovalCardStatus.Pending,
            onChoose = {},
        )
    }

    @Test
    fun paymentSummary() = withSummary(
        name = "34-approval-card-summary-payment",
        summary = "付款 350 元给 张三",
        kind = "payment_amount",
        command = "request_payment_approval amount=350 payee=张三",
        detail = "Bot 准备执行：request_payment_approval amount=350 payee=张三",
        meta = mapOf("tool_name" to "request_payment_approval"),
    )

    @Test
    fun deleteFileSummary() = withSummary(
        name = "35-approval-card-summary-delete-file",
        summary = "删除文件 report.pdf",
        command = "rm report.pdf",
    )

    @Test
    fun deleteFolderSummary() = withSummary(
        name = "36-approval-card-summary-delete-folder",
        summary = "删除整个文件夹 /opt/data",
        command = "rm -rf /opt/data",
    )

    @Test
    fun deleteBotSummary() = withSummary(
        name = "37-approval-card-summary-delete-bot",
        summary = "删除 Bot 小助手",
        command = "bot.delete bot_id=abc",
        meta = mapOf("tool_name" to "bot.delete"),
    )

    @Test
    fun abnormalPaymentSummaryWithReason() = withSummary(
        name = "38-approval-card-summary-abnormal-payment",
        summary = "付款 350 元给 张三",
        summaryReason = "陌生收款方",
        kind = "payment_abnormal",
        command = "request_payment_approval amount=350 payee=张三",
        detail = "Bot 准备执行：request_payment_approval amount=350 payee=张三",
        meta = mapOf("tool_name" to "request_payment_approval"),
    )

    @Test
    fun legacyFallbackSummary() = page("39-approval-card-summary-fallback") {
        ApprovalCard(
            decision = legacyDecision,
            status = ApprovalCardStatus.Pending,
            onChoose = {},
        )
    }

    @Test
    fun longCommandCollapsedToThreeLines() = page("40-approval-card-command-collapsed") {
        ApprovalCard(
            decision = longPathDecision,
            status = ApprovalCardStatus.Pending,
            onChoose = {},
        )
    }

    @Test
    fun longCommandExpandedShowsWholePath() = page("41-approval-card-command-expanded") {
        ApprovalCard(
            decision = longPathDecision,
            status = ApprovalCardStatus.Pending,
            initiallyExpanded = true,
            onChoose = {},
        )
    }

    @Test
    fun positionLabelHiddenForSingleCard() = page("42-approval-card-single-no-position") {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Pending,
            positionLabel = approvalPositionLabel(0, 1),
            onChoose = {},
        )
    }
}
