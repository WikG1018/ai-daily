package com.wikg.aidaily.work

import com.wikg.aidaily.data.DailyRepository
import com.wikg.aidaily.data.local.Prefs
import kotlinx.coroutines.flow.first

data class CheckOutcome(
    val failed: Boolean,
    val latest: String?,
    val latestPublishedAt: String?,
    val newIssue: Boolean,
)

/** 拉 index.json → 比较 latest → 新一期就预取并发本地通知。前台刷新与后台任务共用。 */
class DailyChecker(
    private val repo: DailyRepository,
    private val prefs: Prefs,
    private val notifier: Notifier,
) {
    suspend fun check(notify: Boolean): CheckOutcome {
        val result = repo.refreshIndex()
        val index = result.getOrElse { e ->
            prefs.recordCheck("失败：${e.message ?: e.javaClass.simpleName}", null)
            val cached = repo.index.value
            return CheckOutcome(true, cached?.latest, cached?.issues?.firstOrNull()?.publishedAt, false)
        }
        val latest = index.latest
        val head = index.issues.firstOrNull { it.date == latest } ?: index.issues.firstOrNull()
        if (latest == null || head == null) {
            prefs.recordCheck("暂无任何一期", null)
            return CheckOutcome(false, null, null, false)
        }
        val (advanced, before) = prefs.advanceLatest(latest)
        if (advanced) {
            // 预取，保证点开通知时离线也能看
            repo.loadIssue(latest)
            val settings = prefs.settings.first()
            // before == null：首次安装后的第一次检查，只建立基线，不打扰
            if (notify && before != null && settings.notifyEnabled) notifier.notifyNewIssue(head)
        } else if (repo.peekIssue(latest) == null) {
            repo.loadIssue(latest)
        }
        prefs.recordCheck(if (advanced && before != null) "发现新一期 $latest" else "已是最新（$latest）", null)
        return CheckOutcome(false, latest, head.publishedAt, advanced && before != null)
    }
}
