package top.qiuli55.fluxbot.ip

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import top.qiuli55.fluxbot.ui.Flux
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val MORPH_MS = 520f
private const val N = 200

private val STOP_AT = floatArrayOf(0f, 0.42f, 0.64f, 1f)

private class Pose(
    val pts: FloatArray,
    val ex: Float,
    val ey: Float,
    val erx: Float,
    val ery: Float,
    val stops: LongArray,
    val ink: Long,
) {
    companion object {
        fun of(shape: AvatarData.Shape, palette: AvatarData.Palette) = Pose(
            shape.pts.copyOf(), shape.eye.ex, shape.eye.ey, shape.eye.rx, shape.eye.ry,
            palette.stops.copyOf(), palette.ink,
        )
    }
}

private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

private fun lerpColor(a: Long, b: Long, k: Float): Long {
    val ar = ((a shr 16) and 0xFF).toFloat()
    val ag = ((a shr 8) and 0xFF).toFloat()
    val ab = (a and 0xFF).toFloat()
    val br = ((b shr 16) and 0xFF).toFloat()
    val bg = ((b shr 8) and 0xFF).toFloat()
    val bb = (b and 0xFF).toFloat()
    val r = lerp(ar, br, k).toInt()
    val g = lerp(ag, bg, k).toInt()
    val bl = lerp(ab, bb, k).toInt()
    return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or bl.toLong()
}

/**
 * 头像运行时：形体插值 + 生命感状态机。
 * 参数全部来自 fluxbot-demo/js/{blob,avatar-life}.js。
 */
private class FloRuntime(shape: AvatarData.Shape, palette: AvatarData.Palette) {

    var pts by mutableStateOf(shape.pts.copyOf())
        private set
    var ex by mutableFloatStateOf(shape.eye.ex)
        private set
    var ey by mutableFloatStateOf(shape.eye.ey)
        private set
    var erx by mutableFloatStateOf(shape.eye.rx)
        private set
    var ery by mutableFloatStateOf(shape.eye.ry)
        private set
    var stops by mutableStateOf(palette.stops.copyOf())
        private set
    var ink by mutableLongStateOf(palette.ink)
        private set

    // 生命感
    var mood by mutableStateOf("neutral")
        private set
    var blink by mutableFloatStateOf(0f)
        private set
    var glanceDx by mutableFloatStateOf(0f)
        private set
    var rotation by mutableFloatStateOf(0f)
        private set
    var hop by mutableFloatStateOf(0f)
        private set
    var pulse by mutableFloatStateOf(1f)
        private set

    /** 供绘制缓存使用：pts 变更版本号。 */
    var ptsVersion by mutableIntStateOf(0)
        private set

    private var from: Pose = Pose.of(shape, palette)
    private var to: Pose = Pose.of(shape, palette)
    private var morphAt = 0L
    private var morphing = false

    private var nextActAt = -1L
    private var act: String? = null
    private var actStart = 0L
    private var actDur = 0L
    private var actDir = 1f
    private var hopStart = -1L
    private var mouthLockedUntil = 0L

    private var cachedPath: Path? = null
    private var cachedVersion = -1

    fun morphTo(shape: AvatarData.Shape, palette: AvatarData.Palette, now: Long) {
        val target = Pose.of(shape, palette)
        if (samePose(target)) return
        from = Pose(pts.copyOf(), ex, ey, erx, ery, stops.copyOf(), ink)
        to = target
        morphAt = now
        morphing = true
    }

    private fun samePose(p: Pose): Boolean {
        if (p.ex != ex || p.ey != ey || p.erx != erx || p.ery != ery || p.ink != ink) return false
        if (!p.stops.contentEquals(stops)) return false
        if (p.pts.size != pts.size) return false
        for (i in p.pts.indices) if (p.pts[i] != pts[i]) return false
        return true
    }

    fun pop(face: String = "happy") {
        hopStart = SystemClock.uptimeMillis()
        mood = face
        mouthLockedUntil = hopStart + 640
    }

