package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.Account
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.InitialsAvatar
import top.qiuli55.fluxbot.ui.ItemRow
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.SectionLabel

@Composable
fun SettingsScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val acc = store.current()
    val account = acc.account
    val settings = acc.settings

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("设置", onBack = { ui.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            FluxCard {
                AccountCardRow(account) { ui.push(Route.Account) }
                Hairline()
                UsageRow(account) { ui.sheet = Sheet.Usage }
                Hairline()
                ItemRow(
                    title = "管理订阅",
                    trailing = { RowValue(account.plan); Spacer(Modifier.width(6.dp)); RowChevron() },
                    onClick = { ui.sheet = Sheet.Subscription },
                )
            }

            SectionLabel("Bot")
            FluxCard {
                ItemRow(
                    title = "保管库",
                    sub = "登录信息、支付与联系信息",
                    trailing = { RowValue(acc.vault.size.toString()); Spacer(Modifier.width(6.dp)); RowChevron() },
                    showDivider = true,
                    onClick = { ui.push(Route.Vault) },
                )
                ItemRow(
                    title = "连接应用",
                    sub = "让 Bot 使用 Gmail、Notion 等",
                    trailing = { RowValue(acc.connected.size.toString()); Spacer(Modifier.width(6.dp)); RowChevron() },
                    showDivider = true,
                    onClick = { ui.push(Route.Apps) },
                )
                ItemRow(
                    title = "导入记忆",
                    sub = "从别的 AI 迁移偏好与习惯",
                    trailing = { RowChevron() },
                    showDivider = true,
                    onClick = { ui.push(Route.MemoryImport) },
                )
                ItemRow(
                    title = "权限审批",
                    sub = "哪些操作需要你先点头 · 对所有 Bot 生效",
                    trailing = { RowChevron() },
                    showDivider = true,
                    onClick = { ui.push(Route.ApprovalRules) },
                )
                ItemRow(
                    title = "连接服务器",
                    sub = "Bot 后端地址与访问令牌",
                    trailing = { RowValue(if (store.api.baseUrl.contains("instabox")) "已预填" else "自定义"); Spacer(Modifier.width(6.dp)); RowChevron() },
                    onClick = { ui.sheet = Sheet.Server },
                )
            }

            SectionLabel("偏好")
            FluxCard {
                ItemRow(
                    title = "通知",
                    sub = "任务完成和需要确认时提醒",
                    showDivider = true,
                    trailing = {
                        FluxSwitch(settings.notify) {
                            store.updateSettings { s -> s.copy(notify = it) }
                            ui.haptic(8)
                        }
                    },
                )
                ItemRow(
                    title = "时区",
                    trailing = {
                        RowValue((if (settings.autoTz) "自动 · " else "") + settings.tz)
                        Spacer(Modifier.width(6.dp))
                        RowChevron()
                    },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Timezone },
                )
                ItemRow(
                    title = "外观",
                    trailing = { RowValue(settings.appearance); Spacer(Modifier.width(6.dp)); RowChevron() },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Appearance },
                )
                ItemRow(
                    title = "语言",
                    trailing = { RowValue(settings.language); Spacer(Modifier.width(6.dp)); RowChevron() },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Language },
                )
                ItemRow(
                    title = "触感反馈",
                    sub = "长按与开关时轻微震动",
                    trailing = {
                        FluxSwitch(settings.haptics) {
                            store.updateSettings { s -> s.copy(haptics = it) }
                            ui.haptic(8)
                            ui.showToast(if (it) "触感反馈已开启" else "触感反馈已关闭")
                        }
                    },
                )
            }

            SectionLabel("设备")
            FluxCard {
                ItemRow(
                    title = "我的设备",
                    sub = "账号云同步的设备列表",
                    trailing = { RowValue("3 台在线"); Spacer(Modifier.width(6.dp)); RowChevron() },
                    onClick = { ui.sheet = Sheet.Devices },
                )
            }

            SectionLabel("支持")
            FluxCard {
                ItemRow(
                    title = "帮助中心",
                    trailing = { RowChevron() },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Info("help") },
                )
                ItemRow(
                    title = "隐私政策",
                    trailing = { RowChevron() },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Info("privacy") },
                )
                ItemRow(
                    title = "服务条款",
                    trailing = { RowChevron() },
                    showDivider = true,
                    onClick = { ui.sheet = Sheet.Info("terms") },
                )
                ItemRow(
                    title = "发送反馈",
                    trailing = { RowChevron() },
                    onClick = { ui.sheet = Sheet.Feedback },
                )
            }

            SectionLabel("账号")
            FluxCard {
                ItemRow(
                    title = "切换账户",
                    sub = "两个账号的数据完全隔离",
                    trailing = { RowChevron() },
                    showDivider = true,
                    onClick = { ui.push(Route.Account) },
                )
                ItemRow(
                    title = "退出登录",
                    titleColor = Flux.danger,
                    trailing = { FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.danger) },
                    onClick = {
                        ui.sheet = Sheet.Confirm(
                            title = "退出登录？",
                            text = "本机的对话、记忆与保管库会保留。\n下次用 Flux 账号登录后继续。",
                            action = "退出登录",
                        ) { store.logout() }
                    },
                )
            }

            Text(
                "Flux Bot 0.2.0 · 可交互演示\n账号由 flux.qiuli55.top 账号云统一管理",
                fontSize = 11.sp,
                color = Flux.text4,
                lineHeight = 18.7.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 26.dp, bottom = 10.dp),
            )
        }
    }
}

/* ---------------- 组件 ---------------- */

@Composable
private fun AccountCardRow(account: Account, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsAvatar(account.initials, 46.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.name,
                fontSize = 15.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(account.email, fontSize = 12.sp, color = Flux.text3)
        }
        FIcon(FIcon.ChevronRight, size = 16.dp, color = Flux.text4)
    }
}

@Composable
private fun UsageRow(account: Account, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("用量", fontSize = 15.sp, fontWeight = FontWeight(460), color = Flux.text)
            Spacer(Modifier.height(3.dp))
            Text("本月已用 ${account.used}% · ${account.plan}", fontSize = 12.sp, color = Flux.text3)
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Flux.line),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(account.used / 100f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Flux.accent2),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("${account.used}%", fontSize = 14.sp, color = Flux.text2)
    }
}
