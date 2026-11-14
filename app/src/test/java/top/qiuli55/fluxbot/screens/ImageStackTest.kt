package top.qiuli55.fluxbot.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.parseComponentEnvelope

/**
 * 多图叠卡的 JVM 单测：只验证「什么时候叠 / 几张 / 页数」这些纯逻辑，
 * 不拉起 Android 框架，也不需要手势。
 */
class ImageStackTest {

    private fun image(alt: String, index: Int) = ComponentNode.Image(
        deliveryId = "d-$index",
        alt = alt,
        mime = "image/png",
        index = index,
    )

    private fun text(value: String) = ComponentNode.Text(value)

    @Test
    fun noImageIsNotAStack() {
        assertNull(stackedImages(listOf(text("只有文字，没有图片"))))
        assertNull(stackedImages(emptyList()))
    }

    @Test
    fun singleImageStaysInline() {
        assertNull(stackedImages(listOf(text("一张图"), image("白板", 0))))
        assertNull(stackedImages(listOf(image("白板", 0))))
    }

    @Test
    fun twoOrMoreImagesBecomeStack() {
        val two = stackedImages(listOf(text("两张"), image("第一张", 0), image("第二张", 1)))
        assertNotNull(two)
        assertEquals(2, two!!.size)
        assertEquals("第二张", two[1].alt)

        val four = stackedImages(
            listOf(
                text("四张"),
                image("规则列表", 0),
                image("付款规则", 1),
                image("命令规则", 2),
                image("文本兜底", 3),
            ),
        )
        assertEquals(4, four!!.size)
    }

    @Test
    fun countLabelUsesChinese() {
        assertEquals("2 张图片", imageCountLabel(2))
        assertEquals("4 张图片", imageCountLabel(4))
    }

    @Test
    fun deckPageCountMatchesImages() {
        val deck = ImageDeckState(4)
        assertEquals(4, deck.count)
        assertEquals(0, deck.clampPage(-3))
        assertEquals(3, deck.clampPage(9))
        assertEquals(0, deck.settlePosition(0.2f))
        assertEquals(1, deck.settlePosition(0.6f))
        assertEquals(3, deck.settlePosition(2.8f))
        assertEquals(0, ImageDeckState(1).settlePosition(5f))
    }

    /** 从服务端 schema_version=1 的原始 payload 检测叠卡，不新增节点类型。 */
    @Test
    fun stackDetectedFromExistingSchema() {
        val payload = JSONObject(
            """
            {
              "schema_version": 1,
              "fallback_text": "四张截图",
              "component": {
                "type": "card",
                "children": [
                  {"type": "text", "text": "四张截图"},
                  {"type": "image", "alt": "规则列表", "index": 0},
                  {"type": "image", "alt": "付款规则", "index": 1},
                  {"type": "image", "alt": "命令规则", "index": 2},
                  {"type": "image", "alt": "文本兜底", "index": 3}
                ]
              }
            }
            """.trimIndent(),
        )
        val root = parseComponentEnvelope(payload)?.root as? ComponentNode.Card
        assertNotNull(root)
        val stack = stackedImages(root!!.children)
        assertEquals(4, stack!!.size)
        assertEquals("4 张图片", imageCountLabel(stack.size))
    }
}