    fun tick(now: Long, working: Boolean) {
        // 形变
        if (morphing) {
            val elapsed = (now - morphAt).toFloat()
            val p = min(1f, elapsed / MORPH_MS)
            val k = if (p >= 1f) 1f else 1f - exp(-(elapsed / 1000f) * 9f)
            val np = FloatArray(from.pts.size)
            for (i in np.indices) np[i] = lerp(from.pts[i], to.pts[i], k)
            pts = np
            ptsVersion++
            ex = lerp(from.ex, to.ex, k)
            ey = lerp(from.ey, to.ey, k)
            erx = lerp(from.erx, to.erx, k)
            ery = lerp(from.ery, to.ery, k)
            stops = LongArray(4) { lerpColor(from.stops[it], to.stops[it], k) }
            ink = lerpColor(from.ink, to.ink, k)
            if (p >= 1f) {
                morphing = false
                pts = to.pts.copyOf()
                ex = to.ex; ey = to.ey; erx = to.erx; ery = to.ery
                stops = to.stops.copyOf(); ink = to.ink
            }
        }

        // 绿点呼吸（2.2s）
        pulse = 1f + 0.07f * (0.5f + 0.5f * sin((now % 2200L) / 2200f * 2f * PI.toFloat()))

        // 动作调度
        if (nextActAt < 0) nextActAt = now + Random.nextLong(180, 1400)
        if (act == null) {
            if (now >= nextActAt) startAct(now, working)
        } else {
            tickAct(now, working)
        }

        // 跳一跳
        if (hopStart >= 0) {
            val p = ((now - hopStart).toFloat() / 640f).coerceIn(0f, 1f)
            hop = -9f * sin(PI.toFloat() * min(1f, p * 1.06f)) * (1f - p * 0.26f)
            if (p >= 0.66f && mood == "happy") mood = "neutral"
            if (p >= 1f) {
                hopStart = -1
                hop = 0f
                if (mood == "happy") mood = "neutral"
            }
        }
    }

    private fun startAct(now: Long, working: Boolean) {
        val roll = Random.nextFloat()
        val blinkW = if (working) 0.38f else 0.66f
        val glanceW = blinkW + if (working) 0.32f else 0.22f
        act = when {
            roll < blinkW -> "blink"
            roll < glanceW -> "glance"
            else -> "face"
        }
        actStart = now
        actDir = if (Random.nextBoolean()) 1f else -1f
        actDur = when (act) {
            "blink" -> 240L
            "glance" -> 1100L
            else -> Random.nextLong(1300, 2400)
        }
        if (act == "face") {
            val pool = if (working || Random.nextFloat() < 0.55f) {
                listOf("happy", "curious", "surprised", "sleepy")
            } else {
                listOf("happy", "curious", "surprised")
            }
            mood = pool[Random.nextInt(pool.size)]
        }
    }

    private fun tickAct(now: Long, working: Boolean) {
        val p = ((now - actStart).toFloat() / actDur.toFloat()).coerceIn(0f, 1f)
        when (act) {
            "blink" -> {
                blink = if (p < 0.5f) p * 2f else (1f - p) * 2f
            }
            "glance" -> {
                val s = sin(PI.toFloat() * p)
                glanceDx = actDir * 2.6f * s
                rotation = actDir * 5.5f * s
            }
            "face" -> {
                if (p > 0.82f) mood = "neutral"
            }
        }
        if (p >= 1f) {
            if (act == "blink") blink = 0f
            if (act == "glance") { glanceDx = 0f; rotation = 0f }
            if (act == "face" && now < mouthLockedUntil) {
                // 跳一跳期间保持表情
            } else if (act == "face") {
                mood = "neutral"
            }
            act = null
            nextActAt = now + if (working) Random.nextLong(1900, 3900) else Random.nextLong(3200, 6400)
        }
    }

    fun draw(scope: DrawScope, showDot: Boolean) {
        val s = scope.size.minDimension
        val unit = s / 100f

        // 形体路径（0..100 单位坐标，仅在形变帧重建）
        val path = if (cachedPath == null || cachedVersion != ptsVersion) {
            buildPath(pts).also {
                cachedPath = it
                cachedVersion = ptsVersion
            }
        } else {
            cachedPath!!
        }

        scope.withTransform({
            scale(unit, unit, pivot = Offset.Zero)
            translate(0f, hop)
            rotate(rotation, pivot = Offset(50f, 50f))
        }) {
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var i = 0
            while (i < pts.size) {
                val y = pts[i + 1]
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                i += 2
            }
            if (maxY - minY < 1f) { minY = 0f; maxY = 100f }

            val brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    STOP_AT[0] to Color(stops[0]),
                    STOP_AT[1] to Color(stops[1]),
                    STOP_AT[2] to Color(stops[2]),
                    STOP_AT[3] to Color(stops[3]),
                ),
                startY = minY,
                endY = maxY,
            )
            drawPath(path, brush)
            drawPath(
                path, brush,
                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawEyes()
        }

