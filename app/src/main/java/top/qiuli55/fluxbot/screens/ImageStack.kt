package top.qiuli55.fluxbot.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import top.qiuli55.fluxbot.data.ComponentNode
import top.qiuli55.fluxbot.data.mediaRef
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import kotlin.math.roundToInt

/**
 * 同一条消息里的多图叠卡。
 *
 * 服务端 schema 不需要新增节点：当一个容器（card / list）的直接子节点里有
 * 2 个及以上 image 时，就把这些图片收成一副「相册叠卡」——最上面一张完整
 * 可见，其余的在右侧依次顺时针微旋并向右下错开，只露出边缘。单张图片仍然
 * 走 [ImageBubble] 的普通内联渲染。
 */

internal const val IMAGE_STACK_MIN = 2

/** 容器里可叠卡的图片：只有 2 张及以上才算一副叠卡。 */
internal fun stackedImages(children: List<ComponentNode>): List<ComponentNode.Image>? =
    children.filterIsInstance<ComponentNode.Image>().takeIf { it.size >= IMAGE_STACK_MIN }

/** 叠卡下方的小号中文数量标签。 */
internal fun imageCountLabel(count: Int): String = "$count 张图片"

/**
 * 叠卡翻页状态（纯 Kotlin，便于 JVM 单测）。
 *
 * [count] 同时也是全屏查看器里 HorizontalPager 的页数。
 */
internal data class ImageDeckState(val count: Int) {
    init {
        require(count > 0) { "image deck needs at least one image" }
    }

    fun clampPage(page: Int): Int = page.coerceIn(0, count - 1)

    /** 手势松手时把连续位置吸附到最近的一张。 */
    fun settlePosition(position: Float): Int = clampPage(position.roundToInt())
}

private val StackOutline = Color(0x33E7ECEA)
private const val CardRatio = 0.8f
private val PeekStep = 13.dp
private val DropStep = 6.dp
private const val RotateStep = 3.2f
private const val MaxVisibleBack = 3

