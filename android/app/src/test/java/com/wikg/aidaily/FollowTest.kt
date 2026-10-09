package com.wikg.aidaily

import com.wikg.aidaily.data.local.FollowFilter
import com.wikg.aidaily.data.model.Follows
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.SectionIds
import com.wikg.aidaily.data.model.Watchlist
import com.wikg.aidaily.data.model.collectFollowed
import com.wikg.aidaily.data.model.followedHits
import com.wikg.aidaily.data.model.locate
import com.wikg.aidaily.data.model.matches
import com.wikg.aidaily.data.model.normalizeFollowIds
import com.wikg.aidaily.data.remote.AppJson
import com.wikg.aidaily.ui.home.FollowUi
import com.wikg.aidaily.ui.home.HomePage
import com.wikg.aidaily.ui.home.RowStyle
import com.wikg.aidaily.ui.home.buildPages
import com.wikg.aidaily.ui.home.personDisplay
import com.wikg.aidaily.ui.home.productDisplayName
import com.wikg.aidaily.ui.home.rowStyleOf
import com.wikg.aidaily.ui.settings.FollowKind
import com.wikg.aidaily.ui.settings.issueCounts
import com.wikg.aidaily.ui.settings.personEntries
import com.wikg.aidaily.ui.settings.productEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v1.2：releases / people 新字段、watchlist.json 解析与关注匹配（schema §3、§5）。 */
class FollowTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()
    private val issue = AppJson.decodeFromString(Issue.serializer(), res("issue-2026-10-10.json"))
    private val watchlist = AppJson.decodeFromString(Watchlist.serializer(), File("../../data/watchlist.json").readText())
    private fun item(id: String) = issue.locate(id)!!.item

    // —— 解析 ——
    @Test fun parsesNewItemFields() {
        val rel = item("2026-10-10-006")
        assertEquals("codex-cli", rel.product)
        assertEquals("0.162.0", rel.version)
        assertEquals("0.162.0", rel.versionText)
        assertEquals(setOf("codex-cli"), rel.productIds)
        assertTrue(rel.personIds.isEmpty())

        // product 与 products 重复要去重
        assertEquals(setOf("deepseek-harness"), item("2026-10-10-008").productIds)
        // person ∪ people，大小写统一、去重
        val p = item("2026-10-10-016")
        assertEquals(setOf("claudedevs", "bcherny"), p.personIds)
        assertEquals(setOf("claude-code"), p.productIds)
        // 子条目也能带字段
        assertEquals("mntruell", item("2026-10-10-005").person)
        // 不带字段的条目
        val plain = item("2026-10-10-003")
        assertNull(plain.product); assertNull(plain.version)
        assertTrue(plain.productIds.isEmpty() && plain.personIds.isEmpty())
        // 未知字段被忽略
        assertEquals("9.9.9", item("2026-10-10-010").version)
    }

    @Test fun syntheticIssueCountsAndSections() {
        assertEquals(17, issue.newsCount)
        assertEquals(listOf("models", "harness", "releases", "people", "other"), issue.sections.map { it.id })
        val ids = issue.allItems().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun parsesRepoWatchlist() {
        assertEquals(1, watchlist.schemaVersion)
        assertEquals(22, watchlist.products.size)
        assertEquals(40, watchlist.people.size)
        assertEquals(40, watchlist.individuals.size + watchlist.officials.size)
        assertTrue(watchlist.officials.all { it.isOfficial })
        assertEquals("Codex CLI", watchlist.product("codex-cli")!!.displayName)
        assertEquals("OpenAI", watchlist.product("CODEX-CLI")!!.vendor)
        val fuli = watchlist.person("_luofuli")!!
        assertEquals("_LuoFuli", fuli.displayHandle)
        assertEquals("小米 · Xiaomi MiMo 团队负责人", fuli.orgRole)
        assertEquals("https://x.com/_LuoFuli", fuli.safeUrl)
        assertNotNull(watchlist.person("@_LuoFuli"))
        assertNull(watchlist.product("ghost-cli"))
        // 所有条目引用的 id（除了故意放的未知 id）都在清单里
        val unknown = issue.allItems().flatMap { it.productIds }.filter { watchlist.product(it) == null }.toSet() +
            issue.allItems().flatMap { it.personIds }.filter { watchlist.person(it) == null }.toSet()
        assertEquals(setOf("ghost-cli", "nobody_here"), unknown)
    }

    @Test fun brokenOrEmptyWatchlistNeverCrashes() {
        val w = AppJson.decodeFromString(Watchlist.serializer(), res("watchlist-broken.json"))
        assertEquals(3, w.schemaVersion)
        assertEquals(1, w.validProducts.size)            // 没有 id 的被跳过
        assertEquals("ide", w.product("codex-cli")!!.kind) // 未知 kind 原样保留，不影响使用
        assertNotNull(w.person("thsottiaux"))             // id 大小写不敏感
        assertFalse(w.person("thsottiaux")!!.isOfficial)  // 未知 kind 按个人
        assertEquals(1, w.officials.size)
        val empty = AppJson.decodeFromString(Watchlist.serializer(), "{}")
        assertTrue(empty.products.isEmpty() && empty.people.isEmpty())
        assertNull(empty.product("x"))
        // 往返（离线缓存）
        val again = AppJson.decodeFromString(Watchlist.serializer(), AppJson.encodeToString(Watchlist.serializer(), watchlist))
        assertEquals(watchlist.products, again.products)
        assertEquals(watchlist.people, again.people)
    }

    // —— 匹配 ——
    private fun hits(products: List<String> = emptyList(), people: List<String> = emptyList()) =
        issue.followedHits(Follows.of(products, people)).map { it.item.id }

    @Test fun productMatchesAcrossSections() {
        // product（releases）、products（models、people）都算，按 xiaomi → 各栏目顺序
        assertEquals(listOf("2026-10-10-002", "2026-10-10-006", "2026-10-10-014"), hits(products = listOf("codex-cli")))
        // xiaomi.items 也参与；同时记录来源栏目
        val mimo = issue.followedHits(Follows.of(listOf("mimo-code"), emptyList()))
        assertEquals(listOf("2026-10-10-001" to "xiaomi", "2026-10-10-009" to SectionIds.RELEASES), mimo.map { it.item.id to it.sectionId })
    }

    @Test fun personMatchesSingleAndArrayAndChildren() {
        assertEquals(listOf("2026-10-10-014"), hits(people = listOf("thsottiaux")))
        assertEquals(listOf("2026-10-10-016"), hits(people = listOf("bcherny")))       // people[]
        assertEquals(listOf("2026-10-10-016"), hits(people = listOf("ClaudeDevs")))    // 大小写
        assertEquals(listOf("2026-10-10-005"), hits(people = listOf("mntruell")))      // 子条目命中，父条目不带字段
        assertEquals(listOf("2026-10-10-018"), hits(people = listOf("jietang")))       // other 栏目
    }

    @Test fun groupContainersDescendAndDedupe() {
        assertEquals(listOf("2026-10-10-012"), hits(products = listOf("copilot-cli")))
        // 同一条同时命中产品和人物，只出现一次
        assertEquals(
            listOf("2026-10-10-002", "2026-10-10-006", "2026-10-10-007", "2026-10-10-014", "2026-10-10-016"),
            hits(products = listOf("codex-cli", "claude-code"), people = listOf("thsottiaux", "claudedevs")),
        )
    }

    @Test fun unknownIdsAreHarmless() {
        assertTrue(hits(products = listOf("does-not-exist"), people = listOf("nobody")).isEmpty())
        // 条目里的未知 id 不在清单里也照样能按 id 匹配（关注只存 id）
        assertEquals(listOf("2026-10-10-010"), hits(products = listOf("ghost-cli")))
        assertTrue(hits().isEmpty())
        assertFalse(item("2026-10-10-003").matches(Follows.of(listOf("codex-cli"), listOf("thsottiaux"))))
    }

    @Test fun sectionFilterForReleasesPage() {
        val releases = issue.sections.first { it.id == SectionIds.RELEASES }.items
        assertEquals(listOf("2026-10-10-007"), collectFollowed(releases, Follows.of(listOf("claude-code"), emptyList())).map { it.id })
        // 跨类：只关注人物时，releases 里没有人物字段 → 空
        assertTrue(collectFollowed(releases, Follows.of(emptyList(), listOf("thsottiaux"))).isEmpty())
    }

    @Test fun normalizeKeepsOrderAndUnknowns() {
        assertEquals(listOf("codex-cli", "ghost", "_luofuli"), normalizeFollowIds(listOf(" Codex-CLI ", "ghost", "codex-cli", "", "@_LuoFuli")))
    }

    // —— 首页 / 设置逻辑 ——
    @Test fun followFilterDefaults() {
        val none = FollowUi()
        assertFalse(none.followedOnly(SectionIds.RELEASES))
        assertFalse(none.followedOnly(SectionIds.PEOPLE))
        val products = FollowUi(follows = Follows.of(listOf("codex-cli"), emptyList()))
        assertTrue(products.followedOnly(SectionIds.RELEASES))
        assertFalse(products.followedOnly(SectionIds.PEOPLE))
        assertFalse(products.followedOnly("models"))
        // 用户的选择优先
        assertFalse(products.copy(releasesFilter = FollowFilter.ALL).followedOnly(SectionIds.RELEASES))
        assertTrue(none.copy(peopleFilter = FollowFilter.FOLLOWED).followedOnly(SectionIds.PEOPLE))
    }

    @Test fun pagesIncludeFollowTabOnlyWhenFollowing() {
        val plain = buildPages(issue, listOf("小米"))
        assertTrue(plain.none { it is HomePage.Follow })
        assertEquals(listOf("today", "f-小米", "s-models", "s-harness", "s-releases", "s-people", "s-other"), plain.map { it.key })
        val withFollow = buildPages(issue, listOf("小米"), Follows.of(listOf("codex-cli"), listOf("_luofuli")))
        assertEquals("follow", withFollow[2].key)
        assertEquals(4, (withFollow[2] as HomePage.Follow).count)
    }

    @Test fun rowStylesAndDisplayFallbacks() {
        assertEquals(RowStyle.RELEASE, rowStyleOf(item("2026-10-10-006"), SectionIds.RELEASES))
        assertEquals(RowStyle.NEWS, rowStyleOf(item("2026-10-10-011"), SectionIds.RELEASES)) // group
        assertEquals(RowStyle.PERSON, rowStyleOf(item("2026-10-10-014"), SectionIds.PEOPLE))
        assertEquals(RowStyle.NEWS, rowStyleOf(item("2026-10-10-002"), "models"))

        assertEquals("DeepSeek Harness（dsh）", productDisplayName(item("2026-10-10-008"), watchlist))
        assertEquals("DeepSeek Harness", productDisplayName(item("2026-10-10-008"), null)) // 标题去掉版本号
        assertEquals("Ghost CLI", productDisplayName(item("2026-10-10-010"), watchlist))   // 清单里没有

        val tibo = personDisplay(item("2026-10-10-014"), watchlist)
        assertEquals("Tibo (Thibault Sottiaux)", tibo.name)
        assertEquals("thsottiaux", tibo.handle)
        assertEquals("OpenAI · Codex 负责人", tibo.orgRole)
        val ghost = personDisplay(item("2026-10-10-017"), watchlist)
        assertEquals("nobody_here", ghost.name)
        assertEquals("Unknown", ghost.orgRole)
        assertTrue(personDisplay(item("2026-10-10-016"), watchlist).official)
    }

    @Test fun settingsEntriesAndCounts() {
        assertEquals(22, productEntries(watchlist).size)
        val persons = personEntries(watchlist, official = false)
        val officials = personEntries(watchlist, official = true)
        assertEquals(40, persons.size + officials.size)
        assertTrue(officials.all { it.official } && persons.none { it.official })
        assertTrue(persons.first { it.id == "_luofuli" }.subtitle.startsWith("@_LuoFuli"))
        assertTrue(productEntries(null).isEmpty())
        val counts = issueCounts(issue, FollowKind.PRODUCTS)
        assertEquals(3, counts["codex-cli"])
        assertEquals(2, counts["mimo-code"])
        assertEquals(1, issueCounts(issue, FollowKind.PEOPLE)["claudedevs"])
    }
}
