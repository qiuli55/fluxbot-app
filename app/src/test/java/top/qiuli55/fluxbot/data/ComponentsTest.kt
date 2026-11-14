package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 生成式组件解析：白名单、版本兜底、动作与序列化。 */
class ComponentsTest {

    private fun envelope(component: JSONObject, version: Int = 1, fallback: String = "兜底文本") =
        JSONObject()
            .put("schema_version", version)
            .put("component", component)
            .put("fallback_text", fallback)

    @Test
    fun parsesAllWhitelistedNodeTypes() {
        val children = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "**粗体** 与 `code`").put("markdown", true))
            .put(JSONObject().put("type", "image").put("delivery_id", "d-1").put("alt", "白板"))
            .put(
                JSONObject().put("type", "file").put("name", "明细.xlsx")
                    .put("delivery_id", "d-2").put("mime", "application/vnd.ms-excel").put("size", 131072),
            )
            .put(
                JSONObject().put("type", "audio").put("delivery_id", "d-3")
                    .put("duration_ms", 12000).put("transcript", "记得发我"),
            )
            .put(
                JSONObject().put("type", "button").put("label", "接管")
                    .put("action", JSONObject().put("type", "client").put("name", "open_desktop")),
            )
            .put(
                JSONObject().put("type", "choice")
                    .put("options", JSONArray().put(JSONObject().put("id", "a").put("label", "早班"))),
            )
            .put(
                JSONObject().put("type", "input").put("name", "code")
                    .put("input_type", "secure").put("label", "验证码"),
            )
            .put(JSONObject().put("type", "progress").put("percent", 62).put("status", "running"))
            .put(
                JSONObject().put("type", "list").put("ordered", true)
                    .put("items", JSONArray().put("第一步")),
            )
            .put(JSONObject().put("type", "divider"))

        val parsed = parseComponentEnvelope(
            envelope(
                JSONObject().put("type", "card").put("title", "标题").put("children", children),
            ),
        )
        assertNotNull(parsed)
        val card = parsed!!.root as ComponentNode.Card
        assertEquals("标题", card.title)
        assertEquals(10, card.children.size)

        val text = card.children[0] as ComponentNode.Text
        assertTrue(text.markdown)
        assertEquals("白板", (card.children[1] as ComponentNode.Image).alt)
        assertEquals(131072L, (card.children[2] as ComponentNode.File).size)
        assertEquals(12000L, (card.children[3] as ComponentNode.Audio).durationMs)
        val button = card.children[4] as ComponentNode.Button
        assertEquals("open_desktop", button.action?.name)
        assertEquals("client", button.action?.type)
        assertEquals("早班", (card.children[5] as ComponentNode.Choice).options.single().label)
        assertEquals("secure", (card.children[6] as ComponentNode.Input).inputType)
        assertEquals(62, (card.children[7] as ComponentNode.Progress).percent)
        // list 里的字符串会变成 Text 节点
        val list = card.children[8] as ComponentNode.ListBlock
        assertTrue(list.ordered)
        assertEquals("第一步", (list.items.single() as ComponentNode.Text).text)
        assertEquals(ComponentNode.Divider, card.children[9])
    }

    @Test
    fun parsesUploadIdOnMediaNodes() {
        val parsed = parseComponentEnvelope(
            envelope(
                JSONObject().put("type", "card").put(
                    "children",
                    JSONArray()
                        .put(JSONObject().put("type", "image").put("upload_id", "img_1").put("alt", "截图"))
                        .put(
                            JSONObject().put("type", "file").put("name", "报告.pdf")
                                .put("upload_id", "file_1").put("mime", "application/pdf").put("size", 10),
                        )
                        .put(
                            JSONObject().put("type", "audio").put("upload_id", "voice_1")
                                .put("duration_ms", 1200),
                        ),
                ),
            ),
        )!!
        val card = parsed.root as ComponentNode.Card
        assertEquals("img_1", (card.children[0] as ComponentNode.Image).uploadId)
        assertEquals("file_1", (card.children[1] as ComponentNode.File).uploadId)
        assertEquals("voice_1", (card.children[2] as ComponentNode.Audio).uploadId)
    }

    @Test
    fun uploadIdSurvivesJsonRoundTrip() {
        val image = ComponentNode.Image(uploadId = "img_1", alt = "截图")
        val file = ComponentNode.File(name = "a.pdf", uploadId = "file_1")
        val audio = ComponentNode.Audio(uploadId = "voice_1")

        assertEquals("img_1", (parseComponentNode(image.toJson()) as ComponentNode.Image).uploadId)
        assertEquals("file_1", (parseComponentNode(file.toJson()) as ComponentNode.File).uploadId)
        assertEquals("voice_1", (parseComponentNode(audio.toJson()) as ComponentNode.Audio).uploadId)
    }

    @Test
    fun statusAliasNormalizesToProgress() {
        val parsed = parseComponentEnvelope(
            envelope(
                JSONObject().put("type", "status").put("label", "运行中")
                    .put("percent", 40).put("status", "running"),
            ),
        )
        val node = parsed!!.root as ComponentNode.Progress
        assertEquals("运行中", node.label)
        assertEquals(40, node.percent)
        assertEquals("running", node.status)
    }

    @Test
    fun unknownSchemaVersionFallsBackToText() {
        val parsed = parseComponentEnvelope(
            envelope(JSONObject().put("type", "card"), version = 2, fallback = "新版本卡片"),
        )!!
        assertEquals(2, parsed.schemaVersion)
        assertNull(parsed.root)
        assertEquals("新版本卡片", parsed.fallbackText)
    }

    @Test
    fun unknownRootTypeFallsBackToText() {
        val parsed = parseComponentEnvelope(
            envelope(JSONObject().put("type", "chart"), fallback = "暂不支持图表"),
        )!!
        assertNull(parsed.root)
        assertEquals("暂不支持图表", parsed.fallbackText)
    }

    @Test
    fun unknownNestedNodeFallsBackTheWholeMessage() {
        val card = JSONObject().put("type", "card").put(
            "children",
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", "正常"))
                .put(JSONObject().put("type", "chart")),
        )
        val parsed = parseComponentEnvelope(envelope(card, fallback = "整条兜底"))!!
        assertNull(parsed.root)
        assertEquals("整条兜底", parsed.fallbackText)
    }

    @Test
    fun missingPayloadAndMissingComponentAreSafe() {
        assertNull(parseComponentEnvelope(null))
        val parsed = parseComponentEnvelope(
            JSONObject().put("schema_version", 1).put("fallback_text", "只有兜底"),
        )!!
        assertNull(parsed.root)
        assertEquals("只有兜底", parsed.fallbackText)
    }

    @Test
    fun serverActionKeepsDecisionFields() {
        val node = JSONObject().put("type", "button").put("label", "允许").put(
            "action",
            JSONObject().put("type", "server").put("name", "decision.choose")
                .put("action_id", "decision:42:0").put("decision_id", "42").put("option", "允许"),
        )
        val button = parseComponentEnvelope(envelope(node))!!.root as ComponentNode.Button
        assertEquals("decision.choose", button.action?.name)
        assertEquals("42", button.action?.decisionId)
        assertEquals("允许", button.action?.option)
        assertEquals("decision:42:0", button.action?.actionId)
    }

    @Test
    fun toJsonRoundTripsCards() {
        val original = ComponentNode.Card(
            title = "标题",
            children = listOf(
                ComponentNode.Text("正文", markdown = true),
                ComponentNode.File(name = "a.pdf", deliveryId = "d-1", mime = "application/pdf", size = 2048),
                ComponentNode.Button("接管", ComponentAction(type = "client", name = "open_desktop")),
            ),
        )
        val reparsed = parseComponentNode(original.toJson()) as ComponentNode.Card
        assertEquals("标题", reparsed.title)
        assertEquals(3, reparsed.children.size)
        assertEquals("a.pdf", (reparsed.children[1] as ComponentNode.File).name)
        assertEquals("open_desktop", (reparsed.children[2] as ComponentNode.Button).action?.name)
    }

    // ---------------------------------------------------------- 会话消息

    @Test
    fun legacyKindWithComponentIsParsedInsteadOfDropped() {
        val message = JSONObject()
            .put("role", "assistant")
            .put("kind", "file_card")
            .put("content", "已为你准备好文件")
            .put(
                "payload",
                envelope(
                    JSONObject().put("type", "card").put("title", "已为你准备好文件").put(
                        "children",
                        JSONArray().put(JSONObject().put("type", "file").put("name", "明细.xlsx").put("delivery_id", "d")),
                    ),
                    fallback = "已为你准备好文件",
                ).put("files", JSONArray()),
            )
        val parsed = parseRemoteMessage(message)!!
        assertEquals("file_card", parsed.kind)
        assertNotNull(parsed.component)
        assertEquals("已为你准备好文件", parsed.content)
    }

    @Test
    fun plainTextMessageStaysText() {
        val parsed = parseRemoteMessage(
            JSONObject().put("role", "user").put("kind", "text").put("content", "早上好"),
        )!!
        assertEquals("user", parsed.role)
        assertEquals("早上好", parsed.content)
        assertNull(parsed.component)
    }

    @Test
    fun payloadAsJsonStringIsAccepted() {
        val payload = envelope(
            JSONObject().put("type", "text").put("text", "来自字符串 payload"),
            fallback = "来自字符串 payload",
        ).toString()
        val parsed = parseRemoteMessage(
            JSONObject().put("role", "assistant").put("kind", "component")
                .put("content", "来自字符串 payload").put("payload", payload),
        )!!
        assertEquals("来自字符串 payload", (parsed.component as ComponentNode.Text).text)
    }

    @Test
    fun unknownComponentVersionOnRemoteMessageUsesFallback() {
        val parsed = parseRemoteMessage(
            JSONObject().put("role", "assistant").put("kind", "component").put("content", "")
                .put(
                    "payload",
                    envelope(JSONObject().put("type", "card"), version = 9, fallback = "请升级 App"),
                ),
        )!!
        assertNull(parsed.component)
        assertEquals("请升级 App", parsed.fallbackText)
        assertEquals("请升级 App", parsed.displayText())
    }
}
