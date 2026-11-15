package top.qiuli55.fluxbot.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * PATCH 回退传输的回归测试。
 *
 * Android 的 HttpURLConnection 接受 PATCH，手机上的写操作（暂停例程 / 改技能 /
 * 改页面）走系统实现；只有桌面 JVM 会抛 ProtocolException 并回退到这里。本测试
 * 用本地原始 socket 服务器验证回退实现真的发出了 PATCH，并能解析定长与 chunked
 * 响应。
 *
 * 不用 com.sun.net.httpserver，因为它不在 Android 单测的编译类路径上。
 */
class RawHttpTransportTest {

    private lateinit var server: StubHttpServer
    private val lastSeen = AtomicReference<SeenRequest?>(null)

    @Before
    fun setUp() {
        server = StubHttpServer { request ->
            lastSeen.set(request)
            when (request.path) {
                "/patch" -> StubResponse(
                    code = 200,
                    body = JSONObject()
                        .put("success", true)
                        .put("data", JSONObject().put("method", request.method))
                        .toString()
                        .toByteArray(Charsets.UTF_8),
                    headers = mapOf("Content-Type" to "application/json"),
                )
                "/chunked" -> StubResponse(
                    code = 200,
                    body = """{"success":true,"data":{"chunked":true}}""".toByteArray(Charsets.UTF_8),
                    chunked = true,
                )
                "/unauthorized" -> StubResponse(
                    code = 401,
                    body = """{"success":false,"code":"unauthenticated","message":"缺少或无效的访问令牌"}"""
                        .toByteArray(Charsets.UTF_8),
                    headers = mapOf("Content-Type" to "application/json"),
                )
                else -> StubResponse(
                    code = 404,
                    body = """{"success":false,"message":"not found"}""".toByteArray(Charsets.UTF_8),
                )
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun url(path: String): String = "http://127.0.0.1:${server.port}$path"

    @Test
    fun patchIsSentWithBodyAndAuthorization() {
        val response = RawHttpTransport().execute(
            HttpRequest(
                method = "PATCH",
                url = url("/patch"),
                body = """{"enabled":false}""",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer access-token",
                    "Content-Type" to "application/json; charset=utf-8",
                    "X-Flux-Account" to "cloud-7",
                ),
                timeoutMs = 5_000,
            ),
        )

        assertEquals(200, response.code)
        val seen = requireNotNull(lastSeen.get())
        assertEquals("PATCH", seen.method)
        assertEquals("/patch", seen.path)
        assertEquals("Bearer access-token", seen.headers["authorization"])
        assertEquals("cloud-7", seen.headers["x-flux-account"])
        assertEquals("""{"enabled":false}""", seen.body)
        assertEquals("PATCH", JSONObject(response.body).getJSONObject("data").getString("method"))
    }

    @Test
    fun chunkedResponseIsDecoded() {
        val response = RawHttpTransport().execute(
            HttpRequest(method = "PATCH", url = url("/chunked"), body = "{}", timeoutMs = 5_000),
        )

        assertEquals(200, response.code)
        assertTrue(JSONObject(response.body).getJSONObject("data").getBoolean("chunked"))
    }

    @Test
    fun errorResponseIsReturnedWithoutThrowing() {
        val response = RawHttpTransport().execute(
            HttpRequest(
                method = "PATCH",
                url = url("/unauthorized"),
                body = "{}",
                timeoutMs = 5_000,
            ),
        )

        assertEquals(401, response.code)
        assertTrue(JSONObject(response.body).getString("message").contains("访问令牌"))
    }
}

private data class SeenRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private data class StubResponse(
    val code: Int,
    val body: ByteArray,
    val chunked: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

/** 单请求单连接的极简 HTTP/1.1 服务器，够覆盖传输层解析路径。 */
private class StubHttpServer(
    private val handler: (SeenRequest) -> StubResponse,
) : AutoCloseable {

    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val thread = Thread({ acceptLoop() }, "stub-http-server").apply {
        isDaemon = true
        start()
    }

    val port: Int get() = serverSocket.localPort

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val client = try {
                serverSocket.accept()
            } catch (e: Exception) {
                return
            }
            try {
                client.use { handle(it) }
            } catch (e: Exception) {
                // 客户端提前断开不影响其它用例。
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5_000
        val input = client.getInputStream()
        val requestLine = readAsciiLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) readExactly(input, length) else ByteArray(0)
        val response = handler(
            SeenRequest(parts[0], parts[1], headers, String(body, Charsets.UTF_8)),
        )
        writeResponse(client, response)
    }

    private fun writeResponse(client: Socket, response: StubResponse) {
        val out = client.getOutputStream()
        val head = StringBuilder()
            .append("HTTP/1.1 ")
            .append(response.code)
            .append(' ')
            .append(if (response.code in 200..299) "OK" else "Error")
            .append("\r\n")
        response.headers.forEach { (name, value) ->
            head.append(name).append(": ").append(value).append("\r\n")
        }
        if (response.chunked) {
            head.append("Transfer-Encoding: chunked\r\n")
        } else {
            head.append("Content-Length: ").append(response.body.size).append("\r\n")
        }
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.US_ASCII))
        if (response.chunked) {
            val split = response.body.size / 2
            writeChunk(out, response.body.copyOfRange(0, split))
            writeChunk(out, response.body.copyOfRange(split, response.body.size))
            out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        } else {
            out.write(response.body)
        }
        out.flush()
    }

    private fun writeChunk(out: OutputStream, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        out.write(bytes.size.toString(16).toByteArray(Charsets.US_ASCII))
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) break
            read += n
        }
        return out
    }

    private fun readAsciiLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("ISO-8859-1")
    }

    override fun close() {
        serverSocket.close()
        thread.join(2_000)
    }
}
