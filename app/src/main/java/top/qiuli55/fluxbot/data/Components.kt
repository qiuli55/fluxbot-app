package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 生成式聊天组件（服务端 schema_version = 1）。
 *
 * 服务端把一条消息的 `payload` 写成
 * `{"schema_version":1, "component": <node>, "fallback_text": "..."}`，
 * 既有 kind（file_card / plan_card / handoff / ...）也会在读取时附带同样的
 * `component` 树。App 只认这一套白名单节点，遇到未知类型或未知版本时
 * 整条消息退回纯文本，绝不崩溃。
 *
 * 节点白名单与 flux/core/components/schema.py 保持一致。
 */
sealed interface ComponentNode {

    data class Card(
        val title: String? = null,
        val subtitle: String? = null,
        val children: List<ComponentNode> = emptyList(),
    ) : ComponentNode

    data class Text(
        val text: String,
        val markdown: Boolean = false,
    ) : ComponentNode

    data class Image(
        val url: String = "",
        val deliveryId: String = "",
        val uploadId: String = "",
        val alt: String = "",
        val mime: String = "",
        val width: Int = 0,
        val height: Int = 0,
        val index: Int = -1,
    ) : ComponentNode

    data class File(
        val name: String,
        val url: String = "",
        val deliveryId: String = "",
        val uploadId: String = "",
        val mime: String = "",
        val size: Long = -1,
        val index: Int = -1,
    ) : ComponentNode

    data class Audio(
        val url: String = "",
        val deliveryId: String = "",
        val uploadId: String = "",
        val durationMs: Long = -1,
        val transcript: String = "",
        val index: Int = -1,
    ) : ComponentNode

    data class Button(
        val label: String,
        val action: ComponentAction? = null,
    ) : ComponentNode

    data class Choice(
        val name: String = "",
        val id: String = "",
        val options: List<ChoiceOption> = emptyList(),
        val multiple: Boolean = false,
        val action: ComponentAction? = null,
    ) : ComponentNode

    data class Input(
        val name: String,
        val placeholder: String = "",
        val inputType: String = "text",
        val label: String = "",
        val action: ComponentAction? = null,
    ) : ComponentNode

    data class Progress(
        val label: String = "",
        val percent: Int = -1,
        val status: String = "idle",
    ) : ComponentNode

    data class ListBlock(
        val items: List<ComponentNode> = emptyList(),
        val ordered: Boolean = false,
    ) : ComponentNode

    data object Divider : ComponentNode

    /** 未知节点：保留类型名用于兜底展示，父级可以安全跳过。 */
    data class Unknown(val type: String) : ComponentNode
}

data class ChoiceOption(val id: String, val label: String)

data class ComponentAction(
    val type: String = "",
    val name: String = "",
    val url: String = "",
    val actionId: String = "",
    val decisionId: String = "",
    val option: String = "",
)

/** 一条组件消息的解析结果；[root] 为空时调用方必须展示 [fallbackText]。 */
data class ComponentEnvelope(
    val schemaVersion: Int,
    val root: ComponentNode?,
    val fallbackText: String,
)

const val COMPONENT_SCHEMA_VERSION = 1

/**
 * 解析消息 envelope。任何异常 / 未知版本 / 未知根节点都返回 root=null，
 * 让上层退回 content 或 fallback_text。
 */
fun parseComponentEnvelope(payload: JSONObject?): ComponentEnvelope? {
    if (payload == null) return null
    val version = payload.optInt("schema_version", -1)
    val fallback = payload.optString("fallback_text").trim()
    if (version != COMPONENT_SCHEMA_VERSION) {
        return ComponentEnvelope(version, null, fallback)
    }
    val node = parseComponentNode(payload.optJSONObject("component")) ?: return ComponentEnvelope(
        version, null, fallback,
    )
    return ComponentEnvelope(version, node, fallback)
}

