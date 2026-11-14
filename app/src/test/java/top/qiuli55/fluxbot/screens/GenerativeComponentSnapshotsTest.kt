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
import top.qiuli55.fluxbot.data.ChoiceOption
import top.qiuli55.fluxbot.data.ComponentAction
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.Message

/**
 * 生成式聊天组件的 JVM 整页截图。
 *
 * 全部数据为本地样例，不发网络请求；组件树严格使用服务端 schema_version=1
 * 的白名单节点。图片 / 音频用本地绘制的占位预览，Paparazzi 下不会出现空白洞。
 */
class GenerativeComponentSnapshotsTest {

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

    private fun userMsg(id: String, time: String, text: String) =
        Message(id = id, role = "user", text = text, time = time)

    private fun componentMsg(
        id: String,
        time: String,
        node: ComponentNode,
        fallback: String = "卡片消息",
        kind: String = "component",
    ) = Message(
        id = id,
        role = "bot",
        text = fallback,
        time = time,
        component = node,
        kind = kind,
        fallbackText = fallback,
    )

    private fun textMsg(id: String, time: String, text: String) =
        Message(id = id, role = "bot", text = text, time = time)

    private fun chat(name: String, messages: List<Message>) {
        paparazzi.snapshot(name = name) {
            ChatSnapshot(
                bot = bot,
                messages = messages,
                fontFamily = chinese,
            )
        }
    }

    private fun text(value: String, markdown: Boolean = false) =
        ComponentNode.Text(text = value, markdown = markdown)

    private fun file(name: String, mime: String, size: Long) = ComponentNode.File(
        name = name,
        deliveryId = "delivery-demo",
        mime = mime,
        size = size,
        index = 0,
    )

    private fun image(alt: String, index: Int) = ComponentNode.Image(
        deliveryId = "delivery-$index",
        alt = alt,
        mime = "image/png",
        width = 1200,
        height = 1500,
        index = index,
    )

    private fun serverAction(name: String, actionId: String, decisionId: String = "", option: String = "") =
        ComponentAction(
            type = "server",
            name = name,
            actionId = actionId,
            decisionId = decisionId,
            option = option,
        )

    // ------------------------------------------------------------ 聊天组件

    @Test
    fun image() = chat(
        "01-image",
        listOf(
            userMsg("i-u", "10:02", "把刚才会议室白板拍的照片发我。"),
            componentMsg(
                "i-c",
                "10:03",
                ComponentNode.Card(
                    children = listOf(
                        text("这是刚才拍的会议室白板，右上角是下周的排期。"),
                        ComponentNode.Image(
                            deliveryId = "delivery-whiteboard",
                            alt = "会议室白板 · 11 月 14 日",
                            mime = "image/jpeg",
                            width = 1600,
                            height = 1000,
                        ),
                    ),
                ),
                fallback = "这是刚才拍的会议室白板，右上角是下周的排期。",
            ),
        ),
    )

    @Test
    fun imageStack4() = chat(
        "19-image-stack-4",
        listOf(
            userMsg("is4-u", "10:04", "权限审批的几张截图发我看看。"),
            componentMsg(
                "is4-c",
                "10:05",
                ComponentNode.Card(
                    children = listOf(
                        text("这四张是「权限审批」的截图，左右滑动可以切换，点一下能放大看。"),
                        image("规则列表 · 每条都标了创建者", 0),
                        image("添加付款超过 500 元的规则", 1),
                        image("添加命令或任务的规则", 2),
                        image("收到认不出的组件时退回纯文本", 3),
                    ),
                ),
                fallback = "四张权限审批截图：规则列表、付款规则、命令任务规则、纯文本兜底。",
            ),
        ),
    )

    @Test
    fun imageStack2() = chat(
        "20-image-stack-2",
        listOf(
            userMsg("is2-u", "11:04", "首页改版的两版设计稿都发我。"),
            componentMsg(
                "is2-c",
                "11:05",
                ComponentNode.Card(
                    children = listOf(
                        text("两版设计稿都在这里，左右滑动切换。"),
                        image("首页改版 · 第三版", 0),
                        image("首页改版 · 第四版", 1),
                    ),
                ),
                fallback = "两版首页改版设计稿。",
            ),
        ),
    )

