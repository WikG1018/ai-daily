package com.wikg.aidaily

import android.app.Application
import android.util.Log
import androidx.work.Configuration
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
}

class AiDailyApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()
        appScope.launch {
            val idx = container.repository.loadCachedIndex()
            WorkScheduler.ensureScheduled(this@AiDailyApp, idx?.latest, idx?.issues?.firstOrNull()?.publishedAt)
        }
    }
}
