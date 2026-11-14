package top.qiuli55.fluxbot.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import top.qiuli55.fluxbot.data.MediaLoader
import top.qiuli55.fluxbot.data.MediaRef

/**
 * 生产环境由 [top.qiuli55.fluxbot.nav.FluxApp] 注入真实的鉴权加载器。
 *
 * 默认 null：Paparazzi 截图与纯离线预览不会发起任何网络请求，
 * 图片继续显示本地占位图，已有快照保持稳定。
 */
internal val LocalMediaLoader = staticCompositionLocalOf<MediaLoader?> { null }

/**
 * 异步加载一张组件图片。无来源 / 无加载器 / 加载失败都返回 null，
 * 调用方据此继续绘制占位图。
 */
@Composable
internal fun rememberMediaBitmap(ref: MediaRef?): Bitmap? {
    val loader = LocalMediaLoader.current
    var bitmap by remember(ref) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref, loader) {
        bitmap = if (ref != null && loader != null) loader.loadImage(ref) else null
    }
    return bitmap
}

/** 真实图片底部的渐隐层，保证标题 / alt 文案可读。 */
@Composable
internal fun MediaScrim(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, color),
                startY = size.height * 0.55f,
                endY = size.height,
            ),
        )
    }
}
