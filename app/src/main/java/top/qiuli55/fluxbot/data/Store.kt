package top.qiuli55.fluxbot.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class Store(context: Context) {

    private val prefs = context.getSharedPreferences("fluxbot.v1", Context.MODE_PRIVATE)

    val api = Api(context)

    var state by mutableStateOf(load() ?: DemoData.seed())
        private set

    private fun load(): AppState? = prefs.getString("data", null)?.let {
        runCatching { appStateFromJson(JSONObject(it)) }.getOrNull()
    }

    fun save() {
        prefs.edit().putString("data", state.toJson().toString()).apply()
    }

    private fun update(transform: (AppState) -> AppState) {
        state = transform(state)
        save()
    }

    fun reset() {
        state = DemoData.seed()
        save()
    }

    // ---------- 账户 ----------

    fun login(accountId: String? = null) = update {
        it.copy(currentId = accountId ?: it.currentId, loggedIn = true)
    }

    fun logout() = update { it.copy(loggedIn = false) }

    fun switchAccount(id: String) = update { it.copy(currentId = id, loggedIn = true) }

    fun addAccount(email: String, name: String): String {
        val acc = DemoData.newAccount(email, name)
        update { it.copy(accounts = it.accounts + acc, currentId = acc.account.id, loggedIn = true) }
        return acc.account.id
    }

    fun deleteAccount(id: String) {
        update { s ->
            val left = s.accounts.filterNot { it.account.id == id }
            val accounts = if (left.isEmpty()) listOf(DemoData.mainAccount()) else left
            AppState(accounts, accounts.first().account.id, s.loggedIn)
        }
    }

    private fun updateCur(transform: (AccountState) -> AccountState) {
        val id = state.currentId
        update { s -> s.copy(accounts = s.accounts.map { if (it.account.id == id) transform(it) else it }) }
    }

    fun current(): AccountState = state.current()

    fun bot(id: String?): Bot? = current().bots.firstOrNull { it.id == id }

    private fun updateBot(botId: String, transform: (Bot) -> Bot) {
        updateCur { acc -> acc.copy(bots = acc.bots.map { if (it.id == botId) transform(it) else it }) }
    }

    // ---------- Bot ----------

    fun addBot(name: String, shape: String, palette: String): String {
        val id = "b-" + Random.nextInt(100000, 999999)
        val bot = Bot(
            id = id, name = name, seed = id, shape = shape, palette = palette,
            preview = "你好，我是 $name。把你要办的事交给我，我会先拆成任务，再一步步执行。",
            time = clock(), unread = null, working = false, hero = false,
            computer = "A 机 · Linux 服务器",
            messages = listOf(
                Message(
                    id = "greet-$id", role = "bot",
                    text = "你好，我是 $name。把你要办的事交给我，我会先拆成任务，再一步步执行。",
                    time = clock(),
                ),
            ),
            memories = emptyList(), routines = emptyList(), permissions = emptyMap(), autoMemory = true,
        )
        updateCur { it.copy(bots = it.bots + bot) }
        return id
    }

    fun addMessage(
        botId: String,
        role: String,
        text: String,
        card: TaskCard? = null,
        sender: String? = null,
    ): Message {
        val m = Message("m-${System.nanoTime()}", role, text, clock(), card, sender)
        updateBot(botId) {
            val prefix = if (role == "bot" && sender != null) "$sender：" else ""
            it.copy(
                messages = it.messages + m,
                preview = prefix + text.replace("\n", " ").take(maxOf(8, 46 - prefix.length)),
                time = m.time,
                unread = if (role == "bot") "accent" else it.unread,
            )
        }
        return m
    }

    // ---------- 远端 Bot ----------

    fun mergeRemoteBots(remote: List<RemoteBot>) {
        if (remote.isEmpty()) return
        updateCur { acc ->
            var bots = acc.bots
            remote.forEach { r ->
                val existing = bots.firstOrNull { it.id == r.id }
                bots = if (existing == null) {
                    bots + Bot(
                        id = r.id, name = r.name, seed = r.id,
                        shape = AvatarMap.toAppShape(r.shape),
                        palette = AvatarMap.toAppPalette(r.palette),
                        preview = r.description.ifEmpty { "远端 Bot 已就绪。" },
                        time = clock(), unread = null, working = false, hero = false,
                        computer = "云端 · flux-bot",
                        messages = emptyList(), memories = emptyList(), routines = emptyList(),
                        permissions = emptyMap(), autoMemory = true,
                        taskId = r.taskId, remote = true,
                    )
                } else {
                    bots.map {
                        if (it.id == r.id) it.copy(
                            name = r.name,
                            shape = AvatarMap.toAppShape(r.shape),
                            palette = AvatarMap.toAppPalette(r.palette),
                            preview = r.description.ifEmpty { it.preview },
                            taskId = it.taskId.ifEmpty { r.taskId },
                            remote = true,
                        ) else it
                    }
                }
            }
            acc.copy(bots = bots)
        }
    }

    fun setTaskId(botId: String, taskId: String) = updateBot(botId) { it.copy(taskId = taskId) }

    fun replaceMessages(botId: String, messages: List<Message>) = updateBot(botId) {
        val last = messages.lastOrNull()
        it.copy(
            messages = messages,
            preview = last?.text?.replace("\n", " ")?.take(46) ?: it.preview,
            time = last?.time ?: it.time,
        )
    }

    /** 群聊成员（保持建群时的顺序，忽略已删除或群聊 Bot）。 */
    fun groupMembers(bot: Bot): List<Bot> =
        bot.members.mapNotNull { id -> current().bots.firstOrNull { it.id == id && !it.group } }

    fun addGroup(memberIds: List<String>, name: String?): String? {
        val members = memberIds.distinct().mapNotNull { id -> bot(id)?.takeIf { !it.group } }
        if (members.size < 2) return null
        val id = "g-" + Random.nextInt(100000, 999999)
        val auto = "群聊 · " + members.take(2).joinToString("、") { it.name } +
            if (members.size > 2) " 等 ${members.size} 个" else ""
        val title = name?.trim().orEmpty().ifEmpty { auto }
        val groupBot = Bot(
            id = id, name = title, seed = id, shape = "cloud", palette = "sage",
            preview = "${members.size} 个成员已就位，把任务交给他们。",
            time = clock(), unread = null, working = false, hero = false,
            computer = "A 机 · Linux 服务器",
            messages = listOf(
                Message(
                    id = "greet-$id", role = "bot",
                    text = "群聊建好了，我们 ${members.size} 个都在。把任务发进来，我们分头处理，有结论就贴在群里。",
                    time = clock(),
                    sender = members.first().name,
                ),
            ),
            memories = emptyList(), routines = emptyList(), permissions = emptyMap(), autoMemory = true,
            group = true, members = members.map { it.id },
        )
        updateCur { it.copy(bots = it.bots + groupBot) }
        return id
    }

    fun replaceMessage(botId: String, messageId: String, transform: (Message) -> Message) {
        updateBot(botId) { bot ->
            bot.copy(messages = bot.messages.map { if (it.id == messageId) transform(it) else it })
        }
    }

    fun markRead(botId: String) = updateBot(botId) { it.copy(unread = null) }

    fun deleteBot(botId: String) = updateCur { acc -> acc.copy(bots = acc.bots.filterNot { it.id == botId }) }

    fun renameBot(botId: String, name: String) = updateBot(botId) { it.copy(name = name) }

    fun setWorking(botId: String, working: Boolean) = updateBot(botId) { it.copy(working = working) }

    fun togglePermission(botId: String, key: String) = updateBot(botId) {
        it.copy(permissions = it.permissions + (key to !(it.permissions[key] ?: false)))
    }

    fun setAutoMemory(botId: String, on: Boolean) = updateBot(botId) { it.copy(autoMemory = on) }

    fun setComputer(botId: String, name: String) = updateBot(botId) { it.copy(computer = name) }

    fun removeMessage(botId: String, msgId: String) = updateBot(botId) {
        it.copy(messages = it.messages.filterNot { m -> m.id == msgId })
    }

    // ---------- 记忆 / 定时任务 ----------

    fun addMemory(botId: String, text: String, source: String = "manual") = updateBot(botId) {
        it.copy(memories = listOf(Memory("mem-${System.nanoTime()}", text, source, "刚刚")) + it.memories)
    }

    fun deleteMemory(botId: String, memId: String) = updateBot(botId) {
        it.copy(memories = it.memories.filterNot { m -> m.id == memId })
    }

    fun addRoutine(botId: String, title: String, time: String, freq: String) = updateBot(botId) {
        it.copy(routines = it.routines + Routine("rt-${System.nanoTime()}", title, time, freq, true))
    }

    fun toggleRoutine(botId: String, rtId: String) = updateBot(botId) {
        it.copy(routines = it.routines.map { r -> if (r.id == rtId) r.copy(enabled = !r.enabled) else r })
    }

    fun deleteRoutine(botId: String, rtId: String) = updateBot(botId) {
        it.copy(routines = it.routines.filterNot { r -> r.id == rtId })
    }

    // ---------- 保管库 / 应用 ----------

    fun addVault(cat: String, title: String, sub: String) = updateCur {
        it.copy(vault = listOf(VaultItem("v-${System.nanoTime()}", cat, title, sub)) + it.vault)
    }

    fun deleteVault(id: String) = updateCur { acc -> acc.copy(vault = acc.vault.filterNot { it.id == id }) }

    fun toggleApp(appId: String) = updateCur {
        val on = it.connected.contains(appId)
        it.copy(connected = if (on) it.connected - appId else it.connected + appId)
    }

    // ---------- 设置 ----------

    fun updateSettings(transform: (Settings) -> Settings) = updateCur { it.copy(settings = transform(it.settings)) }

    // ---------- 工具 ----------

    fun clock(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
