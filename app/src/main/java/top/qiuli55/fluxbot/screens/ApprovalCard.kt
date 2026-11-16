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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.PendingDecision

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

/**
 * 卡片正文里的具体操作：优先用后端下发的 command，其次取 detail 里的「命令：」，
 * 最后把 detail 中「触发规则 / 风险 / 准备发送到」这类说明行去掉，只留可读的操作本身。
 */
internal fun approvalOperation(decision: PendingDecision): String {
    val command = decision.meta["command"].orEmpty().trim()
    if (command.isNotEmpty()) return command
    val lines = decision.detail.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    lines.firstOrNull { it.startsWith("命令：") }
        ?.removePrefix("命令：")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return lines
        .filterNot {
            it.startsWith("触发规则：") ||
                it.startsWith("风险：") ||
                it.startsWith("准备发送到")
        }
        .joinToString("\n")
        .trim()
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
        title = approvalTitle(decision.kind, decision.meta),
        operation = approvalOperation(decision),
        options = options,
        footer = approvalFooter(status, decision.timeoutSeconds),
        muted = muted,
        tappable = status is ApprovalCardStatus.Pending || status is ApprovalCardStatus.Failed,
        chosenLabel = (status as? ApprovalCardStatus.Answered)?.chosen,
        error = (status as? ApprovalCardStatus.Failed)?.message,
    )
}

private val CardBg = Color(0xFFF7F8F7)
private val CardBgMuted = Color(0xFFF1F3F2)
private val CardLine = Color(0xFFE2E6E4)
private val CardLineMuted = Color(0xFFE9ECEA)
private val TitleColor = Color(0xFF6D7773)
private val TitleColorMuted = Color(0xFF9AA29F)
private val OperationColor = Color(0xFF3C4642)
private val OperationColorMuted = Color(0xFF8A928F)
private val OptionColor = Color(0xFF1B2320)
private val OptionColorMuted = Color(0xFF8B938F)
private val BadgeBg = Color(0xFFEAEEEC)
private val BadgeBgMuted = Color(0xFFE9ECEA)
private val BadgeText = Color(0xFF4B5551)
private val BadgeTextMuted = Color(0xFF9AA29F)
private val FooterColor = Color(0xFF98A09D)
private val ErrorColor = Color(0xFFB3261E)

/**
 * Grok Bot 风格问题卡：浅色圆角卡、灰色标题、单色操作行、字母徽标。
 *
 * 只负责展示与点击回调，网络请求仍在 ChatScreen 的 respondDecision 里。
 */
@Composable
internal fun ApprovalCard(
    decision: PendingDecision,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: ApprovalCardStatus = ApprovalCardStatus.Pending,
) {
    val ui = approvalCardUi(decision, status)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (ui.muted) CardBgMuted else CardBg)
            .border(1.dp, if (ui.muted) CardLineMuted else CardLine, shape),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                ui.title,
                fontSize = 12.sp,
                fontWeight = FontWeight(500),
                color = if (ui.muted) TitleColorMuted else TitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ui.operation.isNotBlank()) {
                Text(
                    ui.operation,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                    color = if (ui.muted) OperationColorMuted else OperationColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        ui.options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp)
                        .height(1.dp)
                        .background(if (ui.muted) CardLineMuted else CardLine),
                )
            }
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
                        .background(if (ui.muted) BadgeBgMuted else BadgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.letter,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight(560),
                        color = if (ui.muted) BadgeTextMuted else BadgeText,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    option.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = if (ui.muted) OptionColorMuted else OptionColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chosen) {
                    Text(
                        "✓",
                        fontSize = 14.sp,
                        fontWeight = FontWeight(560),
                        color = if (ui.muted) BadgeTextMuted else BadgeText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (ui.muted) CardLineMuted else CardLine),
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            ui.error?.let { message ->
                Text(
                    message,
                    fontSize = 11.sp,
                    color = ErrorColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                ui.footer,
                fontSize = 10.5.sp,
                color = if (ui.muted) TitleColorMuted else FooterColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
