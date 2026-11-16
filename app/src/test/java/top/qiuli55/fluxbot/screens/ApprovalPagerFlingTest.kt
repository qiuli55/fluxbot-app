package top.qiuli55.fluxbot.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审批卡翻页手感：阈值要比 HorizontalPager 默认的 0.5 更灵敏，
 * 否则约 300ms / 600px 的快速轻扫会留在原页；但也不能低到误翻。
 */
class ApprovalPagerFlingTest {

    @Test
    fun quickFlingTurnsPageWithoutBeingTooSensitive() {
        assertTrue(
            "阈值应比默认 0.5 更灵敏，300ms / 600px 轻扫才翻得动",
            approvalPagerSnapThreshold < 0.5f,
        )
        assertTrue(
            "阈值过低会在轻微滑动时误翻页",
            approvalPagerSnapThreshold >= 0.2f,
        )
        assertTrue(
            "阈值过高对快速轻扫仍不够灵敏",
            approvalPagerSnapThreshold <= 0.3f,
        )
    }

    @Test
    fun thresholdIsQuarterPage() {
        assertEquals(0.25f, approvalPagerSnapThreshold, 0.0001f)
    }
}
