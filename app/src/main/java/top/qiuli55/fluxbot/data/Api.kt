package top.qiuli55.fluxbot.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 后端返回的一条 Bot（已换算成 App 的形体 / 配色 key）。 */
data class RemoteBot(
    val id: String,
    val name: String,
    val shape: String,
    val palette: String,
    val description: String,
    val taskId: String,
    val chatTaskId: String = "",
)

/** 后端异步会话里的一轮对话。 */
data class RemoteTurn(
    val id: String,
    val status: String,
    val error: String,
    val errorCode: String,
    val replyText: String,
    val userText: String,
)

/** 云端密钥库提供的一个可选模型；keyId 空表示运行时原生模型。 */
data class RemoteModel(
    val model: String,
    val keyName: String,
    val keyId: String,
)

/** 云端模型目录：默认模型 + 可选路由模型（与 Web 端同一个筛选源）。 */
data class ModelCatalog(
    val defaultModel: String,
    val options: List<RemoteModel>,
)

/** 一条等待用户批准的任务决策（高风险工具会使用「允许 / 拒绝」）。 */
data class PendingDecision(
    val taskId: String,
    val decisionId: String,
    val question: String,
    val detail: String,
    val options: List<String>,
    val kind: String,
)

/** 远端 Bot 的一条长期记忆。 */
data class RemoteMemory(
    val id: String,
    val text: String,
    val source: String,
    val ts: String,
)

/** 远端 Bot 的一条定时任务。 */
data class RemoteRoutine(
    val id: String,
    val title: String,
    val time: String,
    val freq: String,
    val enabled: Boolean,
)

/** 一次任务的运行记录（用于 Bot 页的运行历史）。 */
data class RemoteTask(
    val id: String,
    val status: String,
    val title: String,
    val createdAt: String,
    val finishedAt: String,
    val error: String,
)

/**
 * 后端签发的一次云桌面会话；profile 由服务端计算，客户端直接使用。
 * ticket 模式下 token（gateway_token）与 bridgeKey 可能为空，连接时优先用 ticket。
 */
data class DesktopSession(
    val host: String,
    val token: String,
    val bridge: String,
    val bridgeKey: String,
    val profile: String,
    val ticket: String,
    val expiresAt: String,
)

/** 一个外部连接器（当前只有 GitHub）。 */
data class RemoteConnector(
    val id: String,
    val name: String,
    val description: String,
    val connected: Boolean,
    val account: String,
    val displayName: String,
    val avatarUrl: String,
    val scopes: List<String>,
    val connectedAt: String,
)

/** 设备码登录的起始信息：用户在浏览器里输入 userCode 并授权。 */
data class DeviceLogin(
    val flowId: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val interval: Int,
)

/** 一次轮询结果：pending / success / expired / failed。 */
data class ConnectorPoll(
    val status: String,
    val connector: RemoteConnector?,
    val message: String,
)

class ApiException(
    message: String,
    val status: Int = 0,
    val code: String = "",
) : Exception(message)

/** 与 optString 不同：JSONObject.NULL / 缺失字段都返回空串，而不是字面量 "null"。 */
internal fun JSONObject.optStringOrEmpty(key: String): String =
    if (isNull(key)) "" else optString(key)

/**
 * flux-bot 后端的最小客户端：只依赖 HttpURLConnection + org.json，不引入新依赖。
 *
 * 服务器地址存在普通 SharedPreferences；访问令牌属于登录态，按账号用 Keystore 加密保存。
 */
class Api(context: Context) {

