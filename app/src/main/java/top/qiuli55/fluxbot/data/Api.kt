package top.qiuli55.fluxbot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** 后端返回的一条 Bot（已换算成 App 的形体 / 配色 key）。 */
data class RemoteBot(
    val id: String,
    val name: String,
    val shape: String,
    val palette: String,
    val description: String,
    val taskId: String,
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

class ApiException(message: String) : Exception(message)

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
        get() = (secure.get(tokenKey) ?: secure.get(SYS_TOKEN_KEY) ?: migrateLegacyToken() ?: DEFAULT_TOKEN)
            .ifBlank { DEFAULT_TOKEN }
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
            val model = o.optString("model").trim()
            val keyId = o.optString("key_id").trim()
            if (model.isEmpty() || keyId.isEmpty()) null
            else RemoteModel(model, o.optString("key_name"), keyId)
        }
        ModelCatalog(data.optString("model"), options)
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

    // ---------- Bot 桌面 ----------

    /** 桌面入口参数：隧道地址随隧道变化，凭据由服务端注入，App 不保存。 */
    suspend fun desktopConfig(): DesktopConfig = withContext(Dispatchers.IO) {
        val data = request("GET", "/api/v1/desktop", null).getJSONObject("data")
        DesktopConfig(
            gateway = data.optString("gateway"),
            bridge = data.optString("bridge"),
            token = data.optString("token"),
            bridgeKey = data.optString("bridge_key"),
            configUrl = data.optString("config_url"),
        )
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
    suspend fun waitForReply(taskId: String, afterSeq: Int, timeoutMs: Long = 180_000): String {
        var seq = afterSeq
        var failures = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(700)
            val events = runCatching { pollEvents(taskId, seq) }
                .onSuccess { failures = 0 }
                .onFailure {
                    failures += 1
                    if (failures >= 3) throw it
                }
                .getOrNull() ?: continue
            for (event in events) {
                seq = maxOf(seq, event.seq)
                when (event.type) {
                    "assistant_message" -> {
                        val text = event.data.optJSONObject("message")
                            ?.optString("content").orEmpty()
                        if (text.isNotBlank()) return text
                    }
                    "assistant_error" -> {
                        val reason = event.data.optString("error").ifBlank { "模型返回异常" }
                        throw ApiException(reason)
                    }
                }
            }
        }
        throw ApiException("等模型回复超时")
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
            if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
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
                throw ApiException("服务器返回异常（HTTP $code）")
            }
            val success = root.optBoolean("success", code in 200..299)
            if (code !in 200..299 || !success) {
                val msg = root.optString("message").ifEmpty { "请求失败（HTTP $code）" }
                throw ApiException(msg)
            }
            return root
        } finally {
            conn.disconnect()
        }
    }

    private fun parseConnector(o: JSONObject): RemoteConnector {
        val scopes = o.optJSONArray("scopes") ?: JSONArray()
        return RemoteConnector(
            id = o.optString("id"),
            name = o.optString("name"),
            description = o.optString("description"),
            connected = o.optBoolean("connected"),
            account = o.optString("account"),
            displayName = o.optString("display_name"),
            avatarUrl = o.optString("avatar_url"),
            scopes = (0 until scopes.length()).map { scopes.optString(it) },
            connectedAt = o.optString("connected_at"),
        )
    }

    private fun parseBot(o: JSONObject) = RemoteBot(
        id = o.getString("id"),
        name = o.optString("name"),
        shape = AvatarMap.toAppShape(o.optString("avatar_shape")),
        palette = AvatarMap.toAppPalette(o.optString("avatar_color")),
        description = o.optString("description"),
        taskId = o.optString("last_task_id"),
    )

    companion object {
        /** 未登录 / 未绑定账号时使用的全局令牌。 */
        private const val SYS_TOKEN_KEY = "sys.api_token"

        const val DEFAULT_BASE_URL = "https://flux.qiuli55.top/bot"
        const val DEFAULT_TOKEN = "fb_2f9c7a41d8e34b6fa05c"
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
