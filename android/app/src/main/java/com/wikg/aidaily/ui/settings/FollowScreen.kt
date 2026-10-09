package com.wikg.aidaily.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.AiDailyApp
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.SectionIds
import com.wikg.aidaily.data.model.Watchlist
import com.wikg.aidaily.data.model.normId
import com.wikg.aidaily.ui.components.PersonAvatar
import com.wikg.aidaily.ui.components.ProductMark
import com.wikg.aidaily.ui.components.regionColor
import com.wikg.aidaily.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 关注清单里的两类对象：设置里各一个二级页。 */
enum class FollowKind(val route: String, val title: String) {
    PRODUCTS("follow/products", "关注产品"),
    PEOPLE("follow/people", "关注人物");

    companion object {
        fun forSection(sectionId: String?): FollowKind = if (sectionId == SectionIds.PEOPLE) PEOPLE else PRODUCTS
    }
}

/** 一行可关注对象（与清单字段解耦，便于测试 / 截图）。 */
data class FollowEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val region: com.wikg.aidaily.data.model.Region,
    val official: Boolean = false,
    val search: String = "",
)

internal fun productEntries(w: Watchlist?): List<FollowEntry> = w?.validProducts.orEmpty().map { p ->
    FollowEntry(
        id = normId(p.id)!!, title = p.displayName,
        subtitle = listOf(p.vendor, p.regionKind.label).filter { it.isNotBlank() }.joinToString(" · "),
        region = p.regionKind, search = "${p.id} ${p.name} ${p.vendor}".lowercase(),
    )
}

internal fun personEntries(w: Watchlist?, official: Boolean): List<FollowEntry> =
    (if (official) w?.officials else w?.individuals).orEmpty().map { p ->
        FollowEntry(
            id = normId(p.id)!!, title = p.displayName,
            subtitle = listOf("@" + p.displayHandle, p.orgRole).filter { it.isNotBlank() }.joinToString(" · "),
            region = p.regionKind, official = p.isOfficial,
            search = "${p.id} ${p.name} ${p.handle} ${p.org} ${p.role}".lowercase(),
        )
    }

/** 每个 id 在最新一期里命中的条数（用于「本期 N 条」）。 */
internal fun issueCounts(issue: Issue?, kind: FollowKind): Map<String, Int> {
    if (issue == null) return emptyMap()
    val m = HashMap<String, Int>()
    issue.allItems().filter { !it.group }.forEach { item ->
        val ids = if (kind == FollowKind.PRODUCTS) item.productIds else item.personIds
        ids.forEach { m[it] = (m[it] ?: 0) + 1 }
    }
    return m
}

