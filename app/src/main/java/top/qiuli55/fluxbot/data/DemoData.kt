package top.qiuli55.fluxbot.data

/** 演示数据：与 fluxbot-demo/js/data.js 完全对齐。 */
object DemoData {

    data class AppInfo(
        val id: String,
        val name: String,
        val glyph: String,
        val bg: Long,
        val fg: Long,
        val cat: String,
        val featured: Boolean,
        val desc: String,
    )

    val apps: List<AppInfo> = listOf(
        AppInfo("gmail", "Gmail", "M", 0xFFEA4335, 0xFFFFFFFF, "通信", true, "搜索、阅读、起草与管理邮件。"),
        AppInfo("drive", "Google Drive", "▲", 0xFF1A73E8, 0xFFFFFFFF, "效率", true, "搜索、阅读、创建与共享文件。"),
        AppInfo("calendar", "Google Calendar", "31", 0xFFFFFFFF, 0xFF1A73E8, "效率", true, "查看日程、安排会议。"),
        AppInfo("notion", "Notion", "N", 0xFF111417, 0xFFFFFFFF, "效率", true, "搜索 Notion 页面与数据库，创建和更新条目。"),
        AppInfo("1password", "1Password", "1P", 0xFF0B2A4A, 0xFFFFFFFF, "密码管理器", false, "让 Flux Bot 在它的电脑上使用你的 1Password 登录信息。"),
        AppInfo("slack", "Slack", "#", 0xFF4A154B, 0xFFFFFFFF, "通信", false, "读取频道消息、发送回复与汇总待办。"),
        AppInfo("feishu", "飞书", "飞", 0xFF3370FF, 0xFFFFFFFF, "通信", false, "同步群聊、日历与审批，替你跟进待办。"),
        AppInfo("github", "GitHub", "GH", 0xFF161B22, 0xFFFFFFFF, "开发", false, "查看仓库、Issue 与 Pull Request，读取构建结果。"),
    )

    val appCategories = listOf("全部", "通信", "效率", "开发", "密码管理器")

    private fun msg(id: String, role: String, time: String, text: String, card: TaskCard? = null) =
        Message(id, role, text, time, card)

    private fun mem(id: String, text: String, source: String, ts: String) = Memory(id, text, source, ts)

    private fun rt(id: String, title: String, time: String, freq: String, enabled: Boolean = true) =
        Routine(id, title, time, freq, enabled)

    private val allPerms = mapOf("files" to true, "shell" to true, "network" to true, "apps" to true)

    private fun mainMessages() = listOf(
        msg("m1", "bot", "09:12", "早上好。昨晚的构建全部通过，3 个失败用例重跑后也绿了。今天先做哪一件？"),
        msg("m2", "user", "09:14", "先把未读消息汇总一下，20:00 提醒我复盘。"),
        msg(
            "m3", "bot", "09:14", "收到。汇总已经开始，完成后直接发你；复盘我会提前 10 分钟提醒。",
            TaskCard(
                "已安排今天的任务",
                listOf(
                    TaskStep("汇总未读消息", true),
                    TaskStep("生成三条重点摘要", true),
                    TaskStep("设置 19:50 复盘提醒", true),
                ),
                true,
            ),
        ),
        msg("m4", "bot", "09:15", "还有一件：小红书那篇笔记的评论里有人问价格，我拟了一条回复草稿，要发吗？"),
        msg("m5", "user", "09:16", "先别发，等我确认。"),
    )

