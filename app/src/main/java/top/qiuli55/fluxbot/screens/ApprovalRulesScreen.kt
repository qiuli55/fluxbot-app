package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.ApprovalRule
import top.qiuli55.fluxbot.data.ApprovalRuleDraft
import top.qiuli55.fluxbot.data.ApprovalRuleType
import top.qiuli55.fluxbot.data.ApprovalSettings
import top.qiuli55.fluxbot.data.approvalRuleDescription
import top.qiuli55.fluxbot.data.approvalSettings
import top.qiuli55.fluxbot.data.botCreatorLabel
import top.qiuli55.fluxbot.data.displayDescription
import top.qiuli55.fluxbot.data.fallbackApprovalSettings
import top.qiuli55.fluxbot.data.formatThreshold
import top.qiuli55.fluxbot.data.paramsSummary
import top.qiuli55.fluxbot.data.updateApprovalSettings
import top.qiuli55.fluxbot.data.validateThresholdInput
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.EmptyCard
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.SectionLabel

/** 规则编辑器的两种状态：先选类型，或直接编辑某个类型。 */
internal sealed interface RuleEditorState {
    data object PickType : RuleEditorState
    data class Edit(val type: String, val rule: ApprovalRule? = null) : RuleEditorState
}

/**
 * 设置 →「权限审批」。
 *
 * 规则按云用户隔离，对名下所有 Bot 生效；空列表 = 默认零审批。
 * Bot 可以新增更严格的规则，但停用 / 删除 / 放宽只能由用户在这里操作。
 */
@Composable
fun ApprovalRulesScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var rules by remember { mutableStateOf<List<ApprovalRule>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<RuleEditorState?>(null) }
    var settings by remember { mutableStateOf<ApprovalSettings?>(null) }
    var settingsLoading by remember { mutableStateOf(true) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var thresholdEditing by remember { mutableStateOf(false) }
    var thresholdSaving by remember { mutableStateOf(false) }
    var thresholdError by remember { mutableStateOf("") }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            rules = store.api.listApprovalRules()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "加载审批规则失败"
        } finally {
            loading = false
        }
    }

    suspend fun refreshSettings() {
        settingsLoading = true
        settingsError = null
        try {
            settings = store.api.approvalSettings()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            settingsError = e.message ?: "加载默认规则失败"
        } finally {
            settingsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        refreshSettings()
    }

    ApprovalRulesPage(
        rules = rules,
        loading = loading,
        error = error,
        settings = settings,
        settingsLoading = settingsLoading,
        settingsError = settingsError,
        thresholdEditing = thresholdEditing,
        thresholdSaving = thresholdSaving,
        thresholdError = thresholdError,
        onEditThreshold = { thresholdEditing = it; thresholdError = "" },
        onSaveThreshold = { value ->
            thresholdSaving = true
            thresholdError = ""
            ui.scope.launch {
                try {
                    settings = store.api.updateApprovalSettings(value)
                    thresholdEditing = false
                    ui.showToast("已更新大额付款阈值")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    thresholdError = e.message ?: "保存失败"
                } finally {
                    thresholdSaving = false
                }
            }
        },
        onRetrySettings = { ui.scope.launch { refreshSettings() } },
        botNameFor = { id -> store.bot(id)?.name },
        onBack = { ui.back() },
        onRetry = { ui.scope.launch { refresh() } },
        editor = editor,
        onEditorChange = { editor = it },
        onToggle = { rule, enabled ->
            ui.scope.launch {
                try {
                    val updated = store.api.updateApprovalRule(rule.id, enabled = enabled)
                    rules = rules.map {
                        if (it.id == rule.id) updated ?: it.copy(enabled = enabled) else it
                    }
                    ui.showToast(if (enabled) "规则已开启" else "规则已停用")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ui.showToast(e.message ?: "操作失败")
                }
            }
        },
        onDelete = { rule ->
            ui.sheet = Sheet.Confirm(
                title = "删除这条规则？",
                text = "「${rule.displayDescription()}」\n删除后这类操作会直接执行。",
                action = "删除",
            ) {
                ui.scope.launch {
                    try {
                        store.api.deleteApprovalRule(rule.id)
                        rules = rules.filterNot { it.id == rule.id }
                        ui.showToast("规则已删除")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ui.showToast(e.message ?: "删除失败")
                    }
                }
            }
        },
        onSave = { draft, existing ->
            ui.scope.launch {
                try {
                    if (existing == null) {
                        val created = store.api.createApprovalRule(draft)
                        if (created == null) {
                            ui.showToast("保存失败：服务端没有返回规则")
                            return@launch
                        }
                        rules = rules + created
                        ui.showToast("规则已添加")
                    } else {
                        val updated = store.api.updateApprovalRule(
                            existing.id,
                            type = draft.type,
                            params = draft.params,
                            description = draft.description,
                        )
                        if (updated == null) {
                            ui.showToast("保存失败：服务端没有返回规则")
                            return@launch
                        }
                        rules = rules.map { if (it.id == existing.id) updated else it }
                        ui.showToast("规则已更新")
                    }
                    editor = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ui.showToast(e.message ?: "保存失败")
                }
            }
        },
    )
}

