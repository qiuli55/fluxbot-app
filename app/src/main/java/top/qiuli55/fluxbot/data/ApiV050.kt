package top.qiuli55.fluxbot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * v0.5 新增接口：通道归属、审批默认项、自定义 MCP 连接。
 *
 * 全部复用 [Api.request]，路径与后端 `/api/v1` 路由一一对应。
 * 日志只记录数量与 id，不记录鉴权头或完整地址。
 */

// ---------- 通道 ----------

suspend fun Api.channelLinks(): List<RemoteChannelLink> = withContext(Dispatchers.IO) {
    val items = request("GET", "/api/v1/channels/links", null).dataArray()
    val links = (0 until items.length()).mapNotNull { index ->
        items.optJSONObject(index)?.let(::parseChannelLink)
    }
    logDebug("channel links loaded count=${links.size}")
    links
}

suspend fun Api.linkChannel(channel: String, botId: String): RemoteChannelLink =
    withContext(Dispatchers.IO) {
        val payload = JSONObject().put("bot_id", botId)
        val data = request("PUT", "/api/v1/channels/$channel/link", payload)
            .optJSONObject("data")
            ?: throw ApiException("服务器未返回通道状态")
        val link = parseChannelLink(data)
            ?: throw ApiException("通道状态为空")
        logDebug("channel linked channel=$channel bot=$botId")
        link
    }

suspend fun Api.unlinkChannel(channel: String): RemoteChannelLink =
    withContext(Dispatchers.IO) {
        val data = request("DELETE", "/api/v1/channels/$channel/link", null)
            .optJSONObject("data")
            ?: throw ApiException("服务器未返回通道状态")
        val link = parseChannelLink(data)
            ?: throw ApiException("通道状态为空")
        logDebug("channel unlinked channel=$channel")
        link
    }

// ---------- 审批设置 ----------

suspend fun Api.approvalSettings(): ApprovalSettings = withContext(Dispatchers.IO) {
    val data = request("GET", "/api/v1/approval-rules/settings", null)
        .optJSONObject("data") ?: JSONObject()
    val settings = parseApprovalSettings(data)
    logDebug(
        "approval settings loaded threshold=${formatThreshold(settings.largePaymentThreshold)} " +
            "builtins=${settings.builtins.size}",
    )
    settings
}

suspend fun Api.updateApprovalSettings(threshold: Double): ApprovalSettings =
    withContext(Dispatchers.IO) {
        val payload = JSONObject().put("large_payment_threshold", threshold)
        val data = request("PUT", "/api/v1/approval-rules/settings", payload)
            .optJSONObject("data") ?: JSONObject()
        val settings = parseApprovalSettings(data)
        logDebug("approval settings updated threshold=${formatThreshold(threshold)}")
        settings
    }

// ---------- 自定义 MCP ----------

suspend fun Api.listMcpConnections(): List<RemoteMcpConnection> = withContext(Dispatchers.IO) {
    val items = request("GET", "/api/v1/mcp-connections", null).dataArray()
    val connections = (0 until items.length()).mapNotNull { index ->
        items.optJSONObject(index)?.let(::parseMcpConnection)
    }
    logDebug("mcp connections loaded count=${connections.size}")
    connections
}

/**
 * 新建自定义 MCP 连接。
 *
 * 服务端会先连接并检查工具（总超时 10 秒），所以客户端留 15 秒；
 * 失败时什么都不保存，异常里的中文 message 直接展示给用户。
 * 鉴权头的值只在请求体里出现，不写日志、不落本地。
 */
suspend fun Api.createMcpConnection(
    name: String,
    url: String,
    headerName: String = "",
    headerValue: String = "",
): RemoteMcpConnection = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("name", name.trim())
        .put("url", url.trim())
    if (headerName.trim().isNotEmpty() && headerValue.isNotEmpty()) {
        payload.put("header_name", headerName.trim())
        payload.put("header_value", headerValue)
    }
    val data = request(
        "POST",
        "/api/v1/mcp-connections",
        payload,
        timeoutMs = 15_000,
    ).optJSONObject("data") ?: throw ApiException("服务器未返回连接结果")
    val connection = parseMcpConnection(data)
        ?: throw ApiException("连接结果为空")
    logDebug("mcp connection created id=${connection.id} tools=${connection.toolCount}")
    connection
}

suspend fun Api.deleteMcpConnection(id: String): Unit = withContext(Dispatchers.IO) {
    request("DELETE", "/api/v1/mcp-connections/$id", null)
    logDebug("mcp connection deleted id=$id")
    Unit
}