    private val prefs = context.getSharedPreferences("fluxbot.api", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    /** 当前账号；令牌按账号隔离，切账号时由 Store 更新。 */
    var accountId: String? = null

    private val tokenKey: String
        get() = accountId?.let { "acc.$it.api_token" } ?: SYS_TOKEN_KEY

    var baseUrl: String
        get() {
            val saved = prefs.getString("base_url", null)?.trim()?.trimEnd('/')
            // 旧的 InstaBox 裸域名在国内被 DNS 污染，自动迁移到 flux.qiuli55.top 反代
            if (saved.isNullOrEmpty() || saved.contains("instabox.tech")) return DEFAULT_BASE_URL
            return saved
        }
        set(value) {
            prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply()
        }

    var token: String
        get() = secure.get(tokenKey) ?: secure.get(SYS_TOKEN_KEY) ?: migrateLegacyToken() ?: ""
        set(value) {
            secure.put(tokenKey, value.trim())
            prefs.edit().remove("token").apply()
        }

    /** 旧版本把令牌明文放在 SharedPreferences 里，读到后立刻迁进 Keystore 并清掉明文。 */
    private fun migrateLegacyToken(): String? {
        val old = prefs.getString("token", null)?.trim().orEmpty()
        if (old.isEmpty()) return null
        secure.put(SYS_TOKEN_KEY, old)
        prefs.edit().remove("token").apply()
        return old
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        request("GET", "/api/v1/health", null).optBoolean("success", true)
    }

    suspend fun listBots(): List<RemoteBot> = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/bots", null).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).map { parseBot(data.getJSONObject(it)) }
    }

    // ---------- 设备配对 ----------

    /** 用 8 位配对码换取本机设备令牌；返回 fbd_ 开头的新令牌。 */
    suspend fun pairDevice(code: String, deviceName: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("code", code.trim().uppercase())
                .put("device_name", deviceName)
            val data = request("POST", "/api/v1/devices/pair", body).getJSONObject("data")
            data.optStringOrEmpty("token").ifBlank {
                throw ApiException("配对失败：服务器未返回令牌")
            }
        }