@Composable
internal fun ApprovalRulesPage(
    rules: List<ApprovalRule>,
    loading: Boolean = false,
    error: String? = null,
    settings: ApprovalSettings? = null,
    settingsLoading: Boolean = false,
    settingsError: String? = null,
    thresholdEditing: Boolean = false,
    thresholdSaving: Boolean = false,
    thresholdError: String = "",
    onEditThreshold: (Boolean) -> Unit = {},
    onSaveThreshold: (Double) -> Unit = {},
    onRetrySettings: () -> Unit = {},
    botNameFor: (String) -> String? = { null },
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onToggle: (ApprovalRule, Boolean) -> Unit = { _, _ -> },
    onDelete: (ApprovalRule) -> Unit = {},
    onSave: (ApprovalRuleDraft, ApprovalRule?) -> Unit = { _, _ -> },
    editor: RuleEditorState? = null,
    onEditorChange: (RuleEditorState?) -> Unit = {},
) {
    val resolver: (String) -> String? = { botNameFor(it) }
    val userRules = rules.filter { it.createdByKind != "bot" }
    val botRules = rules.filter { it.createdByKind == "bot" }
    // 设置接口失败时继续展示本地内置项：阈值优先用上一次成功读取的值，
    // 从未成功读取过则用出厂默认值；错误提示单独显示在卡片底部。
    val shownSettings = settings
        ?: if (settingsError != null) fallbackApprovalSettings() else null
    BoxWithConstraints(Modifier.fillMaxSize().background(Flux.bg)) {
        // 真实页面由 AnimatedContent 提供有界高度，weight 会撑满中间区域并固定底部按钮。
        // 若被放进无界高度的滚动容器（例如整页截图脚手架），weight 会被压成 0，
        // 此时改为按内容顺序排列，保证说明、默认项和规则列表仍然可见。
        val boundedHeight = constraints.hasBoundedHeight
        Column(Modifier.fillMaxSize().imePadding()) {
            PageHead("权限审批", onBack = onBack)
            Column(
                Modifier
                    .then(if (boundedHeight) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
            ) {
                Text(
                    "只有这些情况会弹出确认，其他操作都直接执行。",
                    fontSize = 12.sp,
                    color = Flux.text3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                )

                SectionLabel("默认需要确认")
                DefaultRulesCard(
                    settings = shownSettings,
                    loading = settingsLoading,
                    error = settingsError,
                    thresholdEditing = thresholdEditing,
                    thresholdSaving = thresholdSaving,
                    thresholdError = thresholdError,
                    onEditThreshold = onEditThreshold,
                    onSaveThreshold = onSaveThreshold,
                    onRetry = onRetrySettings,
                )

                SectionLabel("我设置的规则")
                when {
                    loading -> Text(
                        "正在加载审批规则…",
                        fontSize = 13.sp,
                        color = Flux.text3,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                    error != null -> ErrorCard(error, onRetry)
                    userRules.isEmpty() -> EmptyCard(
                        "还没有你设置的规则。\n" +
                            "默认不需要确认，Bot 的操作会直接执行。\n" +
                            "点下面的「添加规则」开始限制。",
                    )
                    else -> FluxCard {
                        userRules.forEachIndexed { index, rule ->
                            RuleRow(
                                rule = rule,
                                onToggle = { onToggle(rule, it) },
                                onDelete = { onDelete(rule) },
                                onEdit = { onEditorChange(RuleEditorState.Edit(rule.type, rule)) },
                            )
                            if (index != userRules.lastIndex) Hairline()
                        }
                    }
                }

                SectionLabel("Bot 设置的规则")
                if (!loading && error == null) {
                    if (botRules.isEmpty()) {
                        Text(
                            "Bot 还没有给自己加过规则。",
                            fontSize = 12.sp,
                            color = Flux.text4,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
                        )
                    } else {
                        FluxCard {
                            botRules.forEachIndexed { index, rule ->
                                BotRuleRow(
                                    rule = rule,
                                    creator = rule.botCreatorLabel(resolver),
                                    onDelete = { onDelete(rule) },
                                )
                                if (index != botRules.lastIndex) Hairline()
                            }
                        }
                    }
                }

                Text(
                    "Bot 只能添加更严格的规则，不能停用、删除或放宽已有规则；" +
                        "这些操作只能由你完成。",
                    fontSize = 11.5.sp,
                    color = Flux.text4,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Flux.accent)
                        .clickable { onEditorChange(RuleEditorState.PickType) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FIcon(FIcon.Plus, size = 16.dp, color = Flux.ink)
                        Spacer(Modifier.width(7.dp))
                        Text("添加规则", fontSize = 15.sp, fontWeight = FontWeight(560), color = Flux.ink)
                    }
                }
            }
        }

        editor?.let { state ->
            RuleEditorOverlay(
                state = state,
                onClose = { onEditorChange(null) },
                onPickType = { onEditorChange(RuleEditorState.Edit(it)) },
                onSave = onSave,
            )
        }
    }
}

/* ---------------- 列表 ---------------- */

/** 服务端内置的三类确认场景；大额付款一行可以就地改阈值。 */
@Composable
private fun DefaultRulesCard(
    settings: ApprovalSettings?,
    loading: Boolean,
    error: String?,
    thresholdEditing: Boolean,
    thresholdSaving: Boolean,
    thresholdError: String,
    onEditThreshold: (Boolean) -> Unit,
    onSaveThreshold: (Double) -> Unit,
    onRetry: () -> Unit,
) {
    FluxCard {
        when {
            settings == null && loading -> DefaultCardHint("正在读取默认规则…")
            settings == null -> DefaultCardHint("暂时读不到默认规则。")
            settings.builtins.isEmpty() -> DefaultCardHint("服务器没有返回默认规则。")
            else -> settings.builtins.forEachIndexed { index, item ->
                if (item.key == "large_payment") {
                    LargePaymentRow(
                        title = item.title,
                        description = item.description,
                        threshold = settings.largePaymentThreshold,
                        currency = settings.currency,
                        editing = thresholdEditing,
                        saving = thresholdSaving,
                        serverError = thresholdError,
                        onEdit = onEditThreshold,
                        onSave = onSaveThreshold,
                    )
                } else {
                    BuiltinRow(item.title, item.description)
                }
                if (index != settings.builtins.lastIndex) Hairline()
            }
        }
        // 读取失败时仍保留上面的内置项；这里只放一行安静的提示和重试入口。
        if (error != null && !loading) {
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "默认规则读取失败：$error",
                    fontSize = 11.sp,
                    color = Flux.text4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "重试",
                    fontSize = 11.5.sp,
                    color = Flux.accent,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clickable { onRetry() },
                )
            }
        }
    }
}

