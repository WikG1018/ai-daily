package com.wikg.aidaily

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.wikg.aidaily.crash.CrashLog
import com.wikg.aidaily.data.DailyRepository
import com.wikg.aidaily.data.local.CacheStore
import com.wikg.aidaily.data.local.Prefs
import com.wikg.aidaily.data.remote.DailyApi
import com.wikg.aidaily.work.DailyChecker
import com.wikg.aidaily.work.Notifier
import com.wikg.aidaily.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手写依赖容器，够用且零反射。 */
class AppContainer(app: Application) {
    val prefs = Prefs(app)
    val cache = CacheStore(app)
    val api = DailyApi()
    val repository = DailyRepository(api, cache, prefs)
    val notifier = Notifier(app)
    val checker = DailyChecker(repository, prefs, notifier)
    /** v1.3 应用内更新：懒加载，启动路径上不构造。 */
    val updates by lazy { com.wikg.aidaily.update.UpdateManager(app, prefs) }
}

class AiDailyApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, t -> Log.w("AiDailyApp", "background task failed", t) },
    )

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        // 最先装崩溃记录器：之后任何未捕获异常都会写进 filesDir/crash/，下次启动可查看/复制。
        runCatching { CrashLog.install(this) }
        container = AppContainer(this)
        // 启动路径上的一切非必要工作都不允许把 app 带崩（坏缓存 / 网络 / 系统服务异常）。
        runCatching { container.notifier.ensureChannel() }.onFailure { Log.w(TAG, "ensureChannel failed", it) }
        appScope.launch {
            try {
                val idx = runCatching { container.repository.loadCachedIndex() }.getOrNull()
                WorkScheduler.ensureScheduled(this@AiDailyApp, idx?.latest, idx?.issues?.firstOrNull()?.publishedAt)
            } catch (t: Throwable) {
                Log.w(TAG, "schedule failed", t)
            }
        }
    }

    companion object { private const val TAG = "AiDailyApp" }
}
