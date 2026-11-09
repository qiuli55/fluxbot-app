package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.ip.FloAvatar
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Sheet
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.PrimaryButton
import top.qiuli55.fluxbot.ui.SecondaryButton
import top.qiuli55.fluxbot.ui.floaty

@Composable
fun AuthScreen() {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloAvatar(
                shape = "cloud",
                palette = "mint",
                size = 132.dp,
                modifier = Modifier.floaty(amplitude = 7f, periodMs = 7000, rotate = 2f),
            )
            Text(
                "FLUX BOT",
                fontSize = 12.sp,
                fontWeight = FontWeight(460),
                color = Flux.accent,
                letterSpacing = 2.2.sp,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "把事交给 Bot",
                fontSize = 24.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "用 Flux 账号登录。Bot、记忆、保管库与定时任务会在你的设备之间同步，身份由 flux.qiuli55.top 账号云统一管理。",
                fontSize = 13.sp,
                color = Flux.text3,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .widthIn(max = 280.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("使用 Flux 账号登录") { ui.sheet = Sheet.Login }
            SecondaryButton("创建另一个演示账户") { ui.sheet = Sheet.AddAccount }
        }

        Text(
            "Flux Bot 与 Flux 工作台同属 Flux 品牌，共用一套账号体系。\n登录即表示同意服务条款与隐私政策。",
            fontSize = 11.sp,
            color = Flux.text4,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 22.dp),
        )
        Spacer(Modifier.height(0.dp))
    }
}
