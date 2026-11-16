package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.PendingDecision
import top.qiuli55.fluxbot.ui.Flux

/**
 * 审批卡的界面状态。
 *
 * 待批准 / 提交中 / 已答复 / 已超时 / 提交失败（可重试）。状态只影响展示与是否可点，
 * 决策接口与选项文案保持原样。
 */
internal sealed interface ApprovalCardStatus {
    data object Pending : ApprovalCardStatus
    data object Submitting : ApprovalCardStatus
    data class Answered(val chosen: String, val allowed: Boolean) : ApprovalCardStatus
    data object TimedOut : ApprovalCardStatus
    data class Failed(val message: String) : ApprovalCardStatus
}

/** 一行选项：左侧字母徽标 + 右侧真实选项文案。 */
internal data class ApprovalOptionUi(val letter: String, val label: String)

/** 审批卡纯展示模型；不依赖 Compose，可直接做 JVM 单元测试。 */
internal data class ApprovalCardUi(
    val title: String,
    val summaryReason: String,
    val operation: String,
    val options: List<ApprovalOptionUi>,
    val footer: String,
    val muted: Boolean,
    val tappable: Boolean,
    val chosenLabel: String?,
    val error: String?,
)

/** 由审批 kind 决定标题；未知 kind 回落到「需要你确认」。 */
internal fun approvalTitle(kind: String, meta: Map<String, String>): String = when (kind) {
    "delete_data" -> "删除资料需要你确认"
    "payment_amount", "large_payment" -> "大额付款需要你确认"
    "payment_abnormal", "abnormal_payment" -> "异常支付需要你确认"
    "rule_match" -> when (meta["approval_rule_created_by_kind"].orEmpty()) {
        "bot" -> "Bot 规则需要你确认"
        else -> "用户规则需要你确认"
    }
    "user_rule" -> "用户规则需要你确认"
    "bot_rule" -> "Bot 规则需要你确认"
    "approval_rule_change" -> "审批规则变更需要你确认"
    "high_risk_command" -> "执行命令需要你确认"
    "protected_access" -> "访问受保护内容需要你确认"
    "tool_permission_prompt" -> "工具调用需要你确认"
    else -> when {
        kind.endsWith("_outbound") || meta["channel"].orEmpty().isNotBlank() ->
            "发送消息需要你确认"
        else -> "需要你确认"
    }
}

/** detail 里可能出现的执行前缀；后端 `describe_deletion` 用的是全角冒号。 */
private val executionPrefixes = listOf(
    "Bot 准备执行：",
    "Bot 准备执行:",
    "准备执行：",
    "准备执行:",
)

/**
 * 去掉一行末尾的解释性括号，例如「rm -rf x（会删除文件）」→「rm -rf x」。
 *
 * 从右往左配对括号，嵌套括号只去掉最外层的一组；整段只有一个括号时保留原文，
 * 避免把唯一的操作说明清空。半角括号仅在内容含中文时才视为解释，防止误伤
 * `echo (x)` 这类命令语法。
 */
private fun stripTrailingExplanation(text: String): String {
    if (text.isEmpty()) return text
    val closing = text.last()
    val opening = when (closing) {
        '）' -> '（'
        ')' -> '('
        else -> return text
    }
    var depth = 0
    for (index in text.indices.reversed()) {
        when (text[index]) {
            closing -> depth++
            opening -> {
                depth--
                if (depth == 0) {
                    if (index == 0) return text
                    val content = text.substring(index + 1, text.length - 1)
                    val explanatory = opening == '（' || content.any { it.code > 0x2E80 }
                    if (!explanatory) return text
                    return text.substring(0, index).trimEnd()
                }
            }
        }
    }
    return text
}

/** 取 detail 里的操作文本：去执行前缀、去末尾解释括号。 */
internal fun approvalCommandFromDetail(detail: String): String {
    val trimmed = detail.trim()
    val withoutPrefix = executionPrefixes
        .firstOrNull { trimmed.startsWith(it) }
        ?.let { trimmed.removePrefix(it) }
        ?: trimmed
    return stripTrailingExplanation(withoutPrefix.trim()).trim()
}

/**
 * 卡片正文里的具体操作：优先用后端下发的 raw command；没有 command 时从 detail 推导，
 * 去掉「Bot 准备执行：」这类前缀和末尾「（会删除文件）」这类解释，只留操作本身。
 * 兼容旧 detail 的「命令：」行与「触发规则 / 风险 / 准备发送到」说明行。
 */
