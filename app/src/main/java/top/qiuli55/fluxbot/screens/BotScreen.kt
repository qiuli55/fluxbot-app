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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private val TABS = listOf("记忆", "定时任务", "设置")

private fun permissionText(perms: Map<String, Boolean>): String {
    val names = listOf("files" to "文件", "shell" to "命令", "network" to "网络", "apps" to "应用")
    val on = names.filter { perms[it.first] == true }.map { it.second }
    return if (on.isEmpty()) "全部关闭" else on.joinToString(" · ")
}

@Composable
fun BotScreen(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId)
    var tab by remember(botId) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        if (bot == null) {
            Box(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            ) {
                Text("这个 Bot 已经被删除。\n回首页看看其他的 Bot。", fontSize = 13.sp, color = Flux.text3, lineHeight = 20.sp)
            }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(FIcon.Back) { ui.back() }
            Spacer(Modifier.width(2.dp))
            BotAvatar(bot, 34.dp, animated = true)
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
                Text(
                    bot.computer.ifBlank { "A 机" },
                    fontSize = 11.sp,
                    color = Flux.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconBtn(FIcon.Monitor) { ui.push(Route.BotDesktop(bot.id)) }
            IconBtn(FIcon.More) { ui.sheet = Sheet.BotMenu(bot.id) }
        }

        SegTabs(
            TABS, tab, { tab = it },
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

/* ---------------- 记忆 ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryPane(bot: Bot) {
    val ui = LocalUi.current
    TabHint("记忆跟着账号走，换设备也能用。长按条目可以删除。")
    if (bot.memories.isEmpty()) {
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

/* ---------------- 定时任务 ---------------- */

@Composable
private fun RoutinePane(bot: Bot) {
    val store = LocalStore.current
    val ui = LocalUi.current
    TabHint("到点自动开工。Bot 忙的时候会排队，不会打断正在跑的任务。")
    if (bot.routines.isEmpty()) {
        EmptyCard("还没有定时任务。\n比如每天早上汇总新闻，或者每周一生成周报。")
    } else {
        FluxCard {
            bot.routines.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontSize = 14.sp, fontWeight = FontWeight(460), color = Flux.text)
                        Spacer(Modifier.height(3.dp))
                        Text("${r.freq} · ${r.time} · 时区 Asia/Shanghai", fontSize = 11.5.sp, color = Flux.text3)
                    }
                    Spacer(Modifier.width(12.dp))
                    FluxSwitch(r.enabled) {
                        store.toggleRoutine(bot.id, r.id)
                        ui.haptic(8)
                        ui.showToast(if (r.enabled) "定时任务已暂停" else "定时任务已开启")
                    }
                }
                if (i != bot.routines.lastIndex) Hairline()
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    FluxCard {
        AddRow("新建定时任务") { ui.sheet = Sheet.RoutineAdd(bot.id) }
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
