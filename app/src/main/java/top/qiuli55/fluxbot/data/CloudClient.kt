package top.qiuli55.fluxbot.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * Flux 账号云的 HTTP 客户端。
 *
 * 只覆盖原生客户端需要的入口：密码登录、注册、刷新、/me、/providers 与退出。
 * 不做 Cookie 管理；原生客户端用响应体里的 access / refresh token。
 */
class CloudClient(
    private val transport: HttpTransport,
    private val timeoutMs: Int = 30_000,
    private val baseUrl: () -> String,
) {

    suspend fun providers(): CloudProviders = withContext(Dispatchers.IO) {
        parseCloudProviders(call("GET", "/api/auth/providers", null, null))
    }

    suspend fun login(identifier: String, password: String): CloudTokens =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("identifier", identifier.trim())
                .put("password", password)
            parseCloudTokens(call("POST", "/api/auth/login/token", body, null))
        }

    /**
     * 注册成功后服务端只下发 Cookie，不返回令牌；调用方必须紧接着用同一组
     * 凭据再调 [login]，这也是原生客户端的既定流程。
     */
    suspend fun register(
        email: String,
        password: String,
        username: String? = null,
        displayName: String? = null,
    ): CloudMe = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("email", email.trim().lowercase())
            .put("password", password)
        username?.takeIf { it.isNotBlank() }?.let { body.put("username", it.trim()) }
        displayName?.takeIf { it.isNotBlank() }?.let { body.put("display_name", it.trim()) }
        parseCloudMe(call("POST", "/api/auth/register", body, null))
    }

    /** 用 refresh token 换新的 access token；服务端保持同一个 refresh token。 */
    suspend fun refresh(refreshToken: String): CloudTokens = withContext(Dispatchers.IO) {
        val body = JSONObject().put("refresh_token", refreshToken)
        parseCloudTokens(call("POST", "/api/auth/token", body, null))
    }

    suspend fun me(accessToken: String): CloudMe = withContext(Dispatchers.IO) {
        parseCloudMe(call("GET", "/api/auth/me", null, accessToken))
    }

    /**
     * 服务端退出只识别 Cookie，没有可在请求体里吊销 refresh token 的入口。
     * 原生客户端清理本地令牌后尽力调用一次，失败不影响本地退出。
     */
    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        call("POST", "/api/auth/logout", JSONObject(), null)
        Unit
    }

    private fun call(
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String?,
    ): JSONObject {
        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = "application/json"
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        if (!bearer.isNullOrBlank()) headers["Authorization"] = "Bearer $bearer"

        val response = try {
            transport.execute(
                HttpRequest(
                    method = method,
                    url = baseUrl().trimEnd('/') + path,
                    body = body?.toString(),
                    headers = headers,
                    timeoutMs = timeoutMs,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("无法连接账号服务器，请检查网络或服务器地址", 0, "network")
        }

        val root = if (response.body.isBlank()) {
            JSONObject()
        } else {
            runCatching { JSONObject(response.body) }.getOrElse {
                throw ApiException("账号服务器返回异常（HTTP ${response.code}）", response.code)
            }
        }
        if (response.code !in 200..299) {
            val detail = cloudErrorText(root).ifBlank { defaultErrorMessage(response.code) }
            throw ApiException(detail, response.code, errorCode(response.code))
        }
        return root
    }

    private fun defaultErrorMessage(status: Int): String = when (status) {
        401 -> "账号或密码不正确"
        403 -> "当前操作不可用"
        404 -> "账号服务器地址不正确"
        409 -> "该账号已存在"
        429 -> "尝试过于频繁，请稍后再试"
        in 500..599 -> "账号服务器暂时不可用，请稍后重试"
        else -> "请求失败（HTTP $status）"
    }

    private fun errorCode(status: Int): String = when (status) {
        401 -> "unauthorized"
        403 -> "forbidden"
        409 -> "conflict"
        429 -> "rate_limited"
        else -> ""
    }

    companion object {
        /** 默认账号服务器；与 flux-bot 后端地址（默认 .../bot）互相独立。 */
        const val DEFAULT_BASE_URL = "https://flux.qiuli55.top"
    }
}
