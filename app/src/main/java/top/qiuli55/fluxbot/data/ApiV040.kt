package top.qiuli55.fluxbot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.4 新页面用到的接口。
 *
 * 全部通过 [Api.request] 走同一个 HttpURLConnection 客户端；路径与
 * `/workspace/flux-bot/server/flux/api/v1` 里的路由一一对应，没有客户端编造的入口。
 */

private fun JSONObject.items(key: String = "data"): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

suspend fun Api.listRoutineDetails(botId: String): List<RemoteRoutineDetail> =
    withContext(Dispatchers.IO) {
        request("GET", "/api/v1/bots/$botId/routines", null)
            .items()
            .mapNotNull(::parseRoutineDetail)
    }

suspend fun Api.updateRoutineDetail(
    botId: String,
    routineId: String,
    name: String? = null,
    instruction: String? = null,
    schedule: String? = null,
    clearSchedule: Boolean = false,
    enabled: Boolean? = null,
    eventTriggers: List<String>? = null,
): Unit = withContext(Dispatchers.IO) {
    val payload = JSONObject()
    if (name != null) payload.put("name", name)
    if (instruction != null) payload.put("instruction", instruction)
    if (schedule != null) payload.put("schedule", schedule)
    else if (clearSchedule) payload.put("schedule", JSONObject.NULL)
    if (enabled != null) payload.put("enabled", enabled)
    if (eventTriggers != null) payload.put("event_triggers", JSONArray(eventTriggers))
    request("PATCH", "/api/v1/bots/$botId/routines/$routineId", payload)
    Unit
}

suspend fun Api.deleteRoutineDetail(botId: String, routineId: String): Unit =
    withContext(Dispatchers.IO) {
        request("DELETE", "/api/v1/bots/$botId/routines/$routineId", null)
        Unit
    }

suspend fun Api.listSubtasks(botId: String, limit: Int = 50): List<RemoteSubtask> =
    withContext(Dispatchers.IO) {
        request("GET", "/api/v1/bots/$botId/subtasks?limit=$limit", null)
            .items()
            .mapNotNull(::parseSubtask)
    }

suspend fun Api.createSubtask(
    botId: String,
    instruction: String,
    title: String = "",
): RemoteSubtask = withContext(Dispatchers.IO) {
    val payload = JSONObject().put("instruction", instruction.trim())
    if (title.isNotBlank()) payload.put("title", title.trim())
    val data = request("POST", "/api/v1/bots/$botId/subtasks", payload)
        .optJSONObject("data") ?: throw ApiException("服务器未返回子任务")
    parseSubtask(data) ?: throw ApiException("子任务内容为空")
}

suspend fun Api.cancelSubtask(botId: String, subtaskId: String): Unit =
    withContext(Dispatchers.IO) {
        request("POST", "/api/v1/bots/$botId/subtasks/$subtaskId/cancel", JSONObject())
        Unit
    }

suspend fun Api.listOutboundDrafts(
    status: String? = null,
    limit: Int = 100,
): List<RemoteOutboundDraft> = withContext(Dispatchers.IO) {
    val query = buildString {
        append("?limit=$limit")
        if (!status.isNullOrBlank()) append("&status=$status")
    }
    request("GET", "/api/v1/outbound-drafts$query", null)
        .items()
        .mapNotNull(::parseOutboundDraft)
}

suspend fun Api.cancelOutboundDraft(draftId: String): Unit = withContext(Dispatchers.IO) {
    request("POST", "/api/v1/outbound-drafts/$draftId/cancel", JSONObject())
    Unit
}

suspend fun Api.listSkills(): List<RemoteSkill> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/skills?limit=200", null).items().mapNotNull(::parseSkill)
}

suspend fun Api.getSkill(skillId: String): RemoteSkill = withContext(Dispatchers.IO) {
    val data = request("GET", "/api/v1/skills/$skillId", null).optJSONObject("data")
        ?: throw ApiException("服务器未返回技能内容")
    parseSkill(data) ?: throw ApiException("技能内容为空")
}

suspend fun Api.createSkill(
    name: String,
    body: String,
    description: String = "",
    version: String = "v1.0",
): RemoteSkill = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("name", name.trim())
        .put("body", body)
        .put("enabled", true)
    if (description.isNotBlank()) payload.put("description", description.trim())
    if (version.isNotBlank()) payload.put("version", version.trim())
    val data = request("POST", "/api/v1/skills", payload).optJSONObject("data")
        ?: throw ApiException("服务器未返回技能")
    parseSkill(data) ?: throw ApiException("技能内容为空")
}

