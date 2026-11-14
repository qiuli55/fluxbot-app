package top.qiuli55.fluxbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图片采样与缓存文件名的纯逻辑单测；不加载 Bitmap、不联网。 */
class MediaLoaderTest {

    @Test
    fun sampleSizeOnlyShrinksOversizedImages() {
        assertEquals(1, sampleSizeFor(800, 600))
        assertEquals(1, sampleSizeFor(1600, 1200))
        assertEquals(2, sampleSizeFor(3200, 2400))
        assertEquals(1, sampleSizeFor(1601, 900))
        assertEquals(4, sampleSizeFor(6400, 4800))
        assertEquals(1, sampleSizeFor(0, 4800))
        assertEquals(1, sampleSizeFor(3200, 2400, maxDim = 0))
    }

    @Test
    fun sanitizeFileNameDropsPathsAndUnsafeChars() {
        assertEquals("passwd", sanitizeFileName("../../etc/passwd"))
        assertEquals("报告_2026.PDF", sanitizeFileName("报告 2026.PDF"))
        assertEquals("a_b-c.txt", sanitizeFileName("a:b-c.txt"))
        assertEquals("attachment", sanitizeFileName("   "))
        assertEquals("attachment", sanitizeFileName("/"))
    }

    @Test
    fun sanitizeFileNameKeepsExtensionAndLimitsLength() {
        val long = "x".repeat(200) + ".pdf"
        val safe = sanitizeFileName(long)
        assertTrue(safe.length <= 80)
        assertTrue(safe.endsWith(".pdf") || safe.none { it == ' ' })
    }

    @Test
    fun cacheNameIsStableAndDoesNotContainToken() {
        val url = "https://flux.qiuli55.top/bot/api/v1/uploads/abc?download=true"
        val first = mediaFileName(url, "../../etc/passwd")
        assertEquals(first, mediaFileName(url, "../../etc/passwd"))
        assertTrue(first.endsWith("-passwd"))
        assertTrue(!first.contains("Bearer") && !first.contains("token"))
    }

    @Test
    fun differentUrlsDoNotCollideForTheSameName() {
        assertNotEquals(
            mediaFileName("https://a.example/api/v1/uploads/1", "a.pdf"),
            mediaFileName("https://a.example/api/v1/uploads/2", "a.pdf"),
        )
    }
}