    @Test
    fun file() = chat(
        "02-file",
        listOf(
            userMsg("f-u", "10:10", "报销明细整理好了吗？"),
            componentMsg(
                "f-c",
                "10:11",
                ComponentNode.Card(
                    children = listOf(
                        text("整理好了，表格和发票都在这一份里。"),
                        file("出差报销明细.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 131_072),
                    ),
                ),
                fallback = "出差报销明细.xlsx · 表格 · 128 KB",
            ),
        ),
    )

    @Test
    fun audio() = chat(
        "03-audio",
        listOf(
            userMsg("a-u", "10:20", "你刚说什么？我在开会没听清。"),
            componentMsg(
                "a-c",
                "10:21",
                ComponentNode.Card(
                    children = listOf(
                        ComponentNode.Audio(
                            deliveryId = "delivery-voice",
                            durationMs = 12_000,
                            transcript = "记得晚上八点前把方案发我。",
                        ),
                    ),
                ),
                fallback = "语音 0:12：记得晚上八点前把方案发我。",
            ),
        ),
    )

    @Test
    fun fileCard() = chat(
        "04-file-card",
        listOf(
            userMsg("fc-u", "10:30", "把报销要用的材料都发我。"),
            componentMsg(
                "fc-c",
                "10:31",
                ComponentNode.Card(
                    title = "已为你准备好文件",
                    children = listOf(
                        text("报销材料都在这里，按顺序提交就行。"),
                        file("出差报销明细.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 131_072),
                        file("发票汇总.pdf", "application/pdf", 2_480_000),
                    ),
                ),
                fallback = "已为你准备好文件：出差报销明细.xlsx、发票汇总.pdf",
                kind = "file_card",
            ),
        ),
    )

    @Test
    fun designCard() = chat(
        "05-design-card",
        listOf(
            userMsg("dc-u", "11:00", "首页改版的设计稿发我看下。"),
            componentMsg(
                "dc-c",
                "11:01",
                ComponentNode.Card(
                    title = "设计稿",
                    subtitle = "首页改版 · 第三版",
                    children = listOf(
                        ComponentNode.Image(
                            deliveryId = "delivery-design",
                            alt = "首页改版-v3.png",
                            mime = "image/png",
                            width = 1600,
                            height = 1000,
                        ),
                        file("首页改版-v3.png", "image/png", 2_400_000),
                    ),
                ),
                fallback = "设计稿：首页改版-v3.png",
            ),
        ),
    )

    @Test
    fun planCard() = chat(
        "06-plan-card",
        listOf(
            userMsg("pc-u", "11:20", "帮我把这周的行程排一下。"),
            componentMsg(
                "pc-c",
                "11:21",
                ComponentNode.Card(
                    title = "本周行程安排",
                    subtitle = "三步走完，周五复盘",
                    children = listOf(
                        text("实施步骤", markdown = true),
                        ComponentNode.ListBlock(
                            ordered = true,
                            items = listOf(
                                text("**周一** 上午整理需求，下午确认设计稿", markdown = true),
                                text("**周三** 和供应商对齐交付时间与报价", markdown = true),
                                text("**周五** 输出复盘，同步给团队", markdown = true),
                            ),
                        ),
                        text("涉及文件", markdown = true),
                        ComponentNode.ListBlock(
                            items = listOf(
                                text("`本周行程.xlsx`", markdown = true),
                                text("`会议纪要.docx`", markdown = true),
                            ),
                        ),
                    ),
                ),
                fallback = "本周行程安排：周一、周三、周五三步",
                kind = "plan_card",
            ),
        ),
    )

    @Test
    fun handoffTakeover() = chat(
        "07-handoff-takeover",
        listOf(
            userMsg("h-u", "14:00", "小红书的登录我自己来。"),
            componentMsg(
                "h-c",
                "14:01",
                ComponentNode.Card(
                    title = "已为你准备好云桌面",
                    subtitle = "登录这一步需要你本人完成",
                    children = listOf(
                        text("桌面已经准备好，接管后需要你本人输入验证码完成登录。"),
                        ComponentNode.Button(
                            label = "接管",
                            action = ComponentAction(type = "client", name = "open_desktop"),
                        ),
                    ),
                ),
                fallback = "已为你准备好云桌面，点「接管」继续。",
                kind = "handoff",
            ),
        ),
    )

    @Test
    fun secureInput() = chat(
        "08-secure-input",
        listOf(
            userMsg("s-u", "14:20", "验证码发你了，帮我登录。"),
            componentMsg(
                "s-c",
                "14:21",
                ComponentNode.Card(
                    title = "安全输入",
                    children = listOf(
                        text("登录小红书需要短信验证码。验证码只用于本次登录，不会保存。"),
                        ComponentNode.Input(
                            name = "login_code",
                            label = "请输入短信验证码",
                            placeholder = "6 位数字",
                            inputType = "secure",
                            action = serverAction(
                                name = "secure_input.submit",
                                actionId = "secure_input:demo",
                            ),
                        ),
                    ),
                ),
                fallback = "登录小红书需要短信验证码。",
                kind = "secure_input",
            ),
        ),
    )

    @Test
    fun approvalConfirmation() = chat(
        "09-approval-confirmation",
        listOf(
            userMsg("ac-u", "15:00", "把下载目录里的临时文件清掉。"),
            textMsg("ac-t", "15:01", "这个操作命中了你设置的命令规则，我先停下来问你。"),
            componentMsg(
                "ac-c",
                "15:02",
                ComponentNode.Card(
                    title = "需要你确认",
                    subtitle = "命中规则：执行 rm 开头的命令",
                    children = listOf(
                        text("准备执行 `rm -rf ~/Downloads/tmp`，会删除下载目录里的临时文件。", markdown = true),
                        ComponentNode.Button(
                            label = "允许",
                            action = serverAction(
                                "decision.choose",
                                "decision:demo:0",
                                decisionId = "demo",
                                option = "允许",
                            ),
                        ),
                        ComponentNode.Button(
                            label = "拒绝",
                            action = serverAction(
                                "decision.reject",
                                "decision:demo:reject",
                                decisionId = "demo",
                            ),
                        ),
                    ),
                ),
                fallback = "需要你确认：准备执行 rm -rf ~/Downloads/tmp",
                kind = "confirmation",
            ),
        ),
    )

    @Test
    fun decision() = chat(
        "10-decision",
        listOf(
            userMsg("d-u", "16:00", "帮我订下周去北京的机票。"),
            componentMsg(
                "d-c",
                "16:01",
                ComponentNode.Card(
                    title = "需要你决定",
                    children = listOf(
                        text("机票选哪一班？两班价格差不多，都有靠窗座位。"),
                        ComponentNode.Choice(
                            name = "flight",
                            options = listOf(
                                ChoiceOption("morning", "早班 · 07:30 起飞"),
                                ChoiceOption("noon", "午班 · 13:10 起飞"),
                            ),
                        ),
                    ),
                ),
                fallback = "机票选哪一班？早班 07:30 或午班 13:10。",
                kind = "decision",
            ),
        ),
    )

    @Test
    fun summary() = chat(
        "11-summary",
        listOf(
            userMsg("sm-u", "17:00", "今天的事情过一遍。"),
            componentMsg(
                "sm-c",
                "17:01",
                ComponentNode.Card(
                    title = "本轮摘要",
                    children = listOf(
                        text("今天的未读消息过完了，重点有这些："),
                        ComponentNode.ListBlock(
                            items = listOf(
                                text("客户合同已到最后一版，等你签字。"),
                                text("构建失败是缓存问题，已经修复并重跑。"),
                                text("明天 10:00 和设计团队对齐首页改版。"),
                                text("小红书那条笔记曝光 4.2 万，收藏 862。"),
                            ),
                        ),
                    ),
                ),
                fallback = "本轮摘要：合同、构建、设计对齐、小红书数据。",
                kind = "summary",
            ),
        ),
    )

    @Test
    fun subtask() = chat(
        "12-subtask",
        listOf(
            userMsg("st-u", "17:20", "录音整理好了吗？"),
            componentMsg(
                "st-c",
                "17:21",
                ComponentNode.Card(
                    title = "后台子任务 · 整理会议纪要",
                    children = listOf(
                        text("正在转录 42 分钟的录音，完成后我会把要点贴回这里。"),
                        ComponentNode.Progress(
                            label = "转录与分段",
                            percent = 62,
                            status = "running",
                        ),
                    ),
                ),
                fallback = "正在整理会议纪要，已完成 62%。",
                kind = "subtask",
            ),
        ),
    )

    @Test
    fun runFinished() = chat(
        "13-run-finished",
        listOf(
            userMsg("rf-u", "18:00", "周报生成好了吗？"),
            componentMsg(
                "rf-c",
                "18:01",
                ComponentNode.Card(
                    title = "执行完成",
                    children = listOf(
                        text("周报已生成，保存到「文档 / 周报 / 2026-11-14.md」。"),
                        ComponentNode.Progress(
                            label = "运行结果",
                            percent = 100,
                            status = "done",
                        ),
                        ComponentNode.Button(
                            label = "重试",
                            action = ComponentAction(
                                type = "client",
                                name = "open_url",
                                url = "https://flux.qiuli55.top",
                            ),
                        ),
                    ),
                ),
                fallback = "周报已生成，保存到「文档 / 周报 / 2026-11-14.md」。",
                kind = "run_finished",
            ),
        ),
    )

    @Test
    fun taskCard() = chat(
        "14-task-card",
        listOf(
            userMsg("tc-u", "18:20", "这周的汇报材料开始弄吧。"),
            componentMsg(
                "tc-c",
                "18:21",
                ComponentNode.Card(
                    title = "整理本周汇报材料",
                    subtitle = "任务 · 进行中",
                    children = listOf(
                        text("汇总本周 12 个任务的进展与风险，输出一页纸汇报。"),
                        ComponentNode.Progress(
                            label = "进行中",
                            percent = 40,
                            status = "running",
                        ),
                    ),
                ),
                fallback = "任务「整理本周汇报材料」进行中，已完成 40%。",
            ),
        ),
    )

    @Test
    fun fallbackText() = chat(
        "18-fallback-text",
        listOf(
            userMsg("fb-u", "19:00", "服务端又发了什么新卡片？"),
            textMsg(
                "fb-t",
                "19:01",
                "收到一条新版本卡片（schema_version=2），当前版本还不支持。" +
                    "先按纯文本显示：本周行程已更新，周三的会议改到下午三点。",
            ),
        ),
    )

}