internal fun approvalOperation(decision: PendingDecision): String {
    val command = decision.meta["command"].orEmpty().trim()
    if (command.isNotEmpty()) return command
    val lines = decision.detail.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    lines.firstOrNull { it.startsWith("命令：") || it.startsWith("命令:") }
        ?.removePrefix("命令：")
        ?.removePrefix("命令:")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    val operation = lines
        .filterNot {
            it.startsWith("触发规则：") ||
                it.startsWith("风险：") ||
                it.startsWith("准备发送到")
        }
        .joinToString("\n")
    return approvalCommandFromDetail(operation)
}

/** 秒数 -> 中文时长：300 -> 5 分钟，90 -> 1 分 30 秒，45 -> 45 秒。 */
internal fun formatApprovalDuration(seconds: Int?): String? {
    val total = seconds ?: return null
    if (total <= 0) return null
    return when {
        total < 60 -> "$total 秒"
        total % 60 == 0 -> "${total / 60} 分钟"
        else -> "${total / 60} 分 ${total % 60} 秒"
    }
}

/** 底部说明：真实超时时长、已答复或已超时结果。 */
internal fun approvalFooter(
    status: ApprovalCardStatus,
    timeoutSeconds: Int?,
): String = when (status) {
    is ApprovalCardStatus.Answered -> if (status.allowed) "已允许" else "已拒绝"
    ApprovalCardStatus.TimedOut -> "已超时，已自动拒绝"
    else -> formatApprovalDuration(timeoutSeconds)
        ?.let { "仅本次有效 · ${it}无响应自动拒绝" }
        ?: "仅本次有效"
}

internal fun approvalCardUi(
    decision: PendingDecision,
    status: ApprovalCardStatus,
): ApprovalCardUi {
    val labels = decision.options.ifEmpty { listOf("允许", "拒绝") }
    val options = labels.mapIndexed { index, label ->
        ApprovalOptionUi(letter = ('A' + index).toString(), label = label)
    }
    val muted = status is ApprovalCardStatus.Answered || status is ApprovalCardStatus.TimedOut
    return ApprovalCardUi(
        // 后端摘要优先：一句话说清批什么；旧后端没有摘要时回落到 kind 标题。
        title = decision.summary.trim().ifEmpty { approvalTitle(decision.kind, decision.meta) },
        summaryReason = decision.summaryReason.trim(),
        operation = approvalOperation(decision),
        options = options,
        footer = approvalFooter(status, decision.timeoutSeconds),
        muted = muted,
        tappable = status is ApprovalCardStatus.Pending || status is ApprovalCardStatus.Failed,
        chosenLabel = (status as? ApprovalCardStatus.Answered)?.chosen,
        error = (status as? ApprovalCardStatus.Failed)?.message,
    )
}

/**
 * 审批卡配色。深色默认全部取自 [Flux] 令牌，与 App 现有暗色界面一致；
 * 浅色变体是近白卡面，供以后真正上线浅色主题时直接切换，无需改卡片结构。
 */
internal data class ApprovalCardColors(
    val background: Color,
    val backgroundMuted: Color,
    val border: Color,
    val borderMuted: Color,
    val divider: Color,
    val dividerMuted: Color,
    val title: Color,
    val titleMuted: Color,
    val reason: Color,
    val reasonMuted: Color,
    val command: Color,
    val commandMuted: Color,
    val option: Color,
    val optionMuted: Color,
    val badgeBackground: Color,
    val badgeBackgroundMuted: Color,
    val badgeText: Color,
    val badgeTextMuted: Color,
    val footer: Color,
    val footerMuted: Color,
    val check: Color,
    val error: Color,
)

/** 暗色卡面：比聊天背景略亮一层的 panel2/panel3，细边框；标题用 text，原因用 warn，命令用 text3。 */
internal val darkApprovalCardColors = ApprovalCardColors(
    background = Flux.panel2,
    backgroundMuted = Flux.panel,
    border = Flux.line,
    borderMuted = Flux.lineSoft,
    divider = Flux.line,
    dividerMuted = Flux.lineSoft,
    title = Flux.text,
    titleMuted = Flux.text2,
    reason = Flux.warn,
    reasonMuted = Flux.text3,
    command = Flux.text3,
    commandMuted = Flux.text4,
    option = Flux.text,
    optionMuted = Flux.text4,
    badgeBackground = Flux.panel3,
    badgeBackgroundMuted = Flux.panel2,
    badgeText = Flux.text2,
    badgeTextMuted = Flux.text4,
    footer = Flux.text3,
    footerMuted = Flux.text4,
    check = Flux.text2,
    error = Flux.danger,
)

