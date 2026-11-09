package top.qiuli55.fluxbot.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.Api
import top.qiuli55.fluxbot.data.Bot
import top.qiuli55.fluxbot.ip.FloAvatarStill
import top.qiuli55.fluxbot.screens.BotAvatar
import top.qiuli55.fluxbot.ui.EmptyCard
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.FluxCard
import top.qiuli55.fluxbot.ui.FluxInput
import top.qiuli55.fluxbot.ui.FluxSwitch
import top.qiuli55.fluxbot.ui.FluxTextArea
import top.qiuli55.fluxbot.ui.InitialsAvatar
import top.qiuli55.fluxbot.ui.Pill
import top.qiuli55.fluxbot.ui.PrimaryButton
import top.qiuli55.fluxbot.ui.SegTabs
import top.qiuli55.fluxbot.ui.SecondaryButton

sealed interface Sheet {
    data object Login : Sheet
    data object AddAccount : Sheet
    data object Usage : Sheet
    data object Subscription : Sheet
    data object Devices : Sheet
    data class Computer(val botId: String) : Sheet
    data class Permissions(val botId: String) : Sheet
    data object Timezone : Sheet
    data object Appearance : Sheet
    data object Language : Sheet
    data class Info(val kind: String) : Sheet
    data object Feedback : Sheet
    data object Server : Sheet
    data class VaultAdd(val cat: String) : Sheet
    data class MemoryAdd(val botId: String) : Sheet
    data class RoutineAdd(val botId: String) : Sheet
    data class RenameBot(val botId: String) : Sheet
    data object Grid : Sheet
    data object CreateMenu : Sheet
    data object GroupCreate : Sheet
    data class GroupMembers(val botId: String) : Sheet
    data class Attach(val botId: String) : Sheet
    data class BotMenu(val botId: String) : Sheet
    data class MessageMenu(val botId: String, val msgId: String, val text: String) : Sheet
    data class MemoryMenu(val botId: String, val memId: String) : Sheet
    data class VaultMenu(val itemId: String) : Sheet
    data class Confirm(
        val title: String,
        val text: String,
        val action: String,
        val danger: Boolean = false,
        val onConfirm: () -> Unit,
    ) : Sheet
    data class DeleteAccount(val accountId: String, val name: String) : Sheet
}

private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$")

@Composable
fun SheetHost() {
    val ui = LocalUi.current
    val sheet = ui.sheet
    var last by remember { mutableStateOf<Sheet?>(null) }
    LaunchedEffect(sheet) { if (sheet != null) last = sheet }
    val shown = sheet ?: last

    // 高定规范：拖拽回弹用 1200/80 过阻尼弹簧（ζ≈1.15），零回弹。
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragY by remember { mutableFloatStateOf(0f) }
    var panelH by remember { mutableFloatStateOf(0f) }
    val maskFadePx = with(density) { 420.dp.toPx() }
    LaunchedEffect(sheet) { dragY = 0f }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = sheet != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - dragY / maskFadePx).coerceIn(0f, 1f) }
                    .background(Color(0xFF040506).copy(alpha = 0.66f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { ui.closeSheet() },
            )
        }
        AnimatedVisibility(
            visible = sheet != null,
            enter = slideInVertically(tween(260)) { it },
            exit = slideOutVertically(tween(220)) { it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = dragY }
                    .onSizeChanged { panelH = it.height.toFloat() }
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Flux.panel)
                    .border(
                        1.dp, Color(0xFF242A2F),
                        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragY = (dragY + delta).coerceAtLeast(0f)
                            },
                            onDragStopped = { velocity ->
                                val dismiss = dragY > with(density) { 96.dp.toPx() } ||
                                    velocity > with(density) { 550.dp.toPx() }
                                scope.launch {
                                    val target = if (dismiss) panelH.coerceAtLeast(1f) else 0f
                                    animate(
                                        initialValue = dragY,
                                        targetValue = target,
                                        animationSpec = spring(dampingRatio = 1.15f, stiffness = 1200f),
                                    ) { value, _ -> dragY = value }
                                    dragY = 0f
                                    if (dismiss) ui.closeSheet()
                                }
                            },
                        )
                        .padding(top = 9.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape).background(Color(0xFF2B3238)),
                    )
                }
                if (shown != null) SheetBody(shown)
            }
        }
    }
}

