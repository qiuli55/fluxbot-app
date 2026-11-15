package top.qiuli55.fluxbot.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.RemotePage
import top.qiuli55.fluxbot.data.RemotePageRevision
import top.qiuli55.fluxbot.data.deletePage
import top.qiuli55.fluxbot.data.getPage
import top.qiuli55.fluxbot.data.listPages
import top.qiuli55.fluxbot.data.pageRevisions
import top.qiuli55.fluxbot.data.pageStatusText
import top.qiuli55.fluxbot.data.publishPage
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.data.unpublishPage
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.TabHint
import top.qiuli55.fluxbot.ui.V040Empty
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun PagesScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var pages by remember { mutableStateOf<List<RemotePage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            pages = store.api.listPages()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "listPages failed", e)
            error = e.message ?: "页面加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("Pages", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            PagesContent(
                pages = pages,
                loading = loading,
                error = error,
                onRetry = { refresh++ },
                onOpen = { ui.push(Route.PageDetail(it.id)) },
            )
        }
    }
}

@Composable
internal fun PagesContent(
    pages: List<RemotePage>,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
    onOpen: (RemotePage) -> Unit,
) {
    TabHint("Bot 在对话里生成的网页会出现在这里。公开后，任何拿到链接的人都能访问。")
    when {
        loading -> V040Loading("正在读取页面…")
        error.isNotBlank() -> V040Error(error, onRetry)
        pages.isEmpty() -> V040Empty("还没有页面。\n在对话里让 Bot「把这份结果整理成一页」，它会生成一个 Pages 条目。")
        else -> FluxCard {
            pages.forEachIndexed { index, page ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(page) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            page.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight(460),
                            color = Flux.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Pill(
                            pageStatusText(page.status),
                            if (page.public) Flux.accent else Flux.text3,
                            if (page.public) Flux.accentSoft else Flux.panel3,
                        )
                    }
                    Text(
                        listOfNotNull(
                            page.currentRevision.takeIf { it > 0 }?.let { "第 $it 版" },
                            "Bot",
                            relativeTime(page.updatedAt).ifBlank { page.updatedAt },
                        ).joinToString(" · "),
                        fontSize = 11.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (index != pages.lastIndex) Hairline()
            }
        }
    }
}

@Composable
fun PageDetailScreen(pageId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val clipboard = LocalClipboardManager.current
    var page by remember(pageId) { mutableStateOf<RemotePage?>(null) }
    var revisions by remember(pageId) { mutableStateOf<List<RemotePageRevision>>(emptyList()) }
    var loading by remember(pageId) { mutableStateOf(true) }
    var error by remember(pageId) { mutableStateOf("") }
    var refresh by remember(pageId) { mutableIntStateOf(0) }
    var busy by remember(pageId) { mutableStateOf(false) }

    LaunchedEffect(pageId, refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            page = store.api.getPage(pageId)
            revisions = store.api.pageRevisions(pageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getPage failed id=$pageId", e)
            error = e.message ?: "页面加载失败"
        } finally {
            loading = false
        }
    }

    val link = page?.let { p ->
        val path = p.publicPath.ifBlank {
            p.shareToken.takeIf { it.isNotBlank() }?.let { "/api/v1/public/pages/$it" }.orEmpty()
        }
        if (path.isBlank()) "" else store.api.baseUrl.trimEnd('/') + path
    }.orEmpty()

    fun togglePublic(target: Boolean) {
        val current = page ?: return
        if (busy) return
        busy = true
        ui.scope.launch {
            try {
                page = if (target) store.api.publishPage(current.id) else store.api.unpublishPage(current.id)
                ui.showToast(if (target) "已公开" else "已取消公开")
                ui.refreshData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "publish page failed id=${current.id}", e)
                ui.showToast(e.message ?: "操作失败")
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("页面", onBack = { ui.back() }) {
            val current = page
            if (current != null) {
                Text(
                    "编辑",
                    fontSize = 13.sp,
                    color = Flux.text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Flux.panel2)
                        .border(1.dp, Flux.line, RoundedCornerShape(16.dp))
                        .clickable { ui.sheet = Sheet.PageEdit(current.id) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            when {
                loading -> V040Loading("正在读取页面…")
                page == null -> V040Error(error.ifBlank { "页面不存在" }, { refresh++ })
                else -> {
                    val current = page!!
                    FluxCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                current.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight(560),
                                color = Flux.text,
                            )
                            Text(
                                listOfNotNull(
                                    current.currentRevision.takeIf { it > 0 }?.let { "第 $it 版" },
                                    "Bot",
                                    relativeTime(current.updatedAt).ifBlank { current.updatedAt },
                                ).joinToString(" · "),
                                fontSize = 11.sp,
                                color = Flux.text3,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    FluxCard {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("公开访问", fontSize = 13.5.sp, color = Flux.text)
                                Text(
                                    if (current.public) "任何拿到链接的人都能访问" else "只有你自己能看",
                                    fontSize = 11.sp,
                                    color = Flux.text3,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            FluxSwitch(current.public && !busy) { togglePublic(!current.public) }
                        }
                        if (current.public && link.isNotBlank()) {
                            Hairline()
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    link,
                                    fontSize = 11.sp,
                                    color = Flux.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "复制",
                                    fontSize = 11.5.sp,
                                    color = Flux.text2,
                                    modifier = Modifier.clickable {
                                        clipboard.setText(AnnotatedString(link))
                                        ui.showToast("链接已复制")
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        "预览",
                        fontSize = 12.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
                    FluxCard {
                        Text(
                            current.body.ifBlank { "（正文为空）" },
                            fontSize = 12.5.sp,
                            color = Flux.text2,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }

                    Text(
                        "版本记录 · ${revisions.size} 条",
                        fontSize = 12.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
                    if (revisions.isEmpty()) {
                        V040Empty("服务器没有返回版本记录。")
                    } else {
                        FluxCard {
                            revisions.forEachIndexed { index, revision ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "第 ${revision.revision} 版",
                                        fontSize = 12.5.sp,
                                        color = Flux.text2,
                                        modifier = Modifier.width(72.dp),
                                    )
                                    Text(
                                        "${revision.bodyBytes} 字节",
                                        fontSize = 11.sp,
                                        color = Flux.text4,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        relativeTime(revision.createdAt),
                                        fontSize = 10.5.sp,
                                        color = Flux.text4,
                                    )
                                }
                                if (index != revisions.lastIndex) Hairline()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Flux.dangerSoft)
                            .border(1.dp, Flux.danger.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .clickable {
                                ui.sheet = Sheet.Confirm(
                                    title = "删除页面？",
                                    text = "「${current.title}」与全部版本记录都会被删除。",
                                    action = "删除",
                                    danger = true,
                                ) {
                                    ui.scope.launch {
                                        try {
                                            store.api.deletePage(current.id)
                                            ui.refreshData()
                                            ui.showToast("页面已删除")
                                            ui.back()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Log.w(TAG, "delete page failed id=${current.id}", e)
                                            ui.showToast(e.message ?: "删除失败")
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("删除页面", fontSize = 14.sp, color = Flux.danger)
                    }
                }
            }
        }
    }
}
