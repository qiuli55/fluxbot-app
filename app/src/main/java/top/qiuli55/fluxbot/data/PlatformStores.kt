package top.qiuli55.fluxbot.data

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * [Api] 依赖的最小存储面。
 *
 * 生产实现包一层 SharedPreferences；JVM 上的 live 端到端测试注入内存实现，
 * 这样测试跑的就是同一套请求 / 解析 / 令牌逻辑，不需要 Robolectric。
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** [SecureStore] 的读取面；测试用内存实现替换 Keystore。 */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String): Boolean
    fun remove(key: String)
    fun removePrefix(prefix: String)
    fun getJson(key: String): JSONObject?
    fun putJson(key: String, value: JSONObject): Boolean
}
