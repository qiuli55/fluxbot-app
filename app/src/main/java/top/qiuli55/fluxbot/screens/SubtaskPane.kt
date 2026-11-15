package top.qiuli55.fluxbot.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.data.cancelSubtask
import top.qiuli55.fluxbot.data.listSubtasks
import top.qiuli55.fluxbot.data.RemoteSubtask
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.AddRow
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Empty
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun SubtaskPane(bot: Bot) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var subtasks by remember(bot.id) { mutableStateOf<List<RemoteSubtask>>(emptyList()) }
    var loading by remember(bot.id) { mutableStateOf(bot.remote) }
    var error by remember(bot.id) { mutableStateOf("") }
    var refresh by remember(bot.id) { mutableIntStateOf(0) }

    LaunchedEffect(bot.id, bot.remote, refresh, ui.dataRevision) {
        if (!bot.remote) {
            loading = false
            subtasks = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = ""
        try {
            subtasks = store.api.listSubtasks(bot.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "listSubtasks failed bot=${bot.id}", e)
            error = e.message ?: "子任务加载失败"
        } finally {
            loading = false
        }
    }

    SubtaskPaneContent(
        subtasks = subtasks,
        loading = loading,
        error = error,
        remote = bot.remote,
        onRetry = { refresh++ },
        onStop = { item ->
            ui.scope.launch {
                try {
                    store.api.cancelSubtask(bot.id, item.id)
                    ui.showToast("已停止「${item.title}」")
                    refresh++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "cancelSubtask failed bot=${bot.id} id=${item.id}", e)
                    ui.showToast(e.message ?: "停止失败")
                }
            }
        },
        onOpenChat = { ui.push(Route.Chat(bot.id)) },
        onAdd = {
            if (bot.remote) ui.sheet = Sheet.SubtaskAdd(bot.id)
            else ui.showToast("本地演示 Bot 不支持后台子任务")
        },
    )
}

@Composable
internal fun SubtaskPaneContent(
    subtasks: List<RemoteSubtask>,
    loading: Boolean,
    error: String,
    remote: Boolean,
    onRetry: () -> Unit,
    onStop: (RemoteSubtask) -> Unit,
    onOpenChat: () -> Unit,
    onAdd: () -> Unit,
) {
    val running = subtasks.filter { it.running }
    val finished = subtasks.filterNot { it.running }

    TabHint("长活可以派成后台子任务：它自己跑，不占用对话。开始、完成、失败都会回到对话里。")

    when {
        loading -> V040Loading("正在读取子任务…")
        error.isNotBlank() -> V040Error(error, onRetry)
        !remote -> V040Empty("这个 Bot 还只在本机演示。\n连接远端 Bot 后，可以让它把长活派成后台子任务。")
        else -> {
            if (running.isEmpty() && finished.isEmpty()) {
                V040Empty("还没有子任务。\n在对话里让 Bot 派一个，或点下面的「派一个子任务」。")
            }
            if (running.isNotEmpty()) {
                Text(
                    "进行中",
                    fontSize = 12.sp,
                    color = Flux.text3,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                )
                FluxCard {
                    running.forEachIndexed { index, item ->
                        SubtaskRow(item, onStop = { onStop(item) }, onOpen = onOpenChat)
                        if (index != running.lastIndex) Hairline()
                    }
                }
            }
            if (finished.isNotEmpty()) {
                Text(
                    "已结束 · 最近 ${minOf(50, finished.size)} 条",
                    fontSize = 12.sp,
                    color = Flux.text3,
                    modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                )
                FluxCard {
                    finished.take(50).forEachIndexed { index, item ->
                        SubtaskRow(item, onStop = null, onOpen = onOpenChat)
                        if (index != minOf(50, finished.size) - 1) Hairline()
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    FluxCard {
        AddRow("派一个子任务", onAdd)
    }
}

@Composable
private fun SubtaskRow(item: RemoteSubtask, onStop: (() -> Unit)?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight(460),
                color = Flux.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (item.running) {
                    "运行中 · ${relativeTime(item.createdAt).ifBlank { "刚刚" }}"
                } else {
                    listOf(item.finishedAt.ifBlank { item.createdAt })
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                },
                fontSize = 11.5.sp,
                color = Flux.text3,
            )
            if (item.error.isNotBlank()) {
                Text(
                    item.error,
                    fontSize = 11.sp,
                    color = Flux.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (onStop != null) {
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Flux.panel3)
                    .clickable { onStop() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("停止", fontSize = 12.sp, color = Flux.danger)
            }
        } else {
            Pill(
                if (item.status == "completed") "已完成"
                else if (item.status == "cancelled") "已取消" else "失败",
                if (item.status == "completed") Flux.accent
                else if (item.status == "cancelled") Flux.text3 else Flux.danger,
                if (item.status == "completed") Flux.accentSoft
                else if (item.status == "cancelled") Flux.panel3 else Flux.dangerSoft,
            )
        }
    }
}
