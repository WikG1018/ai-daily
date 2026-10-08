package com.wikg.aidaily

import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.VendorMatcher
import com.wikg.aidaily.data.model.featuredColumn
import com.wikg.aidaily.data.model.normalizeFeatured
import com.wikg.aidaily.data.model.vendorCounts
import com.wikg.aidaily.data.remote.AppJson
import com.wikg.aidaily.ui.home.HomePage
import com.wikg.aidaily.ui.home.buildPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeaturedTest {
    private val issue = AppJson.decodeFromString(Issue.serializer(), File("../../data/2026-10-08.json").readText())

    @Test fun xiaomiDefaultUsesSchemaEmptyText() {
        val col = issue.featuredColumn("小米")
        assertTrue(col.isXiaomi)
        assertTrue(col.items.isEmpty())
        assertEquals("本期未收录小米相关动态。", col.emptyText)
    }

    @Test fun collectsAcrossSectionsWithAliases() {
        assertEquals(listOf("2026-10-08-001", "2026-10-08-008"), issue.featuredColumn("anthropic").items.map { it.id })
        val oa = issue.featuredColumn("OpenAI")
        assertEquals(listOf("2026-10-08-003", "2026-10-08-011", "2026-10-08-021"), oa.items.map { it.id })
        assertEquals(4, oa.count) // Codex 条目带 1 个子条目
        // 微软 ↔ Microsoft ↔ GitHub Copilot（group 整体收录，子条目不重复）
        val ms = issue.featuredColumn("Microsoft")
        assertEquals(listOf("2026-10-08-004", "2026-10-08-013"), ms.items.map { it.id })
        assertEquals(ms.items.map { it.id }, issue.featuredColumn("微软").items.map { it.id })
        // 子条目单独匹配：Artificial Analysis 是 Anthropic 条目的子条目
        assertEquals(listOf("2026-10-08-002"), issue.featuredColumn("Artificial Analysis").items.map { it.id })
        val none = issue.featuredColumn("豆包")
        assertTrue(none.items.isEmpty())
        assertEquals("本期未收录豆包相关动态", none.emptyText)
    }

    @Test fun matcherIsStrictEnough() {
        val mi = VendorMatcher.aliasesOf("小米")
        assertTrue(VendorMatcher.matches("Xiaomi", mi))
        assertTrue(VendorMatcher.matches("MI", mi))
        assertTrue(VendorMatcher.matches("小米汽车", mi))
        assertTrue(VendorMatcher.matches("Xiaomi MiMo", mi))
        assertFalse(VendorMatcher.matches("MiniMax", mi))
        assertFalse(VendorMatcher.matches("Microsoft", mi))
        assertFalse(VendorMatcher.matches("Mistral", mi))
        assertTrue(VendorMatcher.matches("阿里云", VendorMatcher.aliasesOf("通义/阿里")))
        assertTrue(VendorMatcher.matches("Qwen", VendorMatcher.aliasesOf("通义/阿里")))
        // 别名表外的名字：精确匹配（不区分大小写）
        assertTrue(VendorMatcher.matches("liquid ai", VendorMatcher.aliasesOf("Liquid AI")))
        assertFalse(VendorMatcher.matches("Liquid", VendorMatcher.aliasesOf("Liquid AI")))
    }

    @Test fun schemaXiaomiItemsComeFirstAndDedupe() {
        val json = """{"date":"2026-01-02","xiaomi":{"items":[{"id":"a","vendor":"小米","title":"A","children":[{"id":"a1","vendor":"澎湃OS","title":"A1"}]}]},
            "sections":[{"id":"m","title":"模型","items":[
              {"id":"b","vendor":"Anthropic","title":"B","children":[{"id":"b1","vendor":"Xiaomi","title":"B1"}]},
              {"id":"a1","vendor":"澎湃OS","title":"dup"},
              {"id":"c","vendor":"HyperOS","title":"C"}]}]}"""
        val i = AppJson.decodeFromString(Issue.serializer(), json)
        assertEquals(listOf("a", "b1", "c"), i.featuredColumn("Xiaomi").items.map { it.id })
        assertEquals(4, i.featuredColumn("小米").count)
    }

    @Test fun pagesAndNormalization() {
        val pages = buildPages(issue, listOf("小米", "Anthropic", "anthropic ", ""))
        assertEquals(listOf("today", "f-小米", "f-anthropic", "s-models", "s-harness", "s-other"), pages.map { it.key })
        assertEquals("编码 Agent", pages.first { it.key == "s-harness" }.tabTitle)
        assertEquals(issue.newsCount, (pages[0] as HomePage.Today).count)
        assertEquals(listOf("小米", "Anthropic"), normalizeFeatured(listOf(" 小米", "Anthropic", "ANTHROPIC", " ")))
        assertEquals(listOf("today", "s-models", "s-harness", "s-other"), buildPages(issue, emptyList()).map { it.key })
        assertTrue(issue.vendorCounts().first().second >= 2)
    }
}
