package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.RemoteConnector
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.InitialsAvatar
import top.qiuli55.fluxbot.ui.ItemRow
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.SectionLabel

@Composable
fun AccountScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val acc = store.current()
    val account = acc.account
    val accounts = store.state.accounts
    // 连接器状态在服务端（按账号隔离），进页面拉一次；失败时仍给出 GitHub 入口，
    // 用户点进去还能重试，不把整块藏起来。
    var connectors by remember { mutableStateOf<List<RemoteConnector>>(emptyList()) }
    var connectorsFailed by remember { mutableStateOf(false) }
    // 弹层关闭后再拉一次：刚在弹层里连接/断开的连接器状态要立刻反映到这一行。
    val sheetOpen = ui.sheet != null
    LaunchedEffect(account.id, sheetOpen) {
        if (sheetOpen) return@LaunchedEffect
        connectorsFailed = false
        runCatching { store.api.listConnectors() }
            .onSuccess { connectors = it }
            .onFailure { connectorsFailed = true }
    }
    val github = connectors.firstOrNull { it.id == "github" } ?: RemoteConnector(
        id = "github",
        name = "GitHub",
        description = "代码仓库、Issue、PR 与 Actions",
        connected = false,
        account = "",
        displayName = "",
        avatarUrl = "",
        scopes = emptyList(),
        connectedAt = "",
    )

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("账户", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            FluxCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialsAvatar(account.initials, 54.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontSize = 15.sp, fontWeight = FontWeight(560), color = Flux.text)
                        Spacer(Modifier.height(2.dp))
                        Text(account.email, fontSize = 12.sp, color = Flux.text3)
                    }
                    PlanPill(account.plan)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Flux.panel)
                    .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FIcon(FIcon.Shield, size = 17.dp, color = Flux.accent, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "身份由 Flux 账号云统一管理，Flux Bot 不保存密码。",
                    fontSize = 12.sp,
                    color = Flux.text3,
                    lineHeight = 19.8.sp,
                )
            }

            SectionLabel("当前账户")
            FluxCard {
                ItemRow(
                    title = "用量",
                    sub = "本月已用 ${account.used}%",
                    trailing = {
                        RowValue("${account.used}%")
                        Spacer(Modifier.width(6.dp))
                        RowChevron()
                    },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Usage },
                )
                ItemRow(
                    title = "管理订阅",
                    trailing = { RowValue(account.plan); Spacer(Modifier.width(6.dp)); RowChevron() },
                    onClick = { ui.sheet = Sheet.Subscription },
                )
            }

            SectionLabel("连接器")
            FluxCard {
                ItemRow(
                    title = github.name,
                    sub = when {
                        github.connected -> "已连接 · @${github.account}"
                        connectorsFailed -> "状态获取失败，点进去重试"
                        else -> "未连接 · ${github.description}"
                    },
                    leading = {
                        InitialsAvatar(
                            if (github.connected && github.account.isNotEmpty()) {
                                github.account.take(2).uppercase()
                            } else {
                                "GH"
                            },
                            36.dp,
                            accent = github.connected,
                        )
                    },
                    trailing = {
                        RowValue(if (github.connected) "已连接" else "连接")
                        Spacer(Modifier.width(6.dp))
                        RowChevron()
                    },
                    onClick = { ui.sheet = Sheet.Connector(github.id) },
                )
            }
            Text(
                "外部连接器都从这里管理，授权按当前账户隔离。",
                fontSize = 11.5.sp,
                color = Flux.text4,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )

            SectionLabel("切换账户")
            FluxCard {
                accounts.forEach { a ->
                    val active = a.account.id == account.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (active) {
                                    ui.showToast("已经是当前账户")
                                } else {
                                    val email = a.account.email
                                    store.switchAccount(a.account.id)
                                    ui.reset(Route.Home)
                                    ui.showToast("已切换到 $email")
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialsAvatar(a.account.initials, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.account.name, fontSize = 15.sp, fontWeight = FontWeight(460), color = Flux.text)
                            Spacer(Modifier.height(3.dp))
                            Text(a.account.email, fontSize = 12.sp, color = Flux.text3)
                        }
                        if (active) {
                            FIcon(FIcon.Check, size = 17.dp, color = Flux.accent, baseline = true)
                        }
                    }
                    Hairline()
                }
                ItemRow(
                    title = "添加账户",
                    sub = "用另一个邮箱登录演示账户",
                    leading = { FIcon(FIcon.Plus, size = 18.dp, color = Flux.accent) },
                    onClick = { ui.sheet = Sheet.AddAccount },
                )
            }

            SectionLabel("安全")
            FluxCard {
                ItemRow(
                    title = "退出登录",
                    sub = "本机数据保留，下次登录继续",
                    titleColor = Flux.danger,
                    trailing = { FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.danger) },
                    showDivider = true,
                    onClick = {
                        ui.sheet = Sheet.Confirm(
                            title = "退出登录？",
                            text = "本机的对话、记忆与保管库会保留。\n下次用 Flux 账号登录后继续。",
                            action = "退出登录",
                        ) { store.logout() }
                    },
                )
                ItemRow(
                    title = "删除账户",
                    sub = "仅清空本机数据，账号云不受影响",
                    titleColor = Flux.danger,
                    trailing = { FIcon(FIcon.Trash, size = 15.dp, color = Flux.danger) },
                    onClick = { ui.sheet = Sheet.DeleteAccount(account.id, account.name) },
                )
            }

            Text(
                "删除后，本机的 Bot、对话与记忆无法恢复；账号云上的账户仍然保留。",
                fontSize = 11.5.sp,
                color = Flux.text4,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )

            Text(
                "两个演示账户的 Bot、消息、记忆、保管库与密钥完全隔离。",
                fontSize = 11.sp,
                color = Flux.text4,
                lineHeight = 18.7.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 26.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun PlanPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Flux.accentSoft)
            .border(1.dp, Flux.accentLine, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight(460), color = Flux.accent)
    }
}
