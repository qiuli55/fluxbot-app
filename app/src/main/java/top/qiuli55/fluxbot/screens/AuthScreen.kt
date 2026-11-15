package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.CloudProviders
import top.qiuli55.fluxbot.data.CloudValidation
import top.qiuli55.fluxbot.data.CustomTabsOAuthBrowser
import top.qiuli55.fluxbot.data.OAuthProvider
import top.qiuli55.fluxbot.data.OAuthState
import top.qiuli55.fluxbot.ip.FloAvatar
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.PrimaryButton
import top.qiuli55.fluxbot.ui.SecondaryButton
import top.qiuli55.fluxbot.ui.SegTabs
import top.qiuli55.fluxbot.ui.floaty

/** 登录 / 注册表单的纯展示状态；截图测试直接构造它，不依赖 Store。 */
internal data class AuthFormState(
    val mode: Int = MODE_LOGIN,
    val identifier: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val error: String = "",
    val identifierError: String = "",
    val emailError: String = "",
    val usernameError: String = "",
    val passwordError: String = "",
) {
    companion object {
        const val MODE_LOGIN = 0
        const val MODE_REGISTER = 1
    }
}

/** 第三方登录按钮的展示状态；providers 为 null 表示尚未探测，先按可用展示。 */
internal data class AuthOAuthView(
    val provider: OAuthProvider? = null,
    val loading: Boolean = false,
    val hint: String = "",
    val providers: CloudProviders? = null,
) {
    fun available(target: OAuthProvider): Boolean = when (target) {
        OAuthProvider.GitHub -> providers?.github != false
        OAuthProvider.Google -> providers?.google != false
    }
}

@Composable
fun AuthScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browser = remember(context) { CustomTabsOAuthBrowser(context) }
    var form by remember { mutableStateOf(AuthFormState()) }
    var oauthView by remember { mutableStateOf(AuthOAuthView()) }

    val serverLabel = store.cloudSettings.baseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    // 不阻塞首屏：探测失败时保持 null，两个按钮仍按可用展示。
    LaunchedEffect(Unit) {
        val available = runCatching { store.cloud.providers() }.getOrNull()
        if (available != null) {
            oauthView = oauthView.copy(providers = available)
        }
    }

    LaunchedEffect(store.oauthState) {
        when (val state = store.oauthState) {
            OAuthState.Idle -> {
                oauthView = oauthView.copy(provider = null, loading = false, hint = "")
            }
            is OAuthState.Starting -> {
                oauthView = oauthView.copy(
                    provider = state.provider,
                    loading = true,
                    hint = "正在跳转 ${state.provider.label}…",
                )
            }
            is OAuthState.Completing -> {
                oauthView = oauthView.copy(
                    provider = state.provider,
                    loading = true,
                    hint = "正在完成登录…",
                )
            }
            is OAuthState.Failed -> {
                oauthView = oauthView.copy(provider = null, loading = false, hint = "")
                form = form.copy(loading = false, error = state.message)
                store.consumeOAuthState()
            }
        }
    }

    fun submit() {
        if (form.loading || oauthView.loading) return
        if (form.mode == AuthFormState.MODE_LOGIN) {
            val identifierError = CloudValidation.loginIdentifier(form.identifier)
            val passwordError = CloudValidation.loginPassword(form.password)
            if (identifierError != null || passwordError != null) {
                form = form.copy(
                    error = "",
                    identifierError = identifierError.orEmpty(),
                    passwordError = passwordError.orEmpty(),
                )
                return
            }
            val identifier = form.identifier.trim()
            val password = form.password
            form = form.copy(loading = true, error = "")
            scope.launch {
                try {
                    store.loginCloud(identifier, password)
                    ui.showToast("已登录 · ${store.cloud.cachedUser()?.email.orEmpty()}")
                    ui.reset(Route.Home)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    form = form.copy(loading = false, error = e.message ?: "登录失败")
                }
            }
        } else {
            val emailError = CloudValidation.email(form.email)
            val usernameError = CloudValidation.optionalUsername(form.username)
            val passwordError = CloudValidation.newPassword(form.password)
            if (emailError != null || usernameError != null || passwordError != null) {
                form = form.copy(
                    error = "",
                    emailError = emailError.orEmpty(),
                    usernameError = usernameError.orEmpty(),
                    passwordError = passwordError.orEmpty(),
                )
                return
            }
            val email = form.email.trim()
            val password = form.password
            val username = form.username.trim().takeIf { it.isNotEmpty() }
            val displayName = form.displayName.trim().takeIf { it.isNotEmpty() }
            form = form.copy(loading = true, error = "")
            scope.launch {
                try {
                    store.registerCloud(email, password, username, displayName)
                    ui.showToast("注册成功，已登录")
                    ui.reset(Route.Home)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    form = form.copy(loading = false, error = e.message ?: "注册失败")
                }
            }
        }
    }

    AuthContent(
        state = form,
        oauth = oauthView,
        serverLabel = serverLabel,
        onModeChange = { mode ->
            if (!form.loading && !oauthView.loading) {
                form = AuthFormState(mode = mode)
            }
        },
        onIdentifierChange = {
            form = form.copy(identifier = it, identifierError = "")
        },
        onEmailChange = { form = form.copy(email = it, emailError = "") },
        onUsernameChange = { form = form.copy(username = it, usernameError = "") },
        onDisplayNameChange = { form = form.copy(displayName = it) },
        onPasswordChange = { form = form.copy(password = it, passwordError = "") },
        onTogglePassword = { form = form.copy(showPassword = !form.showPassword) },
        onSubmit = { submit() },
        onOAuth = { provider ->
            val busy = form.loading || oauthView.loading
            if (!busy && oauthView.available(provider) && store.oauthState is OAuthState.Idle) {
                form = form.copy(error = "")
                val url = store.startOAuth(provider)
                if (url != null) {
                    val opened = runCatching { browser.open(url) }.isSuccess
                    if (!opened) store.onOAuthLaunchFailed(provider)
                }
            }
        },
        onServer = { ui.sheet = Sheet.CloudServer },
    )
}

