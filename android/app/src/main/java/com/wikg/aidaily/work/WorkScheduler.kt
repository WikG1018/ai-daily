package com.wikg.aidaily.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wikg.aidaily.util.nowBeijing
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 两条腿走路（不依赖 FCM / 厂商推送）：
 *  1. 「早间链」：一次性任务，按 [Schedule] 精确排到每天 8:35 起，没发布就 20 分钟重试；
 *  2. 「兜底」：每 3 小时一次的周期任务，链条被系统打断时把它重新接上。
 * 两者都会比较 index.json 的 latest，用 DataStore 原子去重，不会重复通知。
 */
object WorkScheduler {
    const val MORNING = "ai-daily-morning-check"
    const val PERIODIC = "ai-daily-periodic-check"
    const val ONESHOT = "ai-daily-manual-check"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensureScheduled(context: Context, latest: String?, publishedAt: String?) {
        val wm = WorkManager.getInstance(context)
        val periodic = PeriodicWorkRequestBuilder<DailyCheckWorker>(3, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag("ai-daily")
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        scheduleMorning(context, latest, publishedAt, ExistingWorkPolicy.KEEP)
    }

    /** 由早间链自身调用时用 APPEND_OR_REPLACE（排在自己后面），其余场景用 KEEP。 */
    fun scheduleMorning(context: Context, latest: String?, publishedAt: String?, policy: ExistingWorkPolicy) {
        val delay: Duration = Schedule.nextDelay(nowBeijing(), latest, publishedAt)
        val req = OneTimeWorkRequestBuilder<DailyCheckWorker>()
            .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .setInputData(workDataOf(DailyCheckWorker.KEY_CHAIN to true))
            .addTag("ai-daily")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MORNING, policy, req)
    }

    /** 设置页「立即检查」（会发通知，便于验证通知链路）。 */
    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<DailyCheckWorker>()
            .setConstraints(networkConstraints)
            .setInputData(workDataOf(DailyCheckWorker.KEY_MANUAL to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.REPLACE, req)
    }
}
