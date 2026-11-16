package top.qiuli55.fluxbot.screens

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
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.data.PendingDecision

/**
 * 多张审批卡的聊天页截图：同一位置只放一张卡，右上角标「1/2」「2/2」，
 * 左右滑动切换；单卡时不显示位置文案。
 */
class ApprovalPagerSnapshotsTest {

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

    private val bot = Bot(
        id = "b-approval",
        name = "Flux Bot",
        seed = "b-approval",
        shape = "cloud",
        palette = "mint",
        preview = "",
        time = "在线",
        unread = null,
        working = false,
        hero = true,
        computer = "A 机 · Linux 服务器",
        messages = emptyList(),
        memories = emptyList(),
        routines = emptyList(),
        permissions = emptyMap(),
        autoMemory = true,
    )

    private val messages = listOf(
        Message(id = "u-1", role = "user", text = "把测试目录清理一下", time = "21:04"),
        Message(
            id = "b-1",
            role = "bot",
            text = "有两处操作需要你确认，左右滑动可以切换。",
            time = "21:04",
        ),
    )

    private val first = PendingDecision(
        taskId = "task-1",
        decisionId = "dec-1",
        question = "删除资料需要你确认",
        detail = "Bot 准备执行：rm -rf e2e-victim-a（删除目录）",
        options = listOf("允许", "拒绝"),
        kind = "delete_data",
        meta = mapOf("command" to "rm -rf e2e-victim-a", "tool_name" to "Bash"),
        summary = "删除整个文件夹 e2e-victim-a",
        timeoutSeconds = 300,
        expiresAt = "2026-11-17T21:09:00+00:00",
        raisedAt = "2026-11-17T21:04:00+00:00",
    )

    private val second = first.copy(
        taskId = "subtask-2",
        decisionId = "dec-2",
        kind = "high_risk_command",
        summary = "执行命令 sudo systemctl restart flux-bot",
        detail = "Bot 准备执行：sudo systemctl restart flux-bot",
        meta = mapOf("command" to "sudo systemctl restart flux-bot", "tool_name" to "Bash"),
        raisedAt = "2026-11-17T21:04:30+00:00",
        expiresAt = "2026-11-17T21:09:30+00:00",
    )

    @Test
    fun firstOfTwoCards() {
        paparazzi.snapshot(name = "43-approval-pager-1-of-2") {
            ChatSnapshot(
                bot = bot,
                messages = messages,
                pending = first,
                additionalPending = listOf(second),
                initialApprovalPage = 0,
                fontFamily = chinese,
            )
        }
    }

    @Test
    fun secondOfTwoCards() {
        paparazzi.snapshot(name = "44-approval-pager-2-of-2") {
            ChatSnapshot(
                bot = bot,
                messages = messages,
                pending = first,
                additionalPending = listOf(second),
                initialApprovalPage = 1,
                fontFamily = chinese,
            )
        }
    }

    @Test
    fun singleCardHasNoPositionLabel() {
        paparazzi.snapshot(name = "45-approval-pager-single") {
            ChatSnapshot(
                bot = bot,
                messages = messages,
                pending = first,
                fontFamily = chinese,
            )
        }
    }
}