@Composable
private fun DefaultCardHint(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = Flux.text3,
        lineHeight = 19.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

@Composable
private fun BuiltinRow(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight(460), color = Flux.text)
        Text(
            description,
            fontSize = 11.5.sp,
            color = Flux.text3,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun LargePaymentRow(
    title: String,
    description: String,
    threshold: Double,
    currency: String,
    editing: Boolean,
    saving: Boolean,
    serverError: String,
    onEdit: (Boolean) -> Unit,
    onSave: (Double) -> Unit,
) {
    val amount = if (currency == "CNY") {
        "¥" + formatThreshold(threshold)
    } else {
        formatThreshold(threshold) + " " + currency
    }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable { onEdit(!editing) }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight(460), color = Flux.text)
                Text(
                    description,
                    fontSize = 11.5.sp,
                    color = Flux.text3,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(amount, fontSize = 12.sp, color = Flux.text3)
            Spacer(Modifier.width(6.dp))
            FIcon(
                if (editing) FIcon.ChevronDown else FIcon.ChevronRight,
                size = 14.dp,
                color = Flux.text4,
            )
        }
        if (editing) {
            ThresholdEditor(
                value = threshold,
                saving = saving,
                serverError = serverError,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun ThresholdEditor(
    value: Double,
    saving: Boolean,
    serverError: String,
    onSave: (Double) -> Unit,
) {
    var input by remember(value) { mutableStateOf(formatThreshold(value)) }
    var localError by remember(value) { mutableStateOf("") }
    val message = localError.ifBlank { serverError }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        Text(
            "金额阈值（元）",
            fontSize = 11.5.sp,
            color = Flux.text3,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FluxInput(
                    input,
                    { raw ->
                        input = raw.filter { c -> c.isDigit() || c == '.' }
                        localError = ""
                    },
                    placeholder = "200",
                    height = 46.dp,
                    keyboardType = KeyboardType.Decimal,
                )
            }
            Spacer(Modifier.width(10.dp))
            EditorButton(
                if (saving) "保存中…" else "保存",
                primary = true,
                modifier = Modifier.width(88.dp),
                enabled = !saving,
            ) {
                val problem = validateThresholdInput(input)
                if (problem != null) {
                    localError = problem
                } else {
                    input.trim().toDoubleOrNull()?.let(onSave)
                }
            }
        }
        if (message.isNotBlank()) {
            Text(
                message,
                fontSize = 11.5.sp,
                color = Flux.danger,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 4.dp, top = 7.dp),
            )
        }
        Text(
            "达到这个金额的付款，Bot 会先停下来等你确认。",
            fontSize = 11.sp,
            color = Flux.text4,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 4.dp, top = 7.dp),
        )
    }
}

