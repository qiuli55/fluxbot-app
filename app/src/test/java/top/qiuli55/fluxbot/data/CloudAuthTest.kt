package top.qiuli55.fluxbot.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Flux 账号云的纯 JVM 单测：假传输层 + 内存令牌库 + 可注入时钟。
 *
 * 覆盖登录 / 注册后登录 / 提前刷新 / 401 单飞刷新与重试 / 刷新失败登出 /
 * 退出清理 / 日志不含令牌 / 表单校验与地址校验。
 */
class CloudAuthTest {

    // ---------- 假实现 ----------

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

    private val baseUrl = "https://account.example.test"
    private var clock = 1_000_000L

    private fun userJson(
        id: String = "42",
        email: String = "me@example.com",
        username: String = "fluxuser",
        displayName: String = "Flux User",
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email)
        .put("username", username)
        .put("display_name", displayName)
        .put("avatar_url", JSONObject.NULL)
        .put("must_change_password", false)
        .put("created_at", "2026-11-16T00:00:00Z")

    private fun tokenJson(
        access: String,
        refresh: String,
        expiresIn: Int = 1800,
        user: JSONObject = userJson(),
    ): String = JSONObject()
        .put("access_token", access)
        .put("refresh_token", refresh)
        .put("token_type", "Bearer")
        .put("expires_in", expiresIn)
        .put("user", user)
        .toString()

    private fun meJson(user: JSONObject = userJson()): String = JSONObject()
        .put("user", user)
        .put("identities", JSONArray())
        .put("providers", JSONObject().put("github", true).put("google", false))
        .put("model_policy", JSONObject.NULL)
        .toString()

    private fun manager(
        transport: HttpTransport,
        store: CloudTokenStore = MemoryTokenStore(),
        logs: MutableList<String>? = null,
        expired: (() -> Unit)? = null,
        now: () -> Long = { clock },
    ): CloudAuthManager = CloudAuthManager(
        client = CloudClient(transport) { baseUrl },
        tokenStore = store,
        now = now,
        logger = { message -> logs?.add(message) },
        onSessionExpired = { expired?.invoke() },
    )

    private fun requestBody(request: HttpRequest): JSONObject =
        JSONObject(request.body.orEmpty())

    // ---------- 登录 ----------

    @Test
    fun loginSuccessParsesTokensAndUser() = runBlocking {
        val store = MemoryTokenStore()
        val transport = FakeTransport { request ->
            assertEquals("POST", request.method)
            assertEquals("$baseUrl/api/auth/login/token", request.url)
            val body = requestBody(request)
            assertEquals("me@example.com", body.getString("identifier"))
            assertEquals("correct horse", body.getString("password"))
            HttpResponse(200, tokenJson("ACCESS_1", "REFRESH_1"))
        }
        val manager = manager(transport, store)

        val session = manager.login("  me@example.com  ", "correct horse")

        assertEquals("ACCESS_1", session.accessToken)
        assertEquals("REFRESH_1", session.refreshToken)
        assertEquals("42", session.user.id)
        assertEquals("Flux User", session.user.name)
        assertEquals(clock + 1_800_000L, session.expiresAtMillis)
        assertTrue(manager.isLoggedIn)
        assertEquals(session, store.saved)
        assertEquals(1, transport.requests.size)
        assertFalse(transport.requests.first().headers.containsKey("Authorization"))
    }