@Composable
private fun SheetBody(sheet: Sheet) {
    when (sheet) {
        Sheet.Login -> LoginSheet()
        Sheet.AddAccount -> AddAccountSheet()
        Sheet.Usage -> UsageSheet()
        Sheet.Subscription -> SubscriptionSheet()
        Sheet.Devices -> DevicesSheet()
        is Sheet.Computer -> ComputerSheet(sheet.botId)
        is Sheet.Permissions -> PermissionsSheet(sheet.botId)
        Sheet.Timezone -> TimezoneSheet()
        Sheet.Appearance -> AppearanceSheet()
        Sheet.Language -> LanguageSheet()
        is Sheet.Info -> InfoSheet(sheet.kind)
        Sheet.Feedback -> FeedbackSheet()
        Sheet.Server -> ServerSheet()
        is Sheet.VaultAdd -> VaultAddSheet(sheet.cat)
        is Sheet.MemoryAdd -> MemoryAddSheet(sheet.botId)
        is Sheet.RoutineAdd -> RoutineAddSheet(sheet.botId)
        is Sheet.RenameBot -> RenameBotSheet(sheet.botId)
        Sheet.Grid -> GridSheet()
        Sheet.CreateMenu -> CreateMenuSheet()
        Sheet.GroupCreate -> GroupCreateSheet()
        is Sheet.GroupMembers -> GroupMembersSheet(sheet.botId)
        is Sheet.Attach -> AttachSheet(sheet.botId)
        is Sheet.BotMenu -> BotMenuSheet(sheet.botId)
        is Sheet.MessageMenu -> MessageMenuSheet(sheet.botId, sheet.msgId, sheet.text)
        is Sheet.MemoryMenu -> MemoryMenuSheet(sheet.botId, sheet.memId)
        is Sheet.VaultMenu -> VaultMenuSheet(sheet.itemId)
        is Sheet.Confirm -> ConfirmSheet(sheet)
        is Sheet.DeleteAccount -> DeleteAccountSheet(sheet.accountId, sheet.name)
    }
}

// ---------- 通用部件 ----------

@Composable
private fun SheetFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, fontSize = 16.sp, fontWeight = FontWeight(560),
            color = Flux.text, modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Flux.panel3)
                .clickable { ui.closeSheet() },
            contentAlignment = Alignment.Center,
        ) {
            FIcon(FIcon.Close, size = 15.dp, color = Flux.text2)
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
}

@Composable
private fun SheetHint(text: String, top: Int = 10) {
    Text(
        text, fontSize = 11.5.sp, color = Flux.text4, lineHeight = 17.sp,
        modifier = Modifier.padding(top = top.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, fontSize = 11.5.sp, color = Flux.text3,
        modifier = Modifier.padding(top = 14.dp, bottom = 7.dp),
    )
}

@Composable
private fun OptionRow(
    title: String,
    sub: String? = null,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    chevron: Boolean = false,
    titleColor: Color = Flux.text,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = titleColor)
            if (sub != null) {
                Text(sub, fontSize = 11.5.sp, color = Flux.text3, modifier = Modifier.padding(top = 3.dp))
            }
        }
        when {
            selected -> FIcon(FIcon.Check, size = 18.dp, color = Flux.accent)
            chevron -> FIcon(FIcon.ChevronRight, size = 16.dp, color = Flux.text4)
        }
    }
}

@Composable
private fun MenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft),
    )
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 15.sp, color = if (danger) Flux.danger else Flux.text2)
    }
}

// ---------- 账户 ----------

@Composable
private fun LoginSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    SheetFrame("登录 Flux 账号") {
        SheetHint("选择一个演示账户。两个账户的 Bot、消息与记忆完全隔离。", top = 0)
        Spacer(Modifier.height(4.dp))
        store.state.accounts.forEach { acc ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
            OptionRow(
                title = acc.account.name,
                sub = acc.account.email,
                leading = { InitialsAvatar(acc.account.initials, 38.dp) },
                chevron = true,
            ) {
                store.login(acc.account.id)
                ui.closeSheet()
                ui.reset(Route.Home)
                ui.showToast("已登录 · ${acc.account.email}")
            }
        }
        SheetHint("账号由 flux.qiuli55.top 账号云统一管理。")
    }
}

