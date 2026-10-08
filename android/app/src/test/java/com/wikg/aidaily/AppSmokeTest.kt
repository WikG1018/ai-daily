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

    @Before fun seedCache() {
        val ctx = ApplicationProvider.getApplicationContext<AiDailyApp>()
        val dir = File(ctx.filesDir, "daily").apply { mkdirs() }
        File("../../data/index.json").copyTo(File(dir, "index.json"), overwrite = true)
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
}
