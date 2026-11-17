package top.qiuli55.fluxbot.data

/**
 * 极简 SSE 解析（纯函数，可在 JVM 单测里直接验证）。
 *
 * 服务端的对话流是标准 SSE：`event:` / `data:` 字段，空行派发一个事件，
 * 以 `:` 开头的是心跳注释，直接忽略。这里只保留 App 用得到的字段
 * （id / event / data），多行 data 用换行拼接。
 */
data class SseFrame(
    val id: String? = null,
    val event: String? = null,
    val data: String,
)

/**
 * 按行喂入的增量解析器。
 *
 * 网络线程每读到一行就调用 [pushLine]；空行派发并返回一个事件，
 * 其余情况返回 null。[push] 负责把任意切分的文本块拆成完整的行，
 * 因此 `\r\n` 被拆到两个块里也不会误判。
 */
class SseDecoder {

    private val pending = StringBuilder()
    private val dataLines = ArrayList<String>()
    private var id: String? = null
    private var event: String? = null

    /** 喂一段任意文本，返回本次凑齐的全部事件。 */
    fun push(chunk: String): List<SseFrame> {
        if (chunk.isEmpty()) return emptyList()
        pending.append(chunk)
        val frames = ArrayList<SseFrame>()
        while (true) {
            val boundary = pending.indexOf("\n")
            if (boundary < 0) break
            val line = pending.substring(0, boundary)
            pending.delete(0, boundary + 1)
            pushLine(line)?.let(frames::add)
        }
        return frames
    }

    /** 喂一整行（不含换行符）；空行派发事件。 */
    fun pushLine(rawLine: String): SseFrame? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> dataLines.add(value)
        }
        return null
    }

    /** 流结束时冲掉可能残留的最后一个事件（没有空行收尾也容忍）。 */
    fun flush(): SseFrame? {
        val tail = pending.toString()
        pending.setLength(0)
        if (tail.isNotEmpty()) {
            pushLine(tail)
        }
        return dispatch()
    }

    private fun dispatch(): SseFrame? {
        if (dataLines.isEmpty()) {
            id = null
            event = null
            return null
        }
        val frame = SseFrame(id, event, dataLines.joinToString("\n"))
        dataLines.clear()
        id = null
        event = null
        return frame
    }
}