/** Bot 自己加的规则：只展示来源与内容，用户可以直接删除。 */
@Composable
private fun BotRuleRow(
    rule: ApprovalRule,
    creator: String,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Flux.panel3),
            contentAlignment = Alignment.Center,
        ) {
            FIcon(ruleTypeIcon(rule.type), size = 17.dp, color = Flux.accent2)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rule.displayDescription(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight(460),
                color = if (rule.enabled) Flux.text else Flux.text3,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                rule.paramsSummary(),
                fontSize = 11.sp,
                color = Flux.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("由 $creator 设置", color = Flux.warn, bg = Flux.warnSoft)
                if (!rule.enabled) Pill("已停用", color = Flux.text3, bg = Flux.panel2)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(24.dp).clip(CircleShape).clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            FIcon(FIcon.Trash, size = 13.dp, color = Flux.danger)
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text("加载失败：$error", fontSize = 12.5.sp, color = Flux.danger, lineHeight = 18.sp)
        Text(
            "重试",
            fontSize = 12.5.sp,
            color = Flux.accent,
            modifier = Modifier.padding(top = 9.dp).clickable { onRetry() },
        )
    }
}

@Composable
private fun RuleRow(
    rule: ApprovalRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Flux.panel3),
            contentAlignment = Alignment.Center,
        ) {
            FIcon(ruleTypeIcon(rule.type), size = 17.dp, color = Flux.accent2)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rule.displayDescription(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight(460),
                color = if (rule.enabled) Flux.text else Flux.text3,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                rule.paramsSummary(),
                fontSize = 11.sp,
                color = Flux.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("全局生效", color = Flux.text3, bg = Flux.panel2)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            FluxSwitch(rule.enabled, onToggle)
            if (!rule.enabled) {
                Text(
                    "已停用",
                    fontSize = 9.5.sp,
                    color = Flux.text4,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).clickable { onEdit() },
            contentAlignment = Alignment.Center,
        ) {
            FIcon(FIcon.Pencil, size = 13.dp, color = Flux.text4)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            FIcon(FIcon.Trash, size = 13.dp, color = if (rule.enabled) Flux.text4 else Flux.danger)
        }
    }
}

private fun ruleTypeIcon(type: String): FIcon = when (type) {
    ApprovalRuleType.PAYMENT -> FIcon.Wallet
    ApprovalRuleType.COMMAND -> FIcon.Keyboard
    ApprovalRuleType.TASK -> FIcon.ListDots
    ApprovalRuleType.TOOL -> FIcon.Cpu
    ApprovalRuleType.OUTBOUND -> FIcon.Globe
    else -> FIcon.Spark
}

/* ---------------- 编辑 / 添加 ---------------- */

