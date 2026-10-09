package com.wikg.aidaily.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.wikg.aidaily.data.model.Follows
import com.wikg.aidaily.data.model.normalizeFeatured
import com.wikg.aidaily.data.model.normalizeFollowIds

private val Context.dataStore by preferencesDataStore("ai_daily_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 版本更新 / 人物动态页的筛选：全部 / 只看关注。null = 用户还没选过（按是否有关注自动决定）。 */
enum class FollowFilter { ALL, FOLLOWED }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notifyEnabled: Boolean = true,
    val guideDismissed: Boolean = false,
    val autostartConfirmed: Boolean = false,
    val lastCheckAt: Long = 0,
    val lastCheckResult: String? = null,
    val lastMirror: String? = null,
    val notifAsked: Boolean = false,
    /** 首页「关注」专栏的厂商，有序；默认只有小米（与 v1.0 体验一致）。 */
    val featuredVendors: List<String> = DEFAULT_FEATURED,
    /** v1.2：关注的 harness 产品 id（有序）。清单里消失的 id 保留，不自动删除。 */
    val followProducts: List<String> = emptyList(),
    /** v1.2：关注的人物 / 官方账号 id（小写 handle，有序）。 */
    val followPeople: List<String> = emptyList(),
    val releasesFilter: FollowFilter? = null,
    val peopleFilter: FollowFilter? = null,
) {
    val follows: Follows get() = Follows.of(followProducts, followPeople)
}

val DEFAULT_FEATURED = listOf("小米")

class Prefs(private val context: Context) {
    private object K {
        val readIds = stringSetPreferencesKey("read_ids")
        val lastSeenLatest = stringPreferencesKey("last_seen_latest")
        val theme = stringPreferencesKey("theme_mode")
        val notify = booleanPreferencesKey("notify_enabled")
        val guideDismissed = booleanPreferencesKey("guide_dismissed")
        val autostart = booleanPreferencesKey("autostart_confirmed")
        val lastCheckAt = longPreferencesKey("last_check_at")
        val lastCheckResult = stringPreferencesKey("last_check_result")
        val lastMirror = stringPreferencesKey("last_mirror")
        val notifAsked = booleanPreferencesKey("notif_asked")
        /** 有序列表，用换行分隔（字符串集合不保证顺序，而专栏需要可排序）。键不存在 = 默认值；空串 = 用户清空了。 */
        val featured = stringPreferencesKey("featured_vendors")
        val followProducts = stringPreferencesKey("follow_products")
        val followPeople = stringPreferencesKey("follow_people")
        val releasesFilter = stringPreferencesKey("filter_releases")
        val peopleFilter = stringPreferencesKey("filter_people")
    }

    private val store get() = context.dataStore

    val readIds: Flow<Set<String>> = store.data.map { it[K.readIds].orEmpty() }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            notifyEnabled = p[K.notify] ?: true,
            guideDismissed = p[K.guideDismissed] ?: false,
            autostartConfirmed = p[K.autostart] ?: false,
            lastCheckAt = p[K.lastCheckAt] ?: 0,
            lastCheckResult = p[K.lastCheckResult],
            lastMirror = p[K.lastMirror],
            notifAsked = p[K.notifAsked] ?: false,
            featuredVendors = p[K.featured]?.let { raw -> raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() } }
                ?: DEFAULT_FEATURED,
            followProducts = splitIds(p[K.followProducts]),
            followPeople = splitIds(p[K.followPeople]),
            releasesFilter = p[K.releasesFilter]?.let { runCatching { FollowFilter.valueOf(it) }.getOrNull() },
            peopleFilter = p[K.peopleFilter]?.let { runCatching { FollowFilter.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun markRead(id: String) = store.edit { p ->
        val cur = p[K.readIds].orEmpty()
        if (id !in cur) {
            // 只保留最近约 90 天的已读记录（id 以日期开头，按字典序裁剪）
            val next = (cur + id).let { s -> if (s.size > 3000) s.sorted().takeLast(2500).toSet() else s }
            p[K.readIds] = next
        }
    }

    suspend fun markAllRead(ids: Collection<String>) = store.edit { p -> p[K.readIds] = p[K.readIds].orEmpty() + ids }

    suspend fun lastSeenLatest(): String? = store.data.first()[K.lastSeenLatest]

    /**
     * 原子地把“已知最新一期”推进到 [latest]。
     * 返回 Pair(是否推进, 推进前的值)。
     */
    suspend fun advanceLatest(latest: String): Pair<Boolean, String?> {
        var advanced = false
        var before: String? = null
        store.edit { p ->
            before = p[K.lastSeenLatest]
            if (before == null || latest > before!!) {
                p[K.lastSeenLatest] = latest
                advanced = true
            }
        }
        return advanced to before
    }

    suspend fun setFeaturedVendors(list: List<String>) = store.edit {
        it[K.featured] = normalizeFeatured(list).joinToString("\n")
    }

    suspend fun setFollowProducts(ids: List<String>) = store.edit { it[K.followProducts] = normalizeFollowIds(ids).joinToString("\n") }
    suspend fun setFollowPeople(ids: List<String>) = store.edit { it[K.followPeople] = normalizeFollowIds(ids).joinToString("\n") }
    suspend fun setReleasesFilter(f: FollowFilter) = store.edit { it[K.releasesFilter] = f.name }
    suspend fun setPeopleFilter(f: FollowFilter) = store.edit { it[K.peopleFilter] = f.name }

    /** 恢复「全部 / 只看关注」为自动（按有没有关注决定）。 */
    suspend fun resetFollowFilters() = store.edit { it.remove(K.releasesFilter); it.remove(K.peopleFilter) }

    suspend fun setNotifAsked() = store.edit { it[K.notifAsked] = true }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[K.theme] = mode.name }
    suspend fun setNotify(on: Boolean) = store.edit { it[K.notify] = on }
    suspend fun setGuideDismissed(on: Boolean) = store.edit { it[K.guideDismissed] = on }
    suspend fun setAutostartConfirmed(on: Boolean) = store.edit { it[K.autostart] = on }
    suspend fun recordCheck(result: String, mirror: String?) = store.edit {
        it[K.lastCheckAt] = System.currentTimeMillis()
        it[K.lastCheckResult] = result
        if (mirror != null) it[K.lastMirror] = mirror
    }
}

private fun splitIds(raw: String?): List<String> =
    raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
