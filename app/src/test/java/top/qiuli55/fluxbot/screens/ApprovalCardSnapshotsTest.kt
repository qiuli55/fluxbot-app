package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import top.qiuli55.fluxbot.data.PendingDecision
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme

/** Grok Bot 风格审批卡：待批准 / 已允许 / 已超时 / 提交失败四个界面状态的 JVM 截图。 */
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
        timeoutSeconds = 300,
        expiresAt = "2026-11-16T10:05:00+00:00",
    )

    private fun page(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            FluxTheme {
                val base = LocalTextStyle.current
                CompositionLocalProvider(LocalTextStyle provides base.copy(fontFamily = chinese)) {
                    Box(Modifier.fillMaxSize().background(Flux.bg)) {
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
    fun answered() = page("31-approval-card-answered") {
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
    fun networkError() = page("33-approval-card-error") {
        ApprovalCard(
            decision = deleteDecision,
            status = ApprovalCardStatus.Failed("提交失败，请重试"),
            onChoose = {},
        )
    }
}
