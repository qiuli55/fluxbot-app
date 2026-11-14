package top.qiuli55.fluxbot.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 已落盘的附件 + 服务端给出的 MIME，用于交给系统应用打开。 */
data class FetchedMedia(val file: File, val mime: String)

/**
 * 组件媒体的鉴权加载器。
 *
 * - `upload_id` / `delivery_id` 走 [Api.baseUrl] 拼出的 `/api/v1` 地址，
 *   并附带与 [Api.request] 相同的 Bearer 令牌；
 * - 外部 `https` 直链不带令牌；
 * - 任何失败（无令牌 / 404 / 解码失败 / 断网）都返回 null，让 UI 继续显示
 *   现有的本地占位图，不崩溃也不留下空白洞。
 *
 * 令牌只出现在请求头里，不会进入 URL、文件名、缓存键或日志。
 */
class MediaLoader(context: Context, private val api: Api) {

    private val appContext = context.applicationContext

    /** 只缓存解码后的 Bitmap；键是去掉令牌后的请求地址。 */
    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 外部直链原样返回；服务端引用需要登录令牌，没有就返回 null。 */
    fun resolveUrl(ref: MediaRef, download: Boolean = false): String? {
        if (ref is MediaRef.Remote) return ref.url
        if (api.token.isBlank()) return null
        return ref.resolveUrl(api.baseUrl, download)
    }

    suspend fun loadImage(ref: MediaRef): Bitmap? = withContext(Dispatchers.IO) {
        val url = runCatching { resolveUrl(ref) }.getOrNull() ?: return@withContext null
        synchronized(bitmapCache) { bitmapCache.get(url) }?.let { return@withContext it }

        val conn = openConnection(url, authenticated = ref.authenticated, accept = "image/*")
            ?: return@withContext null
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val bytes = conn.inputStream.use { it.readAtMost(MAX_INLINE_BYTES) }
            val bitmap = decodeSampled(bytes) ?: return@withContext null
            synchronized(bitmapCache) { bitmapCache.put(url, bitmap) }
            bitmap
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 把附件下载到应用缓存目录。文件 / 语音走 `?download=true`，
     * 返回 null 时调用方保持原有卡片、不弹出系统面板。
     */
    suspend fun fetchFile(
        ref: MediaRef,
        suggestedName: String,
        mime: String = "",
        download: Boolean = true,
    ): FetchedMedia? = withContext(Dispatchers.IO) {
        if (ref is MediaRef.Remote) return@withContext null
        val url = runCatching { resolveUrl(ref, download) }.getOrNull() ?: return@withContext null
        val conn = openConnection(url, authenticated = true, accept = "*/*") ?: return@withContext null
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val dir = File(appContext.cacheDir, MEDIA_DIR).apply { mkdirs() }
            val file = File(dir, mediaFileName(url, suggestedName))
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.length() <= 0L) {
                file.delete()
                return@withContext null
            }
            val contentType = conn.contentType?.substringBefore(';')?.trim().orEmpty()
            FetchedMedia(file, mime.ifBlank { contentType })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** 用系统应用打开缓存文件；没有处理程序时退回分享面板。 */
    fun openFile(file: File, mime: String): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(appContext, fileProviderAuthority(appContext), file)
        }.getOrNull() ?: return false
        val type = mime.ifBlank { "application/octet-stream" }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        if (startActivity(view)) return true
        val share = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        return startActivity(Intent.createChooser(share, "打开文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** 外部 https 直链交给浏览器 / 系统播放器，不附带任何令牌。 */
    fun openRemoteUrl(url: String, mime: String = ""): Boolean {
        if (secureHttpsUrl(url) == null) return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivity(intent)
    }

    fun notifyOpenFailed() {
        runCatching {
            Toast.makeText(appContext, "无法打开附件，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startActivity(intent: Intent): Boolean =
        runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)

    private fun openConnection(
        url: String,
        authenticated: Boolean,
        accept: String?,
    ): HttpURLConnection? {
        val token = if (authenticated) runCatching { api.token }.getOrDefault("") else ""
        if (authenticated && token.isBlank()) return null
        return runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                accept?.let { setRequestProperty("Accept", it) }
                if (authenticated) {
                    // 只放在请求头；令牌绝不拼进 URL 或日志。
                    setRequestProperty("Authorization", "Bearer $token")
                    api.accountId?.takeIf { it.isNotBlank() }?.let {
                        setRequestProperty("X-Flux-Account", it)
                    }
                }
            }
        }.getOrNull()
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
            .getOrNull()
    }

    companion object {
        private const val MEDIA_DIR = "media"
        private const val MAX_INLINE_BYTES = 24 * 1024 * 1024
        private const val MAX_IMAGE_DIM = 1600

        fun fileProviderAuthority(context: Context): String =
            "${context.packageName}.media"
    }
}

/**
 * 按最长边取 2 的幂采样，避免把整张原图读进内存。
 * 纯函数，便于 JVM 单测。
 */
internal fun sampleSizeFor(
    width: Int,
    height: Int,
    maxDim: Int = 1600,
): Int {
    if (width <= 0 || height <= 0 || maxDim <= 0) return 1
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= maxDim) {
        longest /= 2
        sample *= 2
    }
    return sample
}

/** 只保留最后一段文件名，去掉路径分隔符与不能进文件名的字符。 */
internal fun sanitizeFileName(raw: String, fallback: String = "attachment"): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    val cleaned = buildString {
        base.forEach { ch ->
            append(
                when {
                    ch.isLetterOrDigit() -> ch
                    ch == '.' || ch == '_' || ch == '-' -> ch
                    else -> '_'
                },
            )
        }
    }.trim('.', '_').take(80)
    return cleaned.ifBlank { fallback }
}

/** 缓存文件名带 URL 哈希前缀，避免不同附件同名互相覆盖；哈希不含令牌。 */
internal fun mediaFileName(url: String, suggestedName: String): String {
    val safe = sanitizeFileName(suggestedName)
    val hash = url.hashCode().toLong().and(0xffffffffL).toString(16)
    return "$hash-$safe"
}

/** 读取最多 [maxBytes]，防止异常响应把内存撑爆。 */
private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > maxBytes) throw IOException("response too large")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