/** 浅色卡面：沿用上一轮近白卡片的观感，留给未来的浅色主题。 */
internal fun lightApprovalCardColors() = ApprovalCardColors(
    background = Color(0xFFFAFAF9),
    backgroundMuted = Color(0xFFF1F3F2),
    border = Color(0xFFE2E6E4),
    borderMuted = Color(0xFFE9ECEA),
    divider = Color(0xFFE2E6E4),
    dividerMuted = Color(0xFFE9ECEA),
    title = Color(0xFF1B2320),
    titleMuted = Color(0xFF6D7773),
    reason = Color(0xFF9A6A14),
    reasonMuted = Color(0xFF9AA29F),
    command = Color(0xFF7A8380),
    commandMuted = Color(0xFF9AA29F),
    option = Color(0xFF1B2320),
    optionMuted = Color(0xFF8B938F),
    badgeBackground = Color(0xFFEAEEEC),
    badgeBackgroundMuted = Color(0xFFE9ECEA),
    badgeText = Color(0xFF4B5551),
    badgeTextMuted = Color(0xFF9AA29F),
    footer = Color(0xFF98A09D),
    footerMuted = Color(0xFF9AA29F),
    check = Color(0xFF4B5551),
    error = Color(0xFFB3261E),
)

/** 由当前主题背景亮度决定用哪套卡面：暗色 App 用 Flux 令牌，将来浅色主题自动跟随。 */
internal fun approvalCardColors(darkTheme: Boolean): ApprovalCardColors =
    if (darkTheme) darkApprovalCardColors else lightApprovalCardColors()

/**
 * Grok Bot 风格问题卡：圆角卡面、亮色大白话标题、灰色小字命令、单色操作行、字母徽标。
 *
 * 配色默认跟随当前主题背景亮度：暗色 App 用 Flux 令牌（panel2/panel3 + 细边框），
 * 将来上线浅色主题时自动切到近白卡面。只负责展示与点击回调，网络请求仍在
 * ChatScreen 的 respondDecision 里。
 */
@Composable
internal fun ApprovalCard(
    decision: PendingDecision,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: ApprovalCardStatus = ApprovalCardStatus.Pending,
    colors: ApprovalCardColors? = null,
) {
    val ui = approvalCardUi(decision, status)
    val palette = colors
        ?: approvalCardColors(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (ui.muted) palette.backgroundMuted else palette.background)
            .border(
                1.dp,
                if (ui.muted) palette.borderMuted else palette.border,
                shape,
            ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // 大白话摘要就是卡片头条：亮色、16sp、半粗，一眼先看到「批什么」。
            Text(
                ui.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ui.muted) palette.titleMuted else palette.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ui.summaryReason.isNotBlank()) {
                // 审批原因用警示琥珀色，提示风险但不当作红色告警。
                Text(
                    ui.summaryReason,
                    fontSize = 13.sp,
                    color = if (ui.muted) palette.reasonMuted else palette.reason,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (ui.operation.isNotBlank()) {
                // 原始命令只留一行小灰字，完整内容仍可从后端的 detail 里溯源。
                Text(
                    ui.operation,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    color = if (ui.muted) palette.commandMuted else palette.command,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        ui.options.forEach { option ->
            val chosen = ui.chosenLabel == option.label
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = ui.tappable) { onChoose(option.label) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (ui.muted) palette.badgeBackgroundMuted else palette.badgeBackground,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.letter,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight(560),
                        color = if (ui.muted) palette.badgeTextMuted else palette.badgeText,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    option.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = if (ui.muted) palette.optionMuted else palette.option,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chosen) {
                    Text(
                        "✓",
                        fontSize = 14.sp,
                        fontWeight = FontWeight(560),
                        color = palette.check,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        // 选项之间不再分隔；仅在底部说明上方保留一条整宽的分隔线。
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (ui.muted) palette.dividerMuted else palette.divider),
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            ui.error?.let { message ->
                Text(
                    message,
                    fontSize = 11.sp,
                    color = palette.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                ui.footer,
                fontSize = 10.5.sp,
                color = if (ui.muted) palette.footerMuted else palette.footer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
