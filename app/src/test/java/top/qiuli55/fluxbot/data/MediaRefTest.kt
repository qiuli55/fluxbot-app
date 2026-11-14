package top.qiuli55.fluxbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 媒体来源与鉴权地址构造的 JVM 单测。
 *
 * 只验证纯逻辑：URL 形状、id 白名单、协议白名单与占位回退。
 * 不联网，也不会把令牌写进 URL。
 */
class MediaRefTest {

    private val base = "https://flux.qiuli55.top/bot"

    // ------------------------------------------------------------ URL 构造

    @Test
    fun uploadUrlUsesV1UploadsPath() {
        assertEquals(
            "$base/api/v1/uploads/abc123",
            MediaUrls.upload(base, "abc123"),
        )
    }

    @Test
    fun uploadUrlCanForceDownload() {
        assertEquals(
            "$base/api/v1/uploads/abc123?download=true",
            MediaUrls.upload(base, "abc123", download = true),
        )
    }

    @Test
    fun deliveryUrlUsesIndexedFilesPath() {
        assertEquals(
            "$base/api/v1/deliveries/del_9/files/2",
            MediaUrls.delivery(base, "del_9", 2),
        )
        assertEquals(
            "$base/api/v1/deliveries/del_9/files/0?download=true",
            MediaUrls.delivery(base, "del_9", 0, download = true),
        )
    }

    @Test
    fun trailingSlashOnBaseUrlIsNormalized() {
        assertEquals(
            "$base/api/v1/uploads/abc",
            MediaUrls.upload("$base///", "abc"),
        )
    }

    @Test
    fun blankOrInvalidBaseUrlGivesNoUrl() {
        assertNull(MediaUrls.upload("", "abc"))
        assertNull(MediaUrls.upload("   ", "abc"))
        assertNull(MediaUrls.upload("not a url", "abc"))
        assertNull(MediaUrls.upload("ftp://example.com", "abc"))
        assertNull(MediaUrls.delivery("ftp://example.com", "d", 0))
    }

    @Test
    fun idsAreWhitelistedAgainstTraversal() {
        listOf("../etc/passwd", "a/b", "", "x".repeat(65), "a b", "a?download=true").forEach { bad ->
            assertNull("upload id should be rejected: $bad", MediaUrls.upload(base, bad))
            assertNull("delivery id should be rejected: $bad", MediaUrls.delivery(base, bad, 0))
        }
        assertNull(MediaUrls.delivery(base, "ok", -1))
        assertNull(MediaUrls.delivery(base, "ok", 10_000))
    }

    @Test
    fun tokenIsNeverPartOfTheUrl() {
        val ref = MediaRef.Upload("abc")
        val url = ref.resolveUrl(base, download = true)!!
        assertEquals("$base/api/v1/uploads/abc?download=true", url)
        assertTrue(!url.contains("Bearer") && !url.contains("token"))
    }

    // ------------------------------------------------------------ 来源判定

    @Test
    fun uploadIdBecomesAuthenticatedRef() {
        val ref = mediaRefFor(uploadId = "upload_1")
        assertEquals(MediaRef.Upload("upload_1"), ref)
        assertTrue(ref!!.authenticated)
    }

    @Test
    fun deliveryIdKeepsIndexAndDefaultsMissingIndexToZero() {
        assertEquals(MediaRef.Delivery("d-1", 3), mediaRefFor(deliveryId = "d-1", index = 3))
        assertEquals(MediaRef.Delivery("d-1", 0), mediaRefFor(deliveryId = "d-1", index = -1))
    }

    @Test
    fun httpsUrlIsExternalAndNeedsNoToken() {
        val ref = mediaRefFor(url = "https://cdn.example.com/a.png")
        assertEquals(MediaRef.Remote("https://cdn.example.com/a.png"), ref)
        assertEquals(false, ref!!.authenticated)
        assertEquals(
            "https://cdn.example.com/a.png",
            ref.resolveUrl(base, download = true),
        )
    }

    @Test
    fun unsafeSchemesAreRejected() {
        listOf(
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "file:///etc/passwd",
            "http://example.com/a.png",
            "https://user:pass@example.com/a.png",
            "https:///no-host",
        ).forEach { bad ->
            assertNull("should reject: $bad", mediaRefFor(url = bad))
        }
    }

    @Test
    fun invalidIdDoesNotFallThroughToAnotherSource() {
        assertNull(mediaRefFor(uploadId = "../escape"))
        assertNull(mediaRefFor(deliveryId = "a/b"))
    }

    @Test
    fun noSourceMeansPlaceholder() {
        assertNull(mediaRefFor())
        assertNull(mediaRefFor(url = " ", deliveryId = " ", uploadId = " ", index = 0))
    }

    @Test
    fun nodeHelpersPickTheSameSource() {
        val image = ComponentNode.Image(uploadId = "u-1", alt = "截图")
        assertEquals(MediaRef.Upload("u-1"), image.mediaRef())

        val file = ComponentNode.File(name = "a.pdf", deliveryId = "d-1", index = 1)
        assertEquals(MediaRef.Delivery("d-1", 1), file.mediaRef())

        val audio = ComponentNode.Audio(url = "https://example.com/a.mp3")
        assertEquals(MediaRef.Remote("https://example.com/a.mp3"), audio.mediaRef())
    }
}