@Composable
private fun AddAccountSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    var email by remember { mutableStateOf("") }
    SheetFrame("添加账户") {
        SheetHint("用另一个邮箱登录演示账户。", top = 0)
        Spacer(Modifier.height(12.dp))
        FluxInput(
            email, { email = it },
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
        )
        SheetHint("两个账户的数据完全隔离。")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("添加并切换", enabled = email.isNotBlank()) {
            val v = email.trim()
            if (!EMAIL_RE.matches(v)) {
                ui.showToast("邮箱格式不对")
            } else if (store.state.accounts.any { it.account.email.equals(v, true) }) {
                ui.showToast("这个账号已经在列表里")
            } else {
                store.addAccount(v, "")
                ui.closeSheet()
                ui.reset(Route.Home)
                ui.showToast("已添加并切换到 $v")
            }
        }
    }
}

// ---------- 用量 / 订阅 / 设备 ----------

@Composable
private fun UsageSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val acc = store.current().account
    val aim by animateFloatAsState(acc.used / 100f, tween(500), label = "usage")
    SheetFrame("用量") {
        Row(Modifier.padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                    val sw = 5.dp.toPx()
                    drawCircle(Flux.line, style = Stroke(sw))
                    drawArc(
                        color = Flux.accent2,
                        startAngle = -90f,
                        sweepAngle = 360f * aim,
                        useCenter = false,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                Text("${acc.used}%", fontSize = 24.sp, fontWeight = FontWeight(560), color = Flux.text)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("本月已用 ${acc.used}%", fontSize = 14.sp, color = Flux.text)
                Text(
                    "输入 1.9M token，输出 0.6M token。数据由账号云跨设备归集，包含 4 台设备。",
                    fontSize = 11.5.sp, color = Flux.text3, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        DeviceRow("这台手机", "Pixel 9 · Android 16", true, "68%")
        DeviceRow("A 机 · Linux 服务器", "Ubuntu 24.04 · 在线", true, "27%")
        DeviceRow("华为平板", "HarmonyOS NEXT · 在线", true, "12%")
        DeviceRow("MacBook Pro", "macOS 15 · 离线", false, "5%")
        SheetHint("用量每 10 分钟同步一次，按账号云的口径统计。")
        Spacer(Modifier.height(16.dp))
        SecondaryButton("管理订阅") {
            ui.sheet = Sheet.Subscription
        }
    }
}

@Composable
private fun DeviceRow(name: String, platform: String, online: Boolean, pct: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Flux.panel3),
                contentAlignment = Alignment.Center,
            ) {
                FIcon(FIcon.Monitor, size = 18.dp, color = Flux.text2)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.5.sp, fontWeight = FontWeight(460), color = Flux.text)
                Text(platform, fontSize = 11.sp, color = Flux.text3, modifier = Modifier.padding(top = 3.dp))
            }
            Text(pct, fontSize = 11.sp, color = Flux.text4)
            Text(
                if (online) "在线" else "离线",
                fontSize = 11.sp,
                color = if (online) Flux.accent else Flux.text4,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
    }
}

@Composable
private fun SubscriptionSheet() {
    val ui = LocalUi.current
    SheetFrame("订阅") {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Flux.panel2)
                .border(1.dp, Flux.line, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text("Flux Pro", fontSize = 18.sp, fontWeight = FontWeight(560), color = Flux.text)
            Text("¥ 68 / 月 · 下个账单日 12 月 1 日", fontSize = 13.sp, color = Flux.text3, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "不限量 Bot 与并行任务",
                    "账号云跨设备同步",
                    "完整的记忆与定时任务",
                    "优先使用新模型通道",
                ).forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FIcon(FIcon.Check, size = 14.dp, color = Flux.accent, baseline = true)
                        Spacer(Modifier.width(8.dp))
                        Text(f, fontSize = 12.5.sp, color = Flux.text2)
                    }
                }
            }
        }
        SheetHint("演示环境不会发起真实扣款。")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("升级到 Flux Max") { ui.showToast("演示环境暂不支持升级") }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("管理付款方式") { ui.showToast("演示环境暂不支持付款管理") }
    }
}

