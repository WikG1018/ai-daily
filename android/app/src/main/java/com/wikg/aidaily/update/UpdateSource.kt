package com.wikg.aidaily.update

import com.wikg.aidaily.data.model.GhRelease
import com.wikg.aidaily.data.model.UpdateInfo
import com.wikg.aidaily.data.model.UpdateManifest
import com.wikg.aidaily.data.model.UpdateRules
import com.wikg.aidaily.data.remote.AppJson
import com.wikg.aidaily.data.remote.DailyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class UpdateCheckException(message: String, val rateLimited: Boolean = false) : IOException(message)

/**
 * 读取「最新版本」元数据。顺序：update.json（Raw）→ update.json（jsDelivr）→ GitHub Releases API。
 * 每个来源 8 秒超时（复用 DailyApi 的客户端），一律绕开 HTTP 缓存；任何一个来源给出合法结果即返回。
 */
class UpdateSource(
    private val client: OkHttpClient = DailyApi.defaultClient(),
    private val order: List<String> = UpdateRules.METADATA_ORDER,
) {
    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        if (DailyApi.offlineForTests) throw UpdateCheckException("offline (test)")
        val errors = mutableListOf<String>()
        var rateLimited = false
        for (url in order) {
            try {
                return@withContext fetchOne(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is UpdateCheckException && e.rateLimited) rateLimited = true
                errors += "${label(url)}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        throw UpdateCheckException(
            if (rateLimited) "GitHub 接口限流且镜像不可用，请稍后再试" else "无法连接更新服务器（${errors.joinToString("；")}）",
            rateLimited,
        )
    }

    private fun fetchOne(url: String): UpdateInfo {
        val isApi = url.startsWith("https://api.github.com/")
        val req = Request.Builder().url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .apply {
                if (isApi) {
                    header("Accept", "application/vnd.github+json")
                    header("X-GitHub-Api-Version", "2022-11-28")
                } else header("Accept", "application/json, text/plain, */*")
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (isApi && (resp.code == 403 || resp.code == 429)) {
                val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull()
                throw UpdateCheckException("HTTP ${resp.code}（限流${reset?.let { "，重置于 $it" } ?: ""}）", rateLimited = true)
            }
            if (!resp.isSuccessful) throw UpdateCheckException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw UpdateCheckException("empty body")
            return parse(url, body) ?: throw UpdateCheckException("内容无效")
        }
    }

    companion object {
        fun label(url: String): String = when {
            url.startsWith("https://raw.githubusercontent.com/") -> "GitHub Raw"
            url.startsWith("https://cdn.jsdelivr.net/") -> "jsDelivr"
            url.startsWith("https://api.github.com/") -> "GitHub API"
            else -> runCatching { java.net.URI(url).host }.getOrNull() ?: url
        }

        /** 按来源解析；内容不合法（无 apk、版本号无效、链接不可信）返回 null。 */
        fun parse(url: String, body: String): UpdateInfo? = runCatching {
            if (url.startsWith("https://api.github.com/")) {
                UpdateRules.fromGitHub(AppJson.decodeFromString(GhRelease.serializer(), body), label(url))
            } else {
                UpdateRules.fromManifest(AppJson.decodeFromString(UpdateManifest.serializer(), body), label(url))
            }
        }.getOrNull()
    }
}