/** 解析一个节点；未知类型返回 null（整棵树按 fallback 处理）。 */
fun parseComponentNode(raw: JSONObject?): ComponentNode? {
    if (raw == null) return null
    val rawType = raw.optString("type").trim()
    if (rawType.isEmpty()) return null
    val type = if (rawType == "status") "progress" else rawType
    return try {
        when (type) {
            "card" -> ComponentNode.Card(
                title = raw.optString("title").trim().takeIf { it.isNotEmpty() },
                subtitle = raw.optString("subtitle").trim().takeIf { it.isNotEmpty() },
                children = parseComponentChildren(raw.optJSONArray("children")) ?: return null,
            )

            "text" -> ComponentNode.Text(
                text = raw.optString("text"),
                markdown = raw.optBoolean("markdown", false),
            )

            "image" -> ComponentNode.Image(
                url = raw.optString("url").trim(),
                deliveryId = raw.optString("delivery_id").trim(),
                uploadId = raw.optString("upload_id").trim(),
                alt = raw.optString("alt").trim(),
                mime = raw.optString("mime").trim(),
                width = raw.optInt("width", 0),
                height = raw.optInt("height", 0),
                index = raw.optInt("index", -1),
            )

            "file" -> {
                val name = raw.optString("name").trim()
                if (name.isEmpty()) return null
                ComponentNode.File(
                    name = name,
                    url = raw.optString("url").trim(),
                    deliveryId = raw.optString("delivery_id").trim(),
                    uploadId = raw.optString("upload_id").trim(),
                    mime = raw.optString("mime").trim(),
                    size = raw.optLong("size", -1L),
                    index = raw.optInt("index", -1),
                )
            }

            "audio" -> ComponentNode.Audio(
                url = raw.optString("url").trim(),
                deliveryId = raw.optString("delivery_id").trim(),
                uploadId = raw.optString("upload_id").trim(),
                durationMs = raw.optLong("duration_ms", -1L),
                transcript = raw.optString("transcript").trim(),
                index = raw.optInt("index", -1),
            )

            "button" -> {
                val label = raw.optString("label").trim()
                if (label.isEmpty()) return null
                ComponentNode.Button(label = label, action = parseComponentAction(raw.optJSONObject("action")))
            }

            "choice" -> {
                val options = raw.optJSONArray("options") ?: JSONArray()
                val parsed = (0 until options.length()).mapNotNull { i ->
                    val o = options.optJSONObject(i) ?: return@mapNotNull null
                    val label = o.optString("label").trim()
                    if (label.isEmpty()) return@mapNotNull null
                    ChoiceOption(o.optString("id").trim(), label)
                }
                if (parsed.isEmpty()) return null
                ComponentNode.Choice(
                    name = raw.optString("name").trim(),
                    id = raw.optString("id").trim(),
                    options = parsed,
                    multiple = raw.optBoolean("multiple", false),
                    action = parseComponentAction(raw.optJSONObject("action")),
                )
            }

            "input" -> {
                val name = raw.optString("name").trim()
                ComponentNode.Input(
                    name = name.ifEmpty { raw.optString("label").trim() },
                    placeholder = raw.optString("placeholder").trim(),
                    inputType = raw.optString("input_type", "text").trim().ifEmpty { "text" },
                    label = raw.optString("label").trim(),
                    action = parseComponentAction(raw.optJSONObject("action")),
                )
            }

            "progress" -> ComponentNode.Progress(
                label = raw.optString("label").trim(),
                percent = raw.optInt("percent", -1).coerceIn(-1, 100),
                status = raw.optString("status", "idle").trim().ifEmpty { "idle" },
            )

            "list" -> ComponentNode.ListBlock(
                items = parseComponentChildren(raw.optJSONArray("items")) ?: return null,
                ordered = raw.optBoolean("ordered", false),
            )

            "divider" -> ComponentNode.Divider

            else -> null
        }
    } catch (e: Exception) {
        // 单条消息的坏数据不能拖垮整个会话；交给上层走文本兜底。
        null
    }
}

/**
 * 解析子节点。任一节点类型未知 / 结构非法时返回 null，
 * 让整条消息退回 fallback_text（与「未知类型不渲染、不崩溃」的约定一致）。
 */
private fun parseComponentChildren(array: JSONArray?): List<ComponentNode>? {
    if (array == null) return emptyList()
    val out = ArrayList<ComponentNode>(array.length())
    for (i in 0 until array.length()) {
        when (val item = array.opt(i)) {
            is JSONObject -> out.add(parseComponentNode(item) ?: return null)
            is String -> if (item.isNotBlank()) out.add(ComponentNode.Text(item))
            else -> return null
        }
    }
    return out
}

