package top.qiuli55.fluxbot.data

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * 待决策列表的纯函数：合并、排序、分页锚点、位置文案与首次展示日志。
 *
 * 全部不依赖 Compose / Android，方便 JVM 单元测试；ChatScreen 只负责把结果
 * 放进 state 与触发网络请求。
 */

/** ISO 8601 -> 毫秒；空串或解析失败返回 null（旧后端没有 raised_at）。 */
internal fun approvalTimeMillis(iso: String): Long? {
    val text = iso.trim()
    if (text.isEmpty()) return null
    return try {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}

/** 决策的发起时间：优先 raised_at，缺失时用 expires_at 减去等待上限倒推。 */
internal fun approvalRaisedAtMillis(decision: PendingDecision): Long? {
    approvalTimeMillis(decision.raisedAt)?.let { return it }
    val expires = approvalTimeMillis(decision.expiresAt) ?: return null
    val timeout = decision.timeoutSeconds?.takeIf { it > 0 } ?: return null
    return expires - timeout * 1000L
}

/**
 * 合并多来源的待决策：按 decisionId 去重（后出现的覆盖先出现的），
 * 再按发起时间从旧到新排列。没有发起时间的条目排在最后，并保持来源顺序。
 */
internal fun mergePendingDecisions(groups: List<List<PendingDecision>>): List<PendingDecision> {
    val byId = LinkedHashMap<String, PendingDecision>()
    groups.forEach { group ->
        group.forEach { decision ->
            if (decision.decisionId.isNotBlank()) byId[decision.decisionId] = decision
        }
    }
    if (byId.size <= 1) return byId.values.toList()
    return orderPendingDecisions(byId.values.toList())
}

/** 按发起时间从旧到新；时间相同的保持原顺序（稳定排序）。 */
internal fun orderPendingDecisions(items: List<PendingDecision>): List<PendingDecision> {
    if (items.size <= 1) return items
    return items
        .withIndex()
        .sortedWith(
            compareBy(
                { approvalRaisedAtMillis(it.value) == null },
                { approvalRaisedAtMillis(it.value) ?: 0L },
                { it.index },
            ),
        )
        .map { it.value }
}

/**
 * 列表更新后应停在哪一页。
 *
 * 用户正在看的那张卡还在 -> 停在它现在的位置（新卡插到前面也不会跳走）；
 * 那张卡已被解决 -> 用原来的页号取「顶上来」的下一张；越界时收敛到最后一页。
 */
internal fun stablePendingPage(
    decisions: List<PendingDecision>,
    anchorDecisionId: String?,
    previousPage: Int,
): Int {
    if (decisions.isEmpty()) return 0
    if (anchorDecisionId != null) {
        val index = decisions.indexOfFirst { it.decisionId == anchorDecisionId }
        if (index >= 0) return index
    }
    return previousPage.coerceIn(0, decisions.lastIndex)
}

/** 「1/3」；只有一张卡时不显示位置（避免单卡也挂个 1/1）。 */
internal fun approvalPositionLabel(index: Int, total: Int): String? {
    if (total <= 1) return null
    val human = (index + 1).coerceIn(1, total)
    return "$human/$total"
}

/**
 * 卡片首次出现时的日志：决策 id、来源（sse.ready / sse.raised / poll）与从发起到
 * 现在经过的毫秒数（拿不到发起时间时为 -1）。
 */
internal fun firstShownApprovalLog(
    decision: PendingDecision,
    source: String,
    nowMillis: Long,
): String {
    val latency = approvalRaisedAtMillis(decision)
        ?.let { (nowMillis - it).coerceAtLeast(0L) }
        ?: -1L
    return "approval.card.shown decision=${decision.decisionId} task=${decision.taskId} " +
        "source=$source latency_ms=$latency"
}
