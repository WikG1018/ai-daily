package com.wikg.aidaily

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performScrollToKey
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

    @Test fun homeToDetailAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("小米专栏").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("今日要点").assertExists()
            compose.onNodeWithTag("home_list").performScrollToKey("i-2026-10-08-001")
            compose.onAllNodesWithText("Anthropic 发布 Claude Haiku 5.5").onFirst().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("查看原帖", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("相关进展 · 1").assertExists()
        }
    }

    @Test fun deepLinkOpensItem() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aidaily://item/2026-10-08-003"),
            ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("档位说明", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun settingsOpens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("小米专栏").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("设置").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("设置与提醒").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("允许自启动").assertExists()
        }
    }
}