@Composable
private fun BoxScope.RuleEditorOverlay(
    state: RuleEditorState,
    onClose: () -> Unit,
    onPickType: (String) -> Unit,
    onSave: (ApprovalRuleDraft, ApprovalRule?) -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB8030508))
            .clickable { onClose() },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clickable(interactionSource = noRipple, indication = null) {}
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 12.dp)
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Flux.line),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state is RuleEditorState.Edit && state.rule != null) "编辑规则" else "添加规则",
                fontSize = 16.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Flux.panel3).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                FIcon(FIcon.Close, size = 15.dp, color = Flux.text2)
            }
        }
        Text(
            "规则对你名下所有 Bot 生效。",
            fontSize = 11.5.sp,
            color = Flux.text3,
            modifier = Modifier.padding(top = 3.dp),
        )

        if (state is RuleEditorState.PickType) {
            TypePicker(onPickType)
            return@Column
        }

        val edit = state as RuleEditorState.Edit
        RuleForm(
            initialType = edit.type,
            rule = edit.rule,
            onCancel = onClose,
            onSave = { draft -> onSave(draft, edit.rule) },
        )
    }
}

@Composable
private fun TypePicker(onPickType: (String) -> Unit) {
    SectionLabel("选择规则类型", modifier = Modifier.padding(top = 12.dp))
    FluxCard {
        ApprovalRuleType.all.forEachIndexed { index, type ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPickType(type) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Flux.panel3),
                    contentAlignment = Alignment.Center,
                ) {
                    FIcon(ruleTypeIcon(type), size = 15.dp, color = Flux.accent2)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    ApprovalRuleType.label(type),
                    fontSize = 14.sp,
                    color = Flux.text,
                    modifier = Modifier.weight(1f),
                )
                FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.text4)
            }
            if (index != ApprovalRuleType.all.lastIndex) Hairline()
        }
    }
}

