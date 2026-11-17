package top.qiuli55.fluxbot.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 例程的「结果通知」策略：origin（默认） / none。 */
class RoutineNotifyTest {

    @Test
    fun parsesNotifyFromRoutineDetail() {
        val routine = parseRoutineDetail(
            JSONObject()
                .put("id", "rt-1")
                .put("name", "每日汇总")
                .put("notify", "none"),
        )
        assertNotNull(routine)
        assertEquals(ROUTINE_NOTIFY_NONE, routine!!.notify)
        assertEquals("不发", routineNotifyLabel(routine.notify))
    }

    @Test
    fun missingNotifyDefaultsToOrigin() {
        val routine = parseRoutineDetail(
            JSONObject().put("id", "rt-2").put("name", "每日汇总"),
        )!!
        assertEquals(ROUTINE_NOTIFY_ORIGIN, routine.notify)
        assertEquals("发回原处", routineNotifyLabel(routine.notify))
    }

    @Test
    fun unknownNotifyFallsBackToOrigin() {
        val routine = parseRoutineDetail(
            JSONObject().put("id", "rt-3").put("notify", "somewhere-else"),
        )!!
        assertEquals(ROUTINE_NOTIFY_ORIGIN, routine.notify)
    }

    @Test
    fun normalizesCaseAndWhitespace() {
        assertEquals(ROUTINE_NOTIFY_NONE, normalizeRoutineNotify(" NONE "))
        assertEquals(ROUTINE_NOTIFY_ORIGIN, normalizeRoutineNotify(""))
        assertEquals(ROUTINE_NOTIFY_ORIGIN, normalizeRoutineNotify("origin"))
        assertEquals("不发", routineNotifyLabel("none"))
        assertEquals("发回原处", routineNotifyLabel(""))
    }
}