@Composable
internal fun AuthContent(
    state: AuthFormState,
    oauth: AuthOAuthView,
    serverLabel: String,
    onModeChange: (Int) -> Unit,
    onIdentifierChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onOAuth: (OAuthProvider) -> Unit,
    onServer: () -> Unit,
) {
    val login = state.mode == AuthFormState.MODE_LOGIN
    val busy = state.loading || oauth.loading
    Column(
        Modifier
            .fillMaxSize()
            .background(Flux.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloAvatar(
                shape = "cloud",
                palette = "mint",
                size = 108.dp,
                modifier = Modifier.floaty(amplitude = 7f, periodMs = 7000, rotate = 2f),
            )
            Text(
                "FLUX BOT",
                fontSize = 12.sp,
                fontWeight = FontWeight(460),
                color = Flux.accent,
                letterSpacing = 2.2.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                if (login) "登录 Flux 账号" else "创建 Flux 账号",
                fontSize = 24.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (login) {
                    "Bot、记忆、保管库与定时任务会在你的设备之间同步，身份由 flux.qiuli55.top 账号云统一管理。"
                } else {
                    "注册后立即登录。账号只用于 Flux 服务，密码不会保存在本机。"
                },
                fontSize = 12.5.sp,
                color = Flux.text3,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 292.dp),
            )
        }

        Spacer(Modifier.height(22.dp))
        SegTabs(
            items = listOf("登录", "注册"),
            selected = state.mode,
            onSelect = { if (!busy) onModeChange(it) },
        )
        Spacer(Modifier.height(16.dp))

        if (login) {
            FieldBlock("邮箱或用户名", state.identifierError) {
                FluxInput(
                    state.identifier,
                    onIdentifierChange,
                    placeholder = "you@example.com 或 username",
                    keyboardType = KeyboardType.Email,
                )
            }
            Spacer(Modifier.height(12.dp))
            FieldBlock("密码", state.passwordError) {
                PasswordInput(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    visible = state.showPassword,
                    onToggle = onTogglePassword,
                )
            }
        } else {
            FieldBlock("邮箱", state.emailError) {
                FluxInput(
                    state.email,
                    onEmailChange,
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                )
            }
            Spacer(Modifier.height(12.dp))
            FieldBlock("用户名（可选）", state.usernameError) {
                FluxInput(
                    state.username,
                    onUsernameChange,
                    placeholder = "3-64 位字母、数字、点、下划线、连字符",
                    keyboardType = KeyboardType.Ascii,
                )
            }
            Spacer(Modifier.height(12.dp))
            FieldBlock("显示名称（可选）") {
                FluxInput(
                    state.displayName,
                    onDisplayNameChange,
                    placeholder = "别人看到的名字",
                )
            }
            Spacer(Modifier.height(12.dp))
            FieldBlock("密码", state.passwordError) {
                PasswordInput(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    visible = state.showPassword,
                    onToggle = onTogglePassword,
                )
            }
            Text(
                "密码至少 8 位；注册成功后会自动用同一组凭据登录。",
                fontSize = 11.sp,
                color = Flux.text4,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        if (state.error.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                state.error,
                fontSize = 12.5.sp,
                color = Flux.danger,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Flux.dangerSoft)
                    .border(1.dp, Flux.danger.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            when {
                state.loading && login -> "正在登录…"
                state.loading -> "正在注册…"
                login -> "登录"
                else -> "注册并登录"
            },
            enabled = !busy,
            onClick = onSubmit,
        )
        Spacer(Modifier.height(10.dp))
        ProviderLoginButton(
            label = "GitHub 登录",
            provider = OAuthProvider.GitHub,
            oauth = oauth,
            busy = busy,
            onOAuth = onOAuth,
        )
        Spacer(Modifier.height(10.dp))
        ProviderLoginButton(
            label = "Google 登录",
            provider = OAuthProvider.Google,
            oauth = oauth,
            busy = busy,
            onOAuth = onOAuth,
        )
        if (oauth.hint.isNotBlank()) {
            Text(
                oauth.hint,
                fontSize = 12.sp,
                color = Flux.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "账号服务器：$serverLabel（点按修改）",
            fontSize = 12.sp,
            color = Flux.text3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onServer() }
                .padding(vertical = 6.dp),
        )
        Text(
            "Flux Bot 与 Flux 工作台同属 Flux 品牌，共用一套账号体系。\n登录即表示同意服务条款与隐私政策。",
            fontSize = 11.sp,
            color = Flux.text4,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun ProviderLoginButton(
    label: String,
    provider: OAuthProvider,
    oauth: AuthOAuthView,
    busy: Boolean,
    onOAuth: (OAuthProvider) -> Unit,
) {
    val available = oauth.available(provider)
    val enabled = !busy && available
    SecondaryButton(
        text = if (available) label else "$label · 暂不可用",
        enabled = enabled,
    ) {
        if (enabled) onOAuth(provider)
    }
}

@Composable
private fun FieldBlock(
    label: String,
    error: String = "",
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight(460),
            color = Flux.text3,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        )
        content()
        if (error.isNotBlank()) {
            Text(
                error,
                fontSize = 11.5.sp,
                color = Flux.danger,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    FluxInput(
        value,
        onValueChange,
        placeholder = "请输入密码",
        keyboardType = KeyboardType.Password,
        password = !visible,
        trailing = {
            Text(
                if (visible) "隐藏" else "显示",
                fontSize = 12.sp,
                fontWeight = FontWeight(460),
                color = Flux.accent,
                modifier = Modifier
                    .clickable { onToggle() }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
            )
        },
    )
}
