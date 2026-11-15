package top.qiuli55.fluxbot.data

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * 第三方登录的本地流程：PKCE 生成、pending verifier 加密落盘、回跳解析与兑换。
 *
 * verifier 只写进 [SecretStore]（生产实现是 AES-GCM 的 SecureStore），带时间戳，
 * 默认 10 分钟过期。进程在浏览器里被系统杀掉后，冷启动仍然能凭这份记录完成兑换。
 * 日志只记 provider / 结果 / HTTP 状态，绝不出现 code、verifier 或令牌。
 */
class CloudOAuthManager(
    private val auth: CloudAuthManager,
    private val secure: SecretStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val logger: (String) -> Unit = {},
    private val verifierFactory: () -> String = { generateCodeVerifier() },
    private val pendingTtlMs: Long = DEFAULT_PENDING_TTL_MS,
) {

    /** 生成并保存 verifier，返回需要交给系统浏览器打开的起点 URL。 */
    fun start(provider: OAuthProvider, baseUrl: String): String {
        val verifier = verifierFactory()
        require(isValidCodeVerifier(verifier)) { "code_verifier 不符合 RFC 7636" }
        val pending = PendingOAuth(provider, verifier, now())
        val stored = runCatching {
            secure.put(PENDING_KEY, pending.toJson().toString())
        }.getOrDefault(false)
        if (!stored) {
            logger("cloud oauth start failed provider=${provider.id} reason=secure_store")
            throw ApiException(OAuthMessages.INCOMPLETE, 0, "oauth_store")
        }
        logger("cloud oauth start provider=${provider.id}")
        return oauthStartUrl(baseUrl, provider, codeChallengeS256(verifier))
    }

    fun pendingProvider(): OAuthProvider? = loadPending()?.provider

    fun hasPending(): Boolean = loadPending() != null

    /** 保存的 pending 是否仍在有效期内；用于界面提示，不触发网络。 */
    fun hasFreshPending(): Boolean {
        val pending = loadPending() ?: return false
        return now() - pending.createdAt <= pendingTtlMs
    }

    /**
     * 用回跳 URI 完成登录。成功返回新会话（已写入 CloudAuthManager 的令牌存储）。
     * pending 在兑换前删除，保证同一个登录码只会被消费一次。
     */
    suspend fun complete(callbackUri: String?): CloudSession {
        val pending = loadPending()
        if (pending == null) {
            logger("cloud oauth failed provider=unknown status=0 reason=no_pending")
            throw ApiException(OAuthMessages.INCOMPLETE, 0, "oauth_incomplete")
        }
        if (now() - pending.createdAt > pendingTtlMs) {
            clearPending()
            logger("cloud oauth failed provider=${pending.provider.id} status=0 reason=expired")
            throw ApiException(OAuthMessages.INCOMPLETE, 0, "oauth_expired")
        }
        return when (val parsed = parseOAuthCallback(callbackUri)) {
            is OAuthCallbackResult.Failure -> {
                clearPending()
                val reason = if (parsed.message == OAuthMessages.CANCELLED) {
                    "cancelled"
                } else {
                    "callback"
                }
                logger(
                    "cloud oauth failed provider=${pending.provider.id} " +
                        "status=0 reason=$reason",
                )
                throw ApiException(parsed.message, 0, "oauth_callback")
            }
            is OAuthCallbackResult.Code -> {
                clearPending()
                try {
                    val session = auth.exchangeOAuth(parsed.code, pending.verifier)
                    logger("cloud oauth complete provider=${pending.provider.id} status=200")
                    session
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    logger(
                        "cloud oauth failed provider=${pending.provider.id} " +
                            "status=${e.status} reason=exchange",
                    )
                    throw e
                }
            }
        }
    }

    /** 用户取消（从浏览器返回但没有回跳）。 */
    fun cancel() = clearPending("cancelled")

    /** 等待超时；浏览器一直没把控制权交回 App。 */
    fun timeout() = clearPending("timeout")

    private fun clearPending() {
        secure.remove(PENDING_KEY)
    }

    private fun clearPending(reason: String) {
        val provider = loadPending()?.provider?.id ?: "unknown"
        clearPending()
        logger("cloud oauth cancelled provider=$provider reason=$reason")
    }

    private fun loadPending(): PendingOAuth? {
        val raw = secure.get(PENDING_KEY) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val provider = OAuthProvider.fromId(json.optString("provider")) ?: return null
        val verifier = json.optString("code_verifier")
        val createdAt = json.optLong("created_at", 0L)
        if (verifier.isBlank() || createdAt <= 0L) return null
        return PendingOAuth(provider, verifier, createdAt)
    }

    private data class PendingOAuth(
        val provider: OAuthProvider,
        val verifier: String,
        val createdAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("provider", provider.id)
            .put("code_verifier", verifier)
            .put("created_at", createdAt)
    }

    companion object {
        internal const val PENDING_KEY = "cloud.oauth.pending"
        const val DEFAULT_PENDING_TTL_MS = 10 * 60 * 1000L
    }
}
