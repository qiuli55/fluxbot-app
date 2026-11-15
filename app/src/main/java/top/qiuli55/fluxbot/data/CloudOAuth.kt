package top.qiuli55.fluxbot.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Flux Cloud 的第三方登录（GitHub / Google）协议常量与纯函数。
 *
 * 登录全程使用 PKCE（RFC 7636，仅 S256）：App 只把 challenge 交给账号云，
 * verifier 留在本机加密存储里，回跳时再出示。这样即使自定义 scheme 的
 * 登录码被别的 App 截获，没有 verifier 也无法兑换令牌。
 */

enum class OAuthProvider(val id: String, val label: String) {
    GitHub("github", "GitHub"),
    Google("google", "Google");

    companion object {
        fun fromId(value: String?): OAuthProvider? =
            entries.firstOrNull { it.id.equals(value.orEmpty().trim(), ignoreCase = true) }
    }
}

/** 与账号云 FLUX_CLOUD_MOBILE_REDIRECTS 中的第一条严格一致。 */
internal const val OAUTH_REDIRECT_URI = "flux://auth"

internal const val OAUTH_PROVIDER_PATH = "/api/auth/oauth"

object OAuthMessages {
    const val CANCELLED = "已取消登录"
    const val INCOMPLETE = "登录未完成，请重试"
    const val INVALID_CALLBACK = "登录回调无效，请重试"
    const val INVALID_CODE = "登录码无效或已过期，请重新登录"
    const val ACCOUNT_UNAVAILABLE = "账号不可用"
    const val RATE_LIMITED = "尝试过于频繁，请稍后再试"
    const val SERVER_UNAVAILABLE = "账号服务器暂时不可用，请稍后重试"
    const val NETWORK = "无法连接账号服务器，请检查网络或服务器地址"
}

/** 登录页展示用的 OAuth 状态；由 Store 推进，AuthContent 只读。 */
sealed interface OAuthState {
    data object Idle : OAuthState
    data class Starting(val provider: OAuthProvider) : OAuthState
    data class Completing(val provider: OAuthProvider?) : OAuthState
    data class Failed(val provider: OAuthProvider?, val message: String) : OAuthState
}

/** 浏览器打开能力：生产实现走系统 Custom Tabs，JVM 单测注入假实现。 */
interface OAuthBrowser {
    fun open(url: String)
}

private const val VERIFIER_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

internal const val OAUTH_VERIFIER_MIN_LENGTH = 43
internal const val OAUTH_VERIFIER_MAX_LENGTH = 128
private const val DEFAULT_VERIFIER_LENGTH = 64

private val VERIFIER_REGEX = Regex("^[A-Za-z0-9\\-._~]{$OAUTH_VERIFIER_MIN_LENGTH,$OAUTH_VERIFIER_MAX_LENGTH}$")

/** 生成 [A-Za-z0-9-._~] 组成的 verifier；默认 64 位，落在 RFC 要求的 43-128 之间。 */
fun generateCodeVerifier(
    random: SecureRandom = SecureRandom(),
    length: Int = DEFAULT_VERIFIER_LENGTH,
): String {
    require(length in OAUTH_VERIFIER_MIN_LENGTH..OAUTH_VERIFIER_MAX_LENGTH) {
        "code_verifier 长度必须在 $OAUTH_VERIFIER_MIN_LENGTH-$OAUTH_VERIFIER_MAX_LENGTH 之间"
    }
    val builder = StringBuilder(length)
    repeat(length) {
        builder.append(VERIFIER_ALPHABET[random.nextInt(VERIFIER_ALPHABET.length)])
    }
    return builder.toString()
}

internal fun isValidCodeVerifier(value: String): Boolean = VERIFIER_REGEX.matches(value)

/** BASE64URL(SHA256(verifier))，按 RFC 7636 去掉尾部填充。 */
fun codeChallengeS256(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

/** 账号云的 OAuth 起点；`next` 必须是 flux://auth，服务端按白名单精确匹配。 */
fun oauthStartUrl(
    baseUrl: String,
    provider: OAuthProvider,
    codeChallenge: String,
): String {
    val base = baseUrl.trim().trimEnd('/')
    val encodedNext = URLEncoder.encode(OAUTH_REDIRECT_URI, "UTF-8")
    val encodedChallenge = URLEncoder.encode(codeChallenge, "UTF-8")
    return buildString {
        append(base)
        append(OAUTH_PROVIDER_PATH)
        append('/')
        append(provider.id)
        append("/start?next=")
        append(encodedNext)
        append("&code_challenge=")
        append(encodedChallenge)
        append("&code_challenge_method=S256")
    }
}

/** 回调 URI 是否属于本 App 的 flux://auth；只做 scheme / host 判断。 */
fun isOAuthCallbackUri(raw: String?): Boolean {
    val uri = raw?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?: return false
    return uri.scheme.equals("flux", ignoreCase = true) &&
        uri.host.equals("auth", ignoreCase = true)
}

internal sealed interface OAuthCallbackResult {
    data class Code(val code: String) : OAuthCallbackResult
    data class Failure(val message: String) : OAuthCallbackResult
}

/**
 * 解析 flux://auth?code=... 与 flux://auth?new=1&code=...。
 *
 * 没有 code 视为用户取消；scheme / host 不对视为非法回调。
 */
internal fun parseOAuthCallback(raw: String?): OAuthCallbackResult {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return OAuthCallbackResult.Failure(OAuthMessages.CANCELLED)
    val uri = runCatching { URI(value) }.getOrNull()
        ?: return OAuthCallbackResult.Failure(OAuthMessages.INVALID_CALLBACK)
    if (!uri.scheme.equals("flux", ignoreCase = true) ||
        !uri.host.equals("auth", ignoreCase = true)
    ) {
        return OAuthCallbackResult.Failure(OAuthMessages.INVALID_CALLBACK)
    }
    val path = uri.path.orEmpty()
    if (path.isNotEmpty() && path != "/") {
        return OAuthCallbackResult.Failure(OAuthMessages.INVALID_CALLBACK)
    }
    val code = queryParameters(uri.rawQuery).firstOrNull { it.first == "code" }?.second.orEmpty()
    return if (code.isBlank()) {
        OAuthCallbackResult.Failure(OAuthMessages.CANCELLED)
    } else {
        OAuthCallbackResult.Code(code)
    }
}

private fun queryParameters(rawQuery: String?): List<Pair<String, String>> {
    val query = rawQuery?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return query.split('&').mapNotNull { part ->
        if (part.isEmpty()) return@mapNotNull null
        val separator = part.indexOf('=')
        val name = if (separator >= 0) part.substring(0, separator) else part
        val rawValue = if (separator >= 0) part.substring(separator + 1) else ""
        val decodedName = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
        val decodedValue = runCatching { URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
        decodedName to decodedValue
    }
}
