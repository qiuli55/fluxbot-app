package top.qiuli55.fluxbot.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面入口纯逻辑测试：只覆盖 JSON 解析、会话合并与凭据选择，
 * 不依赖 Android 框架 / Robolectric，也不访问网络。
 */
class BotDesktopTest {

    private val botId = "A1B2C3D4-E5F6-7890-ABCD-EF0123456789"

    @Test
    fun ticketModeConfigTrustsServerConfiguredFlag() {
        val cfg = parseDesktopConfig(
            JSONObject(
                """{"configured":true,"gateway":"gw.example.com",""" +
                    """"bridge":"https://bridge.example.com","token":null,""" +
                    """"bridge_key":null,"config_url":"https://cfg.example.com/desktop.json",""" +
                    """"tunnel_updated_at":"2026-11-12T00:00:00Z"}""",
            ),
        )

        assertTrue(cfg.configured)
        assertEquals("", cfg.token)
        assertEquals("", cfg.bridgeKey)
        assertEquals("https://cfg.example.com/desktop.json", cfg.configUrl)
        assertEquals("2026-11-12T00:00:00Z", cfg.tunnelUpdatedAt)
    }

    @Test
    fun configuredFlagWinsEvenWhenAddressesAreEmpty() {
        val empty = parseDesktopConfig(
            JSONObject("""{"configured":false,"gateway":"","bridge":"","token":"","bridge_key":""}"""),
        )
        assertFalse(empty.configured)
        assertTrue(empty.gateway.isEmpty())
        assertTrue(empty.bridge.isEmpty())

        // 缺少 configured 时按未配置处理，不能凭地址非空反推
        val missingFlag = parseDesktopConfig(
            JSONObject("""{"gateway":"gw.example.com","bridge":"https://bridge.example.com"}"""),
        )
        assertFalse(missingFlag.configured)
    }

    @Test
    fun jsonNullNeverBecomesLiteralNullString() {
        val cfg = parseDesktopConfig(
            JSONObject(
                """{"configured":true,"gateway":null,"bridge":null,"token":null,""" +
                    """"bridge_key":null,"config_url":null,"tunnel_updated_at":null}""",
            ),
        )
        listOf(
            cfg.gateway,
            cfg.bridge,
            cfg.token,
            cfg.bridgeKey,
            cfg.configUrl,
            cfg.tunnelUpdatedAt,
        ).forEach { value ->
            assertNotEquals("null", value)
            assertTrue(value.isEmpty())
        }

        val session = parseDesktopSession(
            JSONObject("""{"host":null,"gateway_token":null,"bridge_key":null,"profile":null,"ticket":null}"""),
        )
        listOf(session.host, session.token, session.bridgeKey, session.profile, session.ticket).forEach {
            assertNotEquals("null", it)
            assertTrue(it.isEmpty())
        }
    }

    @Test
    fun sessionWithTicketAndNoGatewayTokenPrefersTicket() {
        val session = parseDesktopSession(
            JSONObject(
                """{"host":"gw.example.com","bridge":"https://bridge.example.com",""" +
                    """"profile":"bot1234abcd","ticket":"short-ticket","expires_at":"2026-11-12T01:00:00Z",""" +
                    """"gateway_token":null,"bridge_key":null}""",
            ),
        )

        assertEquals("", session.token)
        assertEquals("short-ticket", session.ticket)
        assertEquals("short-ticket", session.preferredCredential())
        assertEquals("", session.bridgeKey)
    }

    @Test
    fun sessionWithOnlyLongLivedTokenStillWorks() {
        val session = parseDesktopSession(
            JSONObject(
                """{"host":"gw.example.com","bridge":"https://bridge.example.com",""" +
                    """"profile":"bot1234abcd","ticket":null,"gateway_token":"gw-token","bridge_key":"bk"}""",
            ),
        )

        assertEquals("gw-token", session.token)
        assertTrue(session.ticket.isEmpty())
        assertEquals("gw-token", session.preferredCredential())
    }

    @Test
    fun profileUsesServerValueWhenPresent() {
        val config = parseDesktopConfig(JSONObject("""{"configured":true}"""))
        val session = parseDesktopSession(JSONObject("""{"profile":"server-profile"}"""))

        assertEquals("server-profile", session.alignedWith(config, botId).profile)
    }

    @Test
    fun profileFallsBackToBotPlusFirstEightHex() {
        val config = parseDesktopConfig(JSONObject("""{"configured":true}"""))
        val session = parseDesktopSession(JSONObject("""{"profile":null}"""))

        assertEquals("bota1b2c3d4", session.alignedWith(config, botId).profile)
        assertEquals("bota1b2c3d4", BotDesktop.profile(botId))
    }

    @Test
    fun sessionAddressesFallBackToGlobalConfig() {
        val config = parseDesktopConfig(
            JSONObject(
                """{"configured":true,"gateway":"gw.example.com","bridge":"https://bridge.example.com",""" +
                    """"token":"cfg-token","bridge_key":"cfg-key","config_url":"https://cfg.example.com/desktop.json"}""",
            ),
        )
        val session = parseDesktopSession(
            JSONObject("""{"host":"","bridge":null,"gateway_token":null,"bridge_key":null,"profile":""}"""),
        )

        val aligned = session.alignedWith(config, botId)
        assertEquals("gw.example.com", aligned.host)
        assertEquals("https://bridge.example.com", aligned.bridge)
        assertEquals("cfg-token", aligned.token)
        assertEquals("cfg-key", aligned.bridgeKey)
        assertEquals("bota1b2c3d4", aligned.profile)
        assertEquals("https://cfg.example.com/desktop.json", desktopConfigUrl(config))
    }

    @Test
    fun desktopConfigUrlFallsBackToBuiltInConstant() {
        val config = parseDesktopConfig(JSONObject("""{"configured":true,"config_url":null}"""))

        assertEquals(BotDesktop.CONFIG_URL, desktopConfigUrl(config))
    }

    @Test
    fun emptyPayloadIsUnconfiguredAndBlank() {
        val config = parseDesktopConfig(JSONObject())
        assertFalse(config.configured)
        assertTrue(config.gateway.isEmpty())
        assertTrue(config.configUrl.isEmpty())

        val session = parseDesktopSession(JSONObject())
        assertTrue(session.host.isEmpty())
        assertTrue(session.token.isEmpty())
        assertTrue(session.ticket.isEmpty())
        assertEquals("", session.preferredCredential())
    }

    @Test
    fun desktopErrorMessagesCoverNormalAndFailurePaths() {
        assertEquals(
            "桌面尚未配置",
            desktopErrorMessage(ApiException("not configured", 503, "desktop_not_configured")),
        )
        assertEquals("请求失败", desktopErrorMessage(ApiException("请求失败", 500)))
        assertEquals("network down", desktopErrorMessage(Exception("network down")))
        assertEquals("无法连接 Bot 桌面", desktopErrorMessage(Exception()))
    }
}
