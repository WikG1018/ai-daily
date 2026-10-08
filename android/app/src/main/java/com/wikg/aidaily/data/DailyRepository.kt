package com.wikg.aidaily.data

import com.wikg.aidaily.data.local.CacheStore
import com.wikg.aidaily.data.local.Prefs
import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.IssueSummary
import com.wikg.aidaily.data.model.ItemContext
import com.wikg.aidaily.data.model.locate
import com.wikg.aidaily.data.remote.DailyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** 单期加载结果：fromCache=true 且 error!=null 表示网络失败、显示的是离线缓存。 */
data class IssueResult(val issue: Issue, val fromCache: Boolean, val error: Throwable? = null)

class DailyRepository(
    private val api: DailyApi,
    private val cache: CacheStore,
    private val prefs: Prefs,
) {
    private val _index = MutableStateFlow<DailyIndex?>(null)
    val index: StateFlow<DailyIndex?> = _index.asStateFlow()

    private val memory = ConcurrentHashMap<String, Issue>()
    private val indexLock = Mutex()
    private var indexLoaded = false

    /** 先读本地缓存的索引（冷启动秒开）。 */
    suspend fun loadCachedIndex(): DailyIndex? = indexLock.withLock {
        if (!indexLoaded) {
            cache.readIndex()?.let { if (_index.value == null) _index.value = it }
            indexLoaded = true
        }
        _index.value
    }

    /** 从网络刷新索引（绕开 HTTP 缓存）。永不把本地已有的较新索引降级成镜像里的旧索引。 */
    suspend fun refreshIndex(): Result<DailyIndex> = runCatching {
        loadCachedIndex()
        val fetched = api.fetchIndex()
        val fresh = fetched.value
        indexLock.withLock {
            val current = _index.value
            val keep = current != null && (current.latest ?: "") > (fresh.latest ?: "")
            if (!keep) {
                _index.value = fresh
                cache.writeIndex(fresh)
            }
        }
        prefs.recordCheck("成功，最新一期 ${_index.value?.latest ?: "无"}", fetched.mirror.label)
        _index.value!!
    }

    fun summaryOf(date: String): IssueSummary? = _index.value?.issues?.firstOrNull { it.date == date }

    fun peekIssue(date: String): Issue? = memory[date]

    /**
     * 读取单期：内存 → 磁盘 → 网络。索引里的 published_at 与缓存不同说明修订过，重新拉取。
     */
    suspend fun loadIssue(date: String, forceNetwork: Boolean = false): Result<IssueResult> {
        val summary = summaryOf(date)
        val cached = memory[date] ?: cache.readIssue(date)?.also { memory[date] = it }
        val revised = cached != null && summary?.publishedAt != null && summary.publishedAt != cached.publishedAt
        if (cached != null && !forceNetwork && !revised) return Result.success(IssueResult(cached, fromCache = true))
        return try {
            val path = summary?.resolvedPath ?: "data/$date.json"
            val issue = api.fetchIssue(path, noCache = revised || forceNetwork).value
            memory[date] = issue
            cache.writeIssue(issue)
            Result.success(IssueResult(issue, fromCache = false))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (cached != null) Result.success(IssueResult(cached, fromCache = true, error = e)) else Result.failure(e)
        }
    }

    /** 详情页：id 以期号日期开头（YYYY-MM-DD-NNN）。 */
    suspend fun locateItem(id: String): ItemContext? {
        val date = id.take(10)
        val issue = memory[date] ?: loadIssue(date).getOrNull()?.issue ?: return null
        return issue.locate(id)
    }

    fun cachedDates(): Set<String> = cache.cachedDates()
}
