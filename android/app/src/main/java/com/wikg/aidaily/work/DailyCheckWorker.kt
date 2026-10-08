package com.wikg.aidaily.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.wikg.aidaily.AiDailyApp

class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AiDailyApp
        val outcome = app.container.checker.check(notify = true)
        val chain = inputData.getBoolean(KEY_CHAIN, false)
        if (chain) {
            // 接上早间链的下一环
            WorkScheduler.scheduleMorning(applicationContext, outcome.latest, outcome.latestPublishedAt, ExistingWorkPolicy.APPEND_OR_REPLACE)
        } else if (!inputData.getBoolean(KEY_MANUAL, false)) {
            // 兜底任务：若早间链断了就补上
            WorkScheduler.scheduleMorning(applicationContext, outcome.latest, outcome.latestPublishedAt, ExistingWorkPolicy.KEEP)
        }
        return if (outcome.failed && !chain && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_CHAIN = "chain"
        const val KEY_MANUAL = "manual"
    }
}
