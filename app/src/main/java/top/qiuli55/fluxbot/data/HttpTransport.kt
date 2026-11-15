package top.qiuli55.fluxbot.data

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 账号服务的最小 HTTP 抽象。
 *
 * 单独抽出来是为了让登录 / 刷新 / 401 重试这些逻辑可以在 JVM 单测里
 * 用假传输层覆盖，不需要模拟 HttpURLConnection，也不依赖 Android。
 */
data class HttpRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Int = 30_000,
    val connectTimeoutMs: Int = 15_000,
)

data class HttpResponse(
    val code: Int,
    val body: String,
)

interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

/** 生产实现：HttpURLConnection，与 [Api] 使用同一套网络能力，不引入新依赖。 */
class UrlConnectionHttpTransport : HttpTransport {

    override fun execute(request: HttpRequest): HttpResponse {
        val conn = URL(request.url).openConnection() as HttpURLConnection
        try {
            // Android 接受 PATCH；桌面 JDK 会在这里抛 ProtocolException，交给调用方决定是否回退。
            conn.requestMethod = request.method
            conn.connectTimeout = request.connectTimeoutMs
            conn.readTimeout = request.timeoutMs
            conn.instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            if (request.body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            return HttpResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * PATCH 的传输选择：优先系统 HttpURLConnection，仅在 JVM 拒绝时回退。
 *
 * Android 的 HttpURLConnection 接受 PATCH，所以手机上始终走系统实现，代理、
 * TLS 与重定向都由系统处理。桌面 JDK 的 setRequestMethod("PATCH") 会抛
 * [ProtocolException]，只有这种异常才改用 [RawHttpTransport]；回退日志只记
 * 一次，避免每次 PATCH 都刷屏。
 */
class PatchFallbackHttpTransport(
    private val primary: HttpTransport = UrlConnectionHttpTransport(),
    private val fallback: HttpTransport = RawHttpTransport(),
    private val logger: (String) -> Unit = {},
) : HttpTransport {

    private val fallbackLogged = AtomicBoolean(false)

    override fun execute(request: HttpRequest): HttpResponse =
        try {
            primary.execute(request)
        } catch (e: ProtocolException) {
            if (fallbackLogged.compareAndSet(false, true)) {
                logger("PATCH rejected by HttpURLConnection (${e.message}); raw socket fallback")
            }
            fallback.execute(request)
        }
}

/**
 * 给 [PatchFallbackHttpTransport] 使用的原始 socket 回退。
 *
 * Android 的 HttpURLConnection 本身接受 PATCH，手机上不会走到这里；只有桌面
 * JDK 在 setRequestMethod 阶段抛出 ProtocolException 时才用一次性 socket
 * 请求补上：HTTP/1.1、Connection: close，按 Content-Length 或 chunked 读取响应。
 *
 * 只处理没有重定向的普通 JSON 接口；HTTPS 走默认信任库并开启主机名校验。
 */
class RawHttpTransport : HttpTransport {

    override fun execute(request: HttpRequest): HttpResponse {
        val uri = URI(request.url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IOException("不支持的协议：$scheme")
        }
        val host = uri.host ?: throw IOException("URL 缺少主机名")
        val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
        val target = buildString {
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
        val socket = openSocket(scheme, host, port, request.connectTimeoutMs)
        socket.use { conn ->
            conn.soTimeout = request.timeoutMs
            val bodyBytes = request.body?.toByteArray(Charsets.UTF_8)
            val headers = LinkedHashMap<String, String>()
            val defaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
            headers["Host"] = if (defaultPort) host else "$host:$port"
            headers.putAll(request.headers)
            headers["Content-Length"] = (bodyBytes?.size ?: 0).toString()
            headers["Connection"] = "close"

            val head = StringBuilder()
                .append(request.method.uppercase())
                .append(' ')
                .append(target)
                .append(" HTTP/1.1\r\n")
            headers.forEach { (name, value) -> head.append(name).append(": ").append(value).append("\r\n") }
            head.append("\r\n")

            val out = conn.getOutputStream()
            out.write(head.toString().toByteArray(Charsets.US_ASCII))
            bodyBytes?.let(out::write)
            out.flush()
            return readResponse(BufferedInputStream(conn.getInputStream()))
        }
    }

    private fun openSocket(scheme: String, host: String, port: Int, timeoutMs: Int): Socket =
        if (scheme == "https") {
            val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.sslParameters = socket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            socket.startHandshake()
            socket
        } else {
            Socket().apply { connect(InetSocketAddress(host, port), timeoutMs) }
        }

    private fun readResponse(input: BufferedInputStream): HttpResponse {
        val statusLine = readAsciiLine(input) ?: throw IOException("服务器没有返回状态行")
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("无法解析状态行：$statusLine")
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
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true ->
                readChunked(input)
            headers["content-length"]?.toIntOrNull() != null ->
                readExactly(input, headers.getValue("content-length").toInt())
            else -> input.readBytes()
        }
        return HttpResponse(code, String(body, Charsets.UTF_8))
    }

    private fun readChunked(input: BufferedInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input) ?: break
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) {
                // 吃掉 trailer 直到空行。
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                }
                break
            }
            out.write(readExactly(input, size))
            readAsciiLine(input)
        }
        return out.toByteArray()
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) throw IOException("响应体提前结束")
            read += n
        }
        return out
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("ISO-8859-1")
    }
}
