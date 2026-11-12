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

/** 后端签发的一次云桌面会话；profile 由服务端计算，客户端直接使用。 */
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

    /** 向服务端换取云桌面连接参数；gateway_token 可能为空，此时改用 ticket。 */
    suspend fun desktopSession(botId: String): DesktopSession = withContext(Dispatchers.IO) {
        val data = request("POST", "/api/v1/bots/$botId/desktop/session", JSONObject())
            .getJSONObject("data")
        DesktopSession(
            host = data.optStringOrEmpty("host"),
            token = data.optStringOrEmpty("gateway_token"),
            bridge = data.optStringOrEmpty("bridge"),
            bridgeKey = data.optStringOrEmpty("bridge_key"),
            profile = data.optStringOrEmpty("profile"),
            ticket = data.optStringOrEmpty("ticket"),
            expiresAt = data.optStringOrEmpty("expires_at"),
        )
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
