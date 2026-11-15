package top.qiuli55.fluxbot.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 用系统 Custom Tabs 打开账号云的 OAuth 起点。
 *
 * 必须是系统浏览器而不是 WebView：账号云的 OAuth state 是第一方 Cookie，
 * WebView 既拿不到同一份登录态，也会把第三方登录页暴露在 App 进程里。
 */
class CustomTabsOAuthBrowser(private val context: Context) : OAuthBrowser {

    override fun open(url: String) {
        val uri = Uri.parse(url)
        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        try {
            customTabs.launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            // 极少数设备没有 Custom Tabs 提供者，退回普通系统浏览器。
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
