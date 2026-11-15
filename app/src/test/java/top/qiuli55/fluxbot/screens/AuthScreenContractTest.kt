package top.qiuli55.fluxbot.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 登录页契约：演示账户入口已经删除，GitHub / Google 入口必须存在。
 *
 * Paparazzi 截图只能证明画面变了，不能证明某个入口「不存在」；这里直接检查
 * 页面源码，避免按钮被重新加回来却没人发现。
 */
class AuthScreenContractTest {

    private fun source(relative: String): String {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative"),
            File("../app/$relative"),
        )
        val found = candidates.firstOrNull { it.isFile }
            ?: error("找不到源文件：$relative")
        return found.readText()
    }

    @Test
    fun loginPageHasGitHubAndGoogleEntries() {
        val auth = source("src/main/java/top/qiuli55/fluxbot/screens/AuthScreen.kt")

        assertTrue(auth.contains("GitHub 登录"))
        assertTrue(auth.contains("Google 登录"))
        assertTrue(auth.contains("onOAuth"))
        assertTrue(auth.contains("暂不可用"))
    }

    @Test
    fun loginPageNoLongerOffersDemoAccount() {
        val auth = source("src/main/java/top/qiuli55/fluxbot/screens/AuthScreen.kt")

        assertFalse("登录页不应再出现演示账户入口", auth.contains("使用演示账户"))
        assertFalse("AuthContent 不应再接收 onDemo", auth.contains("onDemo"))
        assertFalse("登录页不应再打开 Sheet.Login", auth.contains("Sheet.Login"))
    }

    @Test
    fun loginSheetWiringIsRemoved() {
        val sheets = source("src/main/java/top/qiuli55/fluxbot/nav/Sheets.kt")

        assertFalse(sheets.contains("data object Login"))
        assertFalse(sheets.contains("LoginSheet"))
        assertFalse(sheets.contains("Sheet.Login"))
        // 账户页的添加账户能力不属于登录页快捷入口，必须保留。
        assertTrue(sheets.contains("AddAccountSheet"))
    }

    @Test
    fun docsNoLongerTellTestersToUseLoginPageDemoEntry() {
        val docs = source("docs/v0.4-new-pages.md")

        assertFalse(docs.contains("演示账户登录"))
        assertTrue(docs.contains("GitHub"))
        assertTrue(docs.contains("Google"))
    }
}
