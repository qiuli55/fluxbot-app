package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.ComponentAction
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.MediaRef
import top.qiuli55.fluxbot.data.mediaRef
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux

/**
 * 生成式组件渲染器（schema_version = 1）。
 *
 * image / file / audio 的来源由服务端三选一给出：https url、delivery_id+index
 * 或 upload_id。带鉴权的引用通过 [LocalMediaLoader] 注入的加载器读取；
 * 截图 / 离线 / 尚无令牌时加载器为空，继续显示原有本地占位图，不会出现
 * 空白洞、网络请求或崩溃。未知节点由解析层提前过滤，消息层再退回纯文本。
 */

private val BodyColor = Color(0xFFDBE2DF)
private val CardBg = Color(0xFF0C1013)

@Composable
internal fun ComponentView(
    node: ComponentNode,
    onAction: (ComponentAction) -> Unit = {},
) {
    when (node) {
        is ComponentNode.Card -> ComponentCard(node, onAction)
        else -> ComponentNodeView(node, onAction)
    }
}

@Composable
private fun ComponentCard(card: ComponentNode.Card, onAction: (ComponentAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, Flux.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        if (!card.title.isNullOrBlank()) {
            Text(
                card.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight(560),
                color = Flux.text,
                lineHeight = 20.sp,
            )
        }
        if (!card.subtitle.isNullOrBlank()) {
            Text(
                card.subtitle,
                fontSize = 11.5.sp,
                color = Flux.text3,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = if (card.title.isNullOrBlank()) 0.dp else 3.dp),
            )
        }
        if (!card.title.isNullOrBlank() || !card.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(9.dp))
        }
        ComponentChildren(card.children, onAction)
    }
}

/**
 * 子节点列：连续按钮自动并排成一行（「允许 / 拒绝」不会竖着堆）。
 * 同一个容器里有 2 张及以上图片时，图片收成一副叠卡，其余文字照常排在上下。
 */
@Composable
private fun ComponentChildren(children: List<ComponentNode>, onAction: (ComponentAction) -> Unit) {
    val imageStack = stackedImages(children)
    var stackRendered = false
    var index = 0
    while (index < children.size) {
        if (index > 0) Spacer(Modifier.height(9.dp))
        val child = children[index]
        if (child is ComponentNode.Button) {
            val group = ArrayList<ComponentNode.Button>()
            while (index < children.size && children[index] is ComponentNode.Button) {
                group.add(children[index] as ComponentNode.Button)
                index += 1
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.forEach { button ->
                    ActionButton(
                        label = button.label,
                        action = button.action,
                        onAction = onAction,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            continue
        }
        if (child is ComponentNode.Image && imageStack != null) {
            if (!stackRendered) {
                ImageStack(imageStack)
                stackRendered = true
            }
            index += 1
            continue
        }
        ComponentNodeView(child, onAction)
        index += 1
    }
}

@Composable
private fun ComponentNodeView(node: ComponentNode, onAction: (ComponentAction) -> Unit) {
    when (node) {
        is ComponentNode.Card -> ComponentCard(node, onAction)
        is ComponentNode.Text -> MarkdownText(node)
        is ComponentNode.Image -> ImageBubble(node)
        is ComponentNode.File -> FileBubble(node)
        is ComponentNode.Audio -> AudioBubble(node)
        is ComponentNode.Button -> ActionButton(node.label, node.action, onAction)
        is ComponentNode.Choice -> ChoiceBubble(node)
        is ComponentNode.Input -> InputBubble(node, onAction)
        is ComponentNode.Progress -> ProgressBubble(node)
        is ComponentNode.ListBlock -> ListBubble(node, onAction)
        ComponentNode.Divider -> Box(
            Modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft),
        )
        is ComponentNode.Unknown -> Text(
            if (node.type.isBlank()) "暂不支持的消息" else "暂不支持的内容：${node.type}",
            fontSize = 12.sp,
            color = Flux.text4,
        )
    }
}

// ---------------------------------------------------------------- 文本

@Composable
private fun MarkdownText(node: ComponentNode.Text) {
    val body = if (node.markdown) simpleMarkdown(node.text) else AnnotatedString(node.text)
    Text(
        body,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = BodyColor,
    )
}

/**
 * 极简 markdown：**粗体**、`行内代码`、*斜体*，并剥掉 HTML 标签。
 * 只做行内样式，不解析链接 / 图片 / 原始 HTML。
 */
internal fun simpleMarkdown(text: String): AnnotatedString {
    val cleaned = text
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</?[a-zA-Z][^>]*>"), "")
    val token = Regex("""(\*\*[^*\n]+\*\*|`[^`\n]+`|\*[^*\n]+\*)""")
    return buildAnnotatedString {
        var cursor = 0
        for (match in token.findAll(cleaned)) {
            if (match.range.first > cursor) append(cleaned.substring(cursor, match.range.first))
            val value = match.value
            when {
                value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight(600))) {
                    append(value.substring(2, value.length - 2))
                }
                value.startsWith("`") -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Flux.accent2,
                        background = Flux.accentSoft,
                    ),
                ) { append(value.substring(1, value.length - 1)) }
                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(value.substring(1, value.length - 1))
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < cleaned.length) append(cleaned.substring(cursor))
    }
}

