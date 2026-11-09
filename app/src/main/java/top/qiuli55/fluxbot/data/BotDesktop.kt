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
    const val HOST = "due-nearby-sperm-strengths.trycloudflare.com"
    const val TOKEN = "hk_4094f29dcdcf9d5738e976f4dff22d12"

    /** 宿主机上的极简桥：把 bot 的浏览器窗口拉回桌面。 */
    const val BRIDGE = "https://spirit-bangkok-expensive-align.trycloudflare.com"
    const val BRIDGE_KEY = "fdk_7c1e4b93a2f05d8e6b4a9c3f1d7e2085"

    fun profile(botId: String): String = "bot" + botId.replace("-", "").lowercase().take(8)
}