@Composable
private fun DevicesSheet() {
    val ui = LocalUi.current
    SheetFrame("我的设备") {
        DeviceRow("这台手机", "Pixel 9 · Android 16", true, "")
        DeviceRow("A 机 · Linux 服务器", "Ubuntu 24.04 · 在线", true, "")
        DeviceRow("华为平板", "HarmonyOS NEXT · 在线", true, "")
        DeviceRow("MacBook Pro", "macOS 15 · 离线", false, "")
        SheetHint("设备由账号云统一管理，退出登录不会移除其他设备。")
        Spacer(Modifier.height(16.dp))
        SecondaryButton("刷新设备状态") { ui.showToast("设备状态已刷新") }
    }
}

// ---------- Bot 详情相关 ----------

@Composable
private fun ComputerSheet(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId) ?: return
    val options = listOf(
        "A 机 · Linux 服务器" to "8 核 · 在线 · 适合长任务",
        "这台手机" to "Pixel 9 · 当前设备",
        "MacBook Pro" to "macOS 15 · 离线",
    )
    SheetFrame("Bot 电脑") {
        SheetHint("Bot 的任务会在这台设备上执行。", top = 0)
        Spacer(Modifier.height(4.dp))
        options.forEach { (name, sub) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
            OptionRow(name, sub, selected = bot.computer == name) {
                store.setComputer(botId, name)
                ui.closeSheet()
                ui.showToast("Bot 电脑已切换到 $name")
            }
        }
    }
}

@Composable
private fun PermissionsSheet(botId: String) {
    val store = LocalStore.current
    val bot = store.bot(botId) ?: return
    val items = listOf(
        "files" to ("读写文件" to "在 Bot 的电脑上读取与写入文件"),
        "shell" to ("执行命令" to "运行终端命令与脚本"),
        "network" to ("访问网络" to "打开网页、调用接口"),
        "apps" to ("使用连接应用" to "读取你已连接的应用数据"),
    )
    SheetFrame("权限") {
        SheetHint("随时可以收回，正在执行的任务会立即停止。", top = 0)
        Spacer(Modifier.height(4.dp))
        items.forEach { (key, pair) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pair.first, fontSize = 14.sp, color = Flux.text)
                    Text(
                        pair.second, fontSize = 11.5.sp, color = Flux.text3,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                FluxSwitch(bot.permissions[key] == true) { store.togglePermission(botId, key) }
            }
        }
    }
}

@Composable
private fun TimezoneSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val s = store.current().settings
    val zones = listOf(
        "Asia/Shanghai" to "中国标准时间 · UTC+8",
        "Asia/Tokyo" to "日本标准时间 · UTC+9",
        "UTC" to "协调世界时 · UTC+0",
        "America/Los_Angeles" to "太平洋时间 · UTC-8",
    )
    SheetFrame("时区") {
        SheetHint("定时任务按这个时区执行。", top = 0)
        Spacer(Modifier.height(4.dp))
        zones.forEach { (zone, sub) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
            OptionRow(zone, sub, selected = s.tz == zone) {
                store.updateSettings { it.copy(tz = zone, autoTz = false) }
                ui.closeSheet()
                ui.showToast("时区已更新")
            }
        }
    }
}

@Composable
private fun AppearanceSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val s = store.current().settings
    SheetFrame("外观") {
        listOf("跟随系统", "深色", "浅色").forEach { item ->
            OptionRow(item, selected = s.appearance == item) {
                store.updateSettings { it.copy(appearance = item) }
                ui.showToast("外观已设为 $item")
            }
        }
        SheetHint("演示固定为深色；浅色会在正式版开放。")
    }
}

@Composable
private fun LanguageSheet() {
    SheetFrame("语言") {
        OptionRow("简体中文", "当前语言", selected = true) {}
        SheetHint("更多语言会在正式版陆续开放。")
    }
}