    @Test
    fun loginFailureSurfacesServerDetail() = runBlocking {
        val store = MemoryTokenStore()
        val transport = FakeTransport {
            HttpResponse(401, """{"detail":"账号或密码不正确"}""")
        }
        val manager = manager(transport, store)

        val error = runCatching { manager.login("me@example.com", "wrong") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals("账号或密码不正确", error?.message)
        assertEquals(401, (error as ApiException).status)
        assertFalse(manager.isLoggedIn)
        assertNull(store.saved)
    }

    @Test
    fun validationErrorArrayIsUnwrapped() = runBlocking {
        val transport = FakeTransport {
            HttpResponse(
                422,
                """{"detail":[{"loc":["body","email"],"msg":"Value error, 邮箱格式不正确"}]}""",
            )
        }
        val manager = manager(transport)

        val error = runCatching { manager.login("bad", "whatever") }.exceptionOrNull()

        assertEquals("邮箱格式不正确", error?.message)
    }

    @Test
    fun networkFailureHasFriendlyMessage() = runBlocking {
        val transport = FakeTransport { throw IOException("boom") }
        val manager = manager(transport)

        val error = runCatching { manager.login("me@example.com", "password") }.exceptionOrNull()

        assertEquals("无法连接账号服务器，请检查网络或服务器地址", error?.message)
        assertEquals("network", (error as ApiException).code)
    }

    @Test
    fun incompleteTokenResponseIsRejected() = runBlocking {
        val transport = FakeTransport {
            HttpResponse(200, """{"access_token":"a","token_type":"Bearer","expires_in":1800}""")
        }
        val manager = manager(transport)

        val error = runCatching { manager.login("me@example.com", "password") }.exceptionOrNull()

        assertEquals("账号服务器返回的令牌不完整", error?.message)
        assertFalse(manager.isLoggedIn)
    }

    // ---------- 注册后立即登录 ----------

    @Test
    fun registerThenLoginCallsBothEndpointsInOrder() = runBlocking {
        val transport = FakeTransport { request ->
            when (request.url) {
                "$baseUrl/api/auth/register" -> {
                    val body = requestBody(request)
                    assertEquals("new@example.com", body.getString("email"))
                    assertEquals("password-123", body.getString("password"))
                    assertEquals("newuser", body.getString("username"))
                    assertEquals("新用户", body.getString("display_name"))
                    HttpResponse(200, meJson())
                }
                "$baseUrl/api/auth/login/token" -> {
                    val body = requestBody(request)
                    assertEquals("new@example.com", body.getString("identifier"))
                    assertEquals("password-123", body.getString("password"))
                    HttpResponse(
                        200,
                        tokenJson(
                            "ACCESS_REG",
                            "REFRESH_REG",
                            user = userJson(
                                email = "new@example.com",
                                username = "newuser",
                                displayName = "新用户",
                            ),
                        ),
                    )
                }
                else -> HttpResponse(404, """{"detail":"not found"}""")
            }
        }
        val store = MemoryTokenStore()
        val manager = manager(transport, store)

        val session = manager.register(
            " New@Example.com ",
            "password-123",
            "newuser",
            "新用户",
        )

        assertEquals(
            listOf("$baseUrl/api/auth/register", "$baseUrl/api/auth/login/token"),
            transport.requests.map { it.url },
        )
        assertEquals("ACCESS_REG", session.accessToken)
        assertEquals("new@example.com", session.user.email)
        assertEquals(session, store.saved)
    }

    @Test
    fun registerFailureDoesNotAttemptLogin() = runBlocking {
        val transport = FakeTransport {
            HttpResponse(409, """{"detail":"该邮箱已注册，请直接登录"}""")
        }
        val manager = manager(transport)

        val error = runCatching {
            manager.register("new@example.com", "password-123")
        }.exceptionOrNull()

        assertEquals("该邮箱已注册，请直接登录", error?.message)
        assertEquals(1, transport.requests.size)
        assertEquals("$baseUrl/api/auth/register", transport.requests.single().url)
    }

    @Test
    fun registerSuccessButLoginFailureKeepsAccountCreatedMessage() = runBlocking {
        val transport = FakeTransport { request ->
            if (request.url.endsWith("/register")) {
                HttpResponse(200, meJson())
            } else {
                HttpResponse(500, """{"detail":"internal error"}""")
            }
        }
        val manager = manager(transport)

        val error = runCatching {
            manager.register("new@example.com", "password-123")
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().startsWith("注册成功，但自动登录失败"))
        assertFalse(manager.isLoggedIn)
    }

    // ---------- 提前刷新 ----------

    @Test
    fun proactiveRefreshHappensInsideMargin() = runBlocking {
        clock = 2_000_000L
        val user = parseCloudUser(userJson())!!
        val initial = CloudSession(
            accessToken = "ACCESS_OLD",
            refreshToken = "REFRESH_OLD",
            expiresAtMillis = clock + CloudAuthManager.DEFAULT_REFRESH_MARGIN_MS + 1_000L,
            user = user,
        )
        val store = MemoryTokenStore(initial)
        val transport = FakeTransport { request ->
            assertEquals("$baseUrl/api/auth/token", request.url)
            assertEquals("REFRESH_OLD", requestBody(request).getString("refresh_token"))
            HttpResponse(
                200,
                tokenJson("ACCESS_NEW", "REFRESH_OLD", expiresIn = 240),
            )
        }
        val manager = manager(transport, store)

        // 距离过期还有 121 秒：不刷新。
        assertEquals("ACCESS_OLD", manager.accessTokenForRequest())
        assertEquals(0, transport.requests.size)

        // 前进 2 秒后只剩 119 秒：进入 120 秒刷新窗口，先换新令牌。
        clock += 2_000L
        assertEquals("ACCESS_NEW", manager.accessTokenForRequest())
        assertEquals(1, transport.requests.size)
        assertEquals("ACCESS_NEW", store.saved?.accessToken)
        assertEquals(clock + 240_000L, store.saved?.expiresAtMillis)
    }

    @Test
    fun refreshDelayMatchesExpiryMinusMargin() {
        assertEquals(
            1_000L,
            refreshDelayMs(expiresAtMillis = 5_000L, nowMillis = 2_000L, marginMs = 2_000L),
        )
        assertEquals(
            0L,
            refreshDelayMs(expiresAtMillis = 5_000L, nowMillis = 4_000L, marginMs = 2_000L),
        )
        assertTrue(needsRefresh(expiresAtMillis = 4_000L, nowMillis = 2_000L, marginMs = 2_000L))
        assertFalse(needsRefresh(expiresAtMillis = 4_001L, nowMillis = 2_000L, marginMs = 2_000L))
    }

    // ---------- 401 单飞刷新 + 重试 ----------

    @Test
    fun unauthorizedTriggersSingleRefreshThenRetriesOnce() = runBlocking {
        clock = 3_000_000L
        val initial = CloudSession(
            accessToken = "ACCESS_OLD",
            refreshToken = "REFRESH_OLD",
            expiresAtMillis = clock + 1_800_000L,
            user = parseCloudUser(userJson())!!,
        )
        val tokenCalls = AtomicInteger()
        val transport = FakeTransport { request ->
            when (request.url) {
                "$baseUrl/api/auth/token" -> {
                    tokenCalls.incrementAndGet()
                    HttpResponse(200, tokenJson("ACCESS_NEW", "REFRESH_OLD"))
                }
                else -> HttpResponse(404, "")
            }
        }
        val manager = manager(transport, MemoryTokenStore(initial))

        // 两次并发请求都先拿到旧令牌，然后同时 401；只允许刷新一次。
        val barrier = CompletableDeferred<Unit>()
        val entered = AtomicInteger()
        val seenTokens = mutableListOf<String>()
        val results = listOf(
            async(Dispatchers.Default) {
                manager.withAuthorizedToken { token ->
                    synchronized(seenTokens) { seenTokens += token }
                    if (entered.incrementAndGet() == 2) barrier.complete(Unit)
                    barrier.await()
                    if (token == "ACCESS_OLD") throw ApiException("expired", 401, "unauthorized")
                    "ok:$token"
                }
            },
            async(Dispatchers.Default) {
                manager.withAuthorizedToken { token ->
                    synchronized(seenTokens) { seenTokens += token }
                    if (entered.incrementAndGet() == 2) barrier.complete(Unit)
                    barrier.await()
                    if (token == "ACCESS_OLD") throw ApiException("expired", 401, "unauthorized")
                    "ok:$token"
                }
            },
        ).awaitAll()

        assertEquals(listOf("ok:ACCESS_NEW", "ok:ACCESS_NEW"), results)
        assertEquals(1, tokenCalls.get())
        assertEquals(2, seenTokens.count { it == "ACCESS_OLD" })
        assertEquals(2, seenTokens.count { it == "ACCESS_NEW" })
    }

    @Test
    fun retryHappensOnlyOnceWhenRefreshTokenIsAlsoRejected() = runBlocking {
        clock = 4_000_000L
        val initial = CloudSession(
            accessToken = "ACCESS_OLD",
            refreshToken = "REFRESH_OLD",
            expiresAtMillis = clock + 1_800_000L,
            user = parseCloudUser(userJson())!!,
        )
        val transport = FakeTransport {
            HttpResponse(401, """{"detail":"会话已过期，请重新登录"}""")
        }
        val store = MemoryTokenStore(initial)
        var expired = 0
        val manager = manager(transport, store, expired = { expired += 1 })
        val calls = AtomicInteger()

        val error = runCatching {
            manager.withAuthorizedToken {
                calls.incrementAndGet()
                throw ApiException("expired", 401)
            }
        }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(1, calls.get())
        assertEquals(1, transport.requests.size)
        assertFalse(manager.isLoggedIn)
        assertTrue(store.cleared)
        assertEquals(1, expired)
    }

    // ---------- 退出 ----------

    @Test
    fun logoutClearsStorageAndBestEffortNotifiesServer() = runBlocking {
        val transport = FakeTransport { request ->
            if (request.url.endsWith("/login/token")) {
                HttpResponse(200, tokenJson("ACCESS_OUT", "REFRESH_OUT"))
            } else {
                HttpResponse(200, """{"ok":true,"message":"已退出登录"}""")
            }
        }
        val store = MemoryTokenStore()
        val manager = manager(transport, store)
        manager.login("me@example.com", "password")
        assertTrue(manager.isLoggedIn)

        manager.logout()

        assertFalse(manager.isLoggedIn)
        assertTrue(store.cleared)
        assertNull(store.saved)
        assertEquals("$baseUrl/api/auth/logout", transport.requests.last().url)
        assertEquals("POST", transport.requests.last().method)
    }

    @Test
    fun logoutStillClearsWhenServerIsUnreachable() = runBlocking {
        val transport = FakeTransport { request ->
            if (request.url.endsWith("/login/token")) {
                HttpResponse(200, tokenJson("ACCESS_OUT", "REFRESH_OUT"))
            } else {
                throw IOException("offline")
            }
        }
        val store = MemoryTokenStore()
        val manager = manager(transport, store)
        manager.login("me@example.com", "password")

        manager.logout()

        assertFalse(manager.isLoggedIn)
        assertTrue(store.cleared)
    }

    // ---------- 令牌不出现在日志里 ----------

    @Test
    fun loggerNeverReceivesTokensOrPasswords() = runBlocking {
        clock = 5_000_000L
        val accessSecret = "ACCESS_SECRET_DO_NOT_LOG_123456"
        val refreshSecret = "REFRESH_SECRET_DO_NOT_LOG_987654"
        val password = "PASSWORD_DO_NOT_LOG_abcdef"
        val logs = mutableListOf<String>()
        var tokenCall = 0
        val transport = FakeTransport { request ->
            when (request.url) {
                "$baseUrl/api/auth/login/token" -> HttpResponse(
                    200,
                    tokenJson(accessSecret, refreshSecret, expiresIn = 60),
                )
                "$baseUrl/api/auth/token" -> {
                    tokenCall += 1
                    HttpResponse(
                        200,
                        tokenJson("ACCESS_REFRESHED_$tokenCall", refreshSecret, expiresIn = 60),
                    )
                }
                else -> HttpResponse(200, """{"ok":true}""")
            }
        }
        val manager = manager(transport, MemoryCloudTokenStoreForLogs(), logs = logs)

        manager.login("me@example.com", password)
        clock += 60_000L
        manager.accessTokenForRequest()
        manager.logout()

        assertTrue(logs.isNotEmpty())
        assertTrue(logs.any { it.startsWith("cloud login ok") })
        assertTrue(logs.any { it.startsWith("cloud logout") })
        logs.forEach { line ->
            listOf(accessSecret, refreshSecret, password, "ACCESS_REFRESHED", "Bearer")
                .forEach { secret -> assertFalse("日志泄露了 $secret：$line", line.contains(secret)) }
        }
    }

    private class MemoryCloudTokenStoreForLogs : CloudTokenStore {
        private var saved: CloudSession? = null
        override fun load(): CloudSession? = saved
        override fun save(session: CloudSession) { saved = session }
        override fun clear() { saved = null }
    }

    // ---------- 持久化 ----------

    @Test
    fun sessionRoundTripsThroughJsonWithoutHeroics() {
        val user = parseCloudUser(userJson())!!
        val session = CloudSession("ACCESS", "REFRESH", 123_456L, user)

        val restored = parseCloudSession(session.toJson())

        assertNotNull(restored)
        assertEquals("ACCESS", restored?.accessToken)
        assertEquals("REFRESH", restored?.refreshToken)
        assertEquals(123_456L, restored?.expiresAtMillis)
        assertEquals("fluxuser", restored?.user?.username)
        assertFalse(session.toString().contains("ACCESS"))
        assertFalse(session.toString().contains("REFRESH"))
    }

    @Test
    fun malformedSessionJsonIsRejected() {
        assertNull(parseCloudSession(JSONObject().put("access_token", "a")))
        assertNull(
            parseCloudSession(
                JSONObject()
                    .put("access_token", "a")
                    .put("refresh_token", "b")
                    .put("expires_at", 1L),
            ),
        )
    }

    // ---------- /me 与 providers ----------

    @Test
    fun meParsesIdentitiesAndProviders() = runBlocking {
        val transport = FakeTransport { request ->
            assertEquals("$baseUrl/api/auth/me", request.url)
            assertEquals("Bearer ACCESS", request.headers["Authorization"])
            HttpResponse(
                200,
                JSONObject()
                    .put("user", userJson())
                    .put(
                        "identities",
                        JSONArray().put(
                            JSONObject()
                                .put("provider", "github")
                                .put("provider_email", "me@example.com")
                                .put("provider_login", "octocat")
                                .put("created_at", "2026-11-16T00:00:00Z"),
                        ),
                    )
                    .put("providers", JSONObject().put("github", true).put("google", false))
                    .toString(),
            )
        }
        val manager = manager(transport, MemoryTokenStore(sessionWithAccess("ACCESS")))

        val me = manager.fetchMe()

        assertEquals("fluxuser", me.user.username)
        assertEquals(1, me.identities.size)
        assertEquals("octocat", me.identities.first().providerLogin)
        assertEquals(true, me.providers["github"])
    }

    @Test
    fun providersEndpointUsesNoAuthorization() = runBlocking {
        val transport = FakeTransport { request ->
            assertEquals("$baseUrl/api/auth/providers", request.url)
            assertFalse(request.headers.containsKey("Authorization"))
            HttpResponse(200, """{"github":true,"google":false}""")
        }
        val client = CloudClient(transport) { baseUrl }

        val providers = client.providers()

        assertTrue(providers.github)
        assertFalse(providers.google)
    }

    private fun sessionWithAccess(access: String): CloudSession = CloudSession(
        accessToken = access,
        refreshToken = "REFRESH",
        expiresAtMillis = clock + 1_800_000L,
        user = parseCloudUser(userJson())!!,
    )

    // ---------- 表单 / 地址校验 ----------

    @Test
    fun formValidationMatchesServerRules() {
        assertNull(CloudValidation.email("me@example.com"))
        assertEquals("请输入邮箱", CloudValidation.email(""))
        assertEquals("请输入正确的邮箱地址", CloudValidation.email("not-an-email"))

        assertNull(CloudValidation.newPassword("12345678"))
        assertEquals("密码至少 8 位", CloudValidation.newPassword("1234567"))
        assertEquals("密码不能超过 128 位", CloudValidation.newPassword("x".repeat(129)))

        assertNull(CloudValidation.optionalUsername(""))
        assertNull(CloudValidation.optionalUsername("flux.user-1"))
        assertNotNull(CloudValidation.optionalUsername("ab"))
        assertNotNull(CloudValidation.optionalUsername("bad name"))

        assertEquals("请输入邮箱或用户名", CloudValidation.loginIdentifier("  "))
        assertEquals("邮箱或用户名至少 3 个字符", CloudValidation.loginIdentifier("ab"))
    }

    @Test
    fun accountBaseUrlAcceptsHttpAndHttpsOnly() {
        assertEquals("https://flux.qiuli55.top", normalizeAccountBaseUrl("https://flux.qiuli55.top/"))
        assertEquals("http://127.0.0.1:8500", normalizeAccountBaseUrl(" http://127.0.0.1:8500 "))
        assertNull(normalizeAccountBaseUrl(""))
        assertNull(normalizeAccountBaseUrl("flux.qiuli55.top"))
        assertNull(normalizeAccountBaseUrl("ftp://flux.qiuli55.top"))
        assertNull(normalizeAccountBaseUrl("https://flux.qiuli55.top?x=1"))
        assertNull(normalizeAccountBaseUrl("https://user:pass@flux.qiuli55.top"))
    }
}
