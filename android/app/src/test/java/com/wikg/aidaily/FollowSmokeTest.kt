package com.wikg.aidaily

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v1.2 端到端：版本更新 / 人物动态页、筛选切换、关注设置、我的关注、详情页关联卡。数据来自合成离线缓存。 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class FollowSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<AiDailyApp>()
    private fun res(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @org.junit.After fun online() { com.wikg.aidaily.data.remote.DailyApi.offlineForTests = false }

    @Before fun seed() {
        com.wikg.aidaily.data.remote.DailyApi.offlineForTests = true
        val dir = File(app.filesDir, "daily").apply { mkdirs() }
        File(dir, "index.json").writeText(res("index-2026-10-10.json"))
        File(dir, "issue-2026-10-10.json").writeText(res("issue-2026-10-10.json"))
        File("../../data/watchlist.json").copyTo(File(dir, "watchlist.json"), overwrite = true)
        runBlocking {
            app.container.prefs.setGuideDismissed(true)
            app.container.prefs.setFollowProducts(emptyList())
            app.container.prefs.setFollowPeople(emptyList())
            app.container.prefs.resetFollowFilters()
        }
    }

    private fun waitText(text: String, substring: Boolean = false) = try {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        runCatching { println("TREE>> " + compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size + "\n" + compose.onAllNodes(androidx.compose.ui.test.isRoot())[0].printToString()) }
        throw e
    }

    private fun waitTag(tag: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun openTab(key: String) {
        waitTag("tab-$key")
        compose.onNodeWithTag("tab-$key").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tab-$key").assertIsSelected()
    }

    private fun inPage(page: String, tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("page_$page"))

    @Test fun releasesFilterFollowFlow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            assertTrue(compose.onAllNodesWithTag("tab-follow").fetchSemanticsNodes().isEmpty())
            openTab("s-releases")
            compose.onNode(inPage("s-releases", "release-2026-10-10-006")).assertIsDisplayed()
            compose.onNode(hasText("Codex CLI") and hasAnyAncestor(hasTestTag("release-2026-10-10-006")), useUnmergedTree = true).assertExists()
            compose.onNode(hasText("0.162.0") and hasAnyAncestor(hasTestTag("release-2026-10-10-006")), useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("filter-releases-all").assertIsSelected()

            // 没关注时切到「只看关注」→ 引导去关注
            compose.onNodeWithTag("filter-releases-followed").performClick()
            waitText("还没有关注任何产品")
            compose.onNodeWithText("去关注").performClick()
            waitTag("follow-row-claude-code")
            compose.onNodeWithText("关注产品").assertExists()
            compose.onNodeWithTag("follow-row-claude-code").performScrollTo().performClick()
            waitTag("followed-claude-code")
            compose.onNodeWithContentDescription("返回").performClick()

            // 回到首页：只剩 Claude Code，「我的关注」页出现
            waitTag("tab-follow")
            compose.waitUntil(10_000) {
                compose.onAllNodes(inPage("s-releases", "release-2026-10-10-006")).fetchSemanticsNodes().isEmpty()
            }
            compose.onNode(inPage("s-releases", "release-2026-10-10-007")).assertExists()
            assertEquals(com.wikg.aidaily.data.local.FollowFilter.FOLLOWED, runBlocking { app.container.prefs.settings.first().releasesFilter })

            // 切回全部
            compose.onNodeWithTag("filter-releases-all").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(inPage("s-releases", "release-2026-10-10-006")).fetchSemanticsNodes().isNotEmpty() }

            openTab("follow")
            compose.onNode(inPage("follow", "release-2026-10-10-007")).assertExists()
            compose.onNode(inPage("follow", "person-2026-10-10-016")).assertExists() // 人物动态里 products 带 claude-code
        }
    }

    @Test fun peoplePageShowsNameHandleAndOrg() {
        runBlocking { app.container.prefs.setFollowPeople(listOf("_luofuli")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            openTab("s-people")
            // 关注了人物 → 默认「只看关注」
            compose.onNodeWithTag("filter-people-followed").assertIsSelected()
            compose.onNode(inPage("s-people", "person-2026-10-10-015")).assertIsDisplayed()
            assertTrue(compose.onAllNodes(inPage("s-people", "person-2026-10-10-014")).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("filter-people-all").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(inPage("s-people", "person-2026-10-10-014")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Tibo (Thibault Sottiaux)") and hasAnyAncestor(hasTestTag("person-2026-10-10-014")), useUnmergedTree = true).assertExists()
            compose.onNode(hasText("@thsottiaux", substring = true) and hasText("Codex 负责人", substring = true) and
                hasAnyAncestor(hasTestTag("person-2026-10-10-014")), useUnmergedTree = true).assertExists()
            // 清单外的人物不崩，显示原始 id
            compose.onNode(hasText("nobody_here") and hasAnyAncestor(hasTestTag("person-2026-10-10-017")), useUnmergedTree = true).assertExists()
        }
    }

    @Test fun followPeopleSettingsKeepsUnknownIds() {
        runBlocking { app.container.prefs.setFollowPeople(listOf("ghost_person", "thsottiaux")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("今日要点")
            compose.onNodeWithContentDescription("设置").performClick()
            waitTag("hub-follow-people")
            compose.onNodeWithTag("hub-follow-people").performClick()
            waitText("我的关注 · 2")
            compose.onNodeWithText("已下线 / 未知 · ghost_person").assertExists()
            compose.onNodeWithText("官方账号 · ", substring = true).performScrollTo().assertExists()
            compose.onNodeWithTag("follow-row-_luofuli").performScrollTo().performClick()
            waitText("我的关注 · 3")
            compose.onNodeWithContentDescription("取消关注 ghost_person").performScrollTo().performClick()
            waitText("我的关注 · 2")
            val s = runBlocking { app.container.prefs.settings.first() }
            assertEquals(listOf("thsottiaux", "_luofuli"), s.followPeople)
        }
    }

    @Test fun detailShowsVersionBadgeAndFollowCard() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aidaily://item/2026-10-10-006"), app, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitTag("detail-refs")
            compose.onAllNodesWithTag("version-badge").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
            compose.onNodeWithText("更新日志 ›").assertExists()
            compose.onNodeWithTag("follow-p-codex-cli").performClick()
            compose.waitUntil(10_000) { runBlocking { app.container.prefs.settings.first().followProducts } == listOf("codex-cli") }
        }
    }

    @Test fun detailPersonCard() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aidaily://item/2026-10-10-016"), app, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitTag("detail-refs")
            compose.onNodeWithTag("follow-u-claudedevs").assertExists()
            compose.onNodeWithTag("follow-u-bcherny").assertExists()
            compose.onNodeWithTag("follow-p-claude-code").assertExists()
        }
    }

    @Test fun corruptWatchlistCacheDoesNotBreakLaunch() {
        File(app.filesDir, "daily/watchlist.json").writeText("{ broken")
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            waitText("今日要点")
            openTab("s-releases")
            compose.onNode(inPage("s-releases", "release-2026-10-10-006")).assertExists()
            s.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
