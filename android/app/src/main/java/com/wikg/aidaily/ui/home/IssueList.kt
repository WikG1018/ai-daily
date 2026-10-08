package com.wikg.aidaily.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.NewsItem
import com.wikg.aidaily.ui.components.CardRadius
import com.wikg.aidaily.ui.components.DotSeparator
import com.wikg.aidaily.ui.components.MiMark
import com.wikg.aidaily.ui.components.Pill
import com.wikg.aidaily.ui.components.UpdateBadge
import com.wikg.aidaily.ui.components.VendorTag
import com.wikg.aidaily.ui.components.groupShape
import com.wikg.aidaily.ui.components.regionColor
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.util.isoToBeijingLabel
import com.wikg.aidaily.util.issueDateLabel
import com.wikg.aidaily.util.parseDate
import com.wikg.aidaily.util.richText
import com.wikg.aidaily.util.stripBold
import com.wikg.aidaily.util.weekdayCn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// 首页由一组扁平的 Row 组成：便于做吸顶栏目条 + 栏目定位 + 分组圆角卡片。
// ---------------------------------------------------------------------------
internal data class Anchor(val key: String, val title: String, val icon: String?, val count: Int, val xiaomi: Boolean)

internal sealed interface HomeRow {
    val key: String
    val type: String

    data object DateStrip : HomeRow { override val key = "dates"; override val type = "dates" }
    data object Hero : HomeRow { override val key = "hero"; override val type = "hero" }
    data object Offline : HomeRow { override val key = "offline"; override val type = "offline" }
    data object Guide : HomeRow { override val key = "guide"; override val type = "guide" }
    data object Tabs : HomeRow { override val key = "tabs"; override val type = "tabs" }
    data class Header(val anchor: Anchor) : HomeRow { override val key = "h-" + anchor.key; override val type = "header" }
    data class Item(val item: NewsItem, val first: Boolean, val last: Boolean, val xiaomi: Boolean) : HomeRow {
        override val key = "i-" + item.id; override val type = "item"
    }
    data class Empty(val text: String, val xiaomi: Boolean, val anchorKey: String) : HomeRow {
        override val key = "e-$anchorKey"; override val type = "empty"
    }
    data object Footer : HomeRow { override val key = "footer"; override val type = "footer" }
}

private fun countNews(items: List<NewsItem>): Int = items.sumOf { (if (it.group) 0 else 1) + countNews(it.children) }

internal fun buildRows(issue: Issue, offline: Boolean, guide: Boolean): Pair<List<HomeRow>, List<Pair<Anchor, Int>>> {
    val rows = mutableListOf<HomeRow>(HomeRow.DateStrip)
    if (offline) rows += HomeRow.Offline
    rows += HomeRow.Hero
    if (guide) rows += HomeRow.Guide
    rows += HomeRow.Tabs
    val anchors = mutableListOf<Pair<Anchor, Int>>()

    fun addSection(anchor: Anchor, items: List<NewsItem>, empty: String) {
        anchors += anchor to rows.size
        rows += HomeRow.Header(anchor)
        if (items.isEmpty()) rows += HomeRow.Empty(empty, anchor.xiaomi, anchor.key)
        else items.forEachIndexed { i, it -> rows += HomeRow.Item(it, i == 0, i == items.lastIndex, anchor.xiaomi) }
    }

    val mi = issue.xiaomi?.items.orEmpty()
    addSection(
        Anchor("xiaomi", "小米专栏", null, countNews(mi), true), mi,
        issue.xiaomi?.emptyText?.takeIf { it.isNotBlank() } ?: "小米今天无新动态",
    )
    issue.sections.forEach { s ->
        addSection(
            Anchor(s.id.ifBlank { s.title }, s.title, s.icon, countNews(s.items), false), s.items,
            s.emptyText?.takeIf { it.isNotBlank() } ?: "本期无相关动态",
        )
    }
    rows += HomeRow.Footer
    return rows to anchors
}

