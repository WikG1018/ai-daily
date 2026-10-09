package com.wikg.aidaily.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wikg.aidaily.AppContainer
import com.wikg.aidaily.data.local.Settings
import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.Watchlist
import com.wikg.aidaily.data.local.FollowFilter
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val index: DailyIndex? = null,
    val selectedDate: String? = null,
    val issue: Issue? = null,
    val initialLoading: Boolean = true,
    val issueLoading: Boolean = false,
    val refreshing: Boolean = false,
    /** 网络失败、正在显示离线缓存 */
    val offline: Boolean = false,
    /** 没有任何可显示的内容时的错误 */
    val fatalError: String? = null,
    val readIds: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    /** v1.2 关注清单；null = 还没有（不影响任何功能，只是少了人名 / 产品名等补充信息）。 */
    val watchlist: Watchlist? = null,
) {
    val latest: String? get() = index?.latest
    val isLatestSelected: Boolean get() = selectedDate == null || selectedDate == latest
}

private const val TAG = "HomeViewModel"

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 一次性提示（Snackbar） */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private var issueJob: Job? = null

    init {
        viewModelScope.launch { c.prefs.readIds.collect { ids -> _state.update { it.copy(readIds = ids) } } }
        viewModelScope.launch { c.prefs.settings.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch { repo.index.collect { idx -> _state.update { it.copy(index = idx) } } }
        // 关注清单：全部包在 try 里，任何异常都不能影响启动
        viewModelScope.launch {
            try {
                repo.watchlist.collect { w -> _state.update { it.copy(watchlist = w) } }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "watchlist flow failed", t)
            }
        }
        viewModelScope.launch {
            try { repo.loadCachedWatchlist() } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "load cached watchlist failed", t)
            }
        }
        viewModelScope.launch {
            // 冷启动：先显示缓存，再联网
            val cached = repo.loadCachedIndex()
            if (cached?.latest != null) loadIssue(currentDate() ?: cached.latest, showSpinner = false).join()
            refresh(user = false)
        }
    }

    private fun currentDate(): String? = _state.value.selectedDate ?: _state.value.index?.latest

    fun refresh(user: Boolean = true) {
        refreshWatchlist(user)
        viewModelScope.launch {
            _state.update { it.copy(refreshing = user) }
            val r = repo.refreshIndex()
            r.onSuccess { idx ->
                // 前台已经看到了，就不要再为这一期发通知
                idx.latest?.let { c.prefs.advanceLatest(it) }
                _state.update { it.copy(offline = false) }
            }.onFailure {
                _state.update { it.copy(offline = true) }
                if (user) _messages.value = "网络不给力，正在显示离线内容"
            }
            val date = currentDate()
            if (date != null) loadIssue(date, showSpinner = _state.value.issue?.date != date, force = false).join()
            _state.update { s ->
                s.copy(
                    refreshing = false,
                    initialLoading = false,
                    fatalError = when {
                        s.issue != null && s.issue.date == (s.selectedDate ?: s.index?.latest) -> null
                        r.isFailure && s.index?.latest == null -> "暂时连不上数据源，请检查网络后下拉重试"
                        else -> s.fatalError
                    },
                )
            }
        }
    }

    fun select(date: String) {
        if (date == currentDate() && _state.value.issue?.date == date) return
        _state.update { it.copy(selectedDate = date) }
        loadIssue(date, showSpinner = true)
    }

    fun backToLatest() {
        val latest = _state.value.index?.latest ?: return
        _state.update { it.copy(selectedDate = null) }
        loadIssue(latest, showSpinner = true)
    }

    /** 通知 / 深链进入某一期 */
    fun openIssue(date: String) {
        _state.update { it.copy(selectedDate = date) }
        viewModelScope.launch {
            repo.loadCachedIndex()
            loadIssue(date, showSpinner = _state.value.issue?.date != date).join()
            if (_state.value.issue?.date != date) refresh(user = false)
        }
    }

    private fun loadIssue(date: String, showSpinner: Boolean, force: Boolean = false): Job {
        issueJob?.cancel()
        return viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(issueLoading = true) }
            val r = repo.loadIssue(date, forceNetwork = force)
            r.onSuccess { res ->
                _state.update {
                    it.copy(issue = res.issue, issueLoading = false, initialLoading = false, fatalError = null,
                        offline = it.offline || res.error != null)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(issueLoading = false, fatalError = if (it.issue?.date == date) null else "这一期还没有缓存，联网后再试（${e.message ?: "网络错误"}）")
                }
            }
        }.also { issueJob = it }
    }

    fun markAllRead() {
        val ids = _state.value.issue?.allItems()?.map { it.id } ?: return
        // 反馈由首页顶栏的就地动画负责，不再弹 Snackbar
        viewModelScope.launch { c.prefs.markAllRead(ids) }
    }

    /** 约每天一次随 index 刷新；用户下拉时放宽到 1 小时。独立协程，失败静默（继续用缓存）。 */
    private fun refreshWatchlist(user: Boolean) {
        viewModelScope.launch {
            try {
                repo.refreshWatchlist(maxAgeMs = if (user) 60L * 60 * 1000 else com.wikg.aidaily.data.WATCHLIST_MAX_AGE_MS)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "refresh watchlist failed", t)
            }
        }
    }

    fun setReleasesFilter(f: FollowFilter) { viewModelScope.launch { c.prefs.setReleasesFilter(f) } }
    fun setPeopleFilter(f: FollowFilter) { viewModelScope.launch { c.prefs.setPeopleFilter(f) } }

    fun consumeMessage() { _messages.value = null }

    fun dismissGuide() { viewModelScope.launch { c.prefs.setGuideDismissed(true) } }
    fun setNotifAsked() { viewModelScope.launch { c.prefs.setNotifAsked() } }
}
