package top.qiuli55.fluxbot.data

/**
 * Hermes Bot Desktop 的入口参数。
 *
 * 连接参数全部由后端 `POST /api/v1/bots/{id}/desktop/session` 下发，profile 也由
 * 服务端按 Bot UUID 计算。App 里不再保留任何构建期注入的桌面地址或密钥——
 * BuildConfig 里的字符串能被直接从 APK 读出来，写进去就等于公开。
 */
object BotDesktop {
    /**
     * 快速隧道每次重启域名都会变，构建时注入的值只是兜底。
     * 页面启动时会先读这个稳定地址拿最新入口（只取配置，画面不经过该服务器）。
     */
    const val CONFIG_URL = "https://flux.qiuli55.top/fluxbot-mobile/desktop.json"

    fun profile(botId: String): String = "bot" + botId.replace("-", "").lowercase().take(8)
}
