package top.qiuli55.fluxbot.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.RemoteSkill
import top.qiuli55.fluxbot.data.deleteSkill
import top.qiuli55.fluxbot.data.getSkill
import top.qiuli55.fluxbot.data.listSkills
import top.qiuli55.fluxbot.data.updateSkill
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
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
fun SkillsScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var skills by remember { mutableStateOf<List<RemoteSkill>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            skills = store.api.listSkills()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "listSkills failed", e)
            error = e.message ?: "技能加载失败"
        } finally {
            loading = false
        }
    }

    fun toggle(skill: RemoteSkill) {
        ui.haptic(8)
        ui.scope.launch {
            try {
                store.api.updateSkill(skill.id, enabled = !skill.enabled)
                ui.showToast(if (skill.enabled) "已停用 ${skill.name}" else "已启用 ${skill.name}")
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "toggle skill failed id=${skill.id}", e)
                ui.showToast(e.message ?: "更新失败")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("技能库", onBack = { ui.back() }) {
            Text(
                "新建",
                fontSize = 13.sp,
                color = Flux.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Flux.accentSoft)
                    .border(1.dp, Flux.accentLine, RoundedCornerShape(16.dp))
                    .clickable { ui.sheet = Sheet.SkillCreate }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            SkillsContent(
                skills = skills,
                loading = loading,
                error = error,
                onRetry = { refresh++ },
                onToggle = ::toggle,
                onOpen = { ui.push(top.qiuli55.fluxbot.nav.Route.SkillDetail(it.id)) },
            )
        }
    }
}

@Composable
internal fun SkillsContent(
    skills: List<RemoteSkill>,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
    onToggle: (RemoteSkill) -> Unit,
    onOpen: (RemoteSkill) -> Unit,
) {
    TabHint("技能是写给 Bot 的说明书：一段 Markdown，说明某类任务怎么做。启用的技能会随对话一起交给 Bot。")
    when {
        loading -> V040Loading("正在读取技能…")
        error.isNotBlank() -> V040Error(error, onRetry)
        skills.isEmpty() -> V040Empty("还没有技能。\n点右上角「新建」，把一套固定做法写成技能。")
        else -> FluxCard {
            skills.forEachIndexed { index, skill ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(skill) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                skill.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight(460),
                                color = Flux.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (!skill.enabled) {
                                Spacer(Modifier.width(7.dp))
                                Pill("已停用", Flux.text3, Flux.panel3)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            skill.metaLine(),
                            fontSize = 11.5.sp,
                            color = Flux.text3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (skill.description.isNotBlank()) {
                            Text(
                                skill.description,
                                fontSize = 11.sp,
                                color = Flux.text4,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    FluxSwitch(skill.enabled) { onToggle(skill) }
                }
                if (index != skills.lastIndex) Hairline()
            }
        }
    }
    Text(
        "服务器只保存当前版本，不保留历史版本记录；「使用次数」也没有开放接口。",
        fontSize = 11.sp,
        color = Flux.text4,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
    )
}

@Composable
fun SkillDetailScreen(skillId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var skill by remember(skillId) { mutableStateOf<RemoteSkill?>(null) }
    var loading by remember(skillId) { mutableStateOf(true) }
    var error by remember(skillId) { mutableStateOf("") }
    var refresh by remember(skillId) { mutableIntStateOf(0) }

    LaunchedEffect(skillId, refresh, ui.dataRevision) {
        loading = true
        error = ""
        try {
            skill = store.api.getSkill(skillId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getSkill failed id=$skillId", e)
            error = e.message ?: "技能加载失败"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("技能", onBack = { ui.back() }) {
            val current = skill
            if (current != null) {
                Text(
                    "编辑",
                    fontSize = 13.sp,
                    color = Flux.text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Flux.panel2)
                        .border(1.dp, Flux.line, RoundedCornerShape(16.dp))
                        .clickable { ui.sheet = Sheet.SkillEdit(current.id) }
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
            SkillDetailContent(
                skill = skill,
                loading = loading,
                error = error,
                onRetry = { refresh++ },
                onToggle = { current ->
                    ui.scope.launch {
                        try {
                            store.api.updateSkill(current.id, enabled = !current.enabled)
                            refresh++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ui.showToast(e.message ?: "更新失败")
                        }
                    }
                },
                onDelete = { current ->
                    ui.sheet = Sheet.Confirm(
                        title = "删除技能？",
                        text = "「${current.name}」会从技能库移除，无法恢复。",
                        action = "删除",
                        danger = true,
                    ) {
                        ui.scope.launch {
                            try {
                                store.api.deleteSkill(current.id)
                                ui.refreshData()
                                ui.showToast("技能已删除")
                                ui.back()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "delete skill failed id=${current.id}", e)
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
internal fun SkillDetailContent(
    skill: RemoteSkill?,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
    onToggle: (RemoteSkill) -> Unit,
    onDelete: (RemoteSkill) -> Unit,
) {
    when {
        loading -> V040Loading("正在读取技能…")
        skill == null -> V040Error(error.ifBlank { "技能不存在" }, onRetry)
        else -> {
            FluxCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            skill.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight(560),
                            color = Flux.text,
                            modifier = Modifier.weight(1f),
                        )
                        FluxSwitch(skill.enabled) { onToggle(skill) }
                    }
                    Text(
                        skill.metaLine(),
                        fontSize = 11.5.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (skill.description.isNotBlank()) {
                        Text(
                            skill.description,
                            fontSize = 12.5.sp,
                            color = Flux.text2,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                    }
                }
            }

            Text(
                "技能正文",
                fontSize = 12.sp,
                color = Flux.text3,
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
            )
            FluxCard {
                Text(
                    skill.body.ifBlank { "（空）" },
                    fontSize = 12.5.sp,
                    color = Flux.text2,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Text(
                "版本记录：服务器只保留当前版本号（${skill.version.ifBlank { "未标注" }}）与字数，不保存历史正文。",
                fontSize = 11.sp,
                color = Flux.text4,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Flux.dangerSoft)
                    .border(1.dp, Flux.danger.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .clickable { onDelete(skill) },
                contentAlignment = Alignment.Center,
            ) {
                Text("删除技能", fontSize = 14.sp, color = Flux.danger)
            }
        }
    }
}