// ---------------------------------------------------------------- 图片

@Composable
private fun ImageBubble(node: ComponentNode.Image) {
    val ratio = if (node.width > 0 && node.height > 0) {
        (node.width.toFloat() / node.height.toFloat()).coerceIn(0.75f, 1.9f)
    } else {
        16f / 10f
    }
    val ref = remember(node) { node.mediaRef() }
    val bitmap = rememberMediaBitmap(ref)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E1C20)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = node.alt.ifBlank { "图片" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            MediaScrim(Color(0xCC04100E))
        } else {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1B3A42), Color(0xFF0D1B20)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            // 太阳
            drawCircle(
                color = Color(0xFF7CF0C4).copy(alpha = 0.55f),
                radius = h * 0.13f,
                center = Offset(w * 0.74f, h * 0.28f),
            )
            // 远山
            val far = Path().apply {
                moveTo(0f, h * 0.74f)
                lineTo(w * 0.27f, h * 0.42f)
                lineTo(w * 0.48f, h * 0.66f)
                lineTo(w * 0.68f, h * 0.48f)
                lineTo(w, h * 0.72f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(far, Color(0xFF143038))
            // 近山
            val near = Path().apply {
                moveTo(0f, h * 0.86f)
                lineTo(w * 0.18f, h * 0.66f)
                lineTo(w * 0.42f, h * 0.82f)
                lineTo(w * 0.62f, h * 0.62f)
                lineTo(w * 0.86f, h * 0.84f)
                lineTo(w, h * 0.76f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(near, Color(0xFF0A181C))
            // 底部渐隐，保证说明文字可读
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC04100E)),
                    startY = h * 0.55f,
                    endY = h,
                ),
            )
        }
        }
        Text(
            node.alt.ifBlank { "图片" },
            fontSize = 12.sp,
            color = Color(0xFFE8F1ED),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 11.dp, vertical = 9.dp),
        )
        Text(
            "图片",
            fontSize = 9.5.sp,
            color = Color(0xFF04120D),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(9.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xCC9FF7D6))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------- 文件

