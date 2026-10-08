package com.wikg.aidaily.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

fun nowBeijing(): ZonedDateTime = ZonedDateTime.now(BEIJING)

fun parseDate(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

fun weekdayCn(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "周一"; DayOfWeek.TUESDAY -> "周二"; DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"; DayOfWeek.FRIDAY -> "周五"; DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

/** 2026-10-08 → 10月8日 周四 */
fun issueDateLabel(date: String, withWeekday: Boolean = true): String {
    val d = parseDate(date) ?: return date
    return "${d.monthValue}月${d.dayOfMonth}日" + if (withWeekday) " " + weekdayCn(d.dayOfWeek) else ""
}

/** 2026-10-08 → 2026年10月8日 */
fun issueDateLong(date: String): String {
    val d = parseDate(date) ?: return date
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
}

/**
 * 条目时间 "MM-DD HH:MM"（北京时间）→ LocalDateTime。
 * 年份取期号所在年；期号在 1 月而条目在 12 月时属于上一年。
 */
fun itemDateTime(issueDate: String, time: String?): LocalDateTime? {
    if (time.isNullOrBlank()) return null
    val d = parseDate(issueDate) ?: return null
    val m = Regex("""^(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})$""").find(time.trim()) ?: return null
    val (mo, da, hh, mm) = m.destructured
    val month = mo.toInt()
    val year = if (d.monthValue == 1 && month == 12) d.year - 1 else d.year
    return runCatching { LocalDateTime.of(year, month, da.toInt(), hh.toInt(), mm.toInt()) }.getOrNull()
}

/** "10-08 02:01" → "10月8日 02:01" */
fun itemTimeLabel(issueDate: String, time: String?): String? {
    val t = itemDateTime(issueDate, time) ?: return time
    return "${t.monthValue}月${t.dayOfMonth}日 " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** "10-08 02:01" → "02:01"（列表里同期内只显示时分 + 日期简写） */
fun itemTimeShort(time: String?): String? = time?.trim()?.let { t ->
    val m = Regex("""^(\d{1,2})-(\d{1,2})\s+(\d{1,2}:\d{2})$""").find(t) ?: return t
    val (mo, da, hm) = m.destructured
    "${mo.toInt()}/${da.toInt()} $hm"
}

/** ISO 8601 带偏移 → 北京时间 "10月9日 08:30" */
fun isoToBeijingLabel(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val t = OffsetDateTime.parse(iso).atZoneSameInstant(BEIJING)
        "${t.monthValue}月${t.dayOfMonth}日 " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(iso)
}

fun epochToBeijingLabel(ms: Long): String {
    if (ms <= 0) return "从未"
    val t = java.time.Instant.ofEpochMilli(ms).atZone(BEIJING)
    return "${t.monthValue}月${t.dayOfMonth}日 " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
}
