package com.wikg.aidaily

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wikg.aidaily.data.DailyRepository
import com.wikg.aidaily.data.local.CacheStore
import com.wikg.aidaily.data.local.Prefs
import com.wikg.aidaily.data.remote.DailyApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** watchlist.json 的双源拉取、绕缓存、约每天一次刷新、失败回退缓存。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class FollowRepositoryTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val real = File("../../data/watchlist.json").readText()

    private class Fake(var raw: Pair<Int, String>, var cdn: Pair<Int, String>) {
        val calls = mutableListOf<String>()
        val noCache = mutableListOf<Boolean>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            calls += req.url.toString()
            noCache += req.header("Cache-Control") == "no-cache"
            val (code, body) = if (req.url.host.contains("jsdelivr")) cdn else raw
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("text/plain".toMediaType())).build()
        }.build()
    }

    private fun repo(f: Fake) = DailyRepository(DailyApi(f.client), CacheStore(ctx), Prefs(ctx))

    @Test fun fallsBackToMirrorCachesAndRefreshesDaily() = runBlocking {
        File(ctx.filesDir, "daily/watchlist.json").delete()
        val f = Fake(500 to "", 200 to real)
        val r = repo(f)
        val w = r.refreshWatchlist().getOrThrow()
        assertEquals(22, w!!.products.size)
        assertEquals(2, f.calls.size)
        assertTrue(f.calls[0].startsWith("https://raw.githubusercontent.com/WikG1018/ai-daily/main/data/watchlist.json"))
        assertTrue(f.calls[1].startsWith("https://cdn.jsdelivr.net/gh/WikG1018/ai-daily@main/data/watchlist.json"))
        assertTrue("watchlist 请求必须绕开 HTTP 缓存", f.noCache.all { it })

        // 缓存新鲜：不联网
        r.refreshWatchlist()
        assertEquals(2, f.calls.size)
        // 新进程：先读离线缓存
        val r2 = repo(Fake(500 to "", 500 to ""))
        assertEquals(40, r2.loadCachedWatchlist()!!.people.size)
        // 过期（maxAge=0）且网络全挂：返回失败，但已有清单保留
        val dead = Fake(500 to "", 503 to "")
        val r3 = repo(dead)
        r3.loadCachedWatchlist()
        assertTrue(r3.refreshWatchlist(maxAgeMs = 0).isFailure)
        assertEquals(2, dead.calls.size)
        assertNotNull(r3.watchlist.value)
    }

    @Test fun brokenJsonFromServerIsAFailureNotACrash() = runBlocking {
        File(ctx.filesDir, "daily/watchlist.json").delete()
        val r = repo(Fake(200 to "<html>blocked</html>", 200 to "{ nope"))
        assertTrue(r.refreshWatchlist().isFailure)
        assertEquals(null, r.watchlist.value)
    }

    @Test fun corruptCacheIsIgnored() = runBlocking {
        File(ctx.filesDir, "daily").mkdirs()
        File(ctx.filesDir, "daily/watchlist.json").writeText("{ corrupt")
        val r = repo(Fake(200 to real, 200 to real))
        assertEquals(null, r.loadCachedWatchlist())
        assertEquals(22, r.refreshWatchlist().getOrThrow()!!.products.size)
    }
}
