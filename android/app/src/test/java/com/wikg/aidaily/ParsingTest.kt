package com.wikg.aidaily

import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.Region
import com.wikg.aidaily.data.model.locate
import com.wikg.aidaily.data.remote.AppJson
import com.wikg.aidaily.util.itemDateTime
import com.wikg.aidaily.util.splitBold
import com.wikg.aidaily.util.stripBold
import com.wikg.aidaily.work.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class ParsingTest {
    private val dataDir = File("../../data")

    @Test fun parsesSampleIndex() {
        val idx = AppJson.decodeFromString(DailyIndex.serializer(), File(dataDir, "index.json").readText())
        assertEquals(idx.latest, idx.issues.first().date)
        assertTrue(idx.issues.first().highlights.isNotBlank())
    }

    @Test fun parsesSampleIssueAndCountsMatchIndex() {
        val idx = AppJson.decodeFromString(DailyIndex.serializer(), File(dataDir, "index.json").readText())
        val s = idx.issues.first()
        val issue = AppJson.decodeFromString(Issue.serializer(), File("../..", s.resolvedPath).readText())
        assertEquals(s.date, issue.date)
        assertEquals(s.itemCount, issue.newsCount)
        val ids = issue.allItems().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val first = ids.first()
        val ctx = issue.locate(first)
        assertNotNull(ctx)
    }

    @Test fun ignoresUnknownFieldsAndNulls() {
        val json = """{"schema_version":2,"latest":null,"updated_at":"x","issues":[],"future":{"a":1}}"""
        val idx = AppJson.decodeFromString(DailyIndex.serializer(), json)
        assertEquals(null, idx.latest)
        val issue = AppJson.decodeFromString(
            Issue.serializer(),
            """{"date":"2026-01-02","highlights":"h","sections":[{"id":"x","title":"t","items":[{"id":"2026-01-02-001","vendor":"V","region":"mars","title":"T","summary":null,"links":[{"url":"https://a"}],"newField":true}]}],"published_at":"2026-01-03T08:20:00+08:00"}""",
        )
        val item = issue.sections[0].items[0]
        assertEquals(Region.INTL, item.regionKind)
        assertEquals("原帖", item.links[0].displayLabel)
    }

    @Test fun boldIsNonGreedy() {
        val parts = splitBold("A **b** c **d** e")
        assertEquals(listOf("A " to false, "b" to true, " c " to false, "d" to true, " e" to false), parts)
        // HTML 原样保留为文本，不解析
        assertEquals("<b>x</b> y", stripBold("<b>x</b> **y**"))
        assertEquals("plain", stripBold("plain"))
        assertEquals("a*b", stripBold("a*b"))
    }

    @Test fun itemYearRollsBackForDecemberInJanuaryIssue() {
        assertEquals(2025, itemDateTime("2026-01-01", "12-31 23:50")!!.year)
        assertEquals(2026, itemDateTime("2026-10-08", "10-08 02:01")!!.year)
    }

    @Test fun morningSchedule() {
        val bj = ZoneId.of("Asia/Shanghai")
        val early = ZonedDateTime.of(2026, 10, 9, 7, 0, 0, 0, bj)
        assertEquals(95, Schedule.nextDelay(early, "2026-10-08", null).toMinutes())
        val nine = ZonedDateTime.of(2026, 10, 9, 9, 0, 0, 0, bj)
        assertEquals(20, Schedule.nextDelay(nine, "2026-10-07", null).toMinutes())
        assertTrue(Schedule.hasTodaysIssue(nine, "2026-10-08", null))
        assertFalse(Schedule.hasTodaysIssue(nine, "2026-10-07", "2026-10-08T08:30:00+08:00"))
        assertTrue(Schedule.hasTodaysIssue(nine, "2026-10-07", "2026-10-09T08:30:00+08:00"))
        // 今天已拿到 → 明天 8:35
        assertEquals(23 * 60 + 35, Schedule.nextDelay(nine, "2026-10-08", null).toMinutes())
        val night = ZonedDateTime.of(2026, 10, 9, 23, 0, 0, 0, bj)
        assertEquals(9 * 60 + 35, Schedule.nextDelay(night, "2026-10-07", null).toMinutes())
    }
}
