package com.wikg.aidaily.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikg.aidaily.data.model.FeaturedColumn
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.NewsItem
import com.wikg.aidaily.data.model.Section
import com.wikg.aidaily.data.model.featuredColumn
import com.wikg.aidaily.data.model.normalizeFeatured
import com.wikg.aidaily.ui.components.CardRadius
import com.wikg.aidaily.ui.components.DotSeparator
import com.wikg.aidaily.ui.components.Pill
import com.wikg.aidaily.ui.components.UpdateBadge
import com.wikg.aidaily.ui.components.VendorMark
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
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 首页 = 吸顶标签条 + HorizontalPager：今日 → 每个关注厂商一页 → 每个栏目一页。
// 每页是独立的 LazyColumn（纵向滚动嵌套在横向分页里，互不干扰；下拉刷新从页内透传到外层）。
// ---------------------------------------------------------------------------
internal sealed interface HomePage {
    val key: String
    val tabTitle: String
    val count: Int
    val featured: Boolean get() = false

    data class Today(override val count: Int) : HomePage {
        override val key = "today"
        override val tabTitle = "今日"
    }

    data class Featured(val column: FeaturedColumn) : HomePage {
        override val key = column.key
        override val tabTitle = column.vendor
        override val count = column.count
        override val featured = true
    }

    data class Sec(val section: Section) : HomePage {
        override val key = "s-" + section.id.ifBlank { section.title }
        override val tabTitle = shortTitle(section)
        override val count = countNews(section.items)
    }
}

internal fun countNews(items: List<NewsItem>): Int = items.sumOf { (if (it.group) 0 else 1) + countNews(it.children) }

/** 标签条里用短名，避免「编码 / Agent 框架」过长。 */
private fun shortTitle(s: Section) = when {
    s.id == "harness" -> "编码 Agent"
    s.title.contains(" / ") -> s.title.substringBefore(" / ")
    else -> s.title
}.ifBlank { s.id.ifBlank { "栏目" } }

internal fun buildPages(issue: Issue, featured: List<String>): List<HomePage> = buildList {
    add(HomePage.Today(issue.newsCount))
    normalizeFeatured(featured).forEach { add(HomePage.Featured(issue.featuredColumn(it))) }
    issue.sections.forEach { add(HomePage.Sec(it)) }
}.distinctBy { it.key }

/** 每页一个 LazyListState，跨导航（进详情再返回）保留滚动位置。 */
internal val ListStatesSaver = Saver<HashMap<String, LazyListState>, ArrayList<Any>>(
    save = { m ->
        ArrayList<Any>().apply {
            m.forEach { (k, s) -> add(k); add(s.firstVisibleItemIndex); add(s.firstVisibleItemScrollOffset) }
        }
    },
    restore = { l ->
        HashMap<String, LazyListState>().apply {
            for (i in 0 until l.size / 3) put(l[i * 3] as String, LazyListState(l[i * 3 + 1] as Int, l[i * 3 + 2] as Int))
        }
    },
)

