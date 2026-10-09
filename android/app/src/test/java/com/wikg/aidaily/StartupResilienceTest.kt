package com.wikg.aidaily

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wikg.aidaily.crash.CrashLog
import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.remote.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** v1.1.1 回归：坏缓存 / 坏数据 / 上次崩溃都不能让启动崩溃；线上数据能被解析。 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class StartupResilienceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<AiDailyApp>()

    @Test fun launchWithCorruptCacheDoesNotCrash() {
        val dir = File(ctx.filesDir, "daily").apply { mkdirs() }
        File(dir, "index.json").writeText("{ this is not json")
        File(dir, "issue-2026-10-08.json").writeBytes(ByteArray(0))
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            compose.waitForIdle()
            s.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test fun crashLogRecordedAndShownOnNextLaunch() {
        CrashLog.clear(ctx)
        CrashLog.record(ctx, Thread.currentThread(), IllegalStateException("boom-test"))
        val text = CrashLog.read(ctx)
        assertNotNull(text)
        assertTrue(text!!.contains("boom-test"))
        assertTrue(text.contains("1.1.1") || text.contains(BuildConfig.VERSION_NAME))
        // Robolectric 里进程刚启动，记录为启动崩溃 → 下次启动先展示日志页
        assertTrue(CrashLog.shouldShowOnLaunch(ctx))
        CrashLog.markSeen(ctx)
        assertFalse(CrashLog.shouldShowOnLaunch(ctx))
        CrashLog.clear(ctx)
        assertEquals(null, CrashLog.read(ctx))
    }

    private fun fetch(url: String): String? = runCatching {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.useCaches = false
        c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    /** 线上 index.json 与最新一期都能被 app 的解析器解析（无网络时跳过）。 */
    @Test fun parsesLiveData() {
        val base = "https://raw.githubusercontent.com/WikG1018/ai-daily/main/"
        val raw = fetch(base + "data/index.json")
        assumeTrue("network unavailable", raw != null)
        val idx = AppJson.decodeFromString(DailyIndex.serializer(), raw!!)
        assertNotNull(idx.latest)
        val s = idx.issues.first()
        val issueRaw = fetch(base + s.resolvedPath)
        assumeTrue("network unavailable", issueRaw != null)
        val issue = AppJson.decodeFromString(Issue.serializer(), issueRaw!!)
        assertEquals(s.date, issue.date)
        assertTrue(issue.allItems().isNotEmpty())
    }
}
