package top.qiuli55.fluxbot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
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

class ApiException(message: String) : Exception(message)

/**
 * flux-bot 后端的最小客户端：只依赖 HttpURLConnection + org.json，不引入新依赖。
 *
 * 服务器地址与令牌存在独立的 SharedPreferences 里，首次启动预填测试机地址。
 */
class Api(context: Context) {

    private val prefs = context.getSharedPreferences("fluxbot.api", Context.MODE_PRIVATE)

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
        get() = prefs.getString("token", DEFAULT_TOKEN) ?: DEFAULT_TOKEN
        set(value) {
            prefs.edit().putString("token", value.trim()).apply()
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
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("description", description)
            .put("start", false)
            .put("bot_name", botName)
            .put("avatar_shape", AvatarMap.toServerShape(shape))
            .put("avatar_color", AvatarMap.toServerPalette(palette))
        request("POST", "/api/v1/tasks", body).getJSONObject("data").getString("id")
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

    private fun parseBot(o: JSONObject) = RemoteBot(
        id = o.getString("id"),
        name = o.optString("name"),
        shape = AvatarMap.toAppShape(o.optString("avatar_shape")),
        palette = AvatarMap.toAppPalette(o.optString("avatar_color")),
        description = o.optString("description"),
        taskId = o.optString("last_task_id"),
    )

    companion object {
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