suspend fun Api.updateSkill(
    skillId: String,
    name: String? = null,
    description: String? = null,
    version: String? = null,
    body: String? = null,
    enabled: Boolean? = null,
): RemoteSkill = withContext(Dispatchers.IO) {
    val payload = JSONObject()
    if (name != null) payload.put("name", name)
    if (description != null) payload.put("description", description)
    if (version != null) payload.put("version", version)
    if (body != null) payload.put("body", body)
    if (enabled != null) payload.put("enabled", enabled)
    val data = request("PATCH", "/api/v1/skills/$skillId", payload).optJSONObject("data")
        ?: throw ApiException("服务器未返回技能")
    parseSkill(data) ?: throw ApiException("技能内容为空")
}

suspend fun Api.deleteSkill(skillId: String): Unit = withContext(Dispatchers.IO) {
    request("DELETE", "/api/v1/skills/$skillId", null)
    Unit
}

suspend fun Api.connectorCatalog(): List<RemoteConnectorCatalog> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/connectors/catalog", null)
        .items()
        .mapNotNull(::parseConnectorCatalog)
}

suspend fun Api.installEmailConnector(
    imapHost: String,
    imapPort: Int,
    imapSsl: Boolean,
    smtpHost: String,
    smtpPort: Int,
    smtpSsl: Boolean,
    smtpStarttls: Boolean,
    username: String,
    password: String,
    fromAddress: String = "",
): RemoteConnector = withContext(Dispatchers.IO) {
    val settings = JSONObject()
        .put("imap_host", imapHost.trim())
        .put("imap_port", imapPort)
        .put("imap_ssl", imapSsl)
        .put("smtp_host", smtpHost.trim())
        .put("smtp_port", smtpPort)
        .put("smtp_ssl", smtpSsl)
        .put("smtp_starttls", smtpStarttls)
        .put("username", username.trim())
    if (fromAddress.isNotBlank()) settings.put("from_address", fromAddress.trim())
    val payload = JSONObject()
        .put("auth_type", "api_key")
        .put("api_key", password)
        .put("settings", settings)
    val data = request("POST", "/api/v1/connectors/email/install", payload)
        .optJSONObject("data") ?: throw ApiException("服务器未返回连接状态")
    parseConnector(data)
}

suspend fun Api.installCalendarConnector(
    mode: String,
    url: String,
    username: String,
    password: String,
    timeoutSeconds: Int = 15,
): RemoteConnector = withContext(Dispatchers.IO) {
    val settings = JSONObject()
        .put("mode", mode)
        .put("url", url.trim())
        .put("username", username.trim())
        .put("timeout", timeoutSeconds)
    val payload = JSONObject()
        .put("auth_type", "api_key")
        .put("api_key", password)
        .put("settings", settings)
    val data = request("POST", "/api/v1/connectors/calendar/install", payload)
        .optJSONObject("data") ?: throw ApiException("服务器未返回连接状态")
    parseConnector(data)
}

suspend fun Api.testEmailConnection(): RemoteEmailTest = withContext(Dispatchers.IO) {
    parseEmailTest(
        request("POST", "/api/v1/connectors/email/test", JSONObject())
            .optJSONObject("data") ?: JSONObject(),
    )
}

suspend fun Api.testCalendarConnection(): RemoteCalendarTest = withContext(Dispatchers.IO) {
    parseCalendarTest(
        request("POST", "/api/v1/connectors/calendar/test", JSONObject())
            .optJSONObject("data") ?: JSONObject(),
    )
}

suspend fun Api.uninstallConnector(connectorId: String): Unit = withContext(Dispatchers.IO) {
    request("DELETE", "/api/v1/connectors/$connectorId", null)
    Unit
}

suspend fun Api.channelStatuses(): List<RemoteChannelStatus> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/channels/status", null)
        .items()
        .mapNotNull(::parseChannelStatus)
}

suspend fun Api.browserStatus(): RemoteBrowserStatus = withContext(Dispatchers.IO) {
    parseBrowserStatus(
        request("GET", "/api/v1/browser/status", null).optJSONObject("data") ?: JSONObject(),
    )
}

suspend fun Api.sharedMemory(journalLimit: Int = 50): RemoteSharedMemory =
    withContext(Dispatchers.IO) {
        parseSharedMemory(
            request("GET", "/api/v1/user-memory?journal_limit=$journalLimit", null)
                .optJSONObject("data") ?: JSONObject(),
        )
    }

suspend fun Api.putProfile(values: Map<String, String>): Unit = withContext(Dispatchers.IO) {
    val payload = JSONObject()
    values.forEach { (key, value) -> payload.put(key, value) }
    request("PUT", "/api/v1/user-memory/profile", payload)
    Unit
}

