package top.qiuli55.fluxbot.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * GitHub / Google 登录的纯 JVM 单测：PKCE、start URL、回跳解析、兑换与错误映射、
 * pending verifier 的落盘与过期，以及日志脱敏。
 */
class CloudOAuthTest {

    private class FakeTransport(
        private val handler: (HttpRequest) -> HttpResponse,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler(request)
        }
    }

    private class MemoryTokenStore(initial: CloudSession? = null) : CloudTokenStore {
        var saved: CloudSession? = initial
        var cleared = false

        override fun load(): CloudSession? = saved

        override fun save(session: CloudSession) {
            saved = session
            cleared = false
        }

        override fun clear() {
            saved = null
            cleared = true
        }
    }

    private class MemorySecretStore : SecretStore {
        val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String): Boolean {
            values[key] = value
            return true
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun removePrefix(prefix: String) {
            values.keys.filter { it.startsWith(prefix) }.forEach { values.remove(it) }
        }

        override fun getJson(key: String): JSONObject? =
            values[key]?.let { runCatching { JSONObject(it) }.getOrNull() }

        override fun putJson(key: String, value: JSONObject): Boolean = put(key, value.toString())
    }

    private val baseUrl = "https://account.example.test"
    private var clock = 1_000_000L

    /** RFC 7636 附录 B 的官方测试向量。 */
    private val rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val rfcChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

    private fun userJson(
        id: String = "42",
        email: String = "me@example.com",
        username: String = "fluxuser",
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email)
        .put("username", username)
        .put("display_name", "Flux User")
        .put("avatar_url", JSONObject.NULL)
        .put("must_change_password", false)

    private fun tokenJson(
        access: String = "ACCESS_OAUTH",
        refresh: String = "REFRESH_OAUTH",
        expiresIn: Int = 1800,
        user: JSONObject = userJson(),
    ): String = JSONObject()
        .put("access_token", access)
        .put("refresh_token", refresh)
        .put("token_type", "Bearer")
        .put("expires_in", expiresIn)
        .put("user", user)
        .toString()

    private fun authManager(
        transport: HttpTransport,
        tokenStore: CloudTokenStore = MemoryTokenStore(),
        logs: MutableList<String>? = null,
    ): CloudAuthManager = CloudAuthManager(
        client = CloudClient(transport) { baseUrl },
        tokenStore = tokenStore,
        now = { clock },
        logger = { message -> logs?.add(message) },
    )

    private fun oauthManager(
        auth: CloudAuthManager,
        secure: SecretStore = MemorySecretStore(),
        logs: MutableList<String>? = null,
        verifierFactory: () -> String = { rfcVerifier },
    ): CloudOAuthManager = CloudOAuthManager(
        auth = auth,
        secure = secure,
        now = { clock },
        logger = { message -> logs?.add(message) },
        verifierFactory = verifierFactory,
    )

    // ---------- PKCE ----------

    @Test
    fun verifierUsesRfc7636CharsetAndLength() {
        repeat(50) {
            val verifier = generateCodeVerifier()
            assertTrue(
                "长度应在 43-128 之间：${verifier.length}",
                verifier.length in OAUTH_VERIFIER_MIN_LENGTH..OAUTH_VERIFIER_MAX_LENGTH,
            )
            assertTrue("字符集不符合 RFC 7636：$verifier", isValidCodeVerifier(verifier))
        }
        assertEquals(64, generateCodeVerifier().length)
        assertEquals(
            OAUTH_VERIFIER_MIN_LENGTH,
            generateCodeVerifier(length = OAUTH_VERIFIER_MIN_LENGTH).length,
        )
        assertFalse(isValidCodeVerifier("短了"))
        assertFalse(isValidCodeVerifier("a".repeat(129)))
        assertFalse(isValidCodeVerifier("bad verifier with spaces".padEnd(43, 'x')))
    }

    @Test
    fun challengeMatchesRfc7636KnownVector() {
        assertEquals(rfcChallenge, codeChallengeS256(rfcVerifier))
        assertFalse(codeChallengeS256(rfcVerifier).contains("="))
        assertTrue(codeChallengeS256(rfcVerifier).matches(Regex("^[A-Za-z0-9_-]+$")))
        assertNotEquals(codeChallengeS256(rfcVerifier), codeChallengeS256("$rfcVerifier-other"))
    }

    // ---------- start URL ----------

    @Test
    fun startUrlTargetsProviderAndCarriesPkce() {
        val github = oauthStartUrl(baseUrl, OAuthProvider.GitHub, rfcChallenge)
        assertEquals(
            "https://account.example.test/api/auth/oauth/github/start" +
                "?next=flux%3A%2F%2Fauth&code_challenge=$rfcChallenge" +
                "&code_challenge_method=S256",
            github,
        )

        val google = oauthStartUrl("$baseUrl/", OAuthProvider.Google, rfcChallenge)
        assertEquals(
            "https://account.example.test/api/auth/oauth/google/start" +
                "?next=flux%3A%2F%2Fauth&code_challenge=$rfcChallenge" +
                "&code_challenge_method=S256",
            google,
        )
    }

    @Test
    fun startUrlUsesCurrentBaseUrl() {
        val custom = oauthStartUrl(
            "http://127.0.0.1:8500/",
            OAuthProvider.GitHub,
            rfcChallenge,
        )
        assertTrue(custom.startsWith("http://127.0.0.1:8500/api/auth/oauth/github/start?"))
    }

    // ---------- 回跳解析 ----------

    @Test
    fun callbackExtractsCodeWithAndWithoutNewFlag() {
        assertEquals(
            "CODE_123",
            (parseOAuthCallback("flux://auth?code=CODE_123") as OAuthCallbackResult.Code).code,
        )
        assertEquals(
            "CODE_456",
            (parseOAuthCallback("flux://auth?new=1&code=CODE_456") as OAuthCallbackResult.Code).code,
        )
        assertEquals(
            "a b/c",
            (parseOAuthCallback("flux://auth?code=a%20b%2Fc") as OAuthCallbackResult.Code).code,
        )
    }

    @Test
    fun callbackWithoutCodeIsTreatedAsCancelled() {
        listOf(null, "", "flux://auth", "flux://auth?new=1", "flux://auth?code=")
            .forEach { raw ->
                val result = parseOAuthCallback(raw)
                assertTrue(result is OAuthCallbackResult.Failure)
                assertEquals(
                    OAuthMessages.CANCELLED,
                    (result as OAuthCallbackResult.Failure).message,
                )
            }
    }

    @Test
    fun callbackWithWrongSchemeOrHostIsRejected() {
        listOf(
            "https://auth?code=CODE",
            "fluxbox://auth?code=CODE",
            "flux://other?code=CODE",
            "not a uri at all",
        ).forEach { raw ->
            val result = parseOAuthCallback(raw)
            assertTrue("应拒绝：$raw", result is OAuthCallbackResult.Failure)
            assertEquals(
                OAuthMessages.INVALID_CALLBACK,
                (result as OAuthCallbackResult.Failure).message,
            )
        }
    }

    @Test
    fun callbackUriGuardMatchesOnlyFluxAuth() {
        assertTrue(isOAuthCallbackUri("flux://auth?code=x"))
        assertFalse(isOAuthCallbackUri("fluxbox://auth?code=x"))
        assertFalse(isOAuthCallbackUri("https://auth?code=x"))
        assertFalse(isOAuthCallbackUri(null))
    }

    // ---------- 兑换成功 ----------

    @Test
    fun exchangeAdoptsTokensIntoSessionStoreAndClearsPending() = runBlocking {
        val tokenStore = MemoryTokenStore()
        val secret = MemorySecretStore()
        val transport = FakeTransport { request ->
            assertEquals("POST", request.method)
            assertEquals("$baseUrl/api/auth/exchange", request.url)
            val body = JSONObject(request.body.orEmpty())
            assertEquals("CODE_123", body.getString("code"))
            assertEquals(rfcVerifier, body.getString("code_verifier"))
            assertEquals(null, request.headers["Authorization"])
            HttpResponse(200, tokenJson())
        }
        val auth = authManager(transport, tokenStore)
        val oauth = oauthManager(auth, secret)

        oauth.start(OAuthProvider.GitHub, baseUrl)
        assertTrue(oauth.hasPending())
        assertEquals(OAuthProvider.GitHub, oauth.pendingProvider())
        assertEquals(1, secret.values.size)

        val session = oauth.complete("flux://auth?new=1&code=CODE_123")

        assertEquals("ACCESS_OAUTH", session.accessToken)
        assertEquals("REFRESH_OAUTH", session.refreshToken)
        assertEquals(session, tokenStore.saved)
        assertTrue(auth.isLoggedIn)
        assertNull(secret.get(CloudOAuthManager.PENDING_KEY))
        assertFalse(oauth.hasPending())
    }

    @Test
    fun pendingVerifierSurvivesManagerRecreation() = runBlocking {
        val tokenStore = MemoryTokenStore()
        val secret = MemorySecretStore()
        val transport = FakeTransport { request ->
            val body = JSONObject(request.body.orEmpty())
            assertEquals(rfcVerifier, body.getString("code_verifier"))
            HttpResponse(200, tokenJson())
        }
        val auth = authManager(transport, tokenStore)

        oauthManager(auth, secret).start(OAuthProvider.Google, baseUrl)

        // 模拟进程被杀后重建：新的 coordinator 只靠 SecretStore 里的 pending 完成兑换。
        val restarted = oauthManager(
            auth,
            secret,
            verifierFactory = { generateCodeVerifier() },
        )
        assertEquals(OAuthProvider.Google, restarted.pendingProvider())
        val session = restarted.complete("flux://auth?code=CODE_AFTER_RESTART")

        assertEquals("ACCESS_OAUTH", session.accessToken)
        assertTrue(auth.isLoggedIn)
    }

    // ---------- 兑换错误映射 ----------

    private fun exchangeFailure(status: Int, detail: String): ApiException = runBlocking {
        val transport = FakeTransport {
            HttpResponse(status, JSONObject().put("detail", detail).toString())
        }
        val auth = authManager(transport)
        val oauth = oauthManager(auth)
        oauth.start(OAuthProvider.GitHub, baseUrl)
        val error = runCatching { oauth.complete("flux://auth?code=CODE_123") }
            .exceptionOrNull()
        assertTrue(error is ApiException)
        error as ApiException
    }

    @Test
    fun unauthorizedExchangeNeverUsesPasswordLoginText() {
        val error = exchangeFailure(401, "账号或密码不正确")
        assertEquals(OAuthMessages.INVALID_CODE, error.message)
        assertEquals(401, error.status)
    }

    @Test
    fun reusedExpiredOrBadVerifierAllMapToInvalidCode() {
        listOf(
            "登录码无效或已过期，请重新登录",
            "登录码校验失败，请重新登录",
            "login code reused",
        ).forEach { detail ->
            assertEquals(OAuthMessages.INVALID_CODE, exchangeFailure(401, detail).message)
        }
    }

    @Test
    fun forbiddenExchangeMapsToAccountUnavailable() {
        val error = exchangeFailure(403, "账号不可用")
        assertEquals(OAuthMessages.ACCOUNT_UNAVAILABLE, error.message)
        assertEquals(403, error.status)
    }

    @Test
    fun rateLimitedExchangeMapsToFriendlyMessage() {
        val error = exchangeFailure(429, "too many requests")
        assertEquals(OAuthMessages.RATE_LIMITED, error.message)
        assertEquals(429, error.status)
    }

    @Test
    fun serverErrorExchangeMapsToFriendlyMessage() {
        listOf(500, 502, 503).forEach { status ->
            val error = exchangeFailure(status, "internal error")
            assertEquals(OAuthMessages.SERVER_UNAVAILABLE, error.message)
            assertEquals(status, error.status)
        }
    }

    @Test
    fun networkFailureKeepsSharedNetworkMessage() = runBlocking {
        val transport = FakeTransport { throw IOException("offline") }
        val auth = authManager(transport)
        val oauth = oauthManager(auth)
        oauth.start(OAuthProvider.Google, baseUrl)

        val error = runCatching { oauth.complete("flux://auth?code=CODE_123") }
            .exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(OAuthMessages.NETWORK, error?.message)
        assertEquals("network", (error as ApiException).code)
        assertFalse(auth.isLoggedIn)
    }

    // ---------- 取消 / 超时 / 过期 ----------

    @Test
    fun cancelClearsPendingAndNoReturnIsIncomplete() = runBlocking {
        val secret = MemorySecretStore()
        val oauth = oauthManager(authManager(FakeTransport { HttpResponse(200, tokenJson()) }), secret)
        oauth.start(OAuthProvider.GitHub, baseUrl)
        assertTrue(oauth.hasPending())

        oauth.cancel()
        assertFalse(oauth.hasPending())

        val error = runCatching { oauth.complete("flux://auth?code=CODE_123") }
            .exceptionOrNull()
        assertEquals(OAuthMessages.INCOMPLETE, error?.message)
        assertEquals("oauth_incomplete", (error as ApiException).code)
    }

    @Test
    fun timeoutClearsPending() {
        val oauth = oauthManager(authManager(FakeTransport { HttpResponse(200, tokenJson()) }))
        oauth.start(OAuthProvider.Google, baseUrl)

        oauth.timeout()

        assertFalse(oauth.hasPending())
        assertFalse(oauth.hasFreshPending())
    }

    @Test
    fun browserNeverReturnedIsIncomplete() = runBlocking {
        val oauth = oauthManager(authManager(FakeTransport { HttpResponse(200, tokenJson()) }))
        oauth.start(OAuthProvider.Google, baseUrl)
        oauth.timeout()

        val error = runCatching { oauth.complete("flux://auth?code=CODE_123") }
            .exceptionOrNull()

        assertEquals(OAuthMessages.INCOMPLETE, error?.message)
    }

    @Test
    fun callbackWithoutCodeClearsPending() = runBlocking {
        val secret = MemorySecretStore()
        val oauth = oauthManager(authManager(FakeTransport { HttpResponse(200, tokenJson()) }), secret)
        oauth.start(OAuthProvider.GitHub, baseUrl)

        val error = runCatching { oauth.complete("flux://auth?new=1") }.exceptionOrNull()

        assertEquals(OAuthMessages.CANCELLED, error?.message)
        assertNull(secret.get(CloudOAuthManager.PENDING_KEY))
    }

    @Test
    fun expiredPendingIsIncompleteAndCleared() = runBlocking {
        val secret = MemorySecretStore()
        val oauth = oauthManager(authManager(FakeTransport { HttpResponse(200, tokenJson()) }), secret)
        oauth.start(OAuthProvider.GitHub, baseUrl)

        clock += CloudOAuthManager.DEFAULT_PENDING_TTL_MS + 1

        val error = runCatching { oauth.complete("flux://auth?code=CODE_123") }
            .exceptionOrNull()
        assertEquals(OAuthMessages.INCOMPLETE, error?.message)
        assertEquals("oauth_expired", (error as ApiException).code)
        assertNull(secret.get(CloudOAuthManager.PENDING_KEY))
    }

    @Test
    fun invalidCallbackClearsPending() = runBlocking {
        val secret = MemorySecretStore()
        val oauth = oauthManager(authManager(FakeTransport { HttpResponse(200, tokenJson()) }), secret)
        oauth.start(OAuthProvider.GitHub, baseUrl)

        val error = runCatching { oauth.complete("https://evil.example/?code=x") }
            .exceptionOrNull()

        assertEquals(OAuthMessages.INVALID_CALLBACK, error?.message)
        assertNull(secret.get(CloudOAuthManager.PENDING_KEY))
    }

    // ---------- 日志脱敏 ----------

    @Test
    fun oauthLogsNeverContainCodeVerifierOrTokens() = runBlocking {
        val code = "LOGIN_CODE_SECRET_123456"
        val access = "ACCESS_SECRET_DO_NOT_LOG_123456"
        val refresh = "REFRESH_SECRET_DO_NOT_LOG_987654"
        val logs = mutableListOf<String>()
        val transport = FakeTransport { request ->
            val body = JSONObject(request.body.orEmpty())
            assertEquals(code, body.getString("code"))
            assertEquals(rfcVerifier, body.getString("code_verifier"))
            HttpResponse(200, tokenJson(access = access, refresh = refresh))
        }
        val auth = authManager(transport, logs = logs)
        val oauth = oauthManager(auth, logs = logs)

        oauth.start(OAuthProvider.GitHub, baseUrl)
        oauth.complete("flux://auth?code=$code")

        assertTrue(logs.any { it.startsWith("cloud oauth start") })
        assertTrue(logs.any { it.startsWith("cloud oauth complete") })
        assertTrue(logs.any { it.startsWith("cloud oauth ok") })
        logs.forEach { line ->
            listOf(code, rfcVerifier, access, refresh, "Bearer").forEach { secret ->
                assertFalse("日志泄露了 $secret：$line", line.contains(secret))
            }
        }
    }

    @Test
    fun failureLogsRecordStatusWithoutSecrets() = runBlocking {
        val code = "LOGIN_CODE_SECRET_654321"
        val logs = mutableListOf<String>()
        val transport = FakeTransport {
            HttpResponse(401, """{"detail":"登录码校验失败，请重新登录"}""")
        }
        val auth = authManager(transport, logs = logs)
        val oauth = oauthManager(auth, logs = logs)

        oauth.start(OAuthProvider.Google, baseUrl)
        runCatching { oauth.complete("flux://auth?code=$code") }

        assertTrue(logs.any { it.contains("status=401") && it.contains("provider=google") })
        logs.forEach { line ->
            listOf(code, rfcVerifier).forEach { secret ->
                assertFalse("日志泄露了 $secret：$line", line.contains(secret))
            }
        }
    }
}
