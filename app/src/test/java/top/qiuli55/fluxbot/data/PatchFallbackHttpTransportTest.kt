package top.qiuli55.fluxbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ProtocolException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * PATCH 传输选择：系统 HttpURLConnection 优先，只有 JVM 拒绝 PATCH
 * （ProtocolException）时才回退到原始 socket。
 */
class PatchFallbackHttpTransportTest {

    private val patch = HttpRequest(method = "PATCH", url = "http://127.0.0.1:1/routine", body = "{}")

    @Test
    fun acceptingTransportIsUsedDirectlyWithoutFallback() {
        val primaryCalls = AtomicInteger()
        val fallbackCalls = AtomicInteger()
        val logs = mutableListOf<String>()
        val seenMethod = AtomicReference<String?>(null)

        val transport = PatchFallbackHttpTransport(
            primary = fakeTransport { request ->
                primaryCalls.incrementAndGet()
                seenMethod.set(request.method)
                HttpResponse(200, """{"success":true}""")
            },
            fallback = fakeTransport {
                fallbackCalls.incrementAndGet()
                HttpResponse(500, """{"success":false}""")
            },
            logger = { logs += it },
        )

        val response = transport.execute(patch)

        assertEquals(200, response.code)
        assertEquals("PATCH", seenMethod.get())
        assertEquals(1, primaryCalls.get())
        assertEquals(0, fallbackCalls.get())
        assertTrue(logs.isEmpty())
    }

    @Test
    fun protocolExceptionFallsBackAndLogsOnlyOnce() {
        val primaryCalls = AtomicInteger()
        val fallbackCalls = AtomicInteger()
        val logs = mutableListOf<String>()

        val transport = PatchFallbackHttpTransport(
            primary = fakeTransport {
                primaryCalls.incrementAndGet()
                throw ProtocolException("Invalid HTTP method: PATCH")
            },
            fallback = fakeTransport {
                fallbackCalls.incrementAndGet()
                HttpResponse(200, """{"success":true}""")
            },
            logger = { logs += it },
        )

        repeat(3) { assertEquals(200, transport.execute(patch).code) }

        assertEquals(3, primaryCalls.get())
        assertEquals(3, fallbackCalls.get())
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("PATCH"))
    }

    @Test
    fun otherIoExceptionsAreNotRetriedOnFallback() {
        val fallbackCalls = AtomicInteger()
        val logs = mutableListOf<String>()

        val transport = PatchFallbackHttpTransport(
            primary = fakeTransport { throw IOException("connection reset") },
            fallback = fakeTransport {
                fallbackCalls.incrementAndGet()
                HttpResponse(200, """{"success":true}""")
            },
            logger = { logs += it },
        )

        try {
            transport.execute(patch)
            fail("IOException should propagate")
        } catch (expected: IOException) {
            assertEquals("connection reset", expected.message)
        }

        assertEquals(0, fallbackCalls.get())
        assertTrue(logs.isEmpty())
    }

    private fun fakeTransport(block: (HttpRequest) -> HttpResponse): HttpTransport =
        object : HttpTransport {
            override fun execute(request: HttpRequest): HttpResponse = block(request)
        }
}
