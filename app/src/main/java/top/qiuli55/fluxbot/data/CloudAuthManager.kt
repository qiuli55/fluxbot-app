package top.qiuli55.fluxbot.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 令牌持久化接口；生产实现走 Keystore 加密的 [SecureStore]，测试用内存实现。 */
interface CloudTokenStore {
    fun load(): CloudSession?
    fun save(session: CloudSession)
    fun clear()
}

/**
 * 登录态管理：持久化、提前刷新、401 单飞刷新与重试、退出清理。
 *
 * 线程模型：所有会改 session 的路径都经过 [refreshMutex]；[now] 可注入，
 * 便于单测精确控制提前刷新的时间点。
 */
class CloudAuthManager(
    private val client: CloudClient,
    private val tokenStore: CloudTokenStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
    private val logger: (String) -> Unit = {},
    private val onSessionExpired: () -> Unit = {},
    private val refreshMarginMs: Long = DEFAULT_REFRESH_MARGIN_MS,
) {

    @Volatile
    private var session: CloudSession? = tokenStore.load()

    private val refreshMutex = Mutex()
    private var autoJob: Job? = null

    init {
        session?.let {
            logger("cloud session restored user_id=${it.user.id}")
            scheduleRefresh()
        }
    }

    val isLoggedIn: Boolean get() = session != null
    val currentSession: CloudSession? get() = session

    /** 已保存的 access token，不做任何网络请求；媒体下载等同步路径用。 */
    fun cachedAccessToken(): String = session?.accessToken.orEmpty()

    fun cachedUser(): CloudUser? = session?.user

    /** 账号服务器是否开启第三方登录；未登录也可调用，用于设置页探测地址。 */
    suspend fun providers(): CloudProviders = client.providers()

    suspend fun login(identifier: String, password: String): CloudSession =
        adopt(client.login(identifier, password), "login")

    /**
     * 注册后立刻用同一组凭据登录：注册接口只下发 Cookie，响应体里没有令牌。
     * 注册成功但自动登录失败时，保留“账号已创建”的信息提示用户去登录。
     */
    suspend fun register(
        email: String,
        password: String,
        username: String? = null,
        displayName: String? = null,
    ): CloudSession {
        client.register(email, password, username, displayName)
        logger("cloud register ok")
        val tokens = try {
            client.login(email.trim().lowercase(), password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw ApiException(
                "注册成功，但自动登录失败，请改用登录：${e.message ?: "未知错误"}",
                e.status,
                e.code,
            )
        }
        return adopt(tokens, "register")
    }

    /**
     * 请求前取令牌：距离过期不足 [refreshMarginMs] 时先刷新。
     * 刷新遇到 401 / 403 或令牌已经过期会清空登录态并通知调用方回登录页；
     * 只是网络抖动且旧令牌还没过期时继续使用旧令牌。
     */
    suspend fun accessTokenForRequest(): String? {
        val current = session ?: return null
        if (!needsRefresh(current.expiresAtMillis, now(), refreshMarginMs)) {
            return current.accessToken
        }
        val refreshed = refreshMutex.withLock { refreshUnderLock(force = false) }
        return refreshed?.accessToken ?: session?.accessToken
    }

    /**
     * 收到 401 后调用：同一个失效令牌只触发一次刷新（单飞）。
     * 如果并发的另一次请求已经换好令牌，直接返回新令牌，不重复刷新。
     */
    suspend fun refreshAfterUnauthorized(failedToken: String): String? =
        refreshMutex.withLock {
            val current = session ?: return@withLock null
            if (current.accessToken != failedToken) return@withLock current.accessToken
            refreshUnderLock(force = true)?.accessToken
        }

    /**
     * 通用鉴权调用：先取令牌，遇到 401 刷新一次并原样重试一次。
     * [Api.request] 与单测都走这条路径，保证两处行为一致。
     */
    suspend fun <T> withAuthorizedToken(call: suspend (String) -> T): T {
        val token = accessTokenForRequest()
            ?: throw ApiException("登录已过期，请重新登录", 401, "cloud_expired")
        return try {
            call(token)
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            val refreshed = refreshAfterUnauthorized(token) ?: throw e
            call(refreshed)
        }
    }

    /** 应用启动 / 回到前台时调用：有登录态就按需续期。 */
    suspend fun ensureFresh() {
        if (session != null) accessTokenForRequest()
    }

    /** 立即读取一次用户资料；401 时刷新并重试一次。 */
    suspend fun fetchMe(): CloudMe = withAuthorizedToken { token -> client.me(token) }

    fun clearLocalSession() {
        autoJob?.cancel()
        autoJob = null
        session = null
        tokenStore.clear()
    }

    /** 本地清理 + 尽力通知服务端；服务端退出只支持 Cookie，失败不影响本地退出。 */
    suspend fun logout() {
        val hadSession = session != null
        clearLocalSession()
        if (hadSession) {
            runCatching { client.logout() }
        }
        logger("cloud logout local_cleared=true")
    }

    /** 只通知服务端，不重复清理（Store 已在主线程同步清理本地令牌）。 */
    suspend fun logoutRemote() {
        runCatching { client.logout() }
    }

    private fun adopt(tokens: CloudTokens, source: String): CloudSession {
        val next = tokens.toSession(now())
        session = next
        tokenStore.save(next)
        logger("cloud $source ok user_id=${next.user.id}")
        scheduleRefresh()
        return next
    }

    /**
     * 调用时必须已持有 [refreshMutex]。
     * force=false 时再检查一次是否真的临近过期，避免并发调用重复刷新。
     */
    private suspend fun refreshUnderLock(force: Boolean): CloudSession? {
        val current = session ?: return null
        if (!force && !needsRefresh(current.expiresAtMillis, now(), refreshMarginMs)) {
            return current
        }
        return try {
            val next = client.refresh(current.refreshToken).toSession(now())
            session = next
            tokenStore.save(next)
            logger("cloud token refreshed user_id=${next.user.id}")
            next
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // 401 / 403 说明 refresh token 已失效，必须回登录页。
            if (e.status == 401 || e.status == 403 || current.expiresAtMillis <= now()) {
                expire()
            } else {
                logger("cloud refresh failed status=${e.status}")
            }
            null
        } catch (e: Exception) {
            if (current.expiresAtMillis <= now()) expire() else logger("cloud refresh error")
            null
        }
    }

    private fun expire() {
        // 不在自动刷新协程里 cancel 自己：循环下一轮看到 session 为空会自然退出。
        autoJob = null
        session = null
        tokenStore.clear()
        logger("cloud session expired")
        onSessionExpired()
    }

    /** 在 access token 到期前 [refreshMarginMs] 主动续期，并在每次续期后重新排程。 */
    private fun scheduleRefresh() {
        val scope = scope ?: return
        autoJob?.cancel()
        autoJob = scope.launch {
            while (isActive) {
                val current = session ?: break
                val wait = refreshDelayMs(current.expiresAtMillis, now(), refreshMarginMs)
                if (wait > 0L) delay(wait)
                val refreshed = refreshMutex.withLock { refreshUnderLock(force = true) }
                if (refreshed == null && session != null) {
                    // 网络抖动但旧令牌仍可用：稍后重试，不把用户踢下线。
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    companion object {
        /** access token 提前 2 分钟续期。 */
        const val DEFAULT_REFRESH_MARGIN_MS = 120_000L
        private const val RETRY_DELAY_MS = 30_000L
    }
}

/** 距离过期时间不足 margin（含已过期）时应当刷新。 */
internal fun needsRefresh(expiresAtMillis: Long, nowMillis: Long, marginMs: Long): Boolean =
    expiresAtMillis - nowMillis <= marginMs

/** 自动刷新协程的等待时长；已进入刷新窗口时返回 0。 */
internal fun refreshDelayMs(expiresAtMillis: Long, nowMillis: Long, marginMs: Long): Long =
    (expiresAtMillis - nowMillis - marginMs).coerceAtLeast(0L)
