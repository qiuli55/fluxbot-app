package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject

data class Account(
    val id: String,
    val name: String,
    val email: String,
    val initials: String,
    val plan: String,
    val used: Int,
)

data class TaskStep(val text: String, val done: Boolean)

data class TaskCard(val title: String, val steps: List<TaskStep>, val finished: Boolean)

data class Message(
    val id: String,
    val role: String, // user / bot / system
    val text: String,
    val time: String,
    val card: TaskCard? = null,
    val sender: String? = null,
)

data class Memory(val id: String, val text: String, val source: String, val ts: String)

data class Routine(
    val id: String,
    val title: String,
    val time: String,
    val freq: String,
    val enabled: Boolean,
)

data class VaultItem(val id: String, val cat: String, val title: String, val sub: String)

/** 资料库里的一条网页记录：Bot 或群聊在任务中看过、并留在本站的东西。 */
data class LibraryEntry(
    val id: String,
    val title: String,
    val host: String,
    val group: String = "本周",
    val mark: String = "",
    val bg: Long = 0xFF23282D,
    val fg: Long = 0xFFE6E9EC,
)

data class Bot(
    val id: String,
    val name: String,
    val seed: String,
    val shape: String,
    val palette: String,
    val preview: String,
    val time: String,
    val unread: String?, // null / "accent" / "warn"
    val working: Boolean,
    val hero: Boolean,
    val computer: String,
    val messages: List<Message>,
    val memories: List<Memory>,
    val routines: List<Routine>,
    val permissions: Map<String, Boolean>,
    val autoMemory: Boolean,
    val group: Boolean = false,
    val members: List<String> = emptyList(),
    val taskId: String = "",
    val remote: Boolean = false,
    val library: List<LibraryEntry> = emptyList(),
    /** 云端路由模型（空 = 跟随平台默认）；modelKeyId 指向密钥库条目。 */
    val model: String = "",
    val modelKeyId: String = "",
)

data class Settings(
    val notify: Boolean = true,
    val autoTz: Boolean = true,
    val tz: String = "Asia/Shanghai",
    val appearance: String = "跟随系统",
    val language: String = "简体中文",
    val haptics: Boolean = false,
)

data class AccountState(
    val account: Account,
    val bots: List<Bot>,
    val settings: Settings,
    val vault: List<VaultItem>,
    val connected: List<String>,
)

data class AppState(
    val accounts: List<AccountState>,
    val currentId: String,
    val loggedIn: Boolean,
) {
    fun current(): AccountState = accounts.firstOrNull { it.account.id == currentId } ?: accounts.first()
}

// ---------- 序列化 ----------

private fun JSONArray.addAllStrings(values: Iterable<String>) = values.forEach { put(it) }

fun Account.toJson() = JSONObject()
    .put("id", id).put("name", name).put("email", email).put("initials", initials)
    .put("plan", plan).put("used", used)

fun accountFromJson(o: JSONObject) = Account(
    o.getString("id"), o.getString("name"), o.getString("email"),
    o.getString("initials"), o.getString("plan"), o.getInt("used"),
)

fun TaskCard.toJson() = JSONObject()
    .put("title", title).put("finished", finished)
    .put("steps", JSONArray().apply {
        steps.forEach { put(JSONObject().put("text", it.text).put("done", it.done)) }
    })

fun cardFromJson(o: JSONObject): TaskCard = TaskCard(
    o.getString("title"), o.getJSONArray("steps").let { a ->
        (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            TaskStep(s.getString("text"), s.getBoolean("done"))
        }
    }, o.getBoolean("finished"),
)

fun Message.toJson() = JSONObject()
    .put("id", id).put("role", role).put("text", text).put("time", time)
    .apply {
        card?.let { put("card", it.toJson()) }
        sender?.let { put("sender", it) }
    }

fun messageFromJson(o: JSONObject) = Message(
    o.getString("id"), o.getString("role"), o.getString("text"), o.getString("time"),
    if (o.has("card")) cardFromJson(o.getJSONObject("card")) else null,
    o.optString("sender").takeIf { it.isNotEmpty() },
)

fun Memory.toJson() = JSONObject().put("id", id).put("text", text).put("source", source).put("ts", ts)

fun memoryFromJson(o: JSONObject) =
    Memory(o.getString("id"), o.getString("text"), o.getString("source"), o.getString("ts"))

fun Routine.toJson() = JSONObject().put("id", id).put("title", title).put("time", time)
    .put("freq", freq).put("enabled", enabled)

fun routineFromJson(o: JSONObject) = Routine(
    o.getString("id"), o.getString("title"), o.getString("time"),
    o.optString("freq", "每天"), o.getBoolean("enabled"),
)

fun VaultItem.toJson() = JSONObject().put("id", id).put("cat", cat).put("title", title).put("sub", sub)

fun vaultFromJson(o: JSONObject) =
    VaultItem(o.getString("id"), o.getString("cat"), o.getString("title"), o.getString("sub"))

