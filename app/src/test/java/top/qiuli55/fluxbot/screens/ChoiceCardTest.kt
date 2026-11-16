package top.qiuli55.fluxbot.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.Message
import top.qiuli55.fluxbot.data.ChoiceOption

/** 选择卡的纯逻辑：字母徽标、回复文案、已答 / 跳过状态推导。 */
class ChoiceCardTest {

    private val choice = ComponentNode.Choice(
        name = "tasks",
        multiple = true,
        options = listOf(
            ChoiceOption("a", "IM 先回收到"),
            ChoiceOption("b", "定时任务结果发回"),
            ChoiceOption("c", "子任务完成回流"),
        ),
    )

    private fun msg(
        id: String = "m-1",
        role: String = "bot",
        payload: Map<String, String> = emptyMap(),
        component: ComponentNode? = choice,
    ) = Message(id = id, role = role, text = "问题", time = "10:00", component = component, payload = payload)

    @Test
    fun lettersContinuePastZ() {
        assertEquals("A", choiceLetter(0))
        assertEquals("Z", choiceLetter(25))
        assertEquals("AA", choiceLetter(26))
        assertEquals("AB", choiceLetter(27))
    }

    @Test
    fun multiSelectionReplyListsLettersAndLabels() {
        val text = choiceReplyText(choice, listOf("a", "c"), "")
        assertEquals("我选择以下 2 项：A. IM 先回收到；C. 子任务完成回流", text)
    }

    @Test
    fun singleSelectionReplyUsesSinglePrefix() {
        val single = choice.copy(multiple = false)
        assertEquals("我选择：B. 定时任务结果发回", choiceReplyText(single, listOf("b"), ""))
    }

    @Test
    fun customInputIsAppended() {
        val text = choiceReplyText(choice, listOf("a"), "  先做 3 和 5 ")
        assertTrue(text.startsWith("我选择以下 1 项：A. IM 先回收到"))
        assertTrue(text.endsWith("其他：先做 3 和 5"))
    }

    @Test
    fun emptySelectionProducesEmptyReply() {
        assertEquals("", choiceReplyText(choice, emptyList(), "  "))
    }

    @Test
    fun pendingWhenNoLaterUserMessage() {
        assertEquals(
            ChoiceCardState.Pending,
            choiceCardState(msg(), hasLaterUserMessage = false),
        )
    }

    @Test
    fun laterUserMessageMeansSkipped() {
        assertEquals(
            ChoiceCardState.Skipped,
            choiceCardState(msg(), hasLaterUserMessage = true),
        )
    }

    @Test
    fun recordedAnswerWinsOverLaterUserMessage() {
        val state = choiceCardState(
            msg(
                payload = mapOf(
                    "choice_state" to "answered",
                    "choice_selection" to "a,c",
                    "choice_custom" to "备注",
                ),
            ),
            hasLaterUserMessage = true,
        ) as ChoiceCardState.Answered
        assertEquals(setOf("a", "c"), state.selected)
        assertEquals("备注", state.custom)
    }
}
