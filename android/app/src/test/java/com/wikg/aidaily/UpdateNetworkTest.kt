package com.wikg.aidaily

import com.wikg.aidaily.data.model.UpdateRules
import com.wikg.aidaily.update.ApkDownloader
import com.wikg.aidaily.update.ChecksumMismatchException
import com.wikg.aidaily.update.DownloadEvent
import com.wikg.aidaily.update.UpdateCheckException
import com.wikg.aidaily.update.UpdateSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/** 用 OkHttp 拦截器伪造各来源的响应，测试回退顺序、限流、哈希校验。全离线。 */
class UpdateNetworkTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gh = "https://github.com/WikG1018/ai-daily/releases/download/v1.3.0/ai-daily-1.3.0.apk"
    private val mirror = "https://ghfast.top/$gh"
    private val apkBytes = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val apkSha = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) }

    private fun manifest(version: String = "1.3.0") =
        """{"versionCode":5,"versionName":"$version","apkUrl":"$gh","apkMirrors":["$mirror"],"sha256":"$apkSha","size":${apkBytes.size}}"""

    private val ghApi = """{"tag_name":"v1.3.0","assets":[{"name":"ai-daily-1.3.0.apk","size":${apkBytes.size},"digest":"sha256:$apkSha","browser_download_url":"$gh"}]}"""

    private class Fake(val routes: (String) -> Pair<Int, ByteArray>) : Interceptor {
        val seen = mutableListOf<String>()
        val headers = mutableListOf<okhttp3.Headers>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url.toString()
            seen += url
            headers += chain.request().headers
            val (code, body) = routes(url)
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("text/plain".toMediaType())).build()
        }
    }

    private fun client(f: Fake) = OkHttpClient.Builder().addInterceptor(f).build()

    @Test fun rawFirst() = runBlocking {
        val f = Fake { url -> if (url == UpdateRules.MANIFEST_RAW) 200 to manifest().toByteArray() else 500 to ByteArray(0) }
        val info = UpdateSource(client(f)).fetchLatest()
        assertEquals("GitHub Raw", info.source)
        assertEquals(listOf(UpdateRules.MANIFEST_RAW), f.seen)
        // 绕开 HTTP 缓存
        assertTrue(f.headers.first()["Cache-Control"]!!.contains("no-cache"))
    }

    @Test fun fallsBackToJsDelivrThenGitHubApi() = runBlocking {
        val f1 = Fake { url -> if (url == UpdateRules.MANIFEST_JSDELIVR) 200 to manifest().toByteArray() else 503 to ByteArray(0) }
        assertEquals("jsDelivr", UpdateSource(client(f1)).fetchLatest().source)
        assertEquals(listOf(UpdateRules.MANIFEST_RAW, UpdateRules.MANIFEST_JSDELIVR), f1.seen)

        // update.json 坏掉（不合法）也会继续往下走
        val f2 = Fake { url ->
            when (url) {
                UpdateRules.MANIFEST_RAW -> 200 to "{oops".toByteArray()
                UpdateRules.GITHUB_API_LATEST -> 200 to ghApi.toByteArray()
                else -> 404 to ByteArray(0)
            }
        }
        val info = UpdateSource(client(f2)).fetchLatest()
        assertEquals("GitHub API", info.source)
        assertEquals(UpdateRules.METADATA_ORDER, f2.seen)
        assertEquals("application/vnd.github+json", f2.headers.last()["Accept"])
    }

    @Test fun rateLimitedIsReportedGracefully() = runBlocking {
        val f = Fake { url -> if (url == UpdateRules.GITHUB_API_LATEST) 403 to """{"message":"API rate limit exceeded"}""".toByteArray() else 500 to ByteArray(0) }
        try {
            UpdateSource(client(f)).fetchLatest(); fail()
        } catch (e: UpdateCheckException) {
            assertTrue(e.rateLimited)
            assertTrue(e.message!!.contains("限流"))
        }
    }

    @Test fun downloadVerifiesSha256() = runBlocking {
        val f = Fake { url -> if (url == gh) 200 to apkBytes else 404 to ByteArray(0) }
        val dest = tmp.root.resolve("updates/ai-daily-1.3.0.apk")
        val events = ApkDownloader(client(f)).download(listOf(gh, mirror), dest, apkSha, apkBytes.size.toLong()).toList()
        val done = events.last() as DownloadEvent.Done
        assertEquals(apkSha, done.sha256)
        assertEquals("GitHub", done.source)
        assertTrue(dest.exists())
        assertTrue(events.any { it is DownloadEvent.Progress && it.bytes == apkBytes.size.toLong() })
        assertFalse(tmp.root.resolve("updates/ai-daily-1.3.0.apk.part").exists())
    }

    @Test fun downloadFallsBackToMirror() = runBlocking {
        val f = Fake { url -> if (url == mirror) 200 to apkBytes else 502 to ByteArray(0) }
        val dest = tmp.root.resolve("u/a.apk")
        val done = ApkDownloader(client(f)).download(listOf(gh, mirror), dest, apkSha, apkBytes.size.toLong()).toList().last() as DownloadEvent.Done
        assertEquals("ghfast.top", done.source)
        assertEquals(listOf(gh, mirror), f.seen)
    }

    @Test fun shaMismatchAbortsAndLeavesNoFile() = runBlocking {
        val tampered = apkBytes.copyOf().also { it[100] = (it[100] + 1).toByte() }
        val f = Fake { _ -> 200 to tampered }
        val dest = tmp.root.resolve("u/a.apk")
        try {
            ApkDownloader(client(f)).download(listOf(gh, mirror), dest, apkSha, apkBytes.size.toLong()).toList()
            fail("should abort")
        } catch (e: ChecksumMismatchException) {
            assertTrue(e.message!!.contains("SHA-256"))
        }
        assertFalse(dest.exists())
        assertTrue(dest.parentFile!!.listFiles()!!.isEmpty())
        assertEquals(2, f.seen.size) // 两个来源都试过，全部校验失败
    }

    @Test fun untrustedUrlsAreNeverRequested() = runBlocking {
        val f = Fake { _ -> 200 to apkBytes }
        try {
            ApkDownloader(client(f)).download(listOf("http://evil/a.apk", "https://evil.com/a.apk"), tmp.root.resolve("a.apk"), null, 0).toList()
            fail()
        } catch (e: java.io.IOException) { }
        assertTrue(f.seen.isEmpty())
    }

    @Test fun wrongSizeTriesNextSource() = runBlocking {
        val f = Fake { url -> if (url == gh) 200 to apkBytes.copyOf(1000) else 200 to apkBytes }
        val done = ApkDownloader(client(f)).download(listOf(gh, mirror), tmp.root.resolve("a.apk"), apkSha, apkBytes.size.toLong()).toList().last()
        assertTrue(done is DownloadEvent.Done && done.source == "ghfast.top")
    }
}