fun LibraryEntry.toJson() = JSONObject()
    .put("id", id).put("title", title).put("host", host).put("group", group)
    .put("mark", mark).put("bg", bg).put("fg", fg)

fun libraryFromJson(o: JSONObject) = LibraryEntry(
    id = o.getString("id"), title = o.optString("title"), host = o.optString("host"),
    group = o.optString("group", "本周"), mark = o.optString("mark"),
    bg = o.optLong("bg", 0xFF23282D), fg = o.optLong("fg", 0xFFE6E9EC),
)

fun Bot.toJson() = JSONObject()
    .put("id", id).put("name", name).put("seed", seed).put("shape", shape).put("palette", palette)
    .put("preview", preview).put("time", time).put("unread", unread)
    .put("working", working).put("hero", hero).put("autoMemory", autoMemory)
    .put("computer", computer)
    .put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    .put("memories", JSONArray().apply { memories.forEach { put(it.toJson()) } })
    .put("routines", JSONArray().apply { routines.forEach { put(it.toJson()) } })
    .put("permissions", JSONObject(permissions))
    .put("group", group)
    .put("members", JSONArray().apply { addAllStrings(members) })
    .put("taskId", taskId)
    .put("remote", remote)
    .put("library", JSONArray().apply { library.forEach { put(it.toJson()) } })
    .put("model", model)
    .put("modelKeyId", modelKeyId)

fun botFromJson(o: JSONObject) = Bot(
    id = o.getString("id"), name = o.getString("name"), seed = o.getString("seed"),
    shape = o.getString("shape"), palette = o.getString("palette"),
    preview = o.optString("preview"), time = o.optString("time"),
    unread = o.optString("unread").takeIf { it.isNotEmpty() }, working = o.optBoolean("working"), hero = o.optBoolean("hero"),
    computer = o.optString("computer", "A 机 · Linux 服务器"),
    messages = o.getJSONArray("messages").let { a -> (0 until a.length()).map { messageFromJson(a.getJSONObject(it)) } },
    memories = o.getJSONArray("memories").let { a -> (0 until a.length()).map { memoryFromJson(a.getJSONObject(it)) } },
    routines = o.getJSONArray("routines").let { a -> (0 until a.length()).map { routineFromJson(a.getJSONObject(it)) } },
    permissions = o.optJSONObject("permissions")?.let { p ->
        p.keys().asSequence().associateWith { p.getBoolean(it) }
    } ?: emptyMap(),
    autoMemory = o.optBoolean("autoMemory", true),
    group = o.optBoolean("group", false),
    members = o.optJSONArray("members")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
    taskId = o.optString("taskId").takeUnless { it == "null" }.orEmpty(),
    remote = o.optBoolean("remote", false),
    library = o.optJSONArray("library")
        ?.let { a -> (0 until a.length()).map { libraryFromJson(a.getJSONObject(it)) } }
        ?: emptyList(),
    model = o.optString("model"),
    modelKeyId = o.optString("modelKeyId"),
)

fun Settings.toJson() = JSONObject()
    .put("notify", notify).put("autoTz", autoTz).put("tz", tz)
    .put("appearance", appearance).put("language", language).put("haptics", haptics)

fun settingsFromJson(o: JSONObject) = Settings(
    o.optBoolean("notify", true), o.optBoolean("autoTz", true),
    o.optString("tz", "Asia/Shanghai"), o.optString("appearance", "跟随系统"),
    o.optString("language", "简体中文"), o.optBoolean("haptics", false),
)

fun AccountState.toJson() = JSONObject()
    .put("account", account.toJson())
    .put("settings", settings.toJson())
    .put("bots", JSONArray().apply { bots.forEach { put(it.toJson()) } })
    .put("vault", JSONArray().apply { vault.forEach { put(it.toJson()) } })
    .put("connected", JSONArray().apply { addAllStrings(connected) })

fun accountStateFromJson(o: JSONObject) = AccountState(
    account = accountFromJson(o.getJSONObject("account")),
    bots = o.getJSONArray("bots").let { a -> (0 until a.length()).map { botFromJson(a.getJSONObject(it)) } },
    settings = settingsFromJson(o.getJSONObject("settings")),
    vault = o.getJSONArray("vault").let { a -> (0 until a.length()).map { vaultFromJson(a.getJSONObject(it)) } },
    connected = o.getJSONArray("connected").let { a -> (0 until a.length()).map { a.getString(it) } },
)

fun AppState.toJson() = JSONObject()
    .put("currentId", currentId).put("loggedIn", loggedIn)
    .put("accounts", JSONArray().apply { accounts.forEach { put(it.toJson()) } })

fun appStateFromJson(o: JSONObject) = AppState(
    accounts = o.getJSONArray("accounts").let { a -> (0 until a.length()).map { accountStateFromJson(a.getJSONObject(it)) } },
    currentId = o.getString("currentId"),
    loggedIn = o.getBoolean("loggedIn"),
)
