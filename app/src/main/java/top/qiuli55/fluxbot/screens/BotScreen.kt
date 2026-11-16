package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.AddRow
import top.qiuli55.fluxbot.ui.EmptyCard
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.IconBtn
import top.qiuli55.fluxbot.ui.ItemRow
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.SegTabs
import top.qiuli55.fluxbot.ui.TabHint

/** Bot 详情的三个分区顺序，测试直接校验这份列表。 */
internal val BOT_TABS = listOf("记忆", "例程", "设置")

private fun permissionText(perms: Map<String, Boolean>): String {
    val names = listOf("files" to "文件", "shell" to "命令", "network" to "网络", "apps" to "应用")
    // 服务端默认权限全开：缺失的键按开启处理，避免把「未记录」误显示成「全部关闭」。
    val on = names.filter { perms[it.first] != false }.map { it.second }
    return when {
        on.isEmpty() -> "全部关闭"
        on.size == names.size -> "权限全开"
        else -> on.joinToString(" · ")
    }
}

@Composable
fun BotScreen(botId: String, initialTab: Int = 0) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId)
    var tab by remember(botId) { mutableIntStateOf(initialTab.coerceIn(0, BOT_TABS.lastIndex)) }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        if (bot == null) {
            Box(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            ) {
                Text("这个 Bot 已经被删除。\n回首页看看其他的 Bot。", fontSize = 13.sp, color = Flux.text3, lineHeight = 20.sp)
            }
            return@Column
        }

        BotDetailHeader(
            bot = bot,
            onBack = { ui.back() },
            onDesktop = { ui.push(Route.BotDesktop(bot.id)) },
            onOpenMenu = { ui.sheet = Sheet.BotMenu(bot.id) },
        )

        SegTabs(
            BOT_TABS, tab, { tab = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            when (tab) {
                0 -> MemoryPane(bot)
                1 -> RoutinePane(bot)
                else -> SettingsPane(bot)
            }
        }
    }
}

/**
 * Bot 详情页顶部：返回、头像、名称、电脑与通道标记、桌面和更多入口。
 *
 * 单独抽出来，JVM 截图可以直接覆盖「有通道标记」的头部，
 * 不需要构造 Store / 路由。
 */
@Composable
internal fun BotDetailHeader(
    bot: Bot,
    onBack: () -> Unit = {},
    onDesktop: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    avatarAnimated: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(FIcon.Back) { onBack() }
        Spacer(Modifier.width(2.dp))
        BotAvatar(bot, 34.dp, animated = avatarAnimated)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bot.name,
                fontSize = 15.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bot.computer.ifBlank { "A 机" },
                    fontSize = 11.sp,
                    color = Flux.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (bot.channels.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    BotChannelMarkers(bot.channels)
                }
            }
        }
        IconBtn(FIcon.Monitor) { onDesktop() }
        IconBtn(FIcon.More) { onOpenMenu() }
    }
}

/* ---------------- 记忆 ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryPane(bot: Bot) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var loading by remember(bot.id, bot.remote) { mutableStateOf(bot.remote) }

    LaunchedEffect(bot.id, bot.remote) {
        if (!bot.remote) return@LaunchedEffect
        loading = true
        try {
            store.refreshBotMemories(bot.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ui.showToast(e.message ?: "记忆加载失败")
        } finally {
            loading = false
        }
    }

    TabHint(
        if (loading) "加载中…"
        else "记忆跟着账号走，换设备也能用。长按条目可以删除。",
    )
    if (loading) {
        EmptyCard("加载中…")
    } else if (bot.memories.isEmpty()) {
        EmptyCard("还没有记忆。\n你告诉它的偏好、常用账号和处理习惯，都会记在这里。")
    } else {
        FluxCard {
            bot.memories.forEachIndexed { i, m ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                ui.haptic(10)
                                ui.sheet = Sheet.MemoryMenu(bot.id, m.id)
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(m.text, fontSize = 13.5.sp, color = Flux.text2, lineHeight = 21.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (m.source) {
                            "auto" -> Pill("自动记录", Flux.text3, Flux.panel3)
                            "import" -> Pill("导入")
                            else -> Pill("手动添加")
                        }
                        Text(m.ts, fontSize = 10.5.sp, color = Flux.text4)
                    }
                }
                if (i != bot.memories.lastIndex) Hairline()
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    FluxCard {
        AddRow("添加记忆") { ui.sheet = Sheet.MemoryAdd(bot.id) }
    }
}

/* ---------------- 设置 ---------------- */

@Composable
private fun SettingsPane(bot: Bot) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val clipboard = LocalClipboardManager.current

    FluxCard {
        if (!bot.group) {
            ItemRow(
                title = "模型",
                sub = if (bot.model.isBlank()) "跟随云端默认" else bot.model,
                trailing = { RowChevron() },
                onClick = { ui.sheet = Sheet.Model(bot.id) },
            )
        }
        ItemRow(
            title = "Bot 电脑",
            sub = "任务在这台设备上执行",
            trailing = {
                RowValue(bot.computer.ifBlank { "A 机" }.split(" · ").first())
                Spacer(Modifier.width(6.dp))
                RowChevron()
            },
            onClick = { ui.sheet = Sheet.Computer(bot.id) },
        )
        ItemRow(
            title = "权限",
            sub = permissionText(bot.permissions),
            trailing = { RowChevron() },
            onClick = { ui.sheet = Sheet.Permissions(bot.id) },
        )
        ItemRow(
            title = "资料库",
            sub = "Bot 和群聊在任务里看过的网页",
            trailing = {
                RowValue(bot.library.size.toString())
                Spacer(Modifier.width(6.dp))
                RowChevron()
            },
            onClick = { ui.push(Route.Library(bot.id)) },
        )
        ItemRow(
            title = "自动记录记忆",
            sub = "从对话里自动提取偏好与习惯",
            trailing = {
                FluxSwitch(bot.autoMemory) {
                    store.setAutoMemory(bot.id, it)
                    ui.haptic(8)
                    ui.showToast(if (it) "已开启自动记忆" else "已关闭自动记忆")
                }
            },
        )
    }

    Spacer(Modifier.height(12.dp))
    FluxCard {
        ItemRow(
            title = "重命名",
            trailing = { RowChevron() },
            onClick = { ui.sheet = Sheet.RenameBot(bot.id) },
        )
        ItemRow(
            title = "导出对话",
            sub = "保存为纯文本，留在本机",
            trailing = { RowChevron() },
            onClick = {
                val text = bot.messages.joinToString("\n") {
                    (if (it.role == "user") "我" else bot.name) + "：" + it.text
                }
                clipboard.setText(AnnotatedString(text))
                ui.showToast("对话已复制为纯文本")
            },
        )
    }

    Spacer(Modifier.height(12.dp))
    FluxCard {
        ItemRow(
            title = "删除 Bot",
            sub = "对话、记忆与定时任务会一起删除",
            titleColor = Flux.danger,
            onClick = {
                if (bot.id == "b-main") {
                    ui.showToast("主 Bot 不能删除")
                } else {
                    ui.sheet = Sheet.Confirm(
                        title = "删除 ${bot.name}？",
                        text = "对话、记忆与定时任务会一起删除，无法恢复。",
                        action = "删除 Bot",
                        danger = true,
                    ) {
                        store.deleteBot(bot.id)
                        ui.reset(Route.Home)
                        ui.showToast("已删除 ${bot.name}")
                    }
                }
            },
        )
    }
}