@Composable
private fun RuleForm(
    initialType: String,
    rule: ApprovalRule?,
    onCancel: () -> Unit,
    onSave: (ApprovalRuleDraft) -> Unit,
) {
    var type by remember(initialType, rule?.id) { mutableStateOf(initialType) }
    var amount by remember(rule?.id) { mutableStateOf(rule?.params?.get("amount") ?: "500") }
    var currency by remember(rule?.id) { mutableStateOf(rule?.params?.get("currency") ?: "CNY") }
    var pattern by remember(rule?.id) { mutableStateOf(rule?.params?.get("pattern") ?: "rm") }
    var style by remember(rule?.id) { mutableStateOf(rule?.params?.get("style") ?: "glob") }
    var taskKind by remember(rule?.id) {
        mutableStateOf(rule?.params?.get("task_kind") ?: "行程规划")
    }
    var toolName by remember(rule?.id) {
        mutableStateOf(rule?.params?.get("tool_name") ?: "Bash")
    }
    var channel by remember(rule?.id) { mutableStateOf(rule?.params?.get("channel") ?: "feishu") }
    var customKey by remember(rule?.id) { mutableStateOf(rule?.params?.get("key").orEmpty()) }
    var customValue by remember(rule?.id) { mutableStateOf(rule?.params?.get("value").orEmpty()) }
    var description by remember(rule?.id) { mutableStateOf(rule?.description.orEmpty()) }

    val params: Map<String, String> = when (type) {
        ApprovalRuleType.PAYMENT -> mapOf("amount" to amount.trim(), "currency" to currency)
        ApprovalRuleType.COMMAND -> mapOf("pattern" to pattern.trim(), "style" to style)
        ApprovalRuleType.TASK -> mapOf("task_kind" to taskKind.trim())
        ApprovalRuleType.TOOL -> mapOf("tool_name" to toolName.trim())
        ApprovalRuleType.OUTBOUND -> mapOf("channel" to channel)
        else -> mapOf("key" to customKey.trim(), "value" to customValue.trim())
    }
    val preview = description.ifBlank { approvalRuleDescription(type, params) }
    val valid = when (type) {
        ApprovalRuleType.PAYMENT -> amount.trim().toDoubleOrNull()?.let { it >= 0 } == true
        ApprovalRuleType.COMMAND -> pattern.isNotBlank()
        ApprovalRuleType.TASK -> taskKind.isNotBlank()
        ApprovalRuleType.TOOL -> toolName.isNotBlank()
        ApprovalRuleType.CUSTOM -> customKey.isNotBlank()
        else -> true
    }

    SectionLabel("规则类型", modifier = Modifier.padding(top = 12.dp))
    RuleTypePills(selected = type, onSelect = { type = it })

    when (type) {
        ApprovalRuleType.PAYMENT -> {
            FormLabel("金额阈值（元）")
            FluxInput(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                placeholder = "500",
                keyboardType = KeyboardType.Decimal,
            )
            FormLabel("币种")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("人民币 · CNY", currency == "CNY") { currency = "CNY" }
                ChoiceChip("美元 · USD", currency == "USD") { currency = "USD" }
            }
            // 这是用户自建的 payment_amount_over 规则：命中条件确实是「超过」。
            FormHint("支付金额超过阈值时，先让你确认再扣款。")
        }

        ApprovalRuleType.COMMAND -> {
            FormLabel("命令开头 / 模式")
            FluxInput(
                value = pattern,
                onValueChange = { pattern = it },
                placeholder = "例如：rm",
            )
            FormLabel("匹配方式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("通配符", style == "glob") { style = "glob" }
                ChoiceChip("正则", style == "regex") { style = "regex" }
            }
            FormHint("匹配以 rm 开头的命令；命中后必须先经过你允许。")
        }

        ApprovalRuleType.TASK -> {
            FormLabel("任务类型")
            FluxInput(
                value = taskKind,
                onValueChange = { taskKind = it },
                placeholder = "例如：行程规划",
            )
            FormHint("任何 Bot 执行这个任务类型前都会先问你。")
        }

        ApprovalRuleType.TOOL -> {
            FormLabel("工具名称")
            FluxInput(
                value = toolName,
                onValueChange = { toolName = it },
                placeholder = "例如：Bash",
            )
            FormHint("Bot 调用这个工具前都会先问你。")
        }

        ApprovalRuleType.OUTBOUND -> {
            FormLabel("出站频道")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("飞书", channel == "feishu") { channel = "feishu" }
                ChoiceChip("企业微信", channel == "wecom") { channel = "wecom" }
                ChoiceChip("钉钉", channel == "dingtalk") { channel = "dingtalk" }
            }
            FormHint("通过所选频道发送消息前都会先问你。")
        }

        else -> {
            FormLabel("键")
            FluxInput(value = customKey, onValueChange = { customKey = it }, placeholder = "例如：env")
            FormLabel("值")
            FluxInput(
                value = customValue,
                onValueChange = { customValue = it },
                placeholder = "例如：production",
            )
        }
    }

    FormLabel("说明（可选）")
    FluxInput(
        value = description,
        onValueChange = { description = it },
        placeholder = preview,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Flux.panel2)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("命中后", fontSize = 10.5.sp, color = Flux.text4)
        Text(
            preview,
            fontSize = 12.5.sp,
            color = Flux.text2,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EditorButton("取消", primary = false, modifier = Modifier.weight(1f), onClick = onCancel)
        EditorButton(
            "保存",
            primary = true,
            enabled = valid,
            modifier = Modifier.weight(1f),
        ) {
            onSave(ApprovalRuleDraft(type = type, params = params, description = preview))
        }
    }
}

@Composable
private fun RuleTypePills(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ApprovalRuleType.all.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { type ->
                    val on = type == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (on) Flux.accentSoft else Flux.panel2)
                            .border(
                                1.dp,
                                if (on) Flux.accentLine else Flux.lineSoft,
                                RoundedCornerShape(11.dp),
                            )
                            .clickable { onSelect(type) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ApprovalRuleType.label(type),
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight(500) else FontWeight(400),
                            color = if (on) Flux.accent else Flux.text2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Flux.accentSoft else Flux.panel2)
            .border(
                1.dp,
                if (selected) Flux.accentLine else Flux.lineSoft,
                RoundedCornerShape(11.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            FIcon(FIcon.Check, size = 13.dp, color = Flux.accent)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Flux.accent else Flux.text2,
        )
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = Flux.text3,
        modifier = Modifier.padding(top = 13.dp, bottom = 6.dp),
    )
}

@Composable
private fun FormHint(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = Flux.text4,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 7.dp),
    )
}

@Composable
private fun EditorButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    Box(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> Flux.panel3
                    primary -> Flux.accent
                    else -> Flux.panel2
                },
            )
            .border(1.dp, if (primary && enabled) Flux.accent else Flux.lineSoft, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight(560),
            color = when {
                !enabled -> Flux.text4
                primary -> Flux.ink
                else -> Flux.text
            },
        )
    }
}
