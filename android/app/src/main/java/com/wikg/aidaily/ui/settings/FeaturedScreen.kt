package com.wikg.aidaily.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.AiDailyApp
import com.wikg.aidaily.data.local.DEFAULT_FEATURED
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.VendorMatcher
import com.wikg.aidaily.data.model.featuredColumn
import com.wikg.aidaily.data.model.normalizeFeatured
import com.wikg.aidaily.data.model.vendorCounts
import com.wikg.aidaily.ui.components.VendorMark
import com.wikg.aidaily.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 「关注厂商」管理页：增删、排序、从本期厂商 / 推荐里一键添加。 */
@Composable
fun FeaturedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = remember { (context.applicationContext as AiDailyApp).container }
    val settings by c.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var issue by remember { mutableStateOf<Issue?>(null) }
    var recentVendors by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val idx = c.repository.loadCachedIndex()
        val latest = idx?.latest
        issue = latest?.let { c.repository.cachedIssue(it) }
        // 本期之外，再从最近几期缓存里补充出现过的厂商
        val dates = c.repository.cachedDates().sortedDescending().take(7)
        val merged = linkedMapOf<String, Pair<String, Int>>()
        dates.forEach { d ->
            c.repository.cachedIssue(d)?.vendorCounts()?.forEach { (v, n) ->
                val k = VendorMatcher.norm(v)
                merged[k] = (merged[k]?.first ?: v) to ((merged[k]?.second ?: 0) + n)
            }
        }
        recentVendors = merged.values.sortedByDescending { it.second }
    }
    val s = settings ?: return
    FeaturedContent(
        featured = s.featuredVendors,
        issue = issue,
        recentVendors = recentVendors,
        onChange = { list -> scope.launch { c.prefs.setFeaturedVendors(list) } },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeaturedContent(
    featured: List<String>,
    issue: Issue?,
    recentVendors: List<Pair<String, Int>>,
    onChange: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val keys = remember(featured) { featured.map { VendorMatcher.norm(it) }.toSet() }
    fun isOn(v: String) = VendorMatcher.norm(v) in keys
    fun toggle(v: String) {
        onChange(if (isOn(v)) featured.filterNot { VendorMatcher.norm(it) == VendorMatcher.norm(v) } else featured + v)
    }
    val issueVendors = remember(issue) { issue?.vendorCounts().orEmpty() }
    val moreVendors = remember(issueVendors, recentVendors) {
        val inIssue = issueVendors.map { VendorMatcher.norm(it.first) }.toSet()
        recentVendors.filter { VendorMatcher.norm(it.first) !in inIssue }.take(20)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("关注厂商") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    if (featured != DEFAULT_FEATURED) TextButton(onClick = { onChange(DEFAULT_FEATURED) }) { Text("恢复默认") }
                },
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
                "每个关注的厂商在首页各占一页专栏，左右滑动切换。专栏会从整期里汇总该厂商的全部动态，" +
                    "并识别常见别名（如 小米 / Xiaomi / 澎湃OS、Anthropic / Claude Code、微软 / GitHub Copilot）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            GroupTitle("我的关注 · ${featured.size}")
            Card {
                if (featured.isEmpty()) {
                    Text(
                        "还没有关注的厂商。首页将只显示「今日」和各栏目页。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                featured.forEachIndexed { i, v ->
                    if (i > 0) Divider()
                    val n = issue?.featuredColumn(v)?.count
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).testTag("featured-row-$v"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VendorMark(v, VendorMatcher.isXiaomi(v), size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v + "专栏", style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    n == null -> "首页第 ${i + 2} 页"
                                    n > 0 -> "本期 $n 条 · 首页第 ${i + 2} 页"
                                    else -> "本期暂无 · 首页第 ${i + 2} 页"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (n != null && n > 0) AppTheme.extra.featured else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onChange(featured.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "上移")
                        }
                        IconButton(onClick = { onChange(featured.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < featured.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "下移")
                        }
                        IconButton(onClick = { onChange(featured.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Rounded.Close, "取消关注 $v", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // —— 手动添加 ——
            var text by rememberSaveable { mutableStateOf("") }
            val submit = {
                val v = text.trim()
                if (v.isNotEmpty()) { if (!isOn(v)) onChange(normalizeFeatured(featured + v)); text = "" }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    modifier = Modifier.weight(1f).testTag("featured-input"),
                    singleLine = true,
                    placeholder = { Text("输入厂商名，如 豆包", maxLines = 1) },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = AppTheme.extra.card,
                        focusedContainerColor = AppTheme.extra.card,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(onClick = submit, enabled = text.isNotBlank()) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加")
                }
            }

            if (issueVendors.isNotEmpty()) {
                GroupTitle("本期出现的厂商")
                ChipFlow(issueVendors.map { it.first to "${it.first} ${it.second}" }, ::isOn, ::toggle)
            }
            if (moreVendors.isNotEmpty()) {
                GroupTitle("最近几期出现过")
                ChipFlow(moreVendors.map { it.first to it.first }, ::isOn, ::toggle)
            }
            GroupTitle("推荐")
            ChipFlow(VendorMatcher.presets.map { it to it }, ::isOn, ::toggle)

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(chips: List<Pair<String, String>>, isOn: (String) -> Boolean, onToggle: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (value, label) ->
            val on = isOn(value)
            FilterChip(
                selected = on,
                onClick = { onToggle(value) },
                label = { Text(label) },
                leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AppTheme.extra.card,
                    selectedContainerColor = AppTheme.extra.featuredContainer,
                    selectedLabelColor = AppTheme.extra.featured,
                    selectedLeadingIconColor = AppTheme.extra.featured,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = on,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = AppTheme.extra.featured.copy(alpha = 0.4f),
                ),
            )
        }
    }
}