suspend fun Api.deleteProfile(key: String): Unit = withContext(Dispatchers.IO) {
    request("DELETE", "/api/v1/user-memory/profile/$key", null)
    Unit
}

suspend fun Api.appendJournal(content: String): Unit = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("content", content.trim())
        .put("source", "user")
    request("POST", "/api/v1/user-memory/journal", payload)
    Unit
}

suspend fun Api.deleteJournal(entryId: String): Unit = withContext(Dispatchers.IO) {
    request("DELETE", "/api/v1/user-memory/journal/$entryId", null)
    Unit
}

suspend fun Api.listPages(limit: Int = 100): List<RemotePage> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/pages?limit=$limit", null).items().mapNotNull(::parsePage)
}

suspend fun Api.getPage(pageId: String): RemotePage = withContext(Dispatchers.IO) {
    val data = request("GET", "/api/v1/pages/$pageId", null).optJSONObject("data")
        ?: throw ApiException("服务器未返回页面")
    parsePage(data) ?: throw ApiException("页面内容为空")
}

suspend fun Api.pageRevisions(pageId: String): List<RemotePageRevision> =
    withContext(Dispatchers.IO) {
        request("GET", "/api/v1/pages/$pageId/revisions", null)
            .items()
            .mapNotNull(::parsePageRevision)
    }

suspend fun Api.updatePage(
    pageId: String,
    title: String? = null,
    body: String? = null,
): RemotePage = withContext(Dispatchers.IO) {
    val payload = JSONObject()
    if (title != null) payload.put("title", title)
    if (body != null) payload.put("body", body)
    val data = request("PATCH", "/api/v1/pages/$pageId", payload).optJSONObject("data")
        ?: throw ApiException("服务器未返回页面")
    parsePage(data) ?: throw ApiException("页面内容为空")
}

suspend fun Api.publishPage(pageId: String): RemotePage = withContext(Dispatchers.IO) {
    val data = request("POST", "/api/v1/pages/$pageId/publish", JSONObject())
        .optJSONObject("data") ?: throw ApiException("服务器未返回页面")
    parsePage(data) ?: throw ApiException("页面内容为空")
}

suspend fun Api.unpublishPage(pageId: String): RemotePage = withContext(Dispatchers.IO) {
    val data = request("POST", "/api/v1/pages/$pageId/unpublish", JSONObject())
        .optJSONObject("data") ?: throw ApiException("服务器未返回页面")
    parsePage(data) ?: throw ApiException("页面内容为空")
}

suspend fun Api.deletePage(pageId: String): Unit = withContext(Dispatchers.IO) {
    request("DELETE", "/api/v1/pages/$pageId", null)
    Unit
}

suspend fun Api.listVaultLogins(): List<RemoteVaultLogin> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/vault/logins", null).items().mapNotNull(::parseVaultLogin)
}

suspend fun Api.listVaultCards(): List<RemoteVaultCard> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/vault/cards", null).items().mapNotNull(::parseVaultCard)
}

suspend fun Api.listVaultContacts(): List<RemoteVaultContact> = withContext(Dispatchers.IO) {
    request("GET", "/api/v1/vault/contacts", null).items().mapNotNull(::parseVaultContact)
}

suspend fun Api.revealLoginPassword(loginId: String): String = withContext(Dispatchers.IO) {
    request("POST", "/api/v1/vault/logins/$loginId/reveal", JSONObject())
        .optJSONObject("data")?.optStringOrEmpty("password").orEmpty()
}

/** 技能库汇总（设置页显示「已启用 / 总数」）。 */
data class SkillSummary(val enabled: Int, val total: Int)

suspend fun Api.skillSummary(): SkillSummary = withContext(Dispatchers.IO) {
    val skills = listSkills()
    SkillSummary(enabled = skills.count { it.enabled }, total = skills.size)
}

/** 待审批草稿数（设置页 badge）。 */
suspend fun Api.pendingDraftCount(): Int = withContext(Dispatchers.IO) {
    listOutboundDrafts(status = "pending", limit = 200).size
}

/** 页面数（设置页显示数量）。 */
suspend fun Api.pageCount(): Int = withContext(Dispatchers.IO) {
    listPages(limit = 200).size
}

/** 共享记忆条目数（画像 + 日志）。 */
suspend fun Api.sharedMemoryCount(): Int = withContext(Dispatchers.IO) {
    val memory = sharedMemory(journalLimit = 50)
    memory.profile.size + memory.journal.size
}