@Composable
private fun FileBubble(node: ComponentNode.File) {
    val ref = remember(node) { node.mediaRef() }
    val loader = LocalMediaLoader.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(12.dp))
            .clickable(enabled = ref != null && loader != null) {
                val target = ref ?: return@clickable
                val media = loader ?: return@clickable
                scope.launch {
                    val opened = when (target) {
                        is MediaRef.Remote -> media.openRemoteUrl(target.url, node.mime)
                        else -> media.fetchFile(target, node.name, node.mime)
                            ?.let { media.openFile(it.file, it.mime) } ?: false
                    }
                    if (!opened) media.notifyOpenFailed()
                }
            }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ext = node.name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Flux.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(ext, fontSize = 9.sp, fontWeight = FontWeight(560), color = Flux.accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                node.name,
                fontSize = 13.sp,
                fontWeight = FontWeight(500),
                color = Flux.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Text(
                fileMeta(node),
                fontSize = 11.sp,
                color = Flux.text3,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        FIcon(FIcon.ChevronRight, size = 15.dp, color = Flux.text4)
    }
}

private fun fileMeta(node: ComponentNode.File): String {
    val kind = when {
        listOf("xlsx", "xls", "csv").contains(node.name.substringAfterLast('.', "").lowercase()) ||
            node.mime.contains("spreadsheet") -> "表格"
        node.mime == "application/pdf" || node.name.endsWith(".pdf", true) -> "PDF"
        node.mime.startsWith("image/") -> "图片"
        node.mime.startsWith("audio/") -> "音频"
        listOf("doc", "docx").contains(node.name.substringAfterLast('.', "").lowercase()) -> "文档"
        listOf("zip", "rar", "7z", "tar", "gz").contains(
            node.name.substringAfterLast('.', "").lowercase(),
        ) -> "压缩包"
        else -> "文件"
    }
    val size = if (node.size >= 0) formatBytes(node.size) else ""
    return if (size.isEmpty()) kind else "$kind · $size"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes / 1024.0).let { if (it >= 100) it.toInt().toString() else String.format("%.1f", it) }} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

// ---------------------------------------------------------------- 音频