    /** 撤销本机令牌；令牌已失效（401）也视为退出成功。 */
    suspend fun revokeThisDevice(): Unit = withContext(Dispatchers.IO) {
        try {
            request("POST", "/api/v1/devices/me/revoke", JSONObject())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.status != 401) throw e
        }
    }

    // ---------- Bot 桌面 ----------

    /**
     * 桌面全局配置：configured 由服务端判定，ticket 模式下凭据可能为 JSON null。
     * 缺少 data 时按未配置处理，让界面停在提示页而不是抛异常。
     */
    suspend fun desktopConfig(): DesktopConfig = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/desktop", null).optJSONObject("data") ?: JSONObject()
        parseDesktopConfig(data)
    }

    /** 换取这个 Bot 的桌面会话；gateway_token 可能为空，此时页面改用 ticket。 */
    suspend fun desktopSession(botId: String): DesktopSession = withContext(Dispatchers.IO) {
        val data = request("POST", "/api/v1/bots/$botId/desktop/session", JSONObject())
            .optJSONObject("data")
            ?: throw ApiException("服务器未返回桌面会话")
        parseDesktopSession(data)
    }

    suspend fun createBot(name: String, shape: String, palette: String): RemoteBot =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("name", name)
                .put("description", "由 Flux Bot App 创建")
                .put("avatar_shape", AvatarMap.toServerShape(shape))
                .put("avatar_color", AvatarMap.toServerPalette(palette))
            parseBot(request("POST", "/api/v1/bots", body).getJSONObject("data"))
        }

    suspend fun deleteBot(id: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/api/v1/bots/$id", null)
    }

    /** 建一个不自动执行的任务，作为这个 Bot 的对话容器；返回 taskId。 */
    suspend fun createTask(
        description: String,
        botName: String,
        shape: String,
        palette: String,
        model: String = "",
        keyId: String = "",
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("description", description)
            .put("start", false)
            .put("bot_name", botName)
            .put("avatar_shape", AvatarMap.toServerShape(shape))
            .put("avatar_color", AvatarMap.toServerPalette(palette))
        if (model.isNotBlank()) body.put("model", model)
        if (keyId.isNotBlank()) body.put("key_id", keyId)
        request("POST", "/api/v1/tasks", body).getJSONObject("data").getString("id")
    }

    /** 云端模型目录：与 Web 端同一个筛选源（原生模型 + 密钥库模型）。 */
    suspend fun listModels(): ModelCatalog = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/dsh/models", null).getJSONObject("data")
        val items = data.optJSONArray("key_models") ?: JSONArray()
        val options = (0 until items.length()).mapNotNull {
            val o = items.getJSONObject(it)
            val model = o.optStringOrEmpty("model").trim()
            val keyId = o.optStringOrEmpty("key_id").trim()
            if (model.isEmpty() || keyId.isEmpty()) null
            else RemoteModel(model, o.optStringOrEmpty("key_name"), keyId)
        }
        ModelCatalog(data.optStringOrEmpty("model"), options)
    }

    /** 切换已有对话（任务）的模型；model 留空 = 回到平台默认。 */
    suspend fun setTaskModel(taskId: String, model: String, keyId: String?) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("model", model)
            if (!keyId.isNullOrBlank() && model.isNotBlank()) body.put("key_id", keyId)
            request("POST", "/api/v1/tasks/$taskId/model", body)
        }

    // ---------- 外部连接器 ----------

    /** 当前本地账号的连接器清单（服务端按 X-Flux-Account 隔离）。 */
    suspend fun listConnectors(): List<RemoteConnector> = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/connectors", null).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).map { parseConnector(data.getJSONObject(it)) }
    }

    /** 发起 GitHub 设备码登录。 */
    suspend fun startGithubLogin(): DeviceLogin = withContext(Dispatchers.IO) {
        val data = request("POST", "/api/v1/connectors/github/login/start", JSONObject())
            .getJSONObject("data")
        DeviceLogin(
            flowId = data.getString("flow_id"),
            userCode = data.optString("user_code"),
            verificationUri = data.optString("verification_uri", "https://github.com/login/device"),
            verificationUriComplete = data.optString("verification_uri_complete"),
            expiresIn = data.optInt("expires_in", 900),
            interval = data.optInt("interval", 5),
        )
    }

    /** 轮询设备码授权结果；服务端会按 GitHub 返回的 interval 限流。 */
    suspend fun pollGithubLogin(flowId: String): ConnectorPoll = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/connectors/github/login/$flowId", null)
            .getJSONObject("data")
        ConnectorPoll(
            status = data.optString("status"),
            connector = data.optJSONObject("connector")?.let(::parseConnector),
            message = data.optString("message"),
        )
    }

    /** 断开当前账号的 GitHub 连接（只删服务端记录，不影响 GitHub 上的授权本身）。 */
    suspend fun disconnectGithub(): RemoteConnector = withContext(Dispatchers.IO) {
        parseConnector(request("DELETE", "/api/v1/connectors/github", null).getJSONObject("data"))
    }

    /** 远端 Bot 的默认模型（定时开工也用它）；model 留空 = 清掉覆盖。 */
    suspend fun updateBotModel(botId: String, model: String, keyId: String?) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("model", model)
            if (!keyId.isNullOrBlank() && model.isNotBlank()) body.put("key_id", keyId)
            request("POST", "/api/v1/bots/$botId/model", body)
        }

    /** 发一条聊天消息，返回模型的回复文本。 */
    suspend fun sendMessage(taskId: String, content: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("content", content)
            val data = request("POST", "/api/v1/tasks/$taskId/messages", body, timeoutMs = 120_000)
                .getJSONObject("data")
            val messages = data.optJSONArray("messages") ?: JSONArray()
            (0 until messages.length())
                .map { messages.getJSONObject(it) }
                .lastOrNull { it.optString("role") == "assistant" }
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: throw ApiException("模型没有返回内容")
        }

    /**
     * 等这一轮助手回复。
     *
     * 公网入口对空闲连接约 20 秒就会断开（返回 502），而模型出完整回复要 30 秒以上，
     * 所以不能干等 POST：POST 只负责触发，回复改从这里轮询增量事件取回。
     */
    suspend fun waitForReply(
        taskId: String,
        afterSeq: Int,
        timeoutMs: Long = 180_000,
        send: Deferred<Throwable?>? = null,
    ): String {
        var seq = afterSeq
        var failures = 0
        var emptyPolls = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(700)
            if (send != null && send.isCompleted) {
                val error = send.await()
                if (error != null && isDefiniteSendFailure(error)) {
                    throw ApiException(
                        "发送失败：${error.message ?: "未知错误"}",
                        (error as? ApiException)?.status ?: 0,
                    )
                }
            }
            val events = try {
                pollEvents(taskId, seq)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += 1
                if (failures >= 3) throw e
                continue
            }
            failures = 0
            if (events.isEmpty()) {
                emptyPolls += 1
                // 服务端重启后 seq 会从 0 重新开始；此时 after_seq 太大永远取不到事件。
                if (emptyPolls >= 10) {
                    emptyPolls = 0
                    val maxSeq = try {
                        pollEvents(taskId, 0).maxOfOrNull { it.seq } ?: 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        seq
                    }
                    if (maxSeq < seq) {
                        seq = 0
                        continue
                    }
                }
                continue
            }
            emptyPolls = 0
            for (event in events) {
                seq = maxOf(seq, event.seq)
                when (event.type) {
                    "assistant_message" -> {
                        val text = event.data.optJSONObject("message")
                            ?.optStringOrEmpty("content").orEmpty()
                        if (text.isNotBlank()) return text
                    }
                    "assistant_error" -> {
                        val reason = event.data.optStringOrEmpty("error").ifBlank { "模型返回异常" }
                        throw ApiException(reason)
                    }
                }
            }
        }
        throw ApiException("等模型回复超时")
    }

    /** 请求确定没有到达服务端 / 被明确拒绝时立即失败；其余（超时、5xx）继续等回复。 */
    private fun isDefiniteSendFailure(error: Throwable): Boolean = when (error) {
        is ApiException -> error.status in 400..499
        is UnknownHostException, is ConnectException, is SSLException -> true
        else -> false
    }

    /** 发消息之前记下当前事件序号，之后只看比它新的回复事件。 */
    suspend fun lastSeq(taskId: String): Int =
        runCatching { pollEvents(taskId, 0).maxOfOrNull { it.seq } ?: 0 }.getOrDefault(0)

    /** 拉取任务的历史消息，返回 (role, content) 列表。 */
    suspend fun listMessages(taskId: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val data = request("GET", "/api/v1/tasks/$taskId/messages?limit=100", null)
                .optJSONArray("data") ?: JSONArray()
            (0 until data.length()).map {
                val o = data.getJSONObject(it)
                o.optString("role") to o.optString("content")
            }
        }

    // ---------- Bot 异步会话 ----------

    /** 发起一轮对话；client_turn_id 重复时后端直接返回同一轮（幂等）。 */
    suspend fun startTurn(botId: String, content: String, clientTurnId: String): RemoteTurn =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("content", content)
                .put("client_turn_id", clientTurnId)
            val data = request("POST", "/api/v1/bots/$botId/turns", body).getJSONObject("data")
            parseTurn(data.getJSONObject("turn"))
        }

    /**
     * 长轮询一轮对话的当前状态。
     *
     * 公网入口约 20 秒断开空闲连接，所以 wait 最多 15 秒，读超时留到 25 秒。
     */
    suspend fun pollTurn(botId: String, turnId: String, waitSeconds: Int = 15): RemoteTurn =
        withContext(Dispatchers.IO) {
            val data = request(
                "GET",
                "/api/v1/bots/$botId/turns/$turnId?wait=$waitSeconds",
                null,
                timeoutMs = 25_000,
            ).getJSONObject("data")
            parseTurn(data.getJSONObject("turn"))
        }

    /** 一直轮询到这一轮结束；容忍少量瞬时网络 / 网关错误。 */
    suspend fun awaitTurn(
        botId: String,
        turnId: String,
        overallTimeoutMs: Long = 15 * 60_000L,
        onStatus: (String) -> Unit = {},
    ): RemoteTurn {
        val deadline = System.currentTimeMillis() + overallTimeoutMs
        var lastStatus = ""
        var failures = 0
        while (System.currentTimeMillis() < deadline) {
            val turn = try {
                pollTurn(botId, turnId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                failures += 1
                if (failures > 3) throw e
                delay(backoffMillis(failures))
                continue
            } catch (e: ApiException) {
                if (e.status != 502 && e.status != 503 && e.status != 504) throw e
                failures += 1
                if (failures > 3) throw e
                delay(backoffMillis(failures))
                continue
            } catch (e: IOException) {
                failures += 1
                if (failures > 3) throw e
                delay(backoffMillis(failures))
                continue
            }
            failures = 0
            if (turn.status != lastStatus) {
                lastStatus = turn.status
                onStatus(turn.status)
            }
            when (turn.status) {
                "completed" -> return turn
                "failed" -> throw ApiException(
                    turnFailureMessage(turn),
                    0,
                    turn.errorCode,
                )
                "cancelled" -> throw ApiException(
                    "这一轮已被取消",
                    0,
                    turn.errorCode,
                )
                // queued / running 以及未知状态都继续等
            }
        }
        throw ApiException("等待回复超时")
    }

    /** 拉取 Bot 会话历史，返回 (role, content) 列表，只保留文本消息。 */
    suspend fun conversation(botId: String, limit: Int = 50): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val data = request("GET", "/api/v1/bots/$botId/conversation?limit=$limit", null)
                .optJSONObject("data") ?: JSONObject()
            val messages = data.optJSONArray("messages") ?: JSONArray()
            (0 until messages.length()).mapNotNull { index ->
                val o = messages.optJSONObject(index) ?: return@mapNotNull null
                val kind = o.optStringOrEmpty("kind")
                if (kind.isNotEmpty() && kind != "text") return@mapNotNull null
                o.optStringOrEmpty("role") to o.optStringOrEmpty("content")
            }
        }

    // ---------- 审批（任务决策） ----------

    /**
     * 列出这个 Bot 正在等待用户批准的决策。
     *
     * 先查所有 waiting_for_user_decision 的任务；如果已知 chatTaskId 且它不在列表里，
     * 再单独取一次 /tasks/{chatTaskId}，避免聊天任务因分页 / 过滤条件被漏掉。
     */
    suspend fun listPendingDecisions(
        botId: String,
        chatTaskId: String = "",
    ): List<PendingDecision> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, PendingDecision>()
        val tasks = request(
            "GET",
            "/api/v1/bots/$botId/tasks?status=waiting_for_user_decision",
            null,
        ).dataArray()
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            val taskId = task.optStringOrEmpty("id")
            if (taskId.isEmpty()) continue
            val pending = task.optJSONObject("pending_decision") ?: continue
            parsePendingDecision(taskId, pending)?.let { found[it.decisionId] = it }
        }
        val chatId = chatTaskId.trim().takeUnless { it == "null" }.orEmpty()
        if (chatId.isNotEmpty() && found.values.none { it.taskId == chatId }) {
            getTaskPending(chatId)?.let { found[it.decisionId] = it }
        }
        found.values.toList()
    }

    /** 单独读取一个任务的待批准决策；没有则返回 null。 */
    suspend fun getTaskPending(taskId: String): PendingDecision? = withContext(Dispatchers.IO) {
        val root = request("GET", "/api/v1/tasks/$taskId", null)
        val data = root.optJSONObject("data") ?: root
        val pending = data.optJSONObject("pending_decision") ?: return@withContext null
        parsePendingDecision(taskId, pending)
    }

    /**
     * 选择某个决策选项。
     *
     * 允许：action=choose, option="允许"；拒绝：action=choose, option="拒绝"。
     * 不使用 action=reject（它表示拒绝全部选项，语义不同）。
     */
    suspend fun chooseDecision(
        taskId: String,
        decisionId: String,
        option: String,
    ): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("decision_id", decisionId)
            .put("action", "choose")
            .put("option", option)
        request("POST", "/api/v1/tasks/$taskId/decisions/choose", body)
        Unit
    }

    // ---------- 记忆 / 定时任务 / 运行记录 ----------

    suspend fun listMemories(botId: String): List<RemoteMemory> = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/bots/$botId/memories", null).dataArray()
        (0 until data.length()).mapNotNull { i ->
            data.optJSONObject(i)?.let(::parseMemory)
        }
    }

    suspend fun addMemory(
        botId: String,
        content: String,
        kind: String = "fact",
        relevance: String = "direct",
    ): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("content", content)
            .put("kind", kind)
            .put("relevance", relevance)
        request("POST", "/api/v1/bots/$botId/memories", body)
        Unit
    }

    suspend fun deleteMemory(botId: String, memoryId: String): Unit =
        withContext(Dispatchers.IO) {
            request("DELETE", "/api/v1/bots/$botId/memories/$memoryId", null)
            Unit
        }

    suspend fun listRoutines(botId: String): List<RemoteRoutine> = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/bots/$botId/routines", null).dataArray()
        (0 until data.length()).mapNotNull { i ->
            data.optJSONObject(i)?.let(::parseRoutine)
        }
    }

    suspend fun addRoutine(
        botId: String,
        name: String,
        instruction: String? = null,
        schedule: String? = null,
        enabled: Boolean = true,
    ): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .put("enabled", enabled)
        if (!instruction.isNullOrBlank()) body.put("instruction", instruction)
        if (!schedule.isNullOrBlank()) body.put("schedule", schedule)
        request("POST", "/api/v1/bots/$botId/routines", body)
        Unit
    }

    /**
     * 更新定时任务，优先用 PATCH（后端目前没有等价的 POST 更新入口）。
     *
     * Android 上 HttpURLConnection 对 PATCH 的支持并不总是可靠；这里不伪造一个
     * 不存在的 POST 接口，方法被拒时会原样抛出，由界面 toast 提示。
     */
    suspend fun updateRoutine(
        botId: String,
        routineId: String,
        enabled: Boolean? = null,
        schedule: String? = null,
        name: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (enabled != null) body.put("enabled", enabled)
        if (!schedule.isNullOrBlank()) body.put("schedule", schedule)
        if (!name.isNullOrBlank()) body.put("name", name)
        request("PATCH", "/api/v1/bots/$botId/routines/$routineId", body)
        Unit
    }

    suspend fun deleteRoutine(botId: String, routineId: String): Unit =
        withContext(Dispatchers.IO) {
            request("DELETE", "/api/v1/bots/$botId/routines/$routineId", null)
            Unit
        }

    suspend fun listTasks(botId: String, limit: Int = 50): List<RemoteTask> =
        withContext(Dispatchers.IO) {
            val data = request("GET", "/api/v1/bots/$botId/tasks?limit=$limit", null).dataArray()
            (0 until data.length()).mapNotNull { i ->
                data.optJSONObject(i)?.let(::parseTask)
            }
        }

    private fun parseTurn(o: JSONObject) = RemoteTurn(
        id = o.optStringOrEmpty("id"),
        status = o.optStringOrEmpty("status"),
        error = o.optStringOrEmpty("error"),
        errorCode = o.optStringOrEmpty("error_code"),
        replyText = o.optJSONObject("reply_message")
            ?.optStringOrEmpty("content").orEmpty(),
        userText = o.optJSONObject("user_message")
            ?.optStringOrEmpty("content").orEmpty(),
    )

    private fun turnFailureMessage(turn: RemoteTurn): String = when (turn.errorCode) {
        "budget_exhausted" -> "今日 token 预算已用完"
        "interrupted" -> "服务器重启，这一轮被中断，请重发"
        else -> turn.error.ifBlank { "模型返回异常" }
    }

    private fun backoffMillis(failures: Int): Long = minOf(1_000L * failures, 3_000L)

    /** 一条任务增量事件；seq 在同一任务内单调递增。 */
    data class TaskEvent(val seq: Int, val type: String, val data: JSONObject)

    /** 取回 afterSeq 之后的增量事件（对话回复的轮询源）。 */
    suspend fun pollEvents(taskId: String, afterSeq: Int): List<TaskEvent> =
        withContext(Dispatchers.IO) {
            val array = request("GET", "/api/v1/tasks/$taskId/events?after_seq=$afterSeq", null)
                .optJSONArray("data") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                TaskEvent(
                    seq = item.optInt("seq"),
                    type = item.optString("type"),
                    data = item.optJSONObject("data") ?: JSONObject(),
                )
            }
        }

    // ---------- 底层请求 ----------

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        timeoutMs: Int = 60_000,
    ): JSONObject {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            // 本地账号隔离：服务端用它区分连接器记录（将来接真实登录后改为令牌 subject）
            accountId?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("X-Flux-Account", it)
            }
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (text.isBlank() && code in 200..299) {
                return JSONObject().put("success", true)
            }
            val root = runCatching { JSONObject(text) }.getOrElse {
                throw ApiException("服务器返回异常（HTTP $code）", code)
            }
            val success = root.optBoolean("success", code in 200..299)
            if (code !in 200..299 || !success) {
                val msg = root.optStringOrEmpty("message").ifEmpty { "请求失败（HTTP $code）" }
                throw ApiException(msg, code, root.optStringOrEmpty("code"))
            }
            return root
        } finally {
            conn.disconnect()
        }
    }

    private fun parseConnector(o: JSONObject): RemoteConnector {
        val scopes = o.optJSONArray("scopes") ?: JSONArray()
        return RemoteConnector(
            id = o.optStringOrEmpty("id"),
            name = o.optStringOrEmpty("name"),
            description = o.optStringOrEmpty("description"),
            connected = o.optBoolean("connected"),
            account = o.optStringOrEmpty("account"),
            displayName = o.optStringOrEmpty("display_name"),
            avatarUrl = o.optStringOrEmpty("avatar_url"),
            scopes = (0 until scopes.length()).map { scopes.optString(it) },
            connectedAt = o.optStringOrEmpty("connected_at"),
        )
    }

    private fun parseBot(o: JSONObject) = RemoteBot(
        id = o.getString("id"),
        name = o.optStringOrEmpty("name"),
        shape = AvatarMap.toAppShape(o.optStringOrEmpty("avatar_shape")),
        palette = AvatarMap.toAppPalette(o.optStringOrEmpty("avatar_color")),
        description = o.optStringOrEmpty("description"),
        taskId = o.optStringOrEmpty("last_task_id").takeUnless { it == "null" }.orEmpty(),
        chatTaskId = o.optStringOrEmpty("chat_task_id").takeUnless { it == "null" }.orEmpty(),
    )

    /** 后端列表接口通常把数组放在 data 里；也兼容 data 对象内再包一层。 */
    private fun JSONObject.dataArray(): JSONArray {
        optJSONArray("data")?.let { return it }
        val obj = optJSONObject("data") ?: return JSONArray()
        return obj.optJSONArray("tasks")
            ?: obj.optJSONArray("items")
            ?: obj.optJSONArray("memories")
            ?: obj.optJSONArray("routines")
            ?: JSONArray()
    }

    private fun parsePendingDecision(taskId: String, o: JSONObject): PendingDecision? {
        val id = o.optStringOrEmpty("id")
        if (id.isEmpty()) return null
        val options = o.optJSONArray("options")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                when (val item = array.opt(index)) {
                    is JSONObject -> item.optStringOrEmpty("label")
                        .ifBlank { item.optStringOrEmpty("option") }
                        .takeIf { it.isNotBlank() }
                    is String -> item.takeIf { it.isNotBlank() }
                    else -> null
                }
            }
        }.orEmpty()
        return PendingDecision(
            taskId = taskId,
            decisionId = id,
            question = o.optStringOrEmpty("question").ifBlank { "需要你批准" },
            detail = o.optStringOrEmpty("context"),
            options = options,
            kind = o.optStringOrEmpty("kind"),
        )
    }

    private fun parseMemory(o: JSONObject): RemoteMemory? {
        val id = o.optStringOrEmpty("id")
        val text = o.optStringOrEmpty("content").ifBlank { o.optStringOrEmpty("text") }
        if (id.isEmpty() || text.isEmpty()) return null
        return RemoteMemory(
            id = id,
            text = text,
            source = o.optStringOrEmpty("source").ifBlank { o.optStringOrEmpty("kind") },
            ts = displayTime(
                o.optStringOrEmpty("created_at").ifBlank { o.optStringOrEmpty("createdAt") },
            ),
        )
    }

    private fun parseRoutine(o: JSONObject): RemoteRoutine? {
        val id = o.optStringOrEmpty("id")
        if (id.isEmpty()) return null
        val schedule = o.optStringOrEmpty("schedule")
        return RemoteRoutine(
            id = id,
            title = o.optStringOrEmpty("name")
                .ifBlank { o.optStringOrEmpty("title") }
                .ifBlank { "定时任务" },
            time = schedule.ifBlank { o.optStringOrEmpty("next_run_at") },
            freq = schedule.ifBlank { "定时" },
            enabled = o.optBoolean("enabled", true),
        )
    }

    private fun parseTask(o: JSONObject): RemoteTask? {
        val id = o.optStringOrEmpty("id")
        if (id.isEmpty()) return null
        val title = o.optStringOrEmpty("title")
            .ifBlank { o.optStringOrEmpty("instruction") }
            .ifBlank { o.optStringOrEmpty("prompt") }
            .ifBlank { o.optStringOrEmpty("description") }
        return RemoteTask(
            id = id,
            status = o.optStringOrEmpty("status"),
            title = title.ifBlank { "任务" },
            createdAt = displayTime(
                o.optStringOrEmpty("created_at").ifBlank { o.optStringOrEmpty("createdAt") },
            ),
            finishedAt = displayTime(
                o.optStringOrEmpty("finished_at").ifBlank {
                    o.optStringOrEmpty("ended_at").ifBlank {
                        o.optStringOrEmpty("finishedAt")
                    }
                },
            ),
            error = o.optStringOrEmpty("error"),
        )
    }

    private fun displayTime(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.replace('T', ' ').removeSuffix("Z").take(16)
    }

    companion object {
        /** 未登录 / 未绑定账号时使用的全局令牌。 */
        private const val SYS_TOKEN_KEY = "sys.api_token"

        const val DEFAULT_BASE_URL = "https://flux.qiuli55.top/bot"
    }
}

