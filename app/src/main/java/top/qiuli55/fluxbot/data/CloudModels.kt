package top.qiuli55.fluxbot.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Flux 账号云（https://flux.qiuli55.top，路由前缀 /api/auth）的数据模型。
 *
 * 这些类型只负责解析与保存，不做网络请求；令牌字段重写了 toString()，
 * 即便被误打进日志也只会看到 <redacted>。
 */

data class CloudUser(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val mustChangePassword: Boolean = false,
) {
    val name: String get() = displayName.ifBlank { username }

    val initials: String
        get() {
            val basis = name.ifBlank { email.substringBefore("@") }.trim()
            return basis.take(2).uppercase()
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email)
        .put("username", username)
        .put("display_name", displayName)
        .put("avatar_url", avatarUrl)
        .put("must_change_password", mustChangePassword)
}

data class CloudIdentity(
    val provider: String,
    val providerEmail: String,
    val providerLogin: String,
    val createdAt: String,
)

data class CloudMe(
    val user: CloudUser,
    val identities: List<CloudIdentity>,
    val providers: Map<String, Boolean>,
    val hasModelPolicy: Boolean = false,
)

/** login/token 与 token 两个接口的响应体。 */
data class CloudTokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Int,
    val user: CloudUser,
) {
    override fun toString(): String =
        "CloudTokens(access=<redacted>, refresh=<redacted>, type=$tokenType, " +
            "expiresIn=$expiresInSeconds, user=${user.username})"

    fun toSession(nowMillis: Long): CloudSession = CloudSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = nowMillis + expiresInSeconds * 1000L,
        user = user,
    )
}

/** 落盘的一整套登录态；expiresAtMillis 是本地算出的 access token 过期时间。 */
data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val user: CloudUser,
) {
    override fun toString(): String =
        "CloudSession(access=<redacted>, refresh=<redacted>, " +
            "expiresAt=$expiresAtMillis, user=${user.username})"

    fun toJson(): JSONObject = JSONObject()
        .put("access_token", accessToken)
        .put("refresh_token", refreshToken)
        .put("expires_at", expiresAtMillis)
        .put("user", user.toJson())
}

data class CloudProviders(val github: Boolean, val google: Boolean)

// ---------------------------------------------------------------- 解析

internal fun parseCloudUser(o: JSONObject?): CloudUser? {
    if (o == null) return null
    val id = o.optStringOrEmpty("id").trim()
    val username = o.optStringOrEmpty("username").trim()
    if (id.isEmpty() || username.isEmpty()) return null
    return CloudUser(
        id = id,
        email = o.optStringOrEmpty("email"),
        username = username,
        displayName = o.optStringOrEmpty("display_name"),
        avatarUrl = o.optStringOrEmpty("avatar_url"),
        mustChangePassword = o.optBoolean("must_change_password", false),
    )
}

internal fun parseCloudTokens(o: JSONObject): CloudTokens {
    val access = o.optStringOrEmpty("access_token").trim()
    val refresh = o.optStringOrEmpty("refresh_token").trim()
    if (access.isEmpty() || refresh.isEmpty()) {
        throw ApiException("账号服务器返回的令牌不完整")
    }
    val user = parseCloudUser(o.optJSONObject("user"))
        ?: throw ApiException("账号服务器未返回用户信息")
    return CloudTokens(
        accessToken = access,
        refreshToken = refresh,
        tokenType = o.optStringOrEmpty("token_type").ifBlank { "Bearer" },
        expiresInSeconds = o.optInt("expires_in", 0).takeIf { it > 0 } ?: DEFAULT_ACCESS_TTL_SEC,
        user = user,
    )
}

internal fun parseCloudMe(o: JSONObject): CloudMe {
    val user = parseCloudUser(o.optJSONObject("user"))
        ?: throw ApiException("账号服务器未返回用户信息")
    val identities = o.optJSONArray("identities")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CloudIdentity(
                provider = item.optStringOrEmpty("provider"),
                providerEmail = item.optStringOrEmpty("provider_email"),
                providerLogin = item.optStringOrEmpty("provider_login"),
                createdAt = item.optStringOrEmpty("created_at"),
            )
        }
    }.orEmpty()
    val providers = o.optJSONObject("providers")?.let { obj ->
        obj.keys().asSequence().associateWith { obj.optBoolean(it) }
    }.orEmpty()
    return CloudMe(
        user = user,
        identities = identities,
        providers = providers,
        hasModelPolicy = o.optJSONObject("model_policy") != null,
    )
}

internal fun parseCloudProviders(o: JSONObject): CloudProviders = CloudProviders(
    github = o.optBoolean("github", false),
    google = o.optBoolean("google", false),
)

internal fun parseCloudSession(o: JSONObject): CloudSession? {
    val access = o.optStringOrEmpty("access_token").trim()
    val refresh = o.optStringOrEmpty("refresh_token").trim()
    val expiresAt = o.optLong("expires_at", 0L)
    val user = parseCloudUser(o.optJSONObject("user")) ?: return null
    if (access.isEmpty() || refresh.isEmpty() || expiresAt <= 0L) return null
    return CloudSession(access, refresh, expiresAt, user)
}

/** FastAPI 的校验错误 detail 可能是字符串，也可能是 [{msg: ...}] 数组。 */
internal fun cloudErrorText(root: JSONObject?): String {
    val detail = root?.opt("detail") ?: return ""
    val raw = when (detail) {
        is String -> detail
        is JSONArray -> detail.optJSONObject(0)?.optStringOrEmpty("msg").orEmpty()
        is JSONObject -> detail.optStringOrEmpty("message")
        else -> ""
    }
    return raw.removePrefix("Value error, ").trim()
}

// ---------------------------------------------------------------- 客户端校验

object CloudValidation {

    private val emailRegex =
        Regex("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[A-Za-z]{2,24}$")
    private val usernameRegex = Regex("^[A-Za-z0-9._-]{3,64}$")

    fun loginIdentifier(value: String): String? = when {
        value.isBlank() -> "请输入邮箱或用户名"
        value.trim().length < 3 -> "邮箱或用户名至少 3 个字符"
        value.trim().length > 255 -> "邮箱或用户名过长"
        else -> null
    }

    fun email(value: String): String? = when {
        value.isBlank() -> "请输入邮箱"
        !emailRegex.matches(value.trim().lowercase()) -> "请输入正确的邮箱地址"
        else -> null
    }

    fun loginPassword(value: String): String? =
        if (value.isEmpty()) "请输入密码" else null

    fun newPassword(value: String): String? = when {
        value.isEmpty() -> "请输入密码"
        value.length < 8 -> "密码至少 8 位"
        value.length > 128 -> "密码不能超过 128 位"
        else -> null
    }

    /** 用户名可选；填了才按服务端规则校验。 */
    fun optionalUsername(value: String): String? = when {
        value.isBlank() -> null
        !usernameRegex.matches(value.trim()) ->
            "用户名需 3-64 位，只能包含字母、数字、点、下划线、连字符"
        else -> null
    }
}

/**
 * 账号服务器地址校验：只接受带主机的 http(s) 绝对地址，不允许查询串 / 片段 /
 * 内嵌用户名密码。返回去掉末尾斜杠后的地址，非法时返回 null。
 */
internal fun normalizeAccountBaseUrl(raw: String?): String? {
    val value = raw?.trim()?.trimEnd('/').orEmpty()
    if (value.isEmpty() || value.any { it.isWhitespace() }) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
    return value
}

internal const val DEFAULT_ACCESS_TTL_SEC = 30 * 60