// —— 吸顶标签条：指示条跟随手指滑动插值，选中项自动居中 ——
@Composable
internal fun HomeTabs(pages: List<HomePage>, pager: PagerState, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val bounds = remember(pages.map { it.key }) { mutableStateListOf<Pair<Float, Float>>().apply { repeat(pages.size) { add(0f to 0f) } } }
    var viewport by remember { mutableIntStateOf(0) }
    val primary = MaterialTheme.colorScheme.primary
    val featuredColor = AppTheme.extra.featured
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val accents = pages.map { if (it.featured) featuredColor else primary }

    LaunchedEffect(pager.targetPage, pages.size, viewport) {
        val b = bounds.getOrNull(pager.targetPage) ?: return@LaunchedEffect
        if (b.second == 0f) return@LaunchedEffect
        val target = (b.first + b.second / 2 - viewport / 2f).roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target)
    }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(44.dp).onSizeChanged { viewport = it.width }) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 8.dp)
                    .drawBehind {
                        if (pages.isEmpty()) return@drawBehind
                        val page = pager.currentPage.coerceIn(0, pages.lastIndex)
                        val f = pager.currentPageOffsetFraction
                        val other = (if (f > 0) page + 1 else page - 1).coerceIn(0, pages.lastIndex)
                        val a = bounds.getOrNull(page) ?: return@drawBehind
                        val b = bounds.getOrNull(other) ?: a
                        if (a.second == 0f) return@drawBehind
                        val t = abs(f).coerceIn(0f, 1f)
                        val ca = a.first + a.second / 2
                        val cb = b.first + b.second / 2
                        val center = ca + (cb - ca) * t
                        // 移动过程中像橡皮筋一样略微拉长
                        val base = 18.dp.toPx()
                        val w = base + abs(cb - ca) * (1f - abs(1f - 2f * t)) * 0.35f
                        val h = 3.dp.toPx()
                        val color = lerp(accents[page], accents[other], t)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(center - w / 2, size.height - h - 4.dp.toPx()),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2, h / 2),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { i, p ->
                    // 选中程度 0..1：随滑动连续变化
                    val pos = pager.currentPage + pager.currentPageOffsetFraction
                    val s = (1f - abs(pos - i)).coerceIn(0f, 1f)
                    val color = lerp(inactive, if (p.featured) featuredColor else MaterialTheme.colorScheme.onSurface, s)
                    Row(
                        Modifier
                            .onPlaced { c -> if (i < bounds.size) bounds[i] = c.positionInParent().x to c.size.width.toFloat() }
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(i) }
                            .semantics { role = Role.Tab; selected = pager.currentPage == i }
                            .testTag("tab-${p.key}")
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.tabTitle,
                            modifier = Modifier.graphicsLayer {
                                val sc = 1f + 0.12f * s
                                scaleX = sc; scaleY = sc
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = if (s > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = color,
                            maxLines = 1,
                        )
                        if (p !is HomePage.Today && p.count > 0) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${p.count}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = color.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 7.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// —— 今日页：日期条 / 引导 / 要点主卡 / 栏目速览 / 补充说明 ——
@Composable
internal fun TodayPage(
    issue: Issue,
    pages: List<HomePage>,
    state: HomeUiState,
    listState: LazyListState,
    showGuide: Boolean,
    notifGranted: Boolean,
    onGuideAction: () -> Unit,
    onGuideDismiss: () -> Unit,
    onSelectDate: (String) -> Unit,
    onShowPicker: () -> Unit,
    onJump: (Int) -> Unit,
    onManageFeatured: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("page_today"),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "dates", contentType = "dates") { DateStrip(state, issue.date, onSelectDate, onShowPicker) }
        if (showGuide) item(key = "guide", contentType = "guide") { GuideCard(notifGranted, onGuideAction, onGuideDismiss) }
        item(key = "hero", contentType = "hero") { HeroCard(issue, state) }
        item(key = "overview-title", contentType = "title") { BlockTitle("栏目速览", "左右滑动切换") }
        val cols = pages.withIndex().filter { it.value !is HomePage.Today }
        itemsIndexed(cols, key = { _, p -> "ov-" + p.value.key }, contentType = { _, _ -> "overview" }) { i, (pageIndex, p) ->
            OverviewRow(p, first = i == 0, last = i == cols.lastIndex, readIds = state.readIds) { onJump(pageIndex) }
        }
        if (pages.none { it is HomePage.Featured }) {
            item(key = "featured-hint", contentType = "hint") { FeaturedHint(onManageFeatured) }
        }
        item(key = "footer", contentType = "footer") { Footer(issue) }
        item(key = "nav-inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun BlockTitle(title: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
    }
}

@Composable
private fun PageIcon(p: HomePage, size: androidx.compose.ui.unit.Dp = 26.dp) {
    when (p) {
        is HomePage.Featured -> VendorMark(p.column.vendor, p.column.isXiaomi, size = size)
        is HomePage.Sec -> Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Text(p.section.icon ?: "•", fontSize = (size.value * 0.54f).sp) }
        is HomePage.Today -> Unit
    }
}

private fun pageTitle(p: HomePage) = when (p) {
    is HomePage.Featured -> p.column.vendor + "专栏"
    is HomePage.Sec -> p.section.title.ifBlank { p.tabTitle }
    is HomePage.Today -> "今日"
}

private fun pageItems(p: HomePage): List<NewsItem> = when (p) {
    is HomePage.Featured -> p.column.items
    is HomePage.Sec -> p.section.items
    is HomePage.Today -> emptyList()
}

private fun pageEmptyText(p: HomePage): String = when (p) {
    is HomePage.Featured -> p.column.emptyText
    is HomePage.Sec -> p.section.emptyText?.takeIf { it.isNotBlank() } ?: "本期无相关动态"
    is HomePage.Today -> ""
}

