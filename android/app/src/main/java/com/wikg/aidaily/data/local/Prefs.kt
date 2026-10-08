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
import com.wikg.aidaily.data.model.normalizeFeatured

private val Context.dataStore by preferencesDataStore("ai_daily_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
)

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