/** 栏目名在吸顶条里用短名，避免“编码 / Agent 框架”过长。 */
private fun shortTitle(a: Anchor) = when (a.key) {
    "xiaomi" -> "小米"
    "harness" -> "编码 Agent"
    "other" -> "其他"
    else -> a.title.replace(" / ", "·")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IssueList(
    issue: Issue,
    state: HomeUiState,
    listState: LazyListState,
    showGuide: Boolean,
    notifGranted: Boolean,
    onGuideAction: () -> Unit,
    onGuideDismiss: () -> Unit,
    onSelectDate: (String) -> Unit,
    onShowPicker: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val (rows, anchors) = remember(issue, state.offline, showGuide) { buildRows(issue, state.offline, showGuide) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tabsHeightPx = with(density) { 52.dp.roundToPx() }
    val activeAnchor by remember(anchors) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex + 1
            anchors.lastOrNull { it.second <= first }?.first?.key ?: anchors.firstOrNull()?.first?.key
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("home_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        rows.forEach { row ->
            when (row) {
                HomeRow.Tabs -> stickyHeader(key = row.key, contentType = row.type) {
                    SectionTabs(anchors.map { it.first }, activeAnchor) { key ->
                        val idx = anchors.firstOrNull { it.first.key == key }?.second ?: return@SectionTabs
                        scope.launch { listState.animateScrollToItem(idx, -tabsHeightPx) }
                    }
                }
                else -> item(key = row.key, contentType = row.type) {
                    when (row) {
                        HomeRow.DateStrip -> DateStrip(state, issue.date, onSelectDate, onShowPicker)
                        HomeRow.Offline -> OfflineNotice()
                        HomeRow.Hero -> HeroCard(issue, state)
                        HomeRow.Guide -> GuideCard(notifGranted, onGuideAction, onGuideDismiss)
                        is HomeRow.Header -> SectionHeader(row.anchor)
                        is HomeRow.Item -> NewsRow(row, state.readIds, onOpenItem)
                        is HomeRow.Empty -> EmptyCard(row.text, row.xiaomi)
                        HomeRow.Footer -> Footer(issue)
                        HomeRow.Tabs -> Unit
                    }
                }
            }
        }
        item(key = "nav-inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

// —— 日期条：横向切换最近几期，末尾“全部” ——
@Composable
private fun DateStrip(state: HomeUiState, selected: String, onSelect: (String) -> Unit, onShowPicker: () -> Unit) {
    val issues = state.index?.issues.orEmpty().take(30)
    val rowState = rememberLazyListState()
    LaunchedEffect(selected, issues.size) {
        val i = issues.indexOfFirst { it.date == selected }
        if (i > 1) rowState.animateScrollToItem(i - 1)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(issues, key = { _, s -> s.date }) { i, s ->
            val sel = s.date == selected
            val d = parseDate(s.date)
            val bg = if (sel) MaterialTheme.colorScheme.primary else AppTheme.extra.card
            val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable { onSelect(s.date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (i == 0) "最新" else d?.let { weekdayCn(it.dayOfWeek) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sel) fg.copy(alpha = 0.8f) else if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    d?.let { "%d.%02d".format(it.monthValue, it.dayOfMonth) } ?: s.date,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = fg,
                )
            }
        }
        item(key = "all") {
            Column(
                Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable(onClick = onShowPicker)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.GridView, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text("全部", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// —— 今日要点：渐变主卡 ——
@Composable
private fun HeroCard(issue: Issue, state: HomeUiState) {
    val e = AppTheme.extra
    val all = remember(issue) { issue.allItems().filter { !it.group } }
    val read = all.count { it.id in state.readIds }
    val issueNo = state.index?.issues?.let { list -> list.size - list.indexOfFirst { it.date == issue.date } }?.takeIf { it in 1..9999 }
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(e.heroStart, e.heroEnd))),
    ) {
        // 装饰：右上角晨光
        Canvas(Modifier.matchParentSize()) {
            val r = size.width * 0.42f
            drawCircle(Color.White.copy(alpha = 0.07f), radius = r, center = Offset(size.width * 0.95f, -r * 0.15f))
            drawCircle(Color(0xFFFFB547).copy(alpha = 0.22f), radius = r * 0.36f, center = Offset(size.width * 0.86f, r * 0.18f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = r * 0.7f, center = Offset(-r * 0.2f, size.height * 1.05f))
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("今日要点", Color.White.copy(alpha = 0.18f), Color.White)
                Spacer(Modifier.width(8.dp))
                if (issueNo != null) Text("第 $issueNo 期", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                issueDateLabel(issue.date),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            issue.window?.let { w ->
                if (w.start != null && w.end != null) {
                    Text(
                        "覆盖 ${w.start} — ${w.end}（${w.tz ?: "北京时间"}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                richText(issue.highlights, SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD99A))),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 26.sp),
                color = Color.White.copy(alpha = 0.95f),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stat("${all.size}", "条资讯")
                Spacer(Modifier.width(18.dp))
                Stat("$read", "已读")
                Spacer(Modifier.weight(1f))
                isoToBeijingLabel(issue.publishedAt)?.let {
                    Text("发布于 $it", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(bottom = 3.dp))
    }
}

// —— 吸顶栏目条 ——
@Composable
private fun SectionTabs(anchors: List<Anchor>, active: String?, onClick: (String) -> Unit) {
    val rowState = rememberLazyListState()
    LaunchedEffect(active) {
        val i = anchors.indexOfFirst { it.key == active }
        if (i >= 0) rowState.animateScrollToItem(maxOf(0, i - 1))
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        LazyRow(
            state = rowState,
            modifier = Modifier.height(52.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(anchors, key = { _, a -> a.key }) { _, a ->
                val sel = a.key == active
                val accent = if (a.xiaomi) AppTheme.extra.xiaomi else MaterialTheme.colorScheme.primary
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) accent else AppTheme.extra.card)
                        .clickable { onClick(a.key) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        shortTitle(a),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    if (a.count > 0) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${a.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sel) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(a: Anchor) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.xiaomi) MiMark()
        else Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.extra.card),
            contentAlignment = Alignment.Center,
        ) { Text(a.icon ?: "•", fontSize = 14.sp) }
        Spacer(Modifier.width(10.dp))
        Text(a.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
        Spacer(Modifier.width(8.dp))
        if (a.count > 0) Text("${a.count} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (a.xiaomi) Text("重点关注", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.xiaomi)
    }
}

// —— 新闻条目（分组圆角卡片中的一行）——
@Composable
private fun NewsRow(row: HomeRow.Item, readIds: Set<String>, onOpen: (String) -> Unit) {
    val item = row.item
    val e = AppTheme.extra
    val bg = if (row.xiaomi && !e.isDark) e.xiaomiContainer.copy(alpha = 0.55f) else e.card
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(groupShape(row.first, row.last))
            .background(bg),
    ) {
        if (!row.first) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
        if (item.group) GroupBlock(item, readIds, onOpen)
        else Column(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(item.id) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            ItemBody(item, read = item.id in readIds)
            if (item.children.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ChildList(item.children, readIds, onOpen)
            }
        }
    }
}

@Composable
private fun MetaLine(item: NewsItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VendorTag(item.vendor, item.regionKind)
        if (item.update) { Spacer(Modifier.width(6.dp)); UpdateBadge() }
        Spacer(Modifier.weight(1f))
        item.time?.let {
            Text(timeShort(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun timeShort(t: String): String =
    Regex("""^(\d{1,2})-(\d{1,2})\s+(\d{1,2}:\d{2})$""").find(t.trim())?.destructured?.let { (m, d, hm) -> "${m.toInt()}/${d.toInt()} $hm" } ?: t

@Composable
private fun ItemBody(item: NewsItem, read: Boolean) {
    MetaLine(item)
    Spacer(Modifier.height(8.dp))
    Text(
        richText(item.title),
        style = MaterialTheme.typography.titleMedium,
        color = if (read) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    item.summary?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            stripBold(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (read) 0.7f else 1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (item.update && !item.updateNote.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "更新：" + stripBold(item.updateNote),
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.extra.update,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChildList(children: List<NewsItem>, readIds: Set<String>, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        children.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 26.dp, end = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.padding(top = 6.dp).size(width = 3.dp, height = 12.dp).clip(RoundedCornerShape(2.dp))
                        .background(regionColor(c.regionKind)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.vendor, style = MaterialTheme.typography.labelMedium, color = regionColor(c.regionKind))
                        if (c.update) { Spacer(Modifier.width(6.dp)); UpdateBadge() }
                        c.time?.let { DotSeparator(); Text(timeShort(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        richText(c.title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (c.id in readIds) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (c.children.isNotEmpty()) {
                    Text("+${c.children.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

/** group=true：只是分组容器，本身不可点，内容在 children。 */
@Composable
private fun GroupBlock(item: NewsItem, readIds: Set<String>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("专题", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                richText(item.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(stripBold(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.height(10.dp))
        ChildList(item.children, readIds, onOpen)
    }
}

@Composable
private fun EmptyCard(text: String, xiaomi: Boolean) {
    val e = AppTheme.extra
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(if (xiaomi) e.xiaomiContainer else e.card)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (xiaomi) e.xiaomi.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stripBold(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            if (xiaomi) Text(
                "小米 / 澎湃OS / 小爱相关动态会第一时间出现在这里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Footer(issue: Issue) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 22.dp)) {
        if (issue.notes.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(AppTheme.extra.card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("补充说明", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                issue.notes.forEach { n ->
                    Row {
                        Box(Modifier.padding(top = 9.dp).size(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(10.dp))
                        Text(richText(n), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        issue.sourceNote?.takeIf { it.isNotBlank() }?.let {
            Text(stripBold(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
        }
        val meta = listOfNotNull(
            issue.generatedAt?.let { "整理于 $it" },
            isoToBeijingLabel(issue.publishedAt)?.let { "发布于 $it（北京时间）" },
        ).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "— 已经到底啦 —",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
