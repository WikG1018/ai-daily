package com.wikg.aidaily.data.remote

import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.Watchlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 数据源：先 GitHub Raw，8 秒内失败再走 jsDelivr 镜像。 */
enum class Mirror(val label: String, val base: String) {
    RAW("GitHub Raw", "https://raw.githubusercontent.com/WikG1018/ai-daily/main/"),
    JSDELIVR("jsDelivr", "https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/"),
}

data class Fetched<T>(val value: T, val mirror: Mirror)

class DailyApi(
    private val client: OkHttpClient = defaultClient(),
    private val mirrors: List<Mirror> = Mirror.entries,
) {
    val json = AppJson

    suspend fun fetchIndex(): Fetched<DailyIndex> =
        fetch("data/index.json", noCache = true) { json.decodeFromString(DailyIndex.serializer(), it) }

    /** 关注清单：缓存策略同 index.json（绕开 HTTP 缓存，Raw 优先、8 秒内失败走 jsDelivr）。 */
    suspend fun fetchWatchlist(): Fetched<Watchlist> =
        fetch("data/watchlist.json", noCache = true) { json.decodeFromString(Watchlist.serializer(), it) }

    suspend fun fetchIssue(path: String, noCache: Boolean = false): Fetched<Issue> =
        fetch(path.trimStart('/'), noCache) { json.decodeFromString(Issue.serializer(), it) }

    private suspend fun <T> fetch(path: String, noCache: Boolean, parse: (String) -> T): Fetched<T> =
        withContext(Dispatchers.IO) {
            if (offlineForTests) throw IOException("offline (test)")
            var last: Exception? = null
            for (m in mirrors) {
                try {
                    val req = Request.Builder()
                        .url(m.base + path)
                        .header("Accept", "application/json, text/plain, */*")
                        .apply {
                            if (noCache) {
                                // 绕开任何 HTTP 缓存（jsDelivr 返回 max-age=7 天）
                                cacheControl(CacheControl.FORCE_NETWORK)
                                header("Cache-Control", "no-cache")
                                header("Pragma", "no-cache")
                            }
                        }
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} @ ${m.label}")
                        // Raw 的 Content-Type 是 text/plain：不看 Content-Type，直接按 JSON 解析
                        val body = resp.body?.string() ?: throw IOException("empty body @ ${m.label}")
                        return@withContext Fetched(parse(body), m)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    last = e
                }
            }
            throw last ?: IOException("no mirror")
        }

    companion object {
        /** 仅测试用：让端到端测试不依赖线上数据（线上每天都在变）。 */
        @Volatile @JvmStatic var offlineForTests: Boolean = false

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "AIDaily-Android/1.0").build())
            }
            .build()
    }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}
