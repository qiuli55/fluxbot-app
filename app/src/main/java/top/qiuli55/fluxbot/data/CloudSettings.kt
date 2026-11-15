package top.qiuli55.fluxbot.data

import android.content.Context

/**
 * 账号服务器地址，与 flux-bot 后端地址完全独立。
 *
 * 默认 https://flux.qiuli55.top；用户在「账户 → 账号服务器」或登录页改完后，
 * 下一次登录 / 刷新立即生效，不需要重启 App。
 */
class CloudSettings(context: Context) {

    private val prefs = context.getSharedPreferences("fluxbot.cloud", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = normalizeAccountBaseUrl(prefs.getString(KEY_BASE_URL, null))
            ?: CloudClient.DEFAULT_BASE_URL
        set(value) {
            val normalized = normalizeAccountBaseUrl(value) ?: CloudClient.DEFAULT_BASE_URL
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        }

    fun reset() {
        prefs.edit().remove(KEY_BASE_URL).apply()
    }

    companion object {
        private const val KEY_BASE_URL = "account_base_url"
        const val DEFAULT_BASE_URL = CloudClient.DEFAULT_BASE_URL
    }
}
