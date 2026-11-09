package top.qiuli55.fluxbot.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计令牌：与 fluxbot-demo/css/app.css 一一对应。 */
object Flux {
    val bg = Color(0xFF050608)
    val bg2 = Color(0xFF090B0D)
    val panel = Color(0xFF0B0E11)
    val panel2 = Color(0xFF101418)
    val panel3 = Color(0xFF161A1E)
    val line = Color(0xFF1B2024)
    val lineSoft = Color(0xFF14181C)

    val text = Color(0xFFE7ECEA)
    val text2 = Color(0xFF9CA6A4)
    val text3 = Color(0xFF6C7674)
    val text4 = Color(0xFF49514F)

    val accent = Color(0xFF3DF5A8)
    val accent2 = Color(0xFF22C98A)
    val accentSoft = Color(0x1F3DF5A8)
    val accentLine = Color(0x573DF5A8)
    val warn = Color(0xFFE8B25E)
    val danger = Color(0xFFFF7A6E)
    val dangerSoft = Color(0x1FFF7A6E)

    val ink = Color(0xFF04120D)

    object Radius {
        val sm = 12.dp
        val md = 18.dp
        val lg = 24.dp
        val sheet = 26.dp
    }

    object Dim {
        val screenMax = 520.dp
    }
}

object FluxType {
    /** 高定规范：数字锁 tabular-nums 防抖。 */
    private const val TNUM = "tnum"

    /** 高定规范：正文开 pretty 断行，避免尾行孤字。 */
    private val pretty = LineBreak.Paragraph

    // 高定规范：暗色底上字重全降 40（600→560 / 500→460 / 400→360）。
    val title = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight(560), color = Flux.text,
        lineHeight = 30.sp, fontFeatureSettings = TNUM, lineBreak = pretty,
    )
    val head = TextStyle(
        fontSize = 17.sp, fontWeight = FontWeight(560), color = Flux.text,
        fontFeatureSettings = TNUM, lineBreak = pretty,
    )
    val body = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight(360), color = Flux.text,
        lineHeight = 20.sp, fontFeatureSettings = TNUM, lineBreak = pretty,
    )
    val rowTitle = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight(460), color = Flux.text,
        fontFeatureSettings = TNUM, lineBreak = pretty,
    )
    val sub = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight(360), color = Flux.text2,
        lineHeight = 17.sp, fontFeatureSettings = TNUM, lineBreak = pretty,
    )
    val section = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight(460), color = Flux.text3,
        letterSpacing = 0.2.sp, fontFeatureSettings = TNUM, lineBreak = pretty,
    )
    val tiny = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight(360), color = Flux.text4,
        fontFeatureSettings = TNUM, lineBreak = pretty,
    )
}

private val scheme = darkColorScheme(
    primary = Flux.accent,
    onPrimary = Flux.ink,
    background = Flux.bg,
    onBackground = Flux.text,
    surface = Flux.panel,
    onSurface = Flux.text,
    surfaceVariant = Flux.panel2,
    onSurfaceVariant = Flux.text2,
    outline = Flux.line,
    error = Flux.danger,
)

@Composable
fun FluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            bodyLarge = FluxType.body,
            bodyMedium = FluxType.body,
            titleMedium = FluxType.rowTitle,
            labelSmall = FluxType.section,
        ),
    ) {
        content()
    }
}