@Composable
private fun InfoSheet(kind: String) {
    val title = when (kind) {
        "help" -> "帮助中心"
        "privacy" -> "隐私政策"
        "terms" -> "服务条款"
        else -> "Bot 服务条款"
    }
    val body = when (kind) {
        "help" -> "Flux Bot 是一个把任务交给 Bot 去做的客户端。你可以创建一个 Bot、给它起名字、选一个形体和配色，然后把事情写成一句话交给它。\n\nBot 会先把事情拆成步骤，再一步步执行；需要你确认时会停下来等你。记忆保存在账号云，换设备也能用。\n\n演示环境中所有任务与消息都是本地模拟数据。"
        "privacy" -> "我们只保存完成任务所必需的信息：你的账号标识、Bot 配置、对话与记忆。\n\n记忆与对话在账号云加密保存，仅你本人可以读取。Bot 在任务中读取保管库内容前会先向你确认。\n\n演示环境不会上传任何真实数据。"
        "terms" -> "使用 Flux Bot 即表示你同意：不将 Bot 用于违法用途；对 Bot 代你执行的操作负责；在涉及付款、发布内容等高风险动作前自行确认。\n\n演示环境按现状提供，不构成任何服务承诺。"
        else -> "Bot 在任务中调用第三方应用时，会遵守对应应用的授权范围。\n\n你随时可以收回权限，正在执行的任务会立即停止。\n\n由 Bot 代发的消息与评论，视为你本人的操作。"
    }
    SheetFrame(title) {
        Text(body, fontSize = 13.sp, color = Flux.text2, lineHeight = 21.sp)
        SheetHint("最后更新：2026 年 11 月")
    }
}

@Composable
private fun FeedbackSheet() {
    val ui = LocalUi.current
    var text by remember { mutableStateOf("") }
    SheetFrame("发送反馈") {
        SheetHint("遇到问题，或者想要什么功能，都可以写在这里。", top = 0)
        Spacer(Modifier.height(12.dp))
        FluxTextArea(text, { text = it }, placeholder = "写点什么…")
        SheetHint("演示环境不会真的发送，但会保留在这次会话里。")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("提交反馈", enabled = text.isNotBlank()) {
            ui.closeSheet()
            ui.showToast("反馈已提交，谢谢你")
        }
    }
}

// ---------- 服务器 ----------

