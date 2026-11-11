package top.qiuli55.fluxbot.data

/**
 * 一次桌面连接的入口参数，由后端 `/api/v1/desktop` 下发。
 *
 * 隧道地址（gateway / bridge）会随 Cloudflare 快速隧道重启变化，凭据（token /
 * bridgeKey）属于服务端配置，都不写进 App 源码——轮换后不需要重新发版。
 */
data class DesktopConfig(
    val gateway: String,
    val bridge: String,
    val token: String,
    val bridgeKey: String,
    val configUrl: String,
) {
    /** 服务端四件参数齐了才算可用。 */
    val configured: Boolean
        get() = gateway.isNotBlank() && bridge.isNotBlank() &&
            token.isNotBlank() && bridgeKey.isNotBlank()
}

/**
 * Hermes Bot Desktop 的入口参数。
 *
 * 每个 Bot 对应一台云电脑上的一个 Hermes profile，
 * profile 名由 bot id 前 8 位推导（Hermes 只允许小写字母数字）。
 */
object BotDesktop {
    /**
     * 稳定入口：App 启动时读这个公开 JSON 拿兜底隧道地址。
     * 真正下发给页面时以服务端返回的 gateway / bridge 为准。
     */
    const val CONFIG_URL = "https://flux.qiuli55.top/fluxbot-mobile/desktop.json"

    fun profile(botId: String): String = "bot" + botId.replace("-", "").lowercase().take(8)
}
