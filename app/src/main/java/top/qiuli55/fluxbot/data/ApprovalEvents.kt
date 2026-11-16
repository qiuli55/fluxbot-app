package top.qiuli55.fluxbot.data

import org.json.JSONObject

/**
 * Bot 级审批事件流的一帧（SSE）。
 *
 * - `ready`：刚连上时的快照，data.pending 是 [{task_id, decision}]；
 * - `approval.raised`：新卡出现，data 里是平铺的卡片字段；
 * - `approval.resolved`：某张卡已解决，客户端重新拉一次列表即可。
 */
internal data class ApprovalEvent(val type: String, val data: JSONObject)

/** 解析一个 SSE 帧（不含结尾空行）；注释 / 缺 event / 非法 JSON 返回 null。 */
internal fun parseApprovalEventFrame(block: String): ApprovalEvent? {
    if (block.isBlank()) return null
    var type = ""
    val payload = StringBuilder()
    block.lineSequence().forEach { raw ->
        val line = raw.trimEnd('\r')
        when {
            line.startsWith("event:") -> type = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (payload.isNotEmpty()) payload.append('\n')
                payload.append(line.removePrefix("data:").trimStart())
            }
        }
    }
    if (type.isEmpty() || payload.isEmpty()) return null
    val json = runCatching { JSONObject(payload.toString()) }.getOrNull() ?: return null
    return ApprovalEvent(type, json)
}

/** 一帧里携带的待决策：ready 是快照列表，raised 是单张卡；其余帧为空。 */
internal fun approvalEventDecisions(event: ApprovalEvent): List<PendingDecision> = when (event.type) {
    "ready" -> {
        val array = event.data.optJSONArray("pending")
        if (array == null) {
            emptyList()
        } else {
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val taskId = item.optStringOrEmpty("task_id")
                val decision = item.optJSONObject("decision") ?: return@mapNotNull null
                if (taskId.isEmpty()) null else parsePendingDecision(taskId, decision)
            }
        }
    }
    "approval.raised" -> {
        val taskId = event.data.optStringOrEmpty("task_id")
        val decisionId = event.data.optStringOrEmpty("decision_id")
        if (taskId.isEmpty() || decisionId.isEmpty()) {
            emptyList()
        } else {
            // raised 帧把卡片字段平铺在 data 上，补一个 id 后复用同一份解析。
            val decision = JSONObject(event.data.toString()).put("id", decisionId)
            listOfNotNull(parsePendingDecision(taskId, decision))
        }
    }
    else -> emptyList()
}
