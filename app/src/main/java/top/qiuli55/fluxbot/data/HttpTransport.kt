package top.qiuli55.fluxbot.data

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

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
        val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.timeoutMs
            instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
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