@Composable
private fun AudioBubble(node: ComponentNode.Audio) {
    val ref = remember(node) { node.mediaRef() }
    val loader = LocalMediaLoader.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Flux.panel)
            .border(1.dp, Flux.lineSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Flux.accent)
                    .clickable(enabled = ref != null && loader != null) {
                        val target = ref ?: return@clickable
                        val media = loader ?: return@clickable
                        scope.launch {
                            val opened = when (target) {
                                is MediaRef.Remote -> media.openRemoteUrl(target.url, "audio/*")
                                else -> media.fetchFile(
                                    target,
                                    suggestedName = "voice-${node.index.coerceAtLeast(0)}",
                                )?.let { media.openFile(it.file, it.mime.ifBlank { "audio/*" }) }
                                    ?: false
                            }
                            if (!opened) media.notifyOpenFailed()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(12.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.24f, 0f)
                        lineTo(size.width, size.height * 0.5f)
                        lineTo(size.width * 0.24f, size.height)
                        close()
                    }
                    drawPath(p, Flux.ink)
                }
            }
            Spacer(Modifier.width(10.dp))
            Waveform(Modifier.weight(1f).height(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "语音 ${formatDuration(node.durationMs)}",
                fontSize = 11.sp,
                color = Flux.text2,
                fontWeight = FontWeight(460),
            )
        }
        if (node.transcript.isNotBlank()) {
            Text(
                node.transcript,
                fontSize = 12.5.sp,
                color = Flux.text2,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Waveform(modifier: Modifier = Modifier) {
    val heights = listOf(
        0.32f, 0.55f, 0.78f, 0.48f, 0.92f, 0.64f, 0.36f, 0.7f, 0.86f, 0.44f, 0.6f,
        0.95f, 0.52f, 0.3f, 0.68f, 0.82f, 0.42f, 0.74f, 0.56f, 0.34f, 0.62f, 0.88f,
        0.46f, 0.72f,
    )
    Canvas(modifier) {
        val barW = size.width / (heights.size * 1.75f)
        heights.forEachIndexed { index, h ->
            val x = index * (size.width / heights.size) + barW * 0.35f
            val barH = size.height * h
            drawRoundRect(
                color = if (index < heights.size * 0.38f) Flux.accent2 else Flux.text4,
                topLeft = Offset(x, (size.height - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

// ---------------------------------------------------------------- 按钮

@Composable
private fun ActionButton(
    label: String,
    action: ComponentAction?,
    onAction: (ComponentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val danger = label in setOf("拒绝", "取消", "删除", "都不合适")
    val primary = !danger && (
        label in setOf("允许", "接管", "提交", "保存", "确认", "重试") ||
            action?.name == "open_desktop"
        )
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        primary -> Flux.accent
        danger -> Flux.dangerSoft
        else -> Flux.panel2
    }
    val fg = when {
        primary -> Flux.ink
        danger -> Flux.danger
        else -> Flux.text
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (primary) Flux.accent else Flux.line, shape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (action?.name == "open_desktop") {
                FIcon(FIcon.Monitor, size = 14.dp, color = fg)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight(560),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- 选项 / 输入 / 进度 / 列表

@Composable
private fun ChoiceBubble(node: ComponentNode.Choice) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        node.options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Flux.panel)
                    .border(1.dp, Flux.lineSoft, RoundedCornerShape(11.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(17.dp)
                        .clip(if (node.multiple) RoundedCornerShape(5.dp) else CircleShape)
                        .border(1.4.dp, Flux.text4, if (node.multiple) RoundedCornerShape(5.dp) else CircleShape),
                )
                Spacer(Modifier.width(9.dp))
                Text(option.label, fontSize = 13.sp, color = Flux.text, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun InputBubble(node: ComponentNode.Input, onAction: (ComponentAction) -> Unit) {
    Column {
        if (node.label.isNotBlank()) {
            Text(
                node.label,
                fontSize = 12.5.sp,
                color = Flux.text2,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Flux.panel)
                .border(1.dp, Flux.line, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                node.placeholder.ifBlank { if (node.inputType == "secure") "••••••" else "请输入" },
                fontSize = 13.sp,
                color = Flux.text4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (node.inputType == "secure") {
                FIcon(FIcon.Lock, size = 15.dp, color = Flux.text3)
            }
        }
        val action = node.action
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            ActionButton(label = "提交", action = action, onAction = onAction)
        }
    }
}

@Composable
private fun ProgressBubble(node: ComponentNode.Progress) {
    val color = when (node.status) {
        "done" -> Flux.accent
        "error" -> Flux.danger
        "running" -> Flux.warn
        else -> Flux.text4
    }
    val statusText = when (node.status) {
        "done" -> "已完成"
        "error" -> "出错"
        "running" -> "进行中"
        else -> "等待中"
    }
    val fraction = when {
        node.percent >= 0 -> node.percent / 100f
        node.status == "done" || node.status == "error" -> 1f
        node.status == "running" -> 0.55f
        else -> 0f
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.label.ifBlank { "进度" },
                fontSize = 12.5.sp,
                color = Flux.text2,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (node.percent >= 0) "$statusText · ${node.percent}%" else statusText,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight(460),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Flux.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ListBubble(node: ComponentNode.ListBlock, onAction: (ComponentAction) -> Unit) {
    val imageStack = stackedImages(node.items)
    var stackRendered = false
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        node.items.forEachIndexed { index, item ->
            if (item is ComponentNode.Image && imageStack != null) {
                if (!stackRendered) {
                    ImageStack(imageStack)
                    stackRendered = true
                }
                return@forEachIndexed
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (node.ordered) {
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Flux.panel2)
                            .border(1.dp, Flux.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", fontSize = 9.5.sp, color = Flux.text3)
                    }
                } else {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Flux.accent2),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    when (item) {
                        is ComponentNode.Text -> Text(
                            if (item.markdown) simpleMarkdown(item.text) else AnnotatedString(item.text),
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = BodyColor,
                        )
                        else -> ComponentNodeView(item, onAction)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 页面级辅助

/** 「权限审批」页也会复用的分隔线。 */
@Composable
internal fun ThinDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Flux.lineSoft))
}