/** 速览行：图标 + 栏目名 + 条数 + 前两条标题预览，点按跳到对应页。 */
@Composable
private fun OverviewRow(p: HomePage, first: Boolean, last: Boolean, readIds: Set<String>, onClick: () -> Unit) {
    val e = AppTheme.extra
    val items = pageItems(p)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(groupShape(first, last))
            .background(e.card),
    ) {
        if (!first) HorizontalDivider(Modifier.padding(start = 54.dp, end = 16.dp), thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PageIcon(p, 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pageTitle(p),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (p.featured) e.featured else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (p.count > 0) "${p.count} 条" else "暂无",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                if (items.isEmpty()) {
                    Text(stripBold(pageEmptyText(p)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    items.take(2).forEach { item ->
                        Text(
                            stripBold(item.title),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            color = if (item.id in readIds) e.readTitle else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Icon(
                Icons.Rounded.ChevronRight, null,
                Modifier.padding(top = 2.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun FeaturedHint(onManage: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(AppTheme.extra.featuredContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("关注你在意的厂商", style = MaterialTheme.typography.titleSmall)
            Text(
                "添加后会多出一页专栏，自动汇总该厂商在本期的全部动态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onManage) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加")
        }
    }
}

// —— 专栏 / 栏目页 ——
@Composable
internal fun ColumnPage(
    page: HomePage,
    readIds: Set<String>,
    listState: LazyListState,
    onOpenItem: (String) -> Unit,
    onManageFeatured: () -> Unit,
) {
    val items = pageItems(page)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("page_${page.key}"),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header", contentType = "header") { PageHeader(page, onManageFeatured) }
        if (items.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                val hint = (page as? HomePage.Featured)?.column?.let { c ->
                    if (c.isXiaomi) "小米 / 澎湃OS / 小爱相关动态会第一时间出现在这里"
                    else "「${c.vendor}」相关动态会第一时间出现在这里"
                }
                EmptyCard(pageEmptyText(page), page.featured, hint)
            }
        } else {
            itemsIndexed(items, key = { _, it -> "i-" + it.id }, contentType = { _, _ -> "item" }) { i, item ->
                NewsRow(item, first = i == 0, last = i == items.lastIndex, featured = page.featured, readIds = readIds, onOpen = onOpenItem)
            }
        }
        item(key = "end", contentType = "end") { PageEnd() }
        item(key = "nav-inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun PageHeader(p: HomePage, onManageFeatured: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageIcon(p, 28.dp)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pageTitle(p),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            if (p.count > 0) Text("${p.count} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (p is HomePage.Featured) {
            TextButton(onClick = onManageFeatured) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp), tint = AppTheme.extra.featured)
                Spacer(Modifier.width(4.dp))
                Text("管理关注", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.featured)
            }
        }
    }
}

@Composable
private fun PageEnd() {
    Text(
        "— 左右滑动查看其他栏目 —",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

// —— 日期条：横向切换最近几期，末尾“全部” ——
@Composable
internal fun DateStrip(state: HomeUiState, selected: String, onSelect: (String) -> Unit, onShowPicker: () -> Unit) {
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
internal fun HeroCard(issue: Issue, state: HomeUiState) {
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
        // 数字变化时上滑翻动（全部已读时「已读」计数会跳到满）
        androidx.compose.animation.AnimatedContent(
            targetState = value,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                    (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
            },
            label = "stat",
        ) { v -> Text(v, style = MaterialTheme.typography.titleLarge, color = Color.White) }
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(bottom = 3.dp))
    }
}

// —— 新闻条目（分组圆角卡片中的一行）——
@Composable
internal fun NewsRow(
    item: NewsItem,
    first: Boolean,
    last: Boolean,
    featured: Boolean,
    readIds: Set<String>,
    onOpen: (String) -> Unit,
) {
    val e = AppTheme.extra
    val bg = if (featured && !e.isDark) e.featuredContainer.copy(alpha = 0.55f) else e.card
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(groupShape(first, last))
            .background(bg),
    ) {
        if (!first) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
    val titleColor by animateColorAsState(
        if (read) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface, tween(450), label = "read",
    )
    Text(
        richText(item.title),
        style = MaterialTheme.typography.titleMedium,
        color = titleColor,
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
                        color = animateColorAsState(
                            if (c.id in readIds) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface, tween(450), label = "readChild",
                        ).value,
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
internal fun EmptyCard(text: String, featured: Boolean, hint: String? = null) {
    val e = AppTheme.extra
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(if (featured) e.featuredContainer else e.card)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (featured) e.featured.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stripBold(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            if (hint != null) Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Footer(issue: Issue) {
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
