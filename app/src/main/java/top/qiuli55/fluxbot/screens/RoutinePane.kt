package top.qiuli55.fluxbot.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import top.qiuli55.fluxbot.data.RemoteRoutineDetail
import top.qiuli55.fluxbot.data.listRoutineDetails
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.data.routineTriggerSummary
import top.qiuli55.fluxbot.data.updateRoutineDetail
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Empty
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun RoutinePane(bot: Bot) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var routines by remember(bot.id) { mutableStateOf<List<RemoteRoutineDetail>>(emptyList()) }
    var loading by remember(bot.id) { mutableStateOf(bot.remote) }
    var error by remember(bot.id) { mutableStateOf("") }
    var refresh by remember(bot.id) { mutableIntStateOf(0) }

    LaunchedEffect(bot.id, bot.remote, refresh, ui.dataRevision) {
        if (!bot.remote) {
            loading = false
            routines = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = ""
        try {
            routines = store.api.listRoutineDetails(bot.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "listRoutineDetails failed bot=${bot.id}", e)
            error = e.message ?: "例程加载失败"
        } finally {
            loading = false
        }
    }

    RoutinePaneContent(
        routines = routines,
        loading = loading,
        error = error,
        remote = bot.remote,
        onRetry = { refresh++ },
        onToggle = { item ->
            ui.haptic(8)
            ui.scope.launch {
                try {
                    store.api.updateRoutineDetail(bot.id, item.id, enabled = !item.enabled)
                    ui.showToast(if (item.enabled) "例程已暂停" else "例程已开启")
                    refresh++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "toggle routine failed bot=${bot.id} id=${item.id}", e)
                    ui.showToast(e.message ?: "更新失败")
                }
            }
        },
        onOpen = { item -> ui.sheet = Sheet.RoutineDetail(bot.id, item.id) },
        onAdd = { ui.sheet = Sheet.RoutineAdd(bot.id) },
    )
}

@Composable
internal fun RoutinePaneContent(
    routines: List<RemoteRoutineDetail>,
    loading: Boolean,
    error: String,
    remote: Boolean,
    onRetry: () -> Unit,
    onToggle: (RemoteRoutineDetail) -> Unit,
    onOpen: (RemoteRoutineDetail) -> Unit,
    onAdd: () -> Unit,
) {
    TabHint("例程由 Bot 在对话里创建：你说「每周一提醒我」，它自己排好时间。你也可以在这里管理。")

    when {
        loading -> V040Loading("正在读取例程…")
        error.isNotBlank() -> V040Error(error, onRetry)
        !remote -> V040Empty("本地演示 Bot 没有例程。\n连接远端 Bot 后，在对话里让它创建一个。")
        routines.isEmpty() -> V040Empty("还没有例程。\n在对话里说「每周一早上九点帮我汇总上周发布」，Bot 会自己创建。")
        else -> FluxCard {
            routines.forEachIndexed { index, item ->
                RoutineRow(item, onToggle = { onToggle(item) }, onOpen = { onOpen(item) })
                if (index != routines.lastIndex) Hairline()
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text(
        "事件入口",
        fontSize = 12.sp,
        color = Flux.text3,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    FluxCard {
        EventEntry(
            "Webhook",
            "POST /api/v1/hooks/routines · 请求头 X-Flux-Signature",
            showDivider = true,
        )
        EventEntry(
            "GitHub",
            "POST /api/v1/hooks/github · push / issue_comment / release",
            showDivider = true,
        )
        EventEntry("邮件", "IMAP 轮询 · 新邮件触发 email.received", showDivider = false)
    }
    Text(
        "事件地址与签名密钥在服务器 .env 里配置；App 只展示入口，不保存密钥。",
        fontSize = 11.sp,
        color = Flux.text4,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
    )

    Spacer(Modifier.height(10.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+ 手动新建", fontSize = 12.sp, color = Flux.accent)
    }
}

@Composable
private fun RoutineRow(
    item: RemoteRoutineDetail,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight(460),
                    color = Flux.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!item.enabled) {
                    Spacer(Modifier.width(7.dp))
                    Text("已暂停", fontSize = 10.sp, color = Flux.text4)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                routineTriggerSummary(item),
                fontSize = 11.5.sp,
                color = Flux.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    item.nextRunAt.takeIf { it.isNotBlank() }?.let { "下次 $it" },
                    item.lastRunAt.takeIf { it.isNotBlank() }?.let { "上次 ${relativeTime(it)}" },
                ).joinToString(" · ").ifBlank { "还没有运行记录" },
                fontSize = 10.5.sp,
                color = Flux.text4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        FluxSwitch(item.enabled) { onToggle() }
    }
}

@Composable
private fun EventEntry(title: String, sub: String, showDivider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Flux.panel3),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.take(1), fontSize = 12.sp, color = Flux.accent)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = Flux.text)
                Text(
                    sub,
                    fontSize = 11.sp,
                    color = Flux.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showDivider) Hairline()
    }
}
