package com.wikg.aidaily.update

import com.wikg.aidaily.data.model.UpdateRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface DownloadEvent {
    data class Progress(val bytes: Long, val total: Long, val source: String) : DownloadEvent
    data class Done(val file: File, val sha256: String, val source: String) : DownloadEvent
}

class ChecksumMismatchException(message: String) : IOException(message)

/**
 * 下载 APK：按顺序尝试候选地址（GitHub 原始地址 → 镜像），边下边算 SHA-256。
 * - 连接 / 首包 10 秒、读 20 秒无数据即切换下一个；
 * - 下载开始 12 秒后平均速度低于 [slowBytesPerSec] 且还有备选时，主动切到下一个（国内直连 GitHub 常见龟速）；
 * - 有期望 SHA-256 时不一致则删除文件、换下一个来源；全部失败抛出最后的错误（校验失败优先报告）。
 * 只写入 `<dest>.part`，校验通过才改名为 dest；取消时删除 .part。
 */
class ApkDownloader(
    private val client: OkHttpClient = defaultClient(),
    private val slowBytesPerSec: Long = 48 * 1024,
    private val slowAfterMs: Long = 12_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun download(urls: List<String>, dest: File, expectedSha256: String?, expectedSize: Long): Flow<DownloadEvent> = flow {
        val candidates = urls.filter { UpdateRules.isTrustedApkUrl(it) }
        if (candidates.isEmpty()) throw IOException("没有可信的下载地址")
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        var last: Exception? = null
        var mismatch: ChecksumMismatchException? = null
        try {
            for ((i, url) in candidates.withIndex()) {
                val source = sourceLabel(url)
                val hasMore = i < candidates.lastIndex
                part.delete()
                try {
                    val md = MessageDigest.getInstance("SHA-256")
                    val req = Request.Builder().url(url).header("Accept", "application/vnd.android.package-archive, */*").build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} @ $source")
                        // 重定向后的最终地址也必须是 https
                        if (!resp.request.url.isHttps) throw IOException("非 https 重定向 @ $source")
                        val body = resp.body ?: throw IOException("empty body @ $source")
                        val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
                        if (expectedSize > 0 && body.contentLength() > 0 && body.contentLength() != expectedSize) {
                            throw IOException("大小不符（${body.contentLength()} ≠ $expectedSize）@ $source")
                        }
                        val started = now()
                        var bytes = 0L
                        var lastEmit = 0L
                        emit(DownloadEvent.Progress(0, total, source))
                        body.byteStream().use { input ->
                            part.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    md.update(buf, 0, n)
                                    bytes += n
                                    val t = now()
                                    if (t - lastEmit >= 120) {
                                        lastEmit = t
                                        emit(DownloadEvent.Progress(bytes, total, source))
                                    }
                                    val elapsed = t - started
                                    if (hasMore && elapsed > slowAfterMs && bytes * 1000 / elapsed < slowBytesPerSec &&
                                        (total <= 0 || bytes < total * 3 / 4)
                                    ) throw SlowSourceException("速度过慢 @ $source")
                                }
                            }
                        }
                        emit(DownloadEvent.Progress(bytes, total, source))
                        if (expectedSize > 0 && bytes != expectedSize) throw IOException("下载不完整（$bytes / $expectedSize）@ $source")
                    }
                    val sha = md.digest().toHex()
                    if (expectedSha256 != null && !sha.equals(expectedSha256, ignoreCase = true)) {
                        part.delete()
                        throw ChecksumMismatchException("SHA-256 校验失败 @ $source")
                    }
                    dest.delete()
                    if (!part.renameTo(dest)) throw IOException("无法保存安装包")
                    emit(DownloadEvent.Done(dest, sha, source))
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    part.delete()
                    if (e is ChecksumMismatchException) mismatch = e
                    last = e
                }
            }
            throw mismatch ?: last ?: IOException("下载失败")
        } finally {
            // 取消 / 失败都不留半截文件
            part.delete()
        }
    }.flowOn(Dispatchers.IO)

    private class SlowSourceException(message: String) : IOException(message)

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(false) // 不允许 https → http 降级
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "AIDaily-Android-Updater/1.3").build())
            }
            .build()

        fun sourceLabel(url: String): String =
            if (url.startsWith(UpdateRules.DOWNLOAD_PREFIX)) "GitHub"
            else runCatching { java.net.URI(url).host }.getOrNull() ?: "镜像"

        fun sha256Of(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().toHex()
        }

        internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
