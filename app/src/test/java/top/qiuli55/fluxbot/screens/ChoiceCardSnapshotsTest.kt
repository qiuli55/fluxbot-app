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
import top.qiuli55.fluxbot.data.ChoiceImage
import top.qiuli55.fluxbot.data.ChoiceOption
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.Message

/**
 * 选择卡片的 JVM 整页截图（412×915 dp，暗色，中文）。
 *
 * 覆盖：多选收起 / 展开 / 已勾选、已答态、单选、自定义输入、选项缩略图。
 * 全部为本地样例数据，不发网络请求；缩略图走本地绘制的占位图。
 */
class ChoiceCardSnapshotsTest {

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
        id = "b-main",
        name = "Flux Bot",
        seed = "b-main",
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

    private fun userMsg(id: String, text: String) =
        Message(id = id, role = "user", text = text, time = "10:02")

    private fun componentMsg(
        id: String,
        node: ComponentNode,
        payload: Map<String, String> = emptyMap(),
    ) = Message(
        id = id,
        role = "bot",
        text = "问题",
        time = "10:03",
        component = node,
        kind = "component",
        fallbackText = "问题",
        payload = payload,
    )

    private fun chat(
        name: String,
        messages: List<Message>,
        preview: ChoicePreview? = null,
    ) {
        paparazzi.snapshot(name = name) {
            ChatSnapshot(
                bot = bot,
                messages = messages,
                fontFamily = chinese,
                choicePreview = preview,
            )
        }
    }

    private val taskOptions = listOf(
        ChoiceOption("a", "IM 先回「收到」并保证送达"),
        ChoiceOption("b", "定时任务结果发回聊天"),
        ChoiceOption("c", "子任务完成回流对话"),
        ChoiceOption("d", "快回复加派活的对话回合"),
        ChoiceOption("e", "修改审批规则与 Bot 权限"),
        ChoiceOption("f", "把长任务交给后台 Claude Code"),
    )

    private fun multiCard(
        hint: String = "选择所有适用项",
        allowCustom: Boolean = false,
        options: List<ChoiceOption> = taskOptions,
    ) = ComponentNode.Card(
        title = "先做哪几条？",
        children = listOf(
            ComponentNode.Text("这几条都会影响后面的排期，先挑出这周要做的。"),
            ComponentNode.Choice(
                name = "tasks",
                multiple = true,
                hint = hint,
                allowCustom = allowCustom,
                options = options,
            ),
        ),
    )

    private val question = userMsg("q-1", "这几条先做哪几个？挑完我就去排。")

    @Test
    fun multiCollapsed() = chat(
        "cc-01-multi-collapsed",
        listOf(question, componentMsg("c-1", multiCard())),
    )

    @Test
    fun multiExpanded() = chat(
        "cc-02-multi-expanded",
        listOf(question, componentMsg("c-1", multiCard())),
        preview = ChoicePreview(expanded = true),
    )

    @Test
    fun multiSelected() = chat(
        "cc-03-multi-selected",
        listOf(question, componentMsg("c-1", multiCard())),
        preview = ChoicePreview(selected = setOf("a", "c", "d")),
    )

    @Test
    fun submitted() = chat(
        "cc-04-submitted",
        listOf(
            question,
            componentMsg(
                "c-1",
                multiCard(),
                payload = mapOf(
                    "choice_state" to "answered",
                    "choice_selection" to "a,c,d",
                    "choice_custom" to "",
                ),
            ),
            userMsg("u-2", "我选择以下 3 项：A. IM 先回「收到」并保证送达；C. 子任务完成回流对话；D. 快回复加派活的对话回合"),
        ),
    )

    @Test
    fun single() = chat(
        "cc-05-single",
        listOf(
            userMsg("q-5", "帮我订下周去北京的机票。"),
            componentMsg(
                "c-5",
                ComponentNode.Card(
                    title = "机票选哪一班？",
                    children = listOf(
                        ComponentNode.Text("两班价格差不多，都有靠窗座位。"),
                        ComponentNode.Choice(
                            name = "flight",
                            hint = "请选择一项",
                            options = listOf(
                                ChoiceOption("morning", "早班 · 07:30 起飞"),
                                ChoiceOption("noon", "午班 · 13:10 起飞"),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun customInput() = chat(
        "cc-06-custom-input",
        listOf(
            question,
            componentMsg(
                "c-6",
                multiCard(hint = "选择所有适用项，也可以在「其他」里自己写", allowCustom = true),
            ),
        ),
        preview = ChoicePreview(
            selected = setOf("a"),
            custom = "先做 3 和 5，其余下周再看",
        ),
    )

    @Test
    fun optionImage() = chat(
        "cc-07-option-image",
        listOf(
            userMsg("q-7", "这两个封面用哪个？"),
            componentMsg(
                "c-7",
                ComponentNode.Card(
                    title = "封面选一张",
                    children = listOf(
                        ComponentNode.Choice(
                            name = "cover",
                            multiple = false,
                            hint = "请选择一项",
                            options = listOf(
                                ChoiceOption(
                                    "cover-a",
                                    "深色渐变 · 带产品截图",
                                    ChoiceImage(url = "https://cdn.example.com/cover-a.png"),
                                ),
                                ChoiceOption(
                                    "cover-b",
                                    "浅色留白 · 只有标题",
                                    ChoiceImage(uploadId = "cover_b"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
