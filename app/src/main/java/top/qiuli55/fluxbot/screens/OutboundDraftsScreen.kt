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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.RemoteOutboundDraft
import top.qiuli55.fluxbot.data.channelLabel
import top.qiuli55.fluxbot.data.draftStatusText
import top.qiuli55.fluxbot.data.listOutboundDrafts
import top.qiuli55.fluxbot.data.relativeTime
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.V040Empty
import top.qiuli55.fluxbot.ui.V040Error
import top.qiuli55.fluxbot.ui.V040Loading

private const val TAG = "FluxBot/V040"

@Composable
fun OutboundDraftsScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var drafts by remember { mutableStateOf<List<RemoteOutboundDraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var busyId by remember { mutableStateOf("") }

    LaunchedEffect(refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            drafts = store.api.listOutboundDrafts(limit = 100)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "listOutboundDrafts failed", e)
            error = e.message ?: "草稿加载失败"
        } finally {
            loading = false
        }
    }

    fun decide(draft: RemoteOutboundDraft, option: String) {
        if (busyId.isNotBlank()) return
        if (draft.taskId.isBlank() || draft.decisionId.isBlank()) {
            ui.showToast("这条草稿没有关联审批，无法在 App 内处理")
            return
        }
        busyId = draft.id
        ui.scope.launch {
            try {
                store.api.chooseDecision(draft.taskId, draft.decisionId, option)
                ui.haptic(8)
                ui.showToast(if (option == "拒绝") "已拒绝" else "已允许发送")
                ui.refreshData()
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "decide draft failed id=${draft.id}", e)
                ui.showToast(e.message ?: "操作失败")
            } finally {
                busyId = ""
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("待发送草稿", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            OutboundDraftsContent(
                drafts = drafts,
                loading = loading,
                error = error,
                busyId = busyId,
                onRetry = { refresh++ },
                onAllow = { decide(it, "允许") },
                onDeny = { decide(it, "拒绝") },
            )
        }
    }
}

@Composable
internal fun OutboundDraftsContent(
    drafts: List<RemoteOutboundDraft>,
    loading: Boolean,
    error: String,
    busyId: String,
    onRetry: () -> Unit,
    onAllow: (RemoteOutboundDraft) -> Unit,
    onDeny: (RemoteOutboundDraft) -> Unit,
) {
    val pending = drafts.filter { it.status == "pending" }
    val processed = drafts.filter { it.status != "pending" }

    when {
        loading -> V040Loading("正在读取草稿…")
        error.isNotBlank() -> V040Error(error, onRetry)
        drafts.isEmpty() -> V040Empty("还没有出站草稿。\nBot 在群里准备发消息、又命中审批规则时，草稿会先停在这里等你。")
        else -> {
            Text(
                "等你审批",
                fontSize = 12.sp,
                color = Flux.text3,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
            )
            if (pending.isEmpty()) {
                V040Empty("没有待审批的草稿。")
            } else {
                pending.forEach { draft ->
                    PendingDraftCard(
                        draft = draft,
                        busy = busyId == draft.id,
                        onAllow = { onAllow(draft) },
                        onDeny = { onDeny(draft) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Text(
                "已处理 · 最近 ${minOf(100, processed.size)} 条",
                fontSize = 12.sp,
                color = Flux.text3,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
            )
            if (processed.isEmpty()) {
                V040Empty("还没有处理记录。")
            } else {
                FluxCard {
                    processed.take(100).forEachIndexed { index, draft ->
                        ProcessedDraftRow(draft)
                        if (index != minOf(100, processed.size) - 1) Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingDraftCard(
    draft: RemoteOutboundDraft,
    busy: Boolean,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Flux.panel2)
            .border(1.dp, Flux.line, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${channelLabel(draft.channel)}${if (draft.target.isBlank()) "" else " · ${draft.target}"}",
                fontSize = 13.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Pill("待审批", Flux.warn, androidx.compose.ui.graphics.Color(0x1FE8B25E))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            draft.body.ifBlank { "（空草稿）" },
            fontSize = 12.5.sp,
            color = Flux.text2,
            lineHeight = 19.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "出站草稿 · 允许后只发送一次",
            fontSize = 10.5.sp,
            color = Flux.text4,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DraftButton("拒绝", true, !busy, Modifier.weight(1f), onDeny)
            DraftButton("允许", false, !busy, Modifier.weight(1f), onAllow)
        }
    }
}

@Composable
private fun DraftButton(
    text: String,
    danger: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(if (danger) Flux.panel3 else Flux.accent)
            .border(1.dp, if (danger) Flux.line else Flux.accent, shape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight(560),
            color = if (danger) Flux.text2 else Flux.ink,
        )
    }
}

@Composable
private fun ProcessedDraftRow(draft: RemoteOutboundDraft) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${channelLabel(draft.channel)}${if (draft.target.isBlank()) "" else " · ${draft.target}"}",
                fontSize = 13.sp,
                color = Flux.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Pill(
                draftStatusText(draft.status),
                when (draft.status) {
                    "sent" -> Flux.accent
                    "failed" -> Flux.danger
                    "denied", "cancelled" -> Flux.text3
                    else -> Flux.text3
                },
                when (draft.status) {
                    "sent" -> Flux.accentSoft
                    "failed" -> Flux.dangerSoft
                    else -> Flux.panel3
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            draft.body.replace("\n", " ").take(80).ifBlank { "（空草稿）" },
            fontSize = 11.5.sp,
            color = Flux.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(
                draft.resolvedAt.takeIf { it.isNotBlank() }?.let { relativeTime(it) },
                draft.sentAt.takeIf { it.isNotBlank() }?.let { "发送于 ${relativeTime(it)}" },
            ).joinToString(" · ").ifBlank { relativeTime(draft.createdAt) },
            fontSize = 10.5.sp,
            color = Flux.text4,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (draft.error.isNotBlank()) {
            Text(
                draft.error,
                fontSize = 11.sp,
                color = Flux.danger,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Flux.dangerSoft)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}
