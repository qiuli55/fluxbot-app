package top.qiuli55.fluxbot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 线性图标集合：按 fluxbot-demo/js/icons.js 的 24×24 几何重绘。 */
enum class FIcon {
    Back, Close, Plus, ChevronRight, ChevronDown, Search, Grid, More, Check, Trash,
    Copy, Send, Spark, Clock, Layers, Key, Shield, Monitor, Bell, Palette, Globe,
    Vibrate, Help, Doc, Flag, Logout, Link, Lock, User, Wallet, Contact, External,
    Refresh, Pencil, ArrowUpRight, Chat, Cpu, Mic, Heart,
}

@Composable
fun FIcon(
    icon: FIcon,
    size: Dp = 20.dp,
    color: Color = Flux.text,
    stroke: Float = 1.7f,
    modifier: Modifier = Modifier,
    baseline: Boolean = false,
) {
    // 高定规范：与文字并排的图标下移 0.125em，贴合中文视觉基线。
    val shift = if (baseline) size * 0.125f else 0.dp
    Canvas(modifier.offset(y = shift).size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) { drawIcon(icon, color, stroke) }
    }
}

private fun DrawScope.drawIcon(icon: FIcon, color: Color, w: Float) {
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun path(block: Path.() -> Unit) {
        val p = Path().apply(block)
        drawPath(p, color, style = stroke)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

    fun circle(cx: Float, cy: Float, r: Float) =
        drawCircle(color, r, Offset(cx, cy), style = stroke)

    fun dot(cx: Float, cy: Float, r: Float) =
        drawCircle(color, r, Offset(cx, cy), style = Fill)

    fun rect(l: Float, t: Float, r: Float, b: Float, rad: Float) =
        drawRoundRect(color, Offset(l, t), Size(r - l, b - t), CornerRadius(rad), style = stroke)

    when (icon) {
        FIcon.Back -> path { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
        FIcon.Close -> { line(6.6f, 6.6f, 17.4f, 17.4f); line(17.4f, 6.6f, 6.6f, 17.4f) }
        FIcon.Plus -> { line(12f, 5.2f, 12f, 18.8f); line(5.2f, 12f, 18.8f, 12f) }
        FIcon.ChevronRight -> path { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }
        FIcon.ChevronDown -> path { moveTo(5.5f, 9.5f); lineTo(12f, 16f); lineTo(18.5f, 9.5f) }
        FIcon.Search -> { circle(11f, 11f, 6.2f); line(15.6f, 15.6f, 20f, 20f) }
        FIcon.Grid -> {
            rect(3.5f, 3.5f, 10.5f, 10.5f, 2.4f); rect(13.5f, 3.5f, 20.5f, 10.5f, 2.4f)
            rect(3.5f, 13.5f, 10.5f, 20.5f, 2.4f); rect(13.5f, 13.5f, 20.5f, 20.5f, 2.4f)
        }
        FIcon.More -> { dot(5.8f, 12f, 1.4f); dot(12f, 12f, 1.4f); dot(18.2f, 12f, 1.4f) }
        FIcon.Check -> path { moveTo(5f, 12.6f); lineTo(9.8f, 17.4f); lineTo(19f, 7.2f) }
        FIcon.Trash -> {
            line(4.5f, 7f, 19.5f, 7f)
            path { moveTo(9f, 7f); lineTo(9f, 4.8f); lineTo(15f, 4.8f); lineTo(15f, 7f) }
            path { moveTo(6.6f, 7f); lineTo(7.8f, 20.4f); lineTo(16.2f, 20.4f); lineTo(17.4f, 7f) }
            line(10.3f, 10.6f, 10.7f, 17f); line(13.7f, 10.6f, 13.3f, 17f)
        }
        FIcon.Copy -> {
            rect(8.5f, 8.5f, 20f, 20f, 2.6f)
            path {
                moveTo(15.5f, 8.5f); lineTo(15.5f, 6.2f)
                quadraticBezierTo(15.5f, 4.2f, 13.5f, 4.2f)
                lineTo(6.2f, 4.2f)
                quadraticBezierTo(4.2f, 4.2f, 4.2f, 6.2f)
                lineTo(4.2f, 13.5f)
                quadraticBezierTo(4.2f, 15.5f, 6.2f, 15.5f)
                lineTo(8.5f, 15.5f)
            }
        }
        FIcon.Send -> path {
            moveTo(4.6f, 11.9f); lineTo(20f, 4.4f); lineTo(13.6f, 20f)
            lineTo(11.4f, 13.2f); close()
        }
        FIcon.Spark -> path {
            moveTo(12f, 3f); lineTo(13.6f, 9f); lineTo(19.5f, 10.5f); lineTo(13.6f, 12f)
            lineTo(12f, 18f); lineTo(10.4f, 12f); lineTo(4.5f, 10.5f); lineTo(10.4f, 9f); close()
        }
        FIcon.Clock -> { circle(12f, 12f, 8.2f); path { moveTo(12f, 7.4f); lineTo(12f, 12.2f); lineTo(15.8f, 14.4f) } }
        FIcon.Layers -> {
            path { moveTo(12f, 3.4f); lineTo(20.6f, 8f); lineTo(12f, 12.6f); lineTo(3.4f, 8f); close() }
            path { moveTo(3.4f, 12f); lineTo(12f, 16.6f); lineTo(20.6f, 12f) }
            path { moveTo(3.4f, 16f); lineTo(12f, 20.6f); lineTo(20.6f, 16f) }
        }
        FIcon.Key -> {
            circle(8.4f, 8.4f, 3.6f)
            line(11f, 11f, 20f, 20f); line(17.4f, 17.4f, 19.4f, 15.4f); line(15f, 15f, 16.8f, 13.2f)
        }
        FIcon.Shield -> path {
            moveTo(12f, 3.2f); lineTo(20f, 6.2f); lineTo(20f, 12f)
            cubicTo(20f, 16.9f, 16.4f, 19.9f, 12f, 20.8f)
            cubicTo(7.6f, 19.9f, 4f, 16.9f, 4f, 12f)
            lineTo(4f, 6.2f); close()
        }
        FIcon.Monitor -> { rect(2.6f, 4f, 21.4f, 16.2f, 2.2f); line(9f, 20.4f, 15f, 20.4f); line(12f, 16.2f, 12f, 20.4f) }
        FIcon.Bell -> {
            path {
                moveTo(6f, 17.2f); lineTo(6f, 11f)
                cubicTo(6f, 7.7f, 8.7f, 5f, 12f, 5f)
                cubicTo(15.3f, 5f, 18f, 7.7f, 18f, 11f)
                lineTo(18f, 17.2f); lineTo(19.8f, 19.2f); lineTo(4.2f, 19.2f); close()
            }
            path { moveTo(9.8f, 19.6f); cubicTo(10.1f, 21f, 13.9f, 21f, 14.2f, 19.6f) }
        }
        FIcon.Palette -> {
            circle(12f, 12f, 8.6f)
            dot(8.4f, 9.6f, 1.15f); dot(13.4f, 8.2f, 1.15f); dot(16f, 12.4f, 1.15f); dot(10.2f, 15.6f, 1.15f)
        }
        FIcon.Globe -> {
            circle(12f, 12f, 8.6f)
            path {
                moveTo(12f, 3.4f)
                cubicTo(8.6f, 7f, 8.6f, 17f, 12f, 20.6f)
                cubicTo(15.4f, 17f, 15.4f, 7f, 12f, 3.4f)
                close()
            }
            line(3.6f, 12f, 20.4f, 12f)
        }
        FIcon.Vibrate -> {
            rect(9f, 5.6f, 15f, 18.4f, 2.2f)
            line(5.4f, 8.4f, 5.4f, 15.6f); line(2.8f, 10f, 2.8f, 14f)
            line(18.6f, 8.4f, 18.6f, 15.6f); line(21.2f, 10f, 21.2f, 14f)
        }
        FIcon.Help -> {
            circle(12f, 12f, 8.6f)
            path {
                moveTo(9.4f, 9.4f)
                cubicTo(9.4f, 7.4f, 10.6f, 6.4f, 12.1f, 6.4f)
                cubicTo(13.7f, 6.4f, 14.8f, 7.5f, 14.8f, 9f)
                cubicTo(14.8f, 10.9f, 12.4f, 11f, 12.4f, 13.4f)
            }
            dot(12.4f, 16.8f, 1.05f)
        }
        FIcon.Doc -> {
            path { moveTo(6.6f, 3.4f); lineTo(14f, 3.4f); lineTo(17.8f, 7.2f); lineTo(17.8f, 20.6f); lineTo(6.6f, 20.6f); close() }
            path { moveTo(14f, 3.6f); lineTo(14f, 7.4f); lineTo(17.6f, 7.4f) }
            line(9.4f, 12f, 15f, 12f); line(9.4f, 15.6f, 15f, 15.6f)
        }
        FIcon.Flag -> {
            line(6.2f, 3.4f, 6.2f, 20.6f)
            path { moveTo(6.2f, 4.4f); lineTo(18.4f, 4.4f); lineTo(15.4f, 8.4f); lineTo(18.4f, 12.4f); lineTo(6.2f, 12.4f) }
        }
        FIcon.Logout -> {
            path {
                moveTo(13.6f, 4f); lineTo(6f, 4f); lineTo(6f, 20f); lineTo(13.6f, 20f)
            }
            line(10.4f, 12f, 20.6f, 12f)
            path { moveTo(17.6f, 9f); lineTo(20.6f, 12f); lineTo(17.6f, 15f) }
        }
        FIcon.Link -> {
            path {
                moveTo(10.2f, 13.8f)
                cubicTo(11.7f, 15.3f, 14.3f, 15.3f, 15.8f, 13.8f)
                lineTo(18.4f, 11.2f)
                cubicTo(19.9f, 9.7f, 19.9f, 7.1f, 18.4f, 5.6f)
                cubicTo(16.9f, 4.1f, 14.3f, 4.1f, 12.8f, 5.6f)
                lineTo(11.4f, 7f)
            }
            path {
                moveTo(13.8f, 10.2f)
                cubicTo(12.3f, 8.7f, 9.7f, 8.7f, 8.2f, 10.2f)
                lineTo(5.6f, 12.8f)
                cubicTo(4.1f, 14.3f, 4.1f, 16.9f, 5.6f, 18.4f)
                cubicTo(7.1f, 19.9f, 9.7f, 19.9f, 11.2f, 18.4f)
                lineTo(12.6f, 17f)
            }
        }
        FIcon.Lock -> {
            rect(4.6f, 10.2f, 19.4f, 20.6f, 2.6f)
            path {
                moveTo(8f, 10.2f); lineTo(8f, 7.8f)
                cubicTo(8f, 5.6f, 9.8f, 3.8f, 12f, 3.8f)
                cubicTo(14.2f, 3.8f, 16f, 5.6f, 16f, 7.8f)
                lineTo(16f, 10.2f)
            }
            dot(12f, 15.2f, 1.35f)
        }
        FIcon.User -> {
            circle(12f, 8.2f, 3.9f)
            path {
                moveTo(4.4f, 20.6f)
                cubicTo(4.4f, 16.2f, 7.8f, 13.6f, 12f, 13.6f)
                cubicTo(16.2f, 13.6f, 19.6f, 16.2f, 19.6f, 20.6f)
            }
        }
        FIcon.Wallet -> {
            rect(3f, 6.2f, 21f, 19.4f, 2.6f)
            path { moveTo(3.2f, 10.4f); lineTo(20.8f, 10.4f) }
            line(16.4f, 14.9f, 18.4f, 14.9f)
        }
        FIcon.Contact -> {
            rect(3f, 4.6f, 21f, 20.2f, 2.6f)
            circle(9.2f, 9.8f, 2.4f)
            path {
                moveTo(5.6f, 16.8f)
                cubicTo(5.6f, 14.6f, 7.2f, 13.4f, 9.2f, 13.4f)
                cubicTo(11.2f, 13.4f, 12.8f, 14.6f, 12.8f, 16.8f)
            }
            line(15.6f, 9.4f, 18.8f, 9.4f); line(15.6f, 13.4f, 18.8f, 13.4f); line(15.6f, 17f, 18.2f, 17f)
        }
        FIcon.External -> {
            path { moveTo(14f, 4.2f); lineTo(20f, 4.2f); lineTo(20f, 10.2f) }
            line(20f, 4.2f, 10.8f, 13.4f)
            path { moveTo(18.6f, 13.6f); lineTo(18.6f, 20.2f); lineTo(4f, 20.2f); lineTo(4f, 5.6f); lineTo(10.6f, 5.6f) }
        }
        FIcon.Refresh -> {
            drawArc(
                color = color, startAngle = 40f, sweepAngle = 285f, useCenter = false,
                topLeft = Offset(4f, 4f), size = Size(16f, 16f), style = stroke,
            )
            path { moveTo(17.4f, 2.8f); lineTo(17.4f, 6.8f); lineTo(13.4f, 6.8f) }
        }
        FIcon.Pencil -> {
            path {
                moveTo(4f, 20f); lineTo(5.2f, 15.2f); lineTo(15.8f, 4.6f)
                cubicTo(16.9f, 3.5f, 18.5f, 3.5f, 19.4f, 4.6f)
                cubicTo(20.4f, 5.6f, 20.4f, 7.2f, 19.2f, 8.4f)
                lineTo(8.6f, 18.6f); close()
            }
            line(13.6f, 6.8f, 17.2f, 10.4f)
        }
        FIcon.ArrowUpRight -> {
            line(6f, 18f, 18f, 6f)
            path { moveTo(9f, 6f); lineTo(18f, 6f); lineTo(18f, 15f) }
        }
        FIcon.Chat -> path {
            moveTo(4f, 5.2f); lineTo(20f, 5.2f); lineTo(20f, 16.2f); lineTo(11.6f, 16.2f)
            lineTo(7f, 20.2f); lineTo(7f, 16.2f); lineTo(4f, 16.2f); close()
        }
        FIcon.Cpu -> {
            rect(7f, 7f, 17f, 17f, 2f)
            rect(10.2f, 10.2f, 13.8f, 13.8f, 0.8f)
            line(12f, 3.4f, 12f, 7f); line(12f, 17f, 12f, 20.6f)
            line(3.4f, 12f, 7f, 12f); line(17f, 12f, 20.6f, 12f)
        }
        FIcon.Mic -> {
            path {
                moveTo(12f, 3.4f)
                cubicTo(13.7f, 3.4f, 15f, 4.7f, 15f, 6.4f)
                lineTo(15f, 12f)
                cubicTo(15f, 13.7f, 13.7f, 15f, 12f, 15f)
                cubicTo(10.3f, 15f, 9f, 13.7f, 9f, 12f)
                lineTo(9f, 6.4f)
                cubicTo(9f, 4.7f, 10.3f, 3.4f, 12f, 3.4f)
                close()
            }
            drawArc(
                color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(5.8f, 5.2f), size = Size(12.4f, 12.4f), style = stroke,
            )
            line(12f, 17.6f, 12f, 21f)
        }
        FIcon.Heart -> path {
            moveTo(12f, 19.3f)
            cubicTo(10.6f, 18.1f, 4.8f, 14.2f, 4.8f, 9.9f)
            cubicTo(4.8f, 7.4f, 6.6f, 5.6f, 8.9f, 5.6f)
            cubicTo(10.4f, 5.6f, 11.4f, 6.5f, 12f, 7.7f)
            cubicTo(12.6f, 6.5f, 13.6f, 5.6f, 15.1f, 5.6f)
            cubicTo(17.4f, 5.6f, 19.2f, 7.4f, 19.2f, 9.9f)
            cubicTo(19.2f, 14.2f, 13.4f, 18.1f, 12f, 19.3f)
            close()
        }
    }
}
