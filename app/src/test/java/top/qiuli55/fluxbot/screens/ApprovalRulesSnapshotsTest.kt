package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme

/**
 * 设置 →「权限审批」页与添加规则表单的 JVM 整页截图。
 *
 * 样例规则覆盖：用户创建 / Bot 创建、启用 / 停用、全局生效；
 * 不访问网络，默认零审批的文案与真实页面完全一致。
 */
class ApprovalRulesSnapshotsTest {

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

    private val sampleRules = listOf(
        ApprovalRule(
            id = "r-payment",
            type = ApprovalRuleType.PAYMENT,
            params = mapOf("amount" to "500", "currency" to "CNY"),
            description = "支付超过 500 元需要审批",
            enabled = true,
            createdByKind = "user",
        ),
        ApprovalRule(
            id = "r-command",
            type = ApprovalRuleType.COMMAND,
            params = mapOf("pattern" to "rm", "style" to "glob"),
            description = "执行 rm 开头的命令需要审批",
            enabled = true,
            createdByKind = "user",
        ),
        ApprovalRule(
            id = "r-task",
            type = ApprovalRuleType.TASK,
            params = mapOf("task_kind" to "行程规划"),
            description = "执行「行程规划」任务时需要审批",
            enabled = true,
            createdByKind = "bot",
            createdByBotId = "bot-trip",
            createdByBotName = "行程助手",
        ),
        ApprovalRule(
            id = "r-outbound",
            type = ApprovalRuleType.OUTBOUND,
            params = mapOf("channel" to "feishu"),
            description = "通过飞书发送出站消息时需要审批",
            enabled = false,
            createdByKind = "user",
        ),
    )

    private fun page(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            FluxTheme {
                val base = LocalTextStyle.current
                CompositionLocalProvider(LocalTextStyle provides base.copy(fontFamily = chinese)) {
                    Box(Modifier.fillMaxSize().background(Flux.bg)) { content() }
                }
            }
        }
    }

    @Test
    fun approvalRulesList() = page("15-settings-approval-rules") {
        ApprovalRulesPage(
            rules = sampleRules,
            botNameFor = { id -> if (id == "bot-trip") "行程助手" else null },
        )
    }

    @Test
    fun addPaymentRule() = page("16-settings-add-payment-rule") {
        ApprovalRulesPage(
            rules = sampleRules,
            botNameFor = { id -> if (id == "bot-trip") "行程助手" else null },
            editor = RuleEditorState.Edit(ApprovalRuleType.PAYMENT),
        )
    }

    @Test
    fun addCommandRule() = page("17-settings-add-command-task-rule") {
        ApprovalRulesPage(
            rules = sampleRules,
            botNameFor = { id -> if (id == "bot-trip") "行程助手" else null },
            editor = RuleEditorState.Edit(ApprovalRuleType.COMMAND),
        )
    }
}