@Composable
fun FollowScreen(kind: FollowKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = remember { (context.applicationContext as AiDailyApp).container }
    val settings by c.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    val watchlist by c.repository.watchlist.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<Issue?>(null) }
    suspend fun load(force: Boolean) {
        loading = true
        val r = runCatching { c.repository.refreshWatchlist(maxAgeMs = if (force) 0 else com.wikg.aidaily.data.WATCHLIST_MAX_AGE_MS) }
        failed = r.getOrNull()?.isFailure ?: true
        loading = false
    }
    LaunchedEffect(Unit) {
        runCatching { c.repository.loadCachedWatchlist() }
        latest = runCatching { c.repository.loadCachedIndex()?.latest?.let { c.repository.cachedIssue(it) } }.getOrNull()
        load(force = false)
    }
    val s = settings ?: return
    FollowContent(
        kind = kind,
        watchlist = watchlist,
        followed = if (kind == FollowKind.PRODUCTS) s.followProducts else s.followPeople,
        counts = remember(latest, kind) { issueCounts(latest, kind) },
        loading = loading,
        failed = failed && watchlist == null,
        onChange = { ids ->
            scope.launch { if (kind == FollowKind.PRODUCTS) c.prefs.setFollowProducts(ids) else c.prefs.setFollowPeople(ids) }
        },
        onRetry = { scope.launch { load(force = true) } },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowContent(
    kind: FollowKind,
    watchlist: Watchlist?,
    followed: List<String>,
    counts: Map<String, Int> = emptyMap(),
    loading: Boolean = false,
    failed: Boolean = false,
    onChange: (List<String>) -> Unit,
    onRetry: () -> Unit = {},
    onBack: () -> Unit,
) {
    val followedSet = remember(followed) { followed.mapNotNull { normId(it) }.toSet() }
    fun toggle(id: String) {
        onChange(if (id in followedSet) followed.filterNot { normId(it) == id } else followed + id)
    }
    val groups: List<Pair<String, List<FollowEntry>>> = remember(watchlist, kind) {
        if (kind == FollowKind.PRODUCTS) listOf("全部产品" to productEntries(watchlist))
        else listOf("个人" to personEntries(watchlist, false), "官方账号" to personEntries(watchlist, true))
    }
    val byId = remember(groups) { groups.flatMap { it.second }.associateBy { it.id } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = { if (followed.isNotEmpty()) TextButton(onClick = { onChange(emptyList()) }) { Text("清空") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                if (kind == FollowKind.PRODUCTS)
                    "关注常用的编码 Agent / harness。首页「版本更新」可切到「只看关注」，其他栏目里提到它们的条目也会汇总到「我的关注」。"
                else
                    "关注关心的负责人和官方账号。首页「人物动态」可切到「只看关注」，其他栏目里涉及他们的条目也会汇总到「我的关注」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            // —— 我的关注（含清单里已下线 / 未知的 id，可移除，绝不自动删除）——
            GroupTitle("我的关注 · ${followed.size}")
            Card {
                if (followed.isEmpty()) {
                    Text(
                        if (kind == FollowKind.PRODUCTS) "还没有关注的产品，从下面的清单里挑几个吧。" else "还没有关注的人物，从下面的清单里挑几位吧。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                followed.forEachIndexed { i, raw ->
                    if (i > 0) Divider()
                    val id = normId(raw) ?: raw
                    val e = byId[id]
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).testTag("followed-$id"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (e != null) EntryMark(kind, e, 32.dp) else UnknownMark(32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e?.title ?: id, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val n = counts[id] ?: 0
                            Text(
                                when {
                                    e == null && watchlist == null -> "清单未加载 · $id"
                                    e == null -> "已下线 / 未知 · $id"
                                    n > 0 -> "本期 $n 条 · ${e.subtitle}"
                                    else -> e.subtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    e == null -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    n > 0 -> AppTheme.extra.featured
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onChange(followed.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Rounded.Close, "取消关注 ${e?.title ?: id}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (watchlist == null) {
                GroupTitle("清单")
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (loading) "正在获取关注清单…" else "关注清单还没加载到", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (failed) "网络不给力，联网后会自动获取；已关注的不受影响。" else "首次使用需要联网获取一次，之后离线也能用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else FilledTonalButton(onClick = onRetry) { Text("重试") }
                    }
                }
            } else {
                var query by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(32) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("follow-search"),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除") } }) else null,
                    placeholder = { Text(if (kind == FollowKind.PRODUCTS) "搜索产品或厂商" else "搜索姓名、@handle 或公司", maxLines = 1) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = AppTheme.extra.card,
                        focusedContainerColor = AppTheme.extra.card,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                val q = query.trim().lowercase().removePrefix("@")
                groups.forEach { (title, entries) ->
                    val shown = if (q.isEmpty()) entries else entries.filter { q in it.search }
                    if (entries.isEmpty() || shown.isEmpty()) return@forEach
                    GroupTitle("$title · ${shown.size}")
                    Card {
                        shown.forEachIndexed { i, e ->
                            if (i > 0) Divider()
                            CatalogRow(kind, e, e.id in followedSet, counts[e.id] ?: 0) { toggle(e.id) }
                        }
                    }
                }
                if (q.isNotEmpty() && groups.all { (_, es) -> es.none { q in it.search } }) {
                    Text(
                        "没有找到「$query」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun EntryMark(kind: FollowKind, e: FollowEntry, size: androidx.compose.ui.unit.Dp) {
    if (kind == FollowKind.PRODUCTS) ProductMark(e.title, e.region, size)
    else PersonAvatar(e.title, e.region, e.official, size)
}

@Composable
private fun UnknownMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) { Text("?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun CatalogRow(kind: FollowKind, e: FollowEntry, on: Boolean, count: Int, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("follow-row-${e.id}")
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryMark(kind, e, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "本期 $count",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.extra.featured,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(AppTheme.extra.featuredContainer).padding(horizontal = 5.dp),
                    )
                }
            }
            Text(
                e.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (kind == FollowKind.PRODUCTS) regionColor(e.region).copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
            if (on) "已关注" else "关注",
            Modifier.size(24.dp),
            tint = if (on) AppTheme.extra.featured else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}
