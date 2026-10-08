package com.wikg.aidaily.work

import com.wikg.aidaily.util.BEIJING
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * 简报每天北京时间约 8:17 开始生成、8:30 前发布。
 * 规则（纯函数，便于测试）：
 *  - 8:35 之前：等到今天 8:35
 *  - 今天这期已拿到：等到明天 8:35
 *  - 8:35–12:00 还没拿到：20 分钟后再查
 *  - 12:00–22:00：2 小时后再查
 *  - 22:00 之后：明天 8:35
 */
object Schedule {
    val FIRST_CHECK_HOUR = 8
    val FIRST_CHECK_MINUTE = 35

    fun hasTodaysIssue(now: ZonedDateTime, latest: String?, latestPublishedAt: String?): Boolean {
        val today = now.withZoneSameInstant(BEIJING).toLocalDate()
        val publishedToday = latestPublishedAt?.let {
            runCatching { OffsetDateTime.parse(it).atZoneSameInstant(BEIJING).toLocalDate() == today }.getOrNull()
        } ?: false
        val dateOk = latest?.let { runCatching { !LocalDate.parse(it).isBefore(today.minusDays(1)) }.getOrNull() } ?: false
        return publishedToday || dateOk
    }

    fun nextDelay(nowAny: ZonedDateTime, latest: String?, latestPublishedAt: String?): Duration {
        val now = nowAny.withZoneSameInstant(BEIJING)
        val firstToday = now.toLocalDate().atTime(FIRST_CHECK_HOUR, FIRST_CHECK_MINUTE).atZone(BEIJING)
        val firstTomorrow = firstToday.plusDays(1)
        return when {
            now.isBefore(firstToday) -> Duration.between(now, firstToday)
            hasTodaysIssue(now, latest, latestPublishedAt) -> Duration.between(now, firstTomorrow)
            now.hour < 12 -> Duration.ofMinutes(20)
            now.hour < 22 -> Duration.ofHours(2)
            else -> Duration.between(now, firstTomorrow)
        }
    }
}
