package top.qiuli55.fluxbot.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos

/**
 * 高定规范：输入框聚焦不描边，改为加深双环阴影（1px 内环 + 4px 外环 + 底部投影）。
 * 内环 alpha 0.46，外环 alpha 0.12，均随聚焦状态 200ms 渐入。
 */
@Composable
private fun Modifier.fieldChrome(focused: Boolean, corner: Dp = 14.dp): Modifier {
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "fieldRing",
    )
    val shape = RoundedCornerShape(corner)
    return this
        .background(if (focused) Color(0xFF0F1317) else Flux.panel2, shape)
        .drawWithContent {
            drawContent()
            if (ring > 0.01f) {
                val r = corner.toPx()
                drawRoundRect(
                    color = Flux.accent.copy(alpha = 0.46f * ring),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(width = 1.dp.toPx()),
                )
                val gap = 3.dp.toPx()
                drawRoundRect(
                    color = Flux.accent.copy(alpha = 0.12f * ring),
                    topLeft = Offset(-gap, -gap),
                    size = Size(size.width + gap * 2, size.height + gap * 2),
                    cornerRadius = CornerRadius(r + gap),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
        }
}

/** demo 的单行输入框（sheet-input）。 */
@Composable
fun FluxInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    fontSize: TextUnit = 14.sp,
    center: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .fieldChrome(focused)
            .padding(horizontal = 14.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = fontSize,
                color = Flux.text,
                textAlign = align,
                fontFeatureSettings = "tnum",
            ),
            cursorBrush = SolidColor(Flux.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, fontSize = fontSize, color = Flux.text4, textAlign = align)
                    }
                    inner()
                }
            },
        )
    }
}

/** demo 的多行输入（sheet-textarea / import-area）。 */
@Composable
fun FluxTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .fieldChrome(focused)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = Flux.text,
                lineHeight = 20.sp,
                fontFeatureSettings = "tnum",
            ),
            cursorBrush = SolidColor(Flux.accent),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, fontSize = 14.sp, color = Flux.text4, lineHeight = 20.sp)
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * demo 的 heroFloat：7s 周期上下浮动 + 轻微旋转。
 */
fun Modifier.floaty(amplitude: Float = 6f, periodMs: Int = 7000, rotate: Float = 2f): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "floaty")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "floatyP",
    )
    val wave = (0.5f - 0.5f * cos(p.toFloat() * 2f * PI.toFloat()))
    this.graphicsLayer {
        translationY = -amplitude * wave
        rotationZ = -rotate * wave
    }
}
