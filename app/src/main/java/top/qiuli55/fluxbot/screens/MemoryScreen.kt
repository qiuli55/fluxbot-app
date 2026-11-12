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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.nav.Route
import top.qiuli55.fluxbot.ui.BottomCta
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.PageHead
import top.qiuli55.fluxbot.ui.PrimaryButton

private val PROMPT = "你是一个记忆整理助手。请把我们这次对话里关于「我」的信息整理成条目，供另一个 AI 助手长期使用。\n\n" +
    "要求：\n" +
    "1. 只写事实与偏好，不写评价；\n" +
    "2. 每条一行，以「- 」开头，不超过 30 字；\n" +
    "3. 合并重复，忽略一次性的临时内容；\n" +
    "4. 不要编造；没有就说「没有可整理的内容」。"

@Composable
fun MemoryScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val clipboard = LocalClipboardManager.current
    val bots = store.current().bots
    val target = bots.firstOrNull { it.hero } ?: bots.firstOrNull()
    var area by remember { mutableStateOf("") }
    // 高定规范：复制后换对勾，2.5s 自隐。
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    Column(Modifier.fillMaxSize().background(Flux.bg)) {
        PageHead("导入记忆", onBack = { ui.back() })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 14.dp)
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Flux.accentSoft)
                    .border(1.dp, Flux.accentLine, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                FIcon(FIcon.Spark, size = 22.dp, color = Flux.accent)
            }

            StepLabel("第 1 步", "复制下面的提示词，发给你现在用的 AI。")

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Flux.panel)
                    .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
                    .padding(start = 17.dp, end = 17.dp, top = 17.dp, bottom = 4.dp),
            ) {
                Text(
                    PROMPT,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 20.6.sp,
                    color = Flux.text2,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboard.setText(AnnotatedString(PROMPT))
                            copied = true
                            ui.haptic(6)
                            ui.showToast("提示词已复制")
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (copied) {
                        FIcon(FIcon.Check, size = 15.dp, color = Flux.accent, stroke = 2f, baseline = true)
                    } else {
                        FIcon(FIcon.Copy, size = 15.dp, color = Flux.accent, baseline = true)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (copied) "已复制" else "复制提示词",
                        fontSize = 13.sp,
                        color = Flux.accent,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            StepLabel("第 2 步", "把它生成的回答粘贴到这里。")

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Flux.panel)
                    .border(1.dp, Flux.lineSoft, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                if (area.isEmpty()) {
                    Text(
                        "每条一行，以「- 」开头。粘贴后会自动拆成一条条记忆。",
                        fontSize = 14.sp,
                        color = Flux.text4,
                        lineHeight = 22.4.sp,
                    )
                }
                BasicTextField(
                    value = area,
                    onValueChange = { area = it },
                    textStyle = TextStyle(fontSize = 14.sp, color = Flux.text, lineHeight = 22.4.sp),
                    cursorBrush = SolidColor(Flux.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Flux.panel)
                    .border(1.dp, Flux.lineSoft, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("导入到 ", fontSize = 12.5.sp, color = Flux.text2)
                if (target != null) {
                    BotAvatar(target, 26.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(target?.name ?: "Flux Bot", fontSize = 12.5.sp, color = Flux.text2)
            }

            Text(
                "导入的条目会先进入这个 Bot 的记忆，之后可以逐条删除。",
                fontSize = 11.5.sp,
                color = Flux.text4,
                lineHeight = 18.4.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }

        BottomCta {
            PrimaryButton("导入记忆", enabled = area.trim().length >= 4) {
                val lines = area.split(Regex("\\n+"))
                    .map {
                        it.replace(Regex("^\\s*[-•*]\\s*"), "")
                            .replace(Regex("^\\d+[.、]\\s*"), "")
                            .trim()
                    }
                    .filter { it.length >= 4 }
                    .take(12)

                if (lines.isEmpty()) {
                    ui.showToast("没有识别到可导入的条目")
                } else if (target == null) {
                    ui.showToast("先创建一个 Bot")
                } else if (target.remote) {
                    ui.scope.launch {
                        try {
                            lines.forEach {
                                store.api.addMemory(target.id, it, kind = "fact", relevance = "direct")
                            }
                            store.refreshBotMemories(target.id)
                            ui.back()
                            ui.push(Route.BotDetail(target.id))
                            ui.showToast("已导入 ${lines.size} 条记忆")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ui.showToast(e.message ?: "导入记忆失败")
                        }
                    }
                } else {
                    lines.forEach { store.addMemory(target.id, it, "import") }
                    ui.back()
                    ui.push(Route.BotDetail(target.id))
                    ui.showToast("已导入 ${lines.size} 条记忆")
                }
            }
        }
    }
}

@Composable
private fun StepLabel(step: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight(560), color = Flux.text2)) { append(step) }
            append(" · ")
            append(text)
        },
        fontSize = 12.5.sp,
        color = Flux.text3,
        lineHeight = 19.4.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
    )
}
