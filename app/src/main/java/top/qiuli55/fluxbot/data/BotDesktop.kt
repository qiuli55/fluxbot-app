package top.qiuli55.fluxbot.data

/**
 * Hermes Bot Desktop 的入口参数。
 *
 * 每个 Bot 对应一台云电脑上的一个 Hermes profile，
 * profile 名由 bot id 前 8 位推导（Hermes 只允许小写字母数字）。
 */
object BotDesktop {
    /**
     * 国内网络对 *.instabox.tech 有 DNS 污染，直连会解析到劫持 IP 并超时。
     * 因此统一走 InstaBox 上的 Cloudflare 隧道（不经用户自己的服务器）。
     */
    const val HOST = "mirror-artwork-affects-bradford.trycloudflare.com"
    const val TOKEN = "hk_REDACTED"

    /** 宿主机上的极简桥：把 bot 的浏览器窗口拉回桌面。 */
    const val BRIDGE = "https://therapy-robots-confidentiality-former.trycloudflare.com"
    const val BRIDGE_KEY = "fdk_REDACTED"

    /**
     * 快速隧道每次重启域名都会变，上面的值只是兜底。
     * 页面启动时会先读这个稳定地址拿最新入口（只取配置，画面不经过该服务器）。
     */
    const val CONFIG_URL = "https://flux.qiuli55.top/fluxbot-mobile/desktop.json"

    fun profile(botId: String): String = "bot" + botId.replace("-", "").lowercase().take(8)
}