/**
 * App 的 10 形体 / 12 配色与后端的 14 形体 / 13 配色之间的双向换算。
 * 名字对不上的就近映射，保证后端校验通过且界面颜色接近。
 */
object AvatarMap {

    private val shapeToServer = mapOf(
        "circle" to "circle",
        "squircle" to "squircle",
        "triangle" to "triangle",
        "star" to "star",
        "cloud" to "cloud",
        "heart" to "blob",
        "flower" to "flower",
        "drop" to "drop",
        "hex" to "hexagon",
        "diamond" to "diamond",
    )

    private val shapeToApp = mapOf(
        "circle" to "circle",
        "squircle" to "squircle",
        "triangle" to "triangle",
        "diamond" to "diamond",
        "star" to "star",
        "flower" to "flower",
        "drop" to "drop",
        "cloud" to "cloud",
        "hexagon" to "hex",
        "blob" to "heart",
        "burst" to "star",
        "shield" to "squircle",
        "gear" to "hex",
        "egg" to "circle",
    )

    private val paletteToServer = mapOf(
        "mint" to "emerald",
        "teal" to "teal",
        "sky" to "sky",
        "lavender" to "violet",
        "plum" to "fuchsia",
        "rose" to "rose",
        "tangerine" to "orange",
        "butter" to "amber",
        "moss" to "lime",
        "sage" to "emerald",
        "clay" to "indigo",
        "slate" to "slate",
    )

    private val paletteToApp = mapOf(
        "emerald" to "mint",
        "teal" to "teal",
        "sky" to "sky",
        "cyan" to "sky",
        "violet" to "lavender",
        "fuchsia" to "plum",
        "rose" to "rose",
        "orange" to "tangerine",
        "amber" to "butter",
        "lime" to "moss",
        "indigo" to "clay",
        "slate" to "slate",
    )

    fun toServerShape(appKey: String): String = shapeToServer[appKey] ?: "circle"

    fun toAppShape(serverKey: String): String = shapeToApp[serverKey] ?: "circle"

    fun toServerPalette(appKey: String): String = paletteToServer[appKey] ?: "emerald"

    fun toAppPalette(serverKey: String): String = paletteToApp[serverKey] ?: "sage"
}