        if (showDot) drawDot(scope, s)
    }

    private fun buildPath(pts: FloatArray): Path {
        val path = Path()
        fun px(i: Int) = pts[((i % N) * 2)]
        fun py(i: Int) = pts[((i % N) * 2 + 1)]
        path.moveTo((px(N - 1) + px(0)) / 2f, (py(N - 1) + py(0)) / 2f)
        for (i in 0 until N) {
            path.quadraticBezierTo(
                px(i), py(i),
                (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f,
            )
        }
        path.close()
        return path
    }

    private fun DrawScope.drawEyes() {
        val inkColor = Color(ink)
        val narrow = when (mood) {
            "sleepy" -> 0.36f
            "focused" -> 0.76f
            "surprised" -> 1.16f
            else -> 1f
        }
        val happy = mood == "happy"
        for (side in intArrayOf(-1, 1)) {
            val right = side > 0
            val cx = 50f + side * ex + glanceDx
            val cy = 50f + ey
            var rx = erx
            var ry = ery
            if (mood == "curious" && right) { rx *= 1.22f; ry *= 1.14f }
            if (mood == "focused") rx *= 0.9f
            ry = max(0.55f, ery * narrow * if (mood == "curious" && right) 1.14f else 1f) *
                (1f - blink * 0.92f)
            if (ry < 0.55f) ry = 0.55f

            if (happy) {
                val endX = 1.22f * rx
                val endY = 0.12f * ry - 0.313f * rx
                val ctrlY = 0.12f * ry - 2.207f * rx
                val ap = Path().apply {
                    moveTo(cx - endX, cy + endY)
                    quadraticBezierTo(cx, cy + ctrlY, cx + endX, cy + endY)
                }
                drawPath(
                    ap, inkColor,
                    style = Stroke(width = rx * 0.76f, cap = StrokeCap.Round),
                )
            } else {
                drawOval(
                    color = inkColor,
                    topLeft = Offset(cx - rx, cy - ry),
                    size = Size(rx * 2f, ry * 2f),
                )
            }
        }
    }

    private fun drawDot(scope: DrawScope, s: Float) {
        val d = (s * 0.2f).coerceIn(5f, s * 0.26f)
        val cx = 50f * (s / 100f) + 0.36f * s
        val cy = 50f * (s / 100f) + 0.36f * s
        val r = d / 2f
        scope.drawCircle(Flux.accent.copy(alpha = 0.22f), radius = r * 1.9f * pulse, center = Offset(cx, cy))
        scope.drawCircle(Flux.bg, radius = r * 1.42f, center = Offset(cx, cy))
        scope.drawCircle(Flux.accent, radius = r * pulse, center = Offset(cx, cy))
    }
}

@Composable
fun FloAvatar(
    shape: String,
    palette: String,
    size: Dp,
    modifier: Modifier = Modifier,
    working: Boolean = false,
    popSignal: Int = 0,
    showDot: Boolean = false,
) {
    val shapeData = remember(shape) { AvatarData.shape(shape) }
    val paletteData = remember(palette) { AvatarData.palette(palette) }
    val runtime = remember { FloRuntime(shapeData, paletteData) }

    LaunchedEffect(shapeData, paletteData) {
        runtime.morphTo(shapeData, paletteData, SystemClock.uptimeMillis())
    }

    LaunchedEffect(popSignal) {
        if (popSignal > 0) runtime.pop("happy")
    }

    val workingState = rememberUpdatedState(working)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { runtime.tick(SystemClock.uptimeMillis(), workingState.value) }
        }
    }

    Canvas(modifier.size(size)) {
        runtime.draw(this, showDot)
    }
}

/**
 * 用于网格/选择器：直接给定形体与配色、不播放生命感动画的静态头像。
 */
@Composable
fun FloAvatarStill(
    shape: String,
    palette: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shapeData = remember(shape) { AvatarData.shape(shape) }
    val paletteData = remember(palette) { AvatarData.palette(palette) }
    val runtime = remember { FloRuntime(shapeData, paletteData) }
    Canvas(modifier.size(size)) { runtime.draw(this, showDot = false) }
}
