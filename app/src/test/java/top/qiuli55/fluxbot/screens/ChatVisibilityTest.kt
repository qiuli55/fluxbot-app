package top.qiuli55.fluxbot.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.qiuli55.fluxbot.data.Message

/** 对话可见性与 Bot 详情分区顺序的纯 JVM 测试。 */
class ChatVisibilityTest {

    private fun message(id: String, kind: String) = Message(
        id = id,
        role = "bot",
        text = "text-$id",
        time = "09:40",
        kind = kind,
    )

    @Test
    fun subtaskKindIsHidden() {
        assertTrue(isHiddenMessageKind("subtask"))
        assertFalse(isHiddenMessageKind(""))
        assertFalse(isHiddenMessageKind("routine_created"))
        assertFalse(isHiddenMessageKind("decision_result"))
    }

    @Test
    fun visibleMessagesDropOnlySubtasks() {
        val messages = listOf(
            message("m-1", ""),
            message("m-2", "subtask"),
            message("m-3", "routine_created"),
            message("m-4", "subtask"),
            message("m-5", "decision_result"),
        )

        assertEquals(
            listOf("m-1", "m-3", "m-5"),
            visibleMessages(messages).map { it.id },
        )
    }

    @Test
    fun visibleMessagesPreserveOrderAndHandleEmptyList() {
        val messages = listOf(
            message("a", ""),
            message("b", ""),
            message("c", ""),
        )

        assertEquals(listOf("a", "b", "c"), visibleMessages(messages).map { it.id })
        assertEquals(emptyList<Message>(), visibleMessages(emptyList()))
    }

    @Test
    fun botTabsAreMemoryRoutinesSettings() {
        assertEquals(listOf("记忆", "例程", "设置"), BOT_TABS)
        assertFalse(BOT_TABS.any { it == "子任务" })
    }
}
