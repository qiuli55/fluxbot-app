package top.qiuli55.fluxbot.data

import org.json.JSONObject

/**
 * 登录态落盘：整份会话（access / refresh / 过期时间 / 用户资料）作为一个
 * JSON 字符串存进 [SecureStore] 的 AES-GCM 密文里。
 *
 * 只占一个键，退出登录时删除这一个键即可保证令牌不残留。
 */
class SecureCloudTokenStore(private val secure: SecureStore) : CloudTokenStore {

    override fun load(): CloudSession? {
        val raw = secure.get(KEY) ?: return null
        return runCatching { parseCloudSession(JSONObject(raw)) }.getOrNull()
    }

    override fun save(session: CloudSession) {
        secure.put(KEY, session.toJson().toString())
    }

    override fun clear() {
        secure.remove(KEY)
    }

    companion object {
        internal const val KEY = "cloud.session"
    }
}
