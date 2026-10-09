package com.wikg.aidaily

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 端到端冒烟：真实 Application / WorkManager / DataStore / 导航，数据来自预置的离线缓存。 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @org.junit.After fun online() { com.wikg.aidaily.data.remote.DailyApi.offlineForTests = false }

    @Before fun seedCache() {
        com.wikg.aidaily.data.remote.DailyApi.offlineForTests = true
        val ctx = ApplicationProvider.getApplicationContext<AiDailyApp>()
        val dir = File(ctx.filesDir, "daily").apply { mkdirs() }
        // 离线、固定数据：索引只保留 2026-10-08 这一期（线上 data/ 每天都在变）
        val idx = com.wikg.aidaily.data.remote.AppJson.decodeFromString(
            com.wikg.aidaily.data.model.DailyIndex.serializer(), File("../../data/index.json").readText(),
        )
        val pinned = idx.copy(latest = "2026-10-08", issues = idx.issues.filter { it.date == "2026-10-08" })
        File(dir, "index.json").writeText(com.wikg.aidaily.data.remote.AppJson.encodeToString(com.wikg.aidaily.data.model.DailyIndex.serializer(), pinned))
        File("../../data/2026-10-08.json").copyTo(File(dir, "issue-2026-10-08.json"), overwrite = true)
    }

    private fun waitText(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun waitTag(tag: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun homeTabsToDetailAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.onNodeWithTag("tab-today").assertIsSelected()
            compose.onNodeWithTag("tab-s-models").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("tab-s-models").assertIsSelected()
            compose.onNode(hasText("Anthropic 发布 Claude Haiku 5.5") and hasAnyAncestor(hasTestTag("page_s-models"))).performClick()
            waitText("查看原帖", substring = true)
            compose.onNodeWithText("相关进展 · 1").assertExists()
        }
    }

    @Test fun swipeBetweenPages() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.onNodeWithTag("home_pager").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNodeWithTag("tab-f-小米").assertIsSelected()
            compose.onNode(hasText("小米专栏") and hasAnyAncestor(hasTestTag("page_f-小米"))).assertIsDisplayed()
            compose.onNode(hasText("本期未收录小米相关动态。") and hasAnyAncestor(hasTestTag("page_f-小米"))).assertIsDisplayed()
            compose.onNodeWithTag("home_pager").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            compose.onNodeWithTag("tab-s-models").assertIsSelected()
            compose.onNodeWithTag("home_pager").performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.onNodeWithTag("tab-f-小米").assertIsSelected()
        }
    }

    @Test fun addFeaturedVendorCreatesPage() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.onNodeWithContentDescription("设置").performClick()
            waitTag("hub-featured")
            compose.onNodeWithTag("hub-featured").performClick()
            waitText("我的关注", substring = true)
            compose.onNodeWithTag("featured-input").performTextInput("OpenAI")
            compose.onNodeWithText("添加").performClick()
            waitText("OpenAI专栏")
            waitText("本期 4 条", substring = true)
            compose.onNodeWithContentDescription("返回").performClick()
            waitTag("hub-featured")
            compose.onNodeWithContentDescription("返回").performClick()
            waitTag("tab-f-openai")
            compose.onNodeWithTag("tab-f-openai").performClick()
            compose.waitForIdle()
            compose.onNode(hasText("OpenAI专栏") and hasAnyAncestor(hasTestTag("page_f-openai"))).assertIsDisplayed()
            compose.onAllNodes(hasText("Codex", substring = true) and hasAnyAncestor(hasTestTag("page_f-openai"))).onFirst().assertExists()
        }
    }

    @Test fun deepLinkOpensItem() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aidaily://item/2026-10-08-003"),
            ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("档位说明", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun settingsHubAndSubpage() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.onNodeWithContentDescription("设置").performClick()
            waitTag("hub-notify")
            compose.onNodeWithTag("hub-notify").performClick()
            waitText("允许自启动")
            compose.onNodeWithText("通知与后台").assertExists()
        }
    }

    @Test fun markAllReadShowsInlineConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.onNodeWithContentDescription("全部已读").performClick()
            waitTag("marked_all_read")
            compose.onAllNodesWithText("已全部标为已读").fetchSemanticsNodes().let { assert(it.isEmpty()) }
        }
    }

    @Test fun darkThemeWindowBackgroundIsDark() {
        val app = ApplicationProvider.getApplicationContext<AiDailyApp>()
        kotlinx.coroutines.runBlocking { app.container.prefs.setTheme(com.wikg.aidaily.data.local.ThemeMode.DARK) }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            waitText("今日要点")
            sc.onActivity { a ->
                val bg = (a.window.decorView.background as? android.graphics.drawable.ColorDrawable)?.color
                org.junit.Assert.assertEquals(com.wikg.aidaily.ui.theme.DarkColors.background.toArgb(), bg)
            }
            compose.onNodeWithContentDescription("设置").performClick()
            waitTag("hub-notify")
        }
        kotlinx.coroutines.runBlocking { app.container.prefs.setTheme(com.wikg.aidaily.data.local.ThemeMode.SYSTEM) }
    }

    /** v1.3：上次检查发现的新版本跨启动保留 → 首页横幅 → 点开更新面板；全程离线、不阻塞启动。 */
    @Test fun cachedUpdateShowsBannerAndSheet() {
        val app = ApplicationProvider.getApplicationContext<AiDailyApp>()
        val info = com.wikg.aidaily.data.model.UpdateInfo(
            versionName = "99.0.0", versionCode = 999, notes = "## 新增\n- 测试更新",
            apkUrls = listOf("https://github.com/WikG1018/ai-daily/releases/download/v99.0.0/ai-daily-99.0.0.apk"), size = 1234567,
        )
        kotlinx.coroutines.runBlocking {
            app.container.prefs.recordUpdateCheck(
                com.wikg.aidaily.data.remote.AppJson.encodeToString(com.wikg.aidaily.data.model.UpdateInfo.serializer(), info),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.mainClock.advanceTimeBy(2000)
            waitTag("update_banner")
            compose.onNodeWithTag("update_banner").performClick()
            waitTag("update_sheet")
            compose.onNodeWithText("v99.0.0").assertExists()
            compose.onNodeWithText("稍后").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("update_sheet").fetchSemanticsNodes().isEmpty() }
        }
        kotlinx.coroutines.runBlocking { app.container.prefs.recordUpdateCheck(null) }
    }
}
