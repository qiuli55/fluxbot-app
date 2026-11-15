package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import top.qiuli55.fluxbot.R
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxTheme

/**
 * 登录 / 注册页的 JVM 整页截图：空表单、填写态、注册态与服务端错误态。
 * 只渲染纯展示层 [AuthContent]，不发网络请求，也不读取 Store。
 */
class AuthScreenSnapshotsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 824,
            screenHeight = 1830,
            xdpi = 320,
            ydpi = 320,
            orientation = ScreenOrientation.PORTRAIT,
            density = Density.XHIGH,
            nightMode = NightMode.NIGHT,
            softButtons = false,
            locale = "zh-rCN",
        ),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
        useDeviceResolution = true,
    )

    private val chinese = FontFamily(Font(R.font.noto_sans_sc))

    private fun page(name: String, state: AuthFormState) {
        paparazzi.snapshot(name = name) {
            FluxTheme {
                val base = LocalTextStyle.current
                CompositionLocalProvider(LocalTextStyle provides base.copy(fontFamily = chinese)) {
                    Box(Modifier.fillMaxSize().background(Flux.bg)) {
                        AuthContent(
                            state = state,
                            serverLabel = "flux.qiuli55.top",
                            onModeChange = {},
                            onIdentifierChange = {},
                            onEmailChange = {},
                            onUsernameChange = {},
                            onDisplayNameChange = {},
                            onPasswordChange = {},
                            onTogglePassword = {},
                            onSubmit = {},
                            onDemo = {},
                            onServer = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun loginEmpty() = page(
        "30-auth-login",
        AuthFormState(),
    )

    @Test
    fun loginFilled() = page(
        "31-auth-login-filled",
        AuthFormState(
            identifier = "you@example.com",
            password = "correct horse battery",
            showPassword = true,
        ),
    )

    @Test
    fun registerForm() = page(
        "32-auth-register",
        AuthFormState(
            mode = AuthFormState.MODE_REGISTER,
            email = "you@example.com",
            username = "flux.user",
            displayName = "Flux 用户",
            password = "12345678",
        ),
    )

    @Test
    fun loginServerError() = page(
        "33-auth-login-error",
        AuthFormState(
            identifier = "you@example.com",
            password = "wrong-password",
            error = "账号或密码不正确",
        ),
    )

    @Test
    fun registerLoading() = page(
        "34-auth-register-loading",
        AuthFormState(
            mode = AuthFormState.MODE_REGISTER,
            email = "you@example.com",
            username = "flux.user",
            password = "12345678",
            loading = true,
        ),
    )
}