    fun mainAccount(): AccountState = AccountState(
        account = Account("acc-liqiu", "li qiu", "qiuli0420@gmail.com", "LQ", "Flux Pro", 72),
        bots = listOf(
            Bot(
                id = "b-main", name = "Flux Bot", seed = "b-main", shape = "cloud", palette = "mint",
                preview = "", time = "在线", unread = null, working = false, hero = true,
                computer = "A 机 · Linux 服务器", messages = mainMessages(),
                memories = listOf(
                    mem("mem-main-1", "偏好中文沟通，回答先给结论再给依据。", "manual", "10 月 2 日"),
                    mem("mem-main-2", "工作时间 09:30–19:00；20:00 后只推送紧急消息。", "auto", "10 月 18 日"),
                    mem("mem-main-3", "常用设备：这台手机、A 机（Linux 服务器）、MacBook。", "manual", "10 月 27 日"),
                ),
                routines = listOf(
                    rt("rt-1", "汇总科技与 AI 新闻", "08:30", "每天"),
                    rt("rt-2", "提醒每日复盘", "19:50", "每天"),
                    rt("rt-3", "生成本周复盘", "09:00", "每周一", false),
                ),
                permissions = allPerms, autoMemory = true,
            ),
            Bot(
                id = "b-report", name = "汇报和统帅", seed = "b-report", shape = "star", palette = "plum",
                preview = "收到更正，今天的快报核查按 8 条口径重算。", time = "22:14", unread = "accent",
                working = true, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("rp1", "bot", "22:10", "今天的快报初稿好了：8 条国内、5 条海外。按老规矩，先给你结论版。"),
                    msg("rp2", "user", "22:12", "口径改成只保留 8 条，重复的合并。"),
                    msg("rp3", "bot", "22:14", "收到更正，今天的快报核查按 8 条口径重算。重复的三条已经合并，标题也重写了。"),
                ),
                memories = listOf(mem("mem-rep-1", "快报固定 8 条，重复内容合并，标题不超过 18 字。", "manual", "10 月 9 日")),
                routines = listOf(rt("rt-rep", "生成每日快报", "21:30", "每天")),
                permissions = mapOf("files" to true, "shell" to false, "network" to true, "apps" to true),
                autoMemory = true,
            ),
            Bot(
                id = "b-xhs", name = "小红书", seed = "b-xhs", shape = "heart", palette = "rose",
                preview = "等待你：用手机号和短信验证码完成登录。", time = "20:40", unread = "warn",
                working = false, hero = false, computer = "这台手机",
                messages = listOf(
                    msg("x1", "bot", "20:36", "那篇笔记的数据起来了：曝光 4.2 万，收藏 862。评论里有 6 个人在问链接。"),
                    msg("x2", "user", "20:38", "帮我回一下，先别给具体价格。"),
                    msg("x3", "bot", "20:40", "草稿写好了，但要发评论得先登录。等待你：用手机号和短信验证码完成登录。"),
                ),
                memories = listOf(mem("mem-xhs-1", "评论区先引导私信，不在公开评论里报价格。", "manual", "10 月 20 日")),
                routines = emptyList(),
                permissions = mapOf("files" to false, "shell" to false, "network" to true, "apps" to true),
                autoMemory = true,
            ),
            Bot(
                id = "b-xianyu", name = "闲鱼", seed = "b-xianyu", shape = "drop", palette = "sky",
                preview = "后台的 4 张截图拍好了，按顺序传给你？", time = "星期二", unread = null,
                working = false, hero = false, computer = "这台手机",
                messages = listOf(
                    msg("y1", "user", "星期二", "把闲置的相机挂上去，价格按我上次说的区间。"),
                    msg("y2", "bot", "星期二", "已按 3200–3600 挂好，标题带上了快门数和成色。后台的 4 张截图拍好了，按顺序传给你？"),
                ),
                memories = emptyList(), routines = emptyList(),
                permissions = mapOf("files" to true, "shell" to false, "network" to true, "apps" to true),
                autoMemory = true,
            ),
            Bot(
                id = "b-wecom", name = "企业微信", seed = "b-wecom", shape = "hex", palette = "slate",
                preview = "修好了。之前一直空转的原因是模型通道没有注入。", time = "星期二", unread = null,
                working = true, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("e1", "user", "星期二", "企业微信那个自动回复怎么一直空转？"),
                    msg("e2", "bot", "星期二", "修好了。之前一直空转的原因是模型通道没有注入；现在已接上 Flux 的默认通道，并补了一条失败重试。"),
                ),
                memories = emptyList(), routines = emptyList(),
                permissions = allPerms, autoMemory = true,
            ),
            Bot(
                id = "b-job", name = "找工作", seed = "b-job", shape = "triangle", palette = "butter",
                preview = "按 HR 活跃度筛完，10 个岗位里只有 3 个值得投。", time = "星期二", unread = null,
                working = false, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("j1", "user", "星期二", "帮我筛一下 Python 后端岗位，优先杭州。"),
                    msg("j2", "bot", "星期二", "按 HR 活跃度筛完，10 个岗位里只有 3 个值得投。两家最近 24 小时活跃，一家在招 AI Agent 方向，匹配度最高。"),
                ),
                memories = emptyList(), routines = emptyList(),
                permissions = mapOf("files" to true, "shell" to false, "network" to true, "apps" to true),
                autoMemory = true,
            ),
            Bot(
                id = "b-stat", name = "统计", seed = "b-stat", shape = "diamond", palette = "moss",
                preview = "Flux 已经装好（1.19.5 版），要不要跑一次基线？", time = "星期二", unread = null,
                working = false, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("s1", "bot", "星期二", "Flux 已经装好（1.19.5 版），要不要跑一次基线？"),
                    msg("s2", "user", "星期二", "跑一下，结果放周报里。"),
                    msg("s3", "bot", "星期二", "基线跑完了：启动 1.8 秒，内存 96 MB，三个核心链路都在阈值内。数据已写进周报草稿。"),
                ),
                memories = listOf(mem("mem-stat-1", "性能基线三项：启动时间、常驻内存、核心链路耗时。", "auto", "10 月 23 日")),
                routines = listOf(rt("rt-stat", "跑性能基线", "10:00", "每周一")),
                permissions = mapOf("files" to true, "shell" to true, "network" to false, "apps" to false),
                autoMemory = true,
            ),
        ),
        settings = Settings(),
        vault = emptyList(),
        connected = listOf("slack", "feishu", "github"),
    )

    fun altAccount(): AccountState = AccountState(
        account = Account("acc-alt", "qiuli0813", "qiuli0813@gmail.com", "QL", "Flux Free", 41),
        bots = listOf(
            Bot(
                id = "b-main", name = "Flux Bot", seed = "b-main", shape = "cloud", palette = "mint",
                preview = "", time = "在线", unread = null, working = false, hero = true,
                computer = "A 机 · Linux 服务器", messages = mainMessages(),
                memories = listOf(mem("mem1", "偏好中文沟通，回答先给结论再给依据。", "manual", "10 月 2 日")),
                routines = listOf(rt("rt1", "每周一生成周报", "09:00", "每周一")),
                permissions = allPerms, autoMemory = true,
            ),
            Bot(
                id = "b-weekly", name = "周报助手", seed = "b-weekly", shape = "flower", palette = "tangerine",
                preview = "本周 12 个任务已完成，2 个延期到下周。", time = "21:05", unread = "accent",
                working = false, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("w1", "bot", "21:00", "周报草稿已经生成：12 个任务完成，2 个延期。要我先发到飞书吗？"),
                    msg("w2", "user", "21:03", "先给我看要点。"),
                    msg("w3", "bot", "21:05", "要点三条：一，构建稳定性提升；二，Android 端登录闭环打通；三，两个延期任务都卡在账号权限。"),
                ),
                memories = listOf(mem("mem2", "周报按「进展 / 风险 / 下周计划」三段式输出。", "auto", "10 月 21 日")),
                routines = emptyList(),
                permissions = allPerms, autoMemory = true,
            ),
            Bot(
                id = "b-review", name = "代码审查", seed = "b-review", shape = "circle", palette = "teal",
                preview = "PR #41 有两处边界问题，已留评论。", time = "18:22", unread = null,
                working = false, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("r1", "user", "18:10", "看一下 PR #41。"),
                    msg("r2", "bot", "18:22", "看完了。两处边界问题：空数组没有提前返回；重试没有上限。其余没问题，已留评论。"),
                ),
                memories = emptyList(), routines = emptyList(),
                permissions = allPerms, autoMemory = true,
            ),
            Bot(
                id = "b-pod", name = "播客摘要", seed = "b-pod", shape = "squircle", palette = "lavender",
                preview = "这期 68 分钟，核心观点整理成 5 条。", time = "星期二", unread = null,
                working = false, hero = false, computer = "A 机 · Linux 服务器",
                messages = listOf(
                    msg("p1", "bot", "星期二", "这期播客 68 分钟，我整理成 5 条核心观点，另有 2 条可执行建议。要看吗？"),
                ),
                memories = emptyList(), routines = emptyList(),
                permissions = allPerms, autoMemory = true,
            ),
        ),
        settings = Settings(notify = false),
        vault = emptyList(),
        connected = listOf("slack", "github"),
    )

    fun seed(): AppState = AppState(
        accounts = listOf(mainAccount(), altAccount()),
        currentId = "acc-liqiu",
        loggedIn = false,
    )

    /** 新账户：一个主 Bot + 一条问候。 */
    fun newAccount(email: String, name: String): AccountState {
        val id = "acc-" + (100000 + (0..899999).random())
        val display = name.ifBlank { email.substringBefore("@") }
        val initials = display.take(2).uppercase()
        return AccountState(
            account = Account(id, display, email, initials, "Flux Free", 0),
            bots = listOf(
                Bot(
                    id = "b-main", name = "Flux Bot", seed = "b-main", shape = "cloud", palette = "mint",
                    preview = "", time = "在线", unread = null, working = false, hero = true,
                    computer = "这台手机",
                    messages = listOf(msg("n1", "bot", "刚刚", "你好，我是你的 Flux Bot。告诉我一件今天要办的事，我会把它安排成任务。")),
                    memories = emptyList(), routines = emptyList(),
                    permissions = allPerms, autoMemory = true,
                ),
            ),
            settings = Settings(),
            vault = emptyList(),
            connected = emptyList(),
        )
    }
}