@Composable
private fun ServerSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(store.api.baseUrl) }
    var token by remember { mutableStateOf(store.api.token) }
    var testing by remember { mutableStateOf(false) }

    SheetFrame("连接服务器") {
        SheetHint("Flux Bot 后端地址与访问令牌。连上后会自动拉取远端 Bot。", top = 0)
        Spacer(Modifier.height(12.dp))
        FieldLabel("服务器地址")
        Spacer(Modifier.height(8.dp))
        FluxInput(url, { url = it }, placeholder = "https://…")
        Spacer(Modifier.height(12.dp))
        FieldLabel("访问令牌")
        Spacer(Modifier.height(8.dp))
        FluxInput(token, { token = it }, placeholder = "fb_…")
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            if (testing) "连接中…" else "测试并保存",
            enabled = !testing && url.isNotBlank(),
        ) {
            testing = true
            scope.launch {
                store.api.baseUrl = url
                store.api.token = token
                val result = runCatching { store.api.health() }
                testing = false
                result.onSuccess {
                    ui.closeSheet()
                    ui.showToast("服务器已连接")
                    runCatching { store.api.listBots() }
                        .onSuccess { store.mergeRemoteBots(it) }
                }.onFailure {
                    ui.showToast(it.message ?: "连接失败")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("恢复默认地址") {
            url = Api.DEFAULT_BASE_URL
            token = Api.DEFAULT_TOKEN
        }
        SheetHint("令牌只保存在本机，测试账号已预填。")
    }
}

// ---------- 保管库 ----------

@Composable
private fun VaultAddSheet(initialCat: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var tab by remember { mutableStateOf(if (initialCat == "all") "login" else initialCat) }
    val catIndex = listOf("login", "payment", "contact").indexOf(tab).coerceAtLeast(0)
    var name by remember(tab) { mutableStateOf("") }
    var f1 by remember(tab) { mutableStateOf("") }
    var f2 by remember(tab) { mutableStateOf("") }

    SheetFrame("添加到保管库") {
        SegTabs(
            listOf("登录信息", "支付", "联系信息"),
            catIndex,
            { tab = listOf("login", "payment", "contact")[it] },
            Modifier.padding(top = 2.dp),
        )
        FieldLabel("名称")
        FluxInput(name, { name = it }, placeholder = "例如：GitHub")
        when (tab) {
            "login" -> {
                FieldLabel("网址（选填）")
                FluxInput(f1, { f1 = it }, placeholder = "github.com")
                FieldLabel("用户名")
                FluxInput(f2, { f2 = it }, placeholder = "qiuli55")
            }
            "payment" -> {
                FieldLabel("卡号")
                FluxInput(f1, { f1 = it }, placeholder = "4242 4242 4242 4242", keyboardType = KeyboardType.Number)
                FieldLabel("有效期")
                FluxInput(f2, { f2 = it }, placeholder = "12/28")
            }
            else -> {
                FieldLabel("电话")
                FluxInput(f1, { f1 = it }, placeholder = "138 0000 0000", keyboardType = KeyboardType.Phone)
                FieldLabel("邮箱（选填）")
                FluxInput(f2, { f2 = it }, placeholder = "you@example.com", keyboardType = KeyboardType.Email)
            }
        }
        SheetHint("保存之后，Bot 每次要用都会先向你确认。")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("保存到保管库", enabled = name.isNotBlank()) {
            if (name.isBlank()) {
                ui.showToast("先给它起个名字")
                return@PrimaryButton
            }
            val sub = when (tab) {
                "login" -> listOfNotNull(
                    f1.ifBlank { "已保存登录信息" },
                    f2.takeIf { it.isNotBlank() }?.let { " · $it" },
                ).joinToString("")
                "payment" -> {
                    val last4 = f1.filter { it.isDigit() }.takeLast(4).ifBlank { "0000" }
                    "•••• $last4" + (if (f2.isNotBlank()) " · 有效期 $f2" else "")
                }
                else -> {
                    val phone = if (f1.length >= 7) {
                        f1.take(3) + "****" + f1.takeLast(4)
                    } else {
                        "已保存联系信息"
                    }
                    phone + (if (f2.isNotBlank()) " · $f2" else "")
                }
            }
            store.addVault(tab, name.trim(), sub)
            ui.closeSheet()
            ui.showToast("已保存到保管库")
        }
    }
}

// ---------- 记忆 / 定时任务 ----------

@Composable
private fun MemoryAddSheet(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var text by remember { mutableStateOf("") }
    SheetFrame("添加记忆") {
        SheetHint("一条记忆是一句事实或偏好，Bot 之后会一直记得。", top = 0)
        Spacer(Modifier.height(12.dp))
        FluxTextArea(text, { text = it }, placeholder = "例如：周报按「进展 / 风险 / 下周计划」三段式输出。")
        SheetHint("记忆跟着账号走，换设备也能用。")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("添加到记忆", enabled = text.isNotBlank()) {
            store.addMemory(botId, text.trim(), "manual")
            ui.closeSheet()
            ui.showToast("已添加到记忆")
        }
    }
}

@Composable
private fun RoutineAddSheet(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("08:30") }
    var freqIndex by remember { mutableStateOf(0) }
    val freqs = listOf("每天", "工作日", "每周一")
    SheetFrame("新建定时任务") {
        FieldLabel("任务名称")
        FluxInput(title, { title = it }, placeholder = "例如：汇总科技与 AI 新闻")
        FieldLabel("执行时间")
        FluxInput(time, { time = it }, placeholder = "08:30", center = true)
        FieldLabel("重复")
        SegTabs(freqs, freqIndex, { freqIndex = it })
        SheetHint("到点自动开工。Bot 忙的时候会排队，不会打断正在跑的任务。")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("创建定时任务", enabled = title.isNotBlank()) {
            if (!Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time.trim())) {
                ui.showToast("时间格式像 08:30")
                return@PrimaryButton
            }
            store.addRoutine(botId, title.trim(), time.trim(), freqs[freqIndex])
            ui.closeSheet()
            ui.showToast("定时任务已创建")
        }
    }
}

@Composable
private fun RenameBotSheet(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId) ?: return
    var name by remember { mutableStateOf(bot.name) }
    SheetFrame("重命名") {
        FluxInput(name, { name = it }, placeholder = "Bot 名字")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("保存", enabled = name.isNotBlank()) {
            store.renameBot(botId, name.trim())
            ui.closeSheet()
            ui.showToast("已重命名为 ${name.trim()}")
        }
    }
}

