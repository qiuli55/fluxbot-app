package top.qiuli55.fluxbot.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore 加密的小型键值库：AES-256-GCM，密钥由系统托管，不出安全硬件。
 * 落盘格式为 base64(iv):base64(ciphertext)，明文不会进入 SharedPreferences。
 */
class SecureStore(context: Context) {

    private val prefs = context.getSharedPreferences("fluxbot.secure", Context.MODE_PRIVATE)

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val parts = stored.split(":")
            require(parts.size == 2) { "密文格式不对" }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val body = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrElse {
            // 换机或恢复备份会让密钥失效，这一项已经解不开，直接丢弃。
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun put(key: String, value: String): Boolean = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(body, Base64.NO_WRAP)
        prefs.edit().putString(key, stored).commit()
    }.getOrDefault(false)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** 删除某个账号名下的全部密文（删账户时用）。 */
    fun removePrefix(prefix: String) {
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    fun getJson(key: String): JSONObject? = get(key)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun putJson(key: String, value: JSONObject): Boolean = put(key, value.toString())

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "fluxbot.secure.v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}

/**
 * 保管库条目的秘密字段，只存在 SecureStore 里。
 * 支付卡只留后四位与有效期，完整卡号、安全码从不落盘。
 */
data class VaultSecrets(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val phone: String = "",
    val email: String = "",
    val last4: String = "",
    val expiry: String = "",
    val holder: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (url.isNotEmpty()) put("url", url)
        if (username.isNotEmpty()) put("username", username)
        if (password.isNotEmpty()) put("password", password)
        if (phone.isNotEmpty()) put("phone", phone)
        if (email.isNotEmpty()) put("email", email)
        if (last4.isNotEmpty()) put("last4", last4)
        if (expiry.isNotEmpty()) put("expiry", expiry)
        if (holder.isNotEmpty()) put("holder", holder)
    }

    companion object {
        fun from(o: JSONObject) = VaultSecrets(
            url = o.optString("url"),
            username = o.optString("username"),
            password = o.optString("password"),
            phone = o.optString("phone"),
            email = o.optString("email"),
            last4 = o.optString("last4"),
            expiry = o.optString("expiry"),
            holder = o.optString("holder"),
        )
    }
}
