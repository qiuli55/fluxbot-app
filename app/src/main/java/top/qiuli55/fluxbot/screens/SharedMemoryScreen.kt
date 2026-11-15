package top.qiuli55.fluxbot.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.RemoteJournalEntry
import top.qiuli55.fluxbot.data.RemoteSharedMemory
import top.qiuli55.fluxbot.data.deleteJournal
import top.qiuli55.fluxbot.data.profileLabel
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.data.sharedMemory
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.AddRow
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.ItemRow
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Empty
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun SharedMemoryScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var memory by remember { mutableStateOf<RemoteSharedMemory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            memory = store.api.sharedMemory()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sharedMemory failed", e)
            error = e.message ?: "共享记忆加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("共享记忆", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            SharedMemoryContent(
                memory = memory,
                loading = loading,
                error = error,
                onRetry = { refresh++ },
                onEditProfile = { key, value -> ui.sheet = Sheet.ProfileEdit(key, value) },
                onAddJournal = { ui.sheet = Sheet.JournalAdd },
                onDeleteJournal = { entry ->
                    ui.sheet = Sheet.Confirm(
                        title = "删除这条记录？",
                        text = entry.content.take(60),
                        action = "删除",
                        danger = true,
                    ) {
                        ui.scope.launch {
                            try {
                                store.api.deleteJournal(entry.id)
                                ui.refreshData()
                                ui.showToast("已删除")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "deleteJournal failed id=${entry.id}", e)
                                ui.showToast(e.message ?: "删除失败")
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
internal fun SharedMemoryContent(
    memory: RemoteSharedMemory?,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
    onEditProfile: (String, String) -> Unit,
    onAddJournal: () -> Unit,
    onDeleteJournal: (RemoteJournalEntry) -> Unit,
) {
    TabHint("这是跨 Bot 共享的资料：所有 Bot 都能读到，用来记住「你是谁」。随手记则记录临时信息。")

    when {
        loading -> V040Loading("正在读取共享记忆…")
        memory == null -> V040Error(error.ifBlank { "共享记忆为空" }, onRetry)
        else -> {
            Text(
                "资料",
                fontSize = 12.sp,
                color = Flux.text3,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
            )
            if (memory.profile.isEmpty()) {
                V040Empty("还没有资料。点下面「+ 加一条资料」写下称呼、时区等。")
            } else {
                FluxCard {
                    memory.profile.entries.forEachIndexed { index, (key, value) ->
                        ItemRow(
                            title = profileLabel(key),
                            sub = value,
                            showDivider = index != memory.profile.size - 1,
                            onClick = { onEditProfile(key, value) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FluxCard {
                AddRow("加一条资料") { onEditProfile("", "") }
            }

            Text(
                "随手记 · 最近 ${memory.journal.size} 条",
                fontSize = 12.sp,
                color = Flux.text3,
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
            )
            if (memory.journal.isEmpty()) {
                V040Empty("还没有随手记。")
            } else {
                FluxCard {
                    memory.journal.forEachIndexed { index, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.content,
                                    fontSize = 13.sp,
                                    color = Flux.text2,
                                    lineHeight = 20.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(
                                        if (entry.source == "user") "我" else "Bot",
                                        relativeTime(entry.createdAt).ifBlank { entry.createdAt },
                                    ).joinToString(" · "),
                                    fontSize = 10.5.sp,
                                    color = Flux.text4,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "删除",
                                fontSize = 11.sp,
                                color = Flux.text4,
                                modifier = Modifier.clickable { onDeleteJournal(entry) },
                            )
                        }
                        if (index != memory.journal.lastIndex) Hairline()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FluxCard {
                AddRow("记一条") { onAddJournal() }
            }
        }
    }
}
