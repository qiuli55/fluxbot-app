package top.qiuli55.fluxbot.data

import org.json.JSONObject

/**
 * `GET /api/v1/desktop` 下发的桌面入口配置。
 *
 * configured 由服务端判定：ticket 模式下可能 configured=true 而 token / bridge_key
 * 都是 JSON null，客户端必须信任该标志，不能以凭据是否为空反推是否配置。
 */
data class DesktopConfig(
    val configured: Boolean,
    val gateway: String,
    val bridge: String,
    val token: String,
    val bridgeKey: String,
    val configUrl: String,
    val tunnelUpdatedAt: String,
)

/**
 * Hermes Bot Desktop 的入口参数。
 *
 * 连接参数全部由后端下发：先 `GET /api/v1/desktop` 拿稳定入口，再
 * `POST /api/v1/bots/{id}/desktop/session` 换这个 Bot 的短期会话。App 里不保留
 * 任何构建期注入的桌面地址或密钥——BuildConfig 里的字符串能被直接从 APK 读出来。
 */
object BotDesktop {
    /**
     * 快速隧道每次重启域名都会变，构建时注入的值只是兜底。
     * 页面启动时会先读这个稳定地址拿最新入口（只取配置，画面不经过该服务器）。
     */
    const val CONFIG_URL = "https://flux.qiuli55.top/fluxbot-mobile/desktop.json"

    /** 与后端 desktop_profile 一致：bot + UUID 去掉横线后的前 8 位小写十六进制。 */
    fun profile(botId: String): String = "bot" + botId.replace("-", "").lowercase().take(8)
}

/** 解析 `data` 对象；JSON null 一律按空串处理，不会变成字面量 "null"。 */
internal fun parseDesktopConfig(data: JSONObject): DesktopConfig = DesktopConfig(
    configured = data.optBoolean("configured", false),
    gateway = data.optStringOrEmpty("gateway"),
    bridge = data.optStringOrEmpty("bridge"),
    token = data.optStringOrEmpty("token"),
    bridgeKey = data.optStringOrEmpty("bridge_key"),
    configUrl = data.optStringOrEmpty("config_url"),
    tunnelUpdatedAt = data.optStringOrEmpty("tunnel_updated_at"),
)

/** 解析桌面会话 `data` 对象；gateway_token / bridge_key 在 ticket 模式下可能为 null。 */
internal fun parseDesktopSession(data: JSONObject): DesktopSession = DesktopSession(
    host = data.optStringOrEmpty("host"),
    token = data.optStringOrEmpty("gateway_token"),
    bridge = data.optStringOrEmpty("bridge"),
    bridgeKey = data.optStringOrEmpty("bridge_key"),
    profile = data.optStringOrEmpty("profile"),
    ticket = data.optStringOrEmpty("ticket"),
    expiresAt = data.optStringOrEmpty("expires_at"),
)

/**
 * 把会话与全局配置合并成页面实际使用的参数。
 *
 * host / bridge 以会话为主，缺失时回退到 `GET /api/v1/desktop`；profile 优先用
 * 服务端按 Bot 算好的值；token 缺失时回退到全局配置里的长期凭据。
 */
internal fun DesktopSession.alignedWith(config: DesktopConfig, botId: String): DesktopSession = copy(
    host = host.ifBlank { config.gateway },
    token = token.ifBlank { config.token },
    bridge = bridge.ifBlank { config.bridge },
    bridgeKey = bridgeKey.ifBlank { config.bridgeKey },
    profile = profile.ifBlank { BotDesktop.profile(botId) },
)

/** 页面连接凭据：有短期 ticket 就用 ticket，否则退回长期 gateway_token。 */
internal fun DesktopSession.preferredCredential(): String = ticket.ifBlank { token }

/** 页面读取配置的稳定地址：服务端给了 config_url 就用它，否则用公开兜底常量。 */
internal fun desktopConfigUrl(config: DesktopConfig): String =
    config.configUrl.ifBlank { BotDesktop.CONFIG_URL }

/** 把桌面接口失败翻译成提示页文案；503 desktop_not_configured 视为未配置。 */
internal fun desktopErrorMessage(error: Throwable): String {
    if (error is ApiException && error.code == "desktop_not_configured") {
        return "桌面尚未配置"
    }
    val message = error.message?.trim().orEmpty()
    return message.ifBlank { "无法连接 Bot 桌面" }
}
