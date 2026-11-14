package top.qiuli55.fluxbot.data

import java.net.URI

/**
 * 组件里 image / file / audio 的媒体来源。
 *
 * 服务端 schema_version=1 只允许三选一：
 *  - `url`        外部 https 直链（不带 App 令牌）
 *  - `upload_id`  用户上传 / Bot 登记的附件，按人鉴权读取
 *  - `delivery_id` + `index`  服务端下发文件清单里的某一项
 *
 * 这里只做纯 Kotlin 判定，不拉起 Android 框架，方便 JVM 单测。
 */
sealed interface MediaRef {

    /** 是否需要附带 `Authorization: Bearer`。 */
    val authenticated: Boolean

    data class Remote(val url: String) : MediaRef {
        override val authenticated: Boolean get() = false
    }

    data class Upload(val id: String) : MediaRef {
        override val authenticated: Boolean get() = true
    }

    data class Delivery(val id: String, val index: Int) : MediaRef {
        override val authenticated: Boolean get() = true
    }
}

/** 与 `flux/core/components/schema.py` 保持一致的 id 白名单。 */
internal val MEDIA_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

/**
 * 选出节点的媒体来源。优先级：合法 https url → upload_id → delivery_id。
 *
 * - 显式拒绝 javascript / data / file / http 等非 https 直链；
 * - 非法 id 当作没有来源，让 UI 保持占位图而不是拼出可疑路径；
 * - 服务端会把缺省的 delivery index 归一化成 0，这里同样处理。
 */
fun mediaRefFor(
    url: String = "",
    deliveryId: String = "",
    uploadId: String = "",
    index: Int = -1,
): MediaRef? {
    secureHttpsUrl(url)?.let { return MediaRef.Remote(it) }
    val upload = uploadId.trim()
    if (upload.isNotEmpty()) {
        return if (MEDIA_ID_PATTERN.matches(upload)) MediaRef.Upload(upload) else null
    }
    val delivery = deliveryId.trim()
    if (delivery.isNotEmpty()) {
        return if (MEDIA_ID_PATTERN.matches(delivery)) {
            MediaRef.Delivery(delivery, index.coerceIn(0, 9999))
        } else {
            null
        }
    }
    return null
}

/** 只有 host 合法的 https 绝对地址才会被 UI 接受。 */
internal fun secureHttpsUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank()) return null
    // 不把 user:password@host 这类内嵌凭据交给图片加载器。
    if (uri.userInfo != null) return null
    return value
}

/** 纯函数 URL 构造；app 与单测共用。 */
object MediaUrls {

    /**
     * `GET {base}/api/v1/uploads/{upload_id}[?download=true]`
     *
     * baseUrl 为空 / 协议不合法 / id 格式不对时返回 null。
     */
    fun upload(baseUrl: String, uploadId: String, download: Boolean = false): String? {
        val root = normalizedBase(baseUrl) ?: return null
        if (!MEDIA_ID_PATTERN.matches(uploadId)) return null
        return root + "/api/v1/uploads/" + uploadId + downloadQuery(download)
    }

    /**
     * `GET {base}/api/v1/deliveries/{delivery_id}/files/{index}[?download=true]`
     *
     * 服务端缺省 index 时按 0 处理；越界值不构造 URL。
     */
    fun delivery(
        baseUrl: String,
        deliveryId: String,
        index: Int,
        download: Boolean = false,
    ): String? {
        val root = normalizedBase(baseUrl) ?: return null
        if (!MEDIA_ID_PATTERN.matches(deliveryId)) return null
        if (index !in 0..9999) return null
        return root + "/api/v1/deliveries/" + deliveryId + "/files/" + index +
            downloadQuery(download)
    }

    private fun downloadQuery(download: Boolean) = if (download) "?download=true" else ""

    private fun normalizedBase(raw: String): String? {
        val base = raw.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val uri = runCatching { URI(base) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return base
    }
}

/** 把来源解析成最终请求地址；外部 https 直链原样返回。 */
fun MediaRef.resolveUrl(baseUrl: String, download: Boolean = false): String? = when (this) {
    is MediaRef.Remote -> url
    is MediaRef.Upload -> MediaUrls.upload(baseUrl, id, download)
    is MediaRef.Delivery -> MediaUrls.delivery(baseUrl, id, index, download)
}

// ---------------------------------------------------------------- 节点便捷方法

fun ComponentNode.Image.mediaRef(): MediaRef? =
    mediaRefFor(url = url, deliveryId = deliveryId, uploadId = uploadId, index = index)

fun ComponentNode.File.mediaRef(): MediaRef? =
    mediaRefFor(url = url, deliveryId = deliveryId, uploadId = uploadId, index = index)

fun ComponentNode.Audio.mediaRef(): MediaRef? =
    mediaRefFor(url = url, deliveryId = deliveryId, uploadId = uploadId, index = index)