private fun parseComponentAction(raw: JSONObject?): ComponentAction? {
    if (raw == null) return null
    return ComponentAction(
        type = raw.optString("type").trim(),
        name = raw.optString("name").trim(),
        url = raw.optString("url").trim(),
        actionId = raw.optString("action_id").trim(),
        decisionId = raw.optString("decision_id").trim(),
        option = raw.optString("option").trim(),
    )
}

// ---------------------------------------------------------------- 序列化

/** 本地持久化用：把组件树写回 schema 形状（不写库，只进 SharedPreferences）。 */
fun ComponentNode.toJson(): JSONObject = when (this) {
    is ComponentNode.Card -> JSONObject().put("type", "card").apply {
        title?.let { put("title", it) }
        subtitle?.let { put("subtitle", it) }
        put("children", children.toJsonArray())
    }

    is ComponentNode.Text -> JSONObject().put("type", "text").put("text", text).apply {
        if (markdown) put("markdown", true)
    }

    is ComponentNode.Image -> JSONObject().put("type", "image").apply {
        if (url.isNotEmpty()) put("url", url)
        if (deliveryId.isNotEmpty()) put("delivery_id", deliveryId)
        if (uploadId.isNotEmpty()) put("upload_id", uploadId)
        if (alt.isNotEmpty()) put("alt", alt)
        if (mime.isNotEmpty()) put("mime", mime)
        if (width > 0) put("width", width)
        if (height > 0) put("height", height)
        if (index >= 0) put("index", index)
    }

    is ComponentNode.File -> JSONObject().put("type", "file").put("name", name).apply {
        if (url.isNotEmpty()) put("url", url)
        if (deliveryId.isNotEmpty()) put("delivery_id", deliveryId)
        if (uploadId.isNotEmpty()) put("upload_id", uploadId)
        if (mime.isNotEmpty()) put("mime", mime)
        if (size >= 0) put("size", size)
        if (index >= 0) put("index", index)
    }

    is ComponentNode.Audio -> JSONObject().put("type", "audio").apply {
        if (url.isNotEmpty()) put("url", url)
        if (deliveryId.isNotEmpty()) put("delivery_id", deliveryId)
        if (uploadId.isNotEmpty()) put("upload_id", uploadId)
        if (durationMs >= 0) put("duration_ms", durationMs)
        if (transcript.isNotEmpty()) put("transcript", transcript)
        if (index >= 0) put("index", index)
    }

    is ComponentNode.Button -> JSONObject().put("type", "button").put("label", label).apply {
        action?.let { put("action", it.toJson()) }
    }

    is ComponentNode.Choice -> JSONObject().put("type", "choice").apply {
        if (name.isNotEmpty()) put("name", name)
        if (id.isNotEmpty()) put("id", id)
        if (multiple) put("multiple", true)
        put("options", JSONArray().apply {
            options.forEach { put(JSONObject().put("id", it.id).put("label", it.label)) }
        })
        action?.let { put("action", it.toJson()) }
    }

    is ComponentNode.Input -> JSONObject().put("type", "input").put("name", name).apply {
        if (placeholder.isNotEmpty()) put("placeholder", placeholder)
        if (inputType != "text") put("input_type", inputType)
        if (label.isNotEmpty()) put("label", label)
        action?.let { put("action", it.toJson()) }
    }

    is ComponentNode.Progress -> JSONObject().put("type", "progress").apply {
        if (label.isNotEmpty()) put("label", label)
        if (percent >= 0) put("percent", percent)
        put("status", status)
    }

    is ComponentNode.ListBlock -> JSONObject().put("type", "list").apply {
        if (ordered) put("ordered", true)
        put("items", items.toJsonArray())
    }

    ComponentNode.Divider -> JSONObject().put("type", "divider")

    is ComponentNode.Unknown -> JSONObject().put("type", type)
}

private fun ComponentAction.toJson(): JSONObject = JSONObject().put("type", type).put("name", name).apply {
    if (url.isNotEmpty()) put("url", url)
    if (actionId.isNotEmpty()) put("action_id", actionId)
    if (decisionId.isNotEmpty()) put("decision_id", decisionId)
    if (option.isNotEmpty()) put("option", option)
}

private fun List<ComponentNode>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { put(it.toJson()) }
}