@Composable
private fun GridSheet() {
    val ui = LocalUi.current
    val bots = LocalStore.current.current().bots
    SheetFrame("Bot") {
        bots.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { bot ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .clickable {
                                ui.closeSheet()
                                ui.push(Route.Chat(bot.id))
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FloAvatarStill(bot.shape, bot.palette, 48.dp)
                        Text(
                            bot.name, fontSize = 11.sp, color = Flux.text2,
                            maxLines = 1, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CreateMenuSheet() {
    val ui = LocalUi.current
    SheetFrame("新建") {
        MenuAction("新建 Bot", "从形状与配色开始，创建一只新 Bot") {
            ui.closeSheet()
            ui.push(Route.Create)
        }
        MenuAction("新建群聊", "选几个已有的 Bot 一起组队") {
            ui.sheet = Sheet.GroupCreate
        }
    }
}

@Composable
private fun MenuAction(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Flux.text)
            Text(sub, fontSize = 11.5.sp, color = Flux.text3, modifier = Modifier.padding(top = 3.dp))
        }
        FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.text4)
    }
}

@Composable
private fun PickRow(bot: Bot, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) Flux.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) Flux.accentLine else Flux.lineSoft, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotAvatar(bot, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(bot.name, fontSize = 14.sp, color = Flux.text, maxLines = 1)
            Text(
                bot.preview, fontSize = 11.5.sp, color = Flux.text3, maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) Flux.accent else Color.Transparent)
                .border(1.dp, if (selected) Flux.accent else Flux.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) FIcon(FIcon.Check, size = 13.dp, color = Flux.bg)
        }
    }
}

@Composable
private fun GroupCreateSheet() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bots = store.current().bots.filter { !it.group }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by remember { mutableStateOf("") }
    SheetFrame("新建群聊") {
        if (bots.size < 2) {
            SheetHint("至少需要 2 个 Bot 才能组成群聊，先创建几个 Bot。", top = 0)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("新建 Bot") {
                ui.closeSheet()
                ui.push(Route.Create)
            }
            return@SheetFrame
        }
        SheetHint("从已有 Bot 里选至少 2 个，任务会分给群成员处理。", top = 0)
        Spacer(Modifier.height(10.dp))
        bots.forEach { bot ->
            PickRow(bot, picked.contains(bot.id)) {
                picked = if (picked.contains(bot.id)) picked - bot.id else picked + bot.id
            }
            Spacer(Modifier.height(8.dp))
        }
        FieldLabel("群名（可留空）")
        FluxInput(name, { name = it }, placeholder = "留空自动生成")
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            "创建群聊" + if (picked.isEmpty()) "" else "（${picked.size}）",
            enabled = picked.size >= 2,
        ) {
            val id = store.addGroup(picked.toList(), name)
            if (id == null) {
                ui.showToast("至少选择 2 个 Bot")
            } else {
                ui.closeSheet()
                ui.push(Route.Chat(id))
                ui.showToast("群聊已创建")
            }
        }
    }
}

@Composable
private fun GroupMembersSheet(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val bot = store.bot(botId) ?: return
    val members = store.groupMembers(bot)
    SheetFrame("群成员 · ${members.size}") {
        members.forEach { m ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        ui.closeSheet()
                        ui.push(Route.Chat(m.id))
                    }
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotAvatar(m, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontSize = 14.sp, color = Flux.text, maxLines = 1)
                    Text(
                        m.preview, fontSize = 11.5.sp, color = Flux.text3, maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.text4)
            }
        }
    }
}

@Composable
private fun AttachSheet(botId: String) {
    val ui = LocalUi.current
    SheetFrame("添加") {
        MenuItem("记住一件事") {
            ui.sheet = Sheet.MemoryAdd(botId)
        }
        MenuItem("新建定时任务") {
            ui.sheet = Sheet.RoutineAdd(botId)
        }
        MenuItem("查看这个 Bot 的记忆") {
            ui.closeSheet()
            ui.push(Route.BotDetail(botId))
        }
    }
}

// ---------- 长按菜单 ----------

