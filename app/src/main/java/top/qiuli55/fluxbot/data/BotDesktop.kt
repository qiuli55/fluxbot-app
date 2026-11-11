package top.qiuli55.fluxbot.data

import top.qiuli55.fluxbot.BuildConfig

/**
 * Hermes Bot Desktop 的入口参数。
 *
 * 每个 Bot 对应一台云电脑上的一个 Hermes profile，
 * profile 名由 bot id 前 8 位推导（Hermes 只允许小写字母数字）。
 *
 * 连接参数在构建时通过 FLUXBOT_DESKTOP_* 环境变量注入，不再硬编码在源码里。
 */
object BotDesktop {
    /**
     * 国内网络对 *.instabox.tech 有 DNS 污染，直连会解析到劫持 IP 并超时。
     * 因此统一走 InstaBox 上的 Cloudflare 隧道（不经用户自己的服务器）。
     */
    val HOST: String = BuildConfig.DESKTOP_HOST
    val TOKEN: String = BuildConfig.DESKTOP_TOKEN

    /** 宿主机上的极简桥：把 bot 的浏览器窗口拉回桌面。 */
    val BRIDGE: String = BuildConfig.DESKTOP_BRIDGE
    val BRIDGE_KEY: String = BuildConfig.DESKTOP_BRIDGE_KEY

    val isConfigured: Boolean
        get() = TOKEN.isNotBlank() && HOST.isNotBlank()

    /**
     * 快速隧道每次重启域名都会变，构建时注入的值只是兜底。
     * 页面启动时会先读这个稳定地址拿最新入口（只取配置，画面不经过该服务器）。
     */
    const val CONFIG_URL = "https://flux.qiuli55.top/fluxbot-mobile/desktop.json"

    fun profile(botId: String): String = "bot" + botId.replace("-", "").lowercase().take(8)
}
