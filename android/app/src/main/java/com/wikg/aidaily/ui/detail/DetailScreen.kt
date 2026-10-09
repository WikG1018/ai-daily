package com.wikg.aidaily.ui.detail

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.AiDailyApp
import com.wikg.aidaily.data.model.ItemContext
import com.wikg.aidaily.data.model.NewsItem
import com.wikg.aidaily.data.model.Follows
import com.wikg.aidaily.data.model.Watchlist
import com.wikg.aidaily.data.model.normId
import com.wikg.aidaily.ui.components.VersionBadge
import kotlinx.coroutines.launch
import com.wikg.aidaily.ui.components.CardRadius
import com.wikg.aidaily.ui.components.DotSeparator
import com.wikg.aidaily.ui.components.RegionLabel
import com.wikg.aidaily.ui.components.UpdateBadge
import com.wikg.aidaily.ui.components.VendorTag
import com.wikg.aidaily.ui.components.regionColor
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.util.hostOf
import com.wikg.aidaily.util.issueDateLabel
import com.wikg.aidaily.util.itemTimeLabel
import com.wikg.aidaily.util.openUrl
import com.wikg.aidaily.util.richText
import com.wikg.aidaily.util.shareText
import com.wikg.aidaily.util.stripBold

private sealed interface DetailLoad {
    data object Loading : DetailLoad
    data object NotFound : DetailLoad
    data class Ready(val ctx: ItemContext) : DetailLoad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemId: String, onBack: () -> Unit, onOpenItem: (String) -> Unit, onOpenIssue: (String) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as AiDailyApp).container }
    var load by remember(itemId) { mutableStateOf<DetailLoad>(DetailLoad.Loading) }
    LaunchedEffect(itemId) {
        val found = container.repository.locateItem(itemId)
        load = if (found != null) DetailLoad.Ready(found) else DetailLoad.NotFound
    }
    val readIds by container.prefs.readIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    val watchlist by container.repository.watchlist.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { container.repository.loadCachedWatchlist() } }
    LaunchedEffect(load) {
        (load as? DetailLoad.Ready)?.let { if (!it.ctx.item.group) container.prefs.markRead(it.ctx.item.id) }
    }
    DetailContent(
        ctx = (load as? DetailLoad.Ready)?.ctx,
        loading = load is DetailLoad.Loading,
        readIds = readIds,
        onBack = onBack,
        onOpenItem = onOpenItem,
        onOpenIssue = onOpenIssue,
        watchlist = watchlist,
        follows = settings?.follows ?: Follows(),
        onToggleProduct = { id ->
            val cur = settings?.followProducts ?: return@DetailContent
            scope.launch { container.prefs.setFollowProducts(if (cur.any { normId(it) == id }) cur.filterNot { normId(it) == id } else cur + id) }
        },
        onTogglePerson = { id ->
            val cur = settings?.followPeople ?: return@DetailContent
            scope.launch { container.prefs.setFollowPeople(if (cur.any { normId(it) == id }) cur.filterNot { normId(it) == id } else cur + id) }
        },
    )
}

/** 无状态详情页（便于截图测试）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContent(
    ctx: ItemContext?,
    loading: Boolean,
    readIds: Set<String>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenIssue: (String) -> Unit,
    watchlist: Watchlist? = null,
    follows: Follows = Follows(),
    onToggleProduct: (String) -> Unit = {},
    onTogglePerson: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val ready = ctx?.let { DetailLoad.Ready(it) }
    val load: DetailLoad = ready ?: if (loading) DetailLoad.Loading else DetailLoad.NotFound
    val primaryUrl = ready?.ctx?.item?.links?.firstOrNull { it.isSafe }?.url
    val e = AppTheme.extra
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ready?.ctx?.sectionTitle ?: "详情",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { shareText(context, stripBold(ready.ctx.item.title), primaryUrl) }) {
                            Icon(Icons.Outlined.Share, "分享")
                        }
                    }
                    if (primaryUrl != null) {
                        IconButton(onClick = { openUrl(context, primaryUrl, toolbarColor, e.isDark) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, "打开原帖")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val l = load) {
                DetailLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                DetailLoad.NotFound -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("找不到这条新闻", style = MaterialTheme.typography.titleMedium)
                    Text("可能是离线状态且这一期尚未缓存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is DetailLoad.Ready -> DetailBody(
                    l.ctx, readIds, onOpenItem, onOpenIssue,
                    openLink = { url -> openUrl(context, url, toolbarColor, e.isDark) },
                    refs = RefsUi(watchlist, follows, onToggleProduct, onTogglePerson),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    ctx: ItemContext,
    readIds: Set<String>,
    onOpenItem: (String) -> Unit,
    onOpenIssue: (String) -> Unit,
    openLink: (String) -> Unit,
    refs: RefsUi = RefsUi(),
) {
    val item = ctx.item
    val e = AppTheme.extra
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        // 期号面包屑
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenIssue(ctx.issue.date) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${ctx.issue.title} · ${issueDateLabel(ctx.issue.date)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            VendorTag(item.vendor, item.regionKind, large = true)
            Spacer(Modifier.width(8.dp))
            RegionLabel(item.regionKind)
            item.versionText?.let { Spacer(Modifier.width(8.dp)); VersionBadge(it, large = true) }
            if (item.update) { Spacer(Modifier.width(8.dp)); UpdateBadge() }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            richText(item.title, SpanStyle(fontWeight = FontWeight.Bold)),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        itemTimeLabel(ctx.issue.date, item.time)?.let { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("$t 北京时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        RefCards(item, refs, openLink)

        ctx.parent?.let { p ->
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = !p.group) { onOpenItem(p.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SubdirectoryArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (p.group) "所属专题" else "相关进展 · 源自", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stripBold(p.title), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!p.group) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (item.update && !item.updateNote.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(e.updateContainer).padding(14.dp),
            ) {
                Box(Modifier.padding(top = 3.dp).size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(e.update))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("本次更新", style = MaterialTheme.typography.labelMedium, color = e.update)
                    Spacer(Modifier.height(2.dp))
                    Text(richText(item.updateNote), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item.summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(18.dp))
            Text(
                richText(it),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }

        val links = item.links.filter { it.isSafe }
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            val first = links.first()
            Button(
                onClick = { openLink(first.url) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("查看${first.displayLabel}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Text(hostOf(first.url), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            }
            if (links.size > 1) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    links.drop(1).forEach { l ->
                        OutlinedButton(onClick = { openLink(l.url) }, shape = RoundedCornerShape(12.dp)) {
                            Text(l.displayLabel, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(4.dp))
                            Text(hostOf(l.url), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (item.children.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(
                if (item.group) "专题内容 · ${item.children.size}" else "相关进展 · ${item.children.size}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item.children.forEach { c -> ChildCard(ctx.issue.date, c, c.id in readIds) { onOpenItem(c.id) } }
            }
        }

        ctx.next?.let { n ->
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(e.card)
                    .clickable { onOpenItem(n.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("下一条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(stripBold(n.title), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(28.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun ChildCard(issueDate: String, c: NewsItem, read: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(AppTheme.extra.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(Modifier.padding(top = 4.dp).size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(regionColor(c.regionKind)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.vendor, style = MaterialTheme.typography.labelMedium, color = regionColor(c.regionKind))
                if (c.update) { Spacer(Modifier.width(6.dp)); UpdateBadge() }
                itemTimeLabel(issueDate, c.time)?.let { DotSeparator(); Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                richText(c.title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (read) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface,
            )
            c.summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(stripBold(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