@Composable
private fun BotMenuSheet(botId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val bot = store.bot(botId) ?: return
    SheetFrame(bot.name) {
        MenuItem("重命名") { ui.sheet = Sheet.RenameBot(botId) }
        MenuItem("标记为已读") {
            store.markRead(botId)
            ui.closeSheet()
            ui.showToast("已标记为已读")
        }
        MenuItem("删除 Bot", danger = true) {
            if (bot.hero) {
                ui.closeSheet()
                ui.showToast("主 Bot 不能删除")
            } else {
                ui.sheet = Sheet.Confirm(
                    title = "删除 Bot",
                    text = "「${bot.name}」的对话、记忆与定时任务会一起删除，无法恢复。",
                    action = "删除 Bot",
                    danger = true,
                ) {
                    if (bot.remote) scope.launch { runCatching { store.api.deleteBot(botId) } }
                    store.deleteBot(botId)
                    if (ui.stack.any { it is Route.Chat && it.botId == botId } ||
                        ui.stack.any { it is Route.BotDetail && it.botId == botId }
                    ) {
                        ui.reset(Route.Home)
                    }
                    ui.showToast("已删除 ${bot.name}")
                }
            }
        }
    }
}

@Composable
private fun MessageMenuSheet(botId: String, msgId: String, text: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    val clipboard = LocalClipboardManager.current
    // 高定规范：复制后换对勾，1s 后自动收起。
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1000)
            ui.closeSheet()
            ui.showToast("已复制")
        }
    }
    SheetFrame("消息") {
        MenuItem(if (copied) "已复制" else "复制文字") {
            clipboard.setText(AnnotatedString(text))
            copied = true
            ui.haptic(6)
        }
        MenuItem("删除这条消息", danger = true) {
            store.removeMessage(botId, msgId)
            ui.closeSheet()
            ui.showToast("已删除这条消息")
        }
    }
}

@Composable
private fun MemoryMenuSheet(botId: String, memId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    SheetFrame("记忆") {
        MenuItem("删除这条记忆", danger = true) {
            store.deleteMemory(botId, memId)
            ui.closeSheet()
            ui.showToast("已删除这条记忆")
        }
    }
}

@Composable
private fun VaultMenuSheet(itemId: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    SheetFrame("保管库") {
        MenuItem("从保管库删除", danger = true) {
            store.deleteVault(itemId)
            ui.closeSheet()
            ui.showToast("已从保管库删除")
        }
    }
}

// ---------- 确认 ----------

@Composable
private fun ConfirmSheet(sheet: Sheet.Confirm) {
    val ui = LocalUi.current
    SheetFrame(sheet.title) {
        Text(sheet.text, fontSize = 13.sp, color = Flux.text2, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        if (sheet.danger) {
            DangerButton(sheet.action) {
                ui.closeSheet()
                sheet.onConfirm()
            }
        } else {
            PrimaryButton(sheet.action) {
                ui.closeSheet()
                sheet.onConfirm()
            }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton("取消") { ui.closeSheet() }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Color(0xFF1A0604))
            .border(1.dp, Color(0x66FF7A6E), RoundedCornerShape(27.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight(560), color = Flux.danger)
    }
}

@Composable
private fun DeleteAccountSheet(accountId: String, name: String) {
    val store = LocalStore.current
    val ui = LocalUi.current
    var input by remember { mutableStateOf("") }
    SheetFrame("删除账户") {
        Text(
            "这会永久删除本机上「$name」的 Bot、消息、记忆、保管库与密钥。",
            fontSize = 13.sp, color = Flux.text2, lineHeight = 20.sp,
        )
        SheetHint("账号云中的账号不会受影响，下次登录会重新同步。")
        FieldLabel("输入账号名 $name 以确认")
        FluxInput(input, { input = it }, placeholder = name)
        Spacer(Modifier.height(16.dp))
        if (input.trim() == name) {
            DangerButton("永久删除本机数据") {
                store.deleteAccount(accountId)
                ui.closeSheet()
                ui.reset(Route.Home)
                ui.showToast("已删除本机数据")
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(Flux.panel3),
                contentAlignment = Alignment.Center,
            ) {
                Text("永久删除本机数据", fontSize = 16.sp, color = Flux.text4)
            }
        }
    }
}