@Composable
internal fun ImageStack(images: List<ComponentNode.Image>) {
    if (images.size < IMAGE_STACK_MIN) return
    val deck = remember(images.size) { ImageDeckState(images.size) }
    var viewerAt by remember(images) { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxDepth = (images.size - 1).coerceAtMost(MaxVisibleBack)
            val reserved = PeekStep * maxDepth + 30.dp
            val cardWidth = (maxWidth - reserved).coerceAtLeast(96.dp)
            val cardHeight = cardWidth / CardRatio
            val stackHeight = cardHeight + DropStep * maxDepth + 16.dp
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val scope = rememberCoroutineScope()
            val position = remember(deck) { Animatable(0f) }

            fun settle() {
                scope.launch {
                    position.animateTo(
                        targetValue = deck.settlePosition(position.value).toFloat(),
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stackHeight)
                    .pointerInput(deck, widthPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = { settle() },
                            onDragCancel = { settle() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val target = (position.value - dragAmount / widthPx)
                                        .coerceIn(0f, (deck.count - 1).toFloat())
                                    position.snapTo(target)
                                }
                            },
                        )
                    }
                    .pointerInput(deck) {
                        detectTapGestures {
                            viewerAt = deck.settlePosition(position.value)
                        }
                    },
            ) {
                // 后面的卡片先画，正面最后画；翻页时按与当前位置的距离平滑重排。
                images.indices
                    .sortedByDescending { it - position.value }
                    .forEach { index ->
                        val rel = index - position.value
                        if (rel < -1f || rel > maxDepth + 0.8f) return@forEach
                        val depth = rel.coerceAtLeast(0f)
                        val fade = if (rel < 0f) (1f + rel).coerceIn(0f, 1f) else 1f
                        val x = if (rel >= 0f) PeekStep * depth else (-26).dp * (-rel)
                        val y = if (rel >= 0f) DropStep * depth else 2.dp * (-rel)

                        Box(
                            Modifier
                                .offset(x = x, y = y)
                                .zIndex(10f - rel)
                                .width(cardWidth)
                                .height(cardHeight)
                                .graphicsLayer {
                                    rotationZ = depth * RotateStep
                                    alpha = fade
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(Flux.panel)
                                .border(1.dp, StackOutline, RoundedCornerShape(14.dp)),
                        ) {
                            PhotoPlaceholder(
                                image = images[index],
                                index = index,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
            }
        }

        Row(
            Modifier.padding(top = 9.dp, start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FIcon(FIcon.Grid, size = 12.dp, color = Flux.text3, stroke = 1.6f)
            Spacer(Modifier.width(5.dp))
            Text(imageCountLabel(images.size), fontSize = 11.5.sp, color = Flux.text3)
        }
    }

    val openAt = viewerAt
    if (openAt != null) {
        ImageStackViewer(images = images, initialPage = openAt, onDismiss = { viewerAt = null })
    }
}

/**
 * 全屏查看器：左右滑动浏览同一组图片，左上角关闭返回聊天。
 * 与叠卡共用同一套本地占位图，不加载网络资源。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageStackViewer(
    images: List<ComponentNode.Image>,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pager = rememberPagerState(
            initialPage = initialPage.coerceIn(0, images.lastIndex),
            pageCount = { images.size },
        )
        Box(Modifier.fillMaxSize().background(Color(0xFF000000))) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 64.dp),
                pageSpacing = 12.dp,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(CardRatio)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Flux.panel)
                            .border(1.dp, StackOutline, RoundedCornerShape(18.dp)),
                    ) {
                        PhotoPlaceholder(
                            image = images[page],
                            index = page,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x33E7ECEA))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    FIcon(FIcon.Close, size = 18.dp, color = Color(0xFFE7ECEA))
                }
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    fontSize = 13.sp,
                    color = Color(0xFFDBE2DF),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 本地占位图

private data class PhotoStyle(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val motif: Int,
)

private val PhotoStyles = listOf(
    PhotoStyle(Color(0xFF1D4E4A), Color(0xFF081C1B), Color(0xFF72E8C0), 0),
    PhotoStyle(Color(0xFF3A2B55), Color(0xFF110C1D), Color(0xFFB79BFF), 1),
    PhotoStyle(Color(0xFF544128), Color(0xFF1A130B), Color(0xFFF0C078), 2),
    PhotoStyle(Color(0xFF23395C), Color(0xFF0A1423), Color(0xFF86B9FF), 3),
    PhotoStyle(Color(0xFF4C2638), Color(0xFF190C13), Color(0xFFFF9BC0), 4),
)

/**
 * 每张叠卡使用不同的配色与图案，保证右侧露出的边缘一眼能区分。
 * 右下角的说明文字和中间的序号只用于本地截图 / 离线演示。
 */
@Composable
internal fun PhotoPlaceholder(
    image: ComponentNode.Image,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val style = PhotoStyles[((index % PhotoStyles.size) + PhotoStyles.size) % PhotoStyles.size]
    val label = image.alt.ifBlank { "示例照片 ${index + 1}" }
    val ref = remember(image) { image.mediaRef() }
    val bitmap = rememberMediaBitmap(ref)
    Box(modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            MediaScrim(Color(0xCC02060A))
        } else {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(style.top, style.bottom),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
            )
            when (style.motif % 5) {
                0 -> {
                    drawCircle(style.accent.copy(alpha = 0.5f), h * 0.13f, Offset(w * 0.72f, h * 0.26f))
                    val far = Path().apply {
                        moveTo(0f, h * 0.72f)
                        lineTo(w * 0.3f, h * 0.4f)
                        lineTo(w * 0.52f, h * 0.64f)
                        lineTo(w * 0.72f, h * 0.46f)
                        lineTo(w, h * 0.7f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(far, style.bottom)
                    val near = Path().apply {
                        moveTo(0f, h * 0.88f)
                        lineTo(w * 0.22f, h * 0.66f)
                        lineTo(w * 0.46f, h * 0.84f)
                        lineTo(w * 0.7f, h * 0.62f)
                        lineTo(w, h * 0.86f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(near, Color(0x66000000))
                }

                1 -> {
                    repeat(4) { ring ->
                        drawCircle(
                            color = style.accent.copy(alpha = 0.28f - ring * 0.05f),
                            radius = w * (0.18f + ring * 0.14f),
                            center = Offset(w * 0.66f, h * 0.38f),
                            style = Stroke(width = 2f + ring),
                        )
                    }
                    drawCircle(style.accent.copy(alpha = 0.6f), h * 0.07f, Offset(w * 0.66f, h * 0.38f))
                }

                2 -> {
                    val step = w * 0.16f
                    var x = -h
                    var i = 0
                    while (x < w + h) {
                        drawLine(
                            color = style.accent.copy(alpha = if (i % 2 == 0) 0.16f else 0.08f),
                            start = Offset(x, h),
                            end = Offset(x + h * 0.6f, 0f),
                            strokeWidth = w * 0.035f,
                        )
                        x += step
                        i += 1
                    }
                    drawCircle(style.accent.copy(alpha = 0.45f), h * 0.11f, Offset(w * 0.3f, h * 0.3f))
                }

                3 -> {
                    val cell = w * 0.15f
                    var row = 0
                    while (row < 6) {
                        var col = 0
                        while (col < 5) {
                            if ((row + col) % 3 == 0) {
                                drawRoundRect(
                                    color = style.accent.copy(alpha = 0.12f + (row + col) % 4 * 0.03f),
                                    topLeft = Offset(w * 0.08f + col * cell, h * 0.16f + row * cell),
                                    size = Size(cell * 0.72f, cell * 0.72f),
                                    cornerRadius = CornerRadius(cell * 0.16f),
                                )
                            }
                            col += 1
                        }
                        row += 1
                    }
                }

                else -> {
                    repeat(3) { wave ->
                        val baseY = h * (0.4f + wave * 0.16f)
                        val path = Path().apply {
                            moveTo(0f, baseY)
                            cubicTo(w * 0.25f, baseY - h * 0.12f, w * 0.5f, baseY + h * 0.12f, w, baseY - h * 0.04f)
                        }
                        drawPath(path, style.accent.copy(alpha = 0.3f - wave * 0.07f), style = Stroke(width = 3f))
                    }
                    drawCircle(style.accent.copy(alpha = 0.55f), h * 0.09f, Offset(w * 0.72f, h * 0.3f))
                }
            }
            // 底部渐隐，保证说明文字可读。
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC02060A)),
                    startY = h * 0.56f,
                    endY = h,
                ),
            )
        }

        Text(
            "${index + 1}",
            fontSize = 56.sp,
            fontWeight = FontWeight(600),
            color = style.accent.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            "示例照片 ${index + 1}",
            fontSize = 9.sp,
            color = Color(0xFF04120D),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(9.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(style.accent.copy(alpha = 0.88f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        }
        Text(
            label,
            fontSize = 12.sp,
            color = Color(0xFFE8F1ED),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 11.dp, end = 34.dp, bottom = 10.dp),
        )
    }
}
