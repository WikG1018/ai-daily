package com.wikg.aidaily.ui.home

import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Snackbar
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.AiDailyApp
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.ui.components.BrandMark
import com.wikg.aidaily.ui.components.CardRadius
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.util.BackgroundGuide
import com.wikg.aidaily.util.issueDateLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeatured: () -> Unit = onOpenSettings,
    onOpenNotifySettings: () -> Unit = onOpenSettings,
    /** 参数：releases → 关注产品；people → 关注人物。 */
    onOpenFollows: (String) -> Unit = { onOpenSettings() },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifier = remember { (context.applicationContext as AiDailyApp).container.notifier }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    // —— 通知权限 / 后台保活状态，每次回到前台刷新 ——
    var notifGranted by remember { mutableStateOf(notifier.canNotify()) }
    var batteryOk by remember { mutableStateOf(BackgroundGuide.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        notifGranted = notifier.canNotify()
        batteryOk = BackgroundGuide.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = notifier.canNotify()
    }
    var askedThisSession by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.initialLoading, state.settings.notifAsked) {
        if (Build.VERSION.SDK_INT >= 33 && !state.initialLoading && !state.settings.notifAsked &&
            !notifGranted && !askedThisSession
        ) {
            askedThisSession = true
            vm.setNotifAsked()
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val needsGuide = !notifGranted || !batteryOk || (BackgroundGuide.isXiaomi && !state.settings.autostartConfirmed)
    val showGuide = needsGuide && !state.settings.guideDismissed

    val effectiveDate = state.selectedDate ?: state.latest
    val updates = remember { runCatching { (context.applicationContext as AiDailyApp).container.updates }.getOrNull() }
    val updateState = updates?.state?.collectAsStateWithLifecycle()?.value
    HomeContent(
        updateBanner = updateState?.bannerVersion,
        onOpenUpdate = { updates?.openSheet() },
        onDismissUpdate = { updates?.dismissBanner() },
        state = state,
        snackbar = snackbar,
        showGuide = showGuide,
        notifGranted = notifGranted,
        onRefresh = { vm.refresh(user = true) },
        onSelectDate = { vm.select(it) },
        onBackToLatest = { vm.backToLatest() },
        onMarkAllRead = vm::markAllRead,
        onShowPicker = { showPicker = true },
        onOpenItem = onOpenItem,
        onOpenSettings = onOpenSettings,
        onGuideAction = {
            if (!notifGranted && Build.VERSION.SDK_INT >= 33 && !state.settings.notifAsked) {
                vm.setNotifAsked(); permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else onOpenNotifySettings()
        },
        onGuideDismiss = vm::dismissGuide,
        onManageFeatured = onOpenFeatured,
        onSetFilter = { sid, f ->
            if (sid == com.wikg.aidaily.data.model.SectionIds.RELEASES) vm.setReleasesFilter(f) else vm.setPeopleFilter(f)
        },
        onManageFollows = onOpenFollows,
    )

    if (showPicker && state.index != null) {
        IssuePickerSheet(
            index = state.index!!,
            selected = effectiveDate,
            cachedDates = remember(showPicker) { (context.applicationContext as AiDailyApp).container.repository.cachedDates() },
            onSelect = { date ->
                showPicker = false
                if (date == state.latest) vm.backToLatest() else vm.select(date)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 无状态的首页 UI（便于截图测试 / 预览）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    showGuide: Boolean,
    notifGranted: Boolean,
    onRefresh: () -> Unit,
    onSelectDate: (String) -> Unit,
    onBackToLatest: () -> Unit,
    onMarkAllRead: () -> Unit,
    onShowPicker: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onGuideAction: () -> Unit,
    onGuideDismiss: () -> Unit,
    onManageFeatured: () -> Unit = onOpenSettings,
    initialPage: Int = 0,
    onSetFilter: (String, com.wikg.aidaily.data.local.FollowFilter) -> Unit = { _, _ -> },
    onManageFollows: (String) -> Unit = {},
    /** v1.3：有新版本时的首页横幅（null 不显示）。 */
    updateBanner: String? = null,
    onOpenUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val effectiveDate = state.selectedDate ?: state.latest
    val issue: Issue? = state.issue?.takeIf { it.date == effectiveDate }
    val featured = state.settings.featuredVendors
    val follows = state.settings.follows
    val pages = remember(issue, featured, follows) { issue?.let { buildPages(it, featured, follows) }.orEmpty() }
    val followUi = FollowUi(
        watchlist = state.watchlist,
        follows = follows,
        releasesFilter = state.settings.releasesFilter,
        peopleFilter = state.settings.peopleFilter,
        onSetFilter = onSetFilter,
        onManage = onManageFollows,
    )
    val pagesRef by rememberUpdatedState(pages)
    val pager = rememberPagerState(initialPage = initialPage) { pagesRef.size }
    // 每页的滚动位置：按期号区分，换一期从头看
    val listStates = rememberSaveable(issue?.date, saver = ListStatesSaver) { HashMap() }
    fun listStateFor(key: String): LazyListState = listStates.getOrPut(key) { LazyListState() }

    // 切换日期 / 修改关注后，尽量停留在同一个栏目（按 key 找回）
    var currentKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 先按 key 找回位置，再开始跟踪当前页（同一个协程里顺序执行，避免返回首页时
    // 恢复的旧页码先把 currentKey 覆盖掉——例如新增关注后多出一页，页码整体后移）
    LaunchedEffect(pages) {
        val idx = pages.indexOfFirst { it.key == currentKey }
        if (idx >= 0 && idx != pager.currentPage) pager.scrollToPage(idx)
        snapshotFlow { pager.settledPage }.collect { p -> pages.getOrNull(p)?.let { currentKey = it.key } }
    }
    val currentList = pages.getOrNull(pager.currentPage)?.let { listStateFor(it.key) }
    // 「全部已读」的就地确认：图标短暂变成带勾的胶囊，1.6 秒后复原
    var justMarked by remember { mutableStateOf(false) }
    LaunchedEffect(justMarked) { if (justMarked) { delay(1600); justMarked = false } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SoftSnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(30.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("AI 日报", style = MaterialTheme.typography.titleLarge)
                                val sub = when {
                                    issue != null -> issueDateLabel(issue.date) + (if (state.isLatestSelected) " · 最新一期" else " · 往期")
                                    state.refreshing || state.initialLoading -> "正在获取…"
                                    else -> "每天早上 8:30 更新"
                                }
                                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        if (issue != null) {
                            MarkAllReadAction(justMarked) {
                                onMarkAllRead()
                                justMarked = true
                            }
                        }
                        IconButton(onClick = onShowPicker, enabled = state.index != null) {
                            Icon(Icons.Outlined.CalendarMonth, "往期")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Box {
                                Icon(Icons.Outlined.Settings, "设置")
                                if (updateBanner != null) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error),
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (pages.isNotEmpty()) {
                    HomeTabs(pages, pager) { i -> scope.launch { pager.animateScrollToPage(i) } }
                }
                AnimatedVisibility(state.offline && issue != null) { OfflineNotice() }
                // 保留最后一个版本号，保证收起动画期间内容不跳
                var lastBanner by remember { mutableStateOf(updateBanner) }
                if (updateBanner != null) lastBanner = updateBanner
                AnimatedVisibility(updateBanner != null) {
                    UpdateBanner(lastBanner.orEmpty(), onClick = onOpenUpdate, onDismiss = onDismissUpdate)
                }
            }
        },
        floatingActionButton = {
            val showTop by remember(currentList) { derivedStateOf { (currentList?.firstVisibleItemIndex ?: 0) > 6 } }
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(showTop, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick = { scope.launch { currentList?.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) { Icon(Icons.Rounded.VerticalAlignTop, "回到顶部") }
                }
                AnimatedVisibility(!state.isLatestSelected && state.latest != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    ExtendedFloatingActionButton(
                        onClick = onBackToLatest,
                        modifier = Modifier.padding(top = 12.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        text = { Text("回到最新一期") },
                        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when {
                issue != null && pages.isNotEmpty() -> {
                    HorizontalPager(
                        state = pager,
                        key = { i -> pagesRef.getOrNull(i)?.key ?: "p$i" },
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize().testTag("home_pager"),
                    ) { i ->
                        when (val p = pages.getOrNull(i)) {
                            is HomePage.Today -> TodayPage(
                                issue = issue,
                                pages = pages,
                                state = state,
                                listState = listStateFor(p.key),
                                showGuide = showGuide,
                                notifGranted = notifGranted,
                                onGuideAction = onGuideAction,
                                onGuideDismiss = onGuideDismiss,
                                onSelectDate = onSelectDate,
                                onShowPicker = onShowPicker,
                                onJump = { target -> scope.launch { pager.animateScrollToPage(target) } },
                                onManageFeatured = onManageFeatured,
                            )
                            null -> Box(Modifier.fillMaxSize())
                            else -> ColumnPage(
                                page = p,
                                readIds = state.readIds,
                                listState = listStateFor(p.key),
                                onOpenItem = onOpenItem,
                                onManageFeatured = onManageFeatured,
                                follow = followUi,
                            )
                        }
                    }
                }
                state.fatalError != null && !state.issueLoading -> MessageState(
                    title = "加载失败",
                    body = state.fatalError!!,
                    action = "重试",
                    onAction = onRefresh,
                    icon = { Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                !state.initialLoading && !state.refreshing && state.index != null && state.latest == null -> MessageState(
                    title = "还没有任何一期",
                    body = "第一期发布后会出现在这里。每天北京时间 8:30 左右更新。",
                    action = "刷新",
                    onAction = onRefresh,
                    icon = { BrandMark(48.dp) },
                )
                else -> LoadingSkeleton()
            }
        }
    }
}

/** 顶栏「全部已读」：点按后就地变成「✓ 已读」胶囊（勾号弹入），不打断阅读、深浅色都协调。 */
@Composable
private fun MarkAllReadAction(justMarked: Boolean, onClick: () -> Unit) {
    AnimatedContent(
        targetState = justMarked,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.9f)) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
        label = "markAllRead",
    ) { done ->
        if (done) {
            val check = remember { Animatable(0f) }
            LaunchedEffect(Unit) { check.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
            Row(
                Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppTheme.extra.updateContainer)
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("marked_all_read"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Check, null,
                    Modifier.size(18.dp).graphicsLayer { scaleX = check.value; scaleY = check.value },
                    tint = AppTheme.extra.update,
                )
                Spacer(Modifier.width(4.dp))
                Text("已读", style = MaterialTheme.typography.labelLarge, color = AppTheme.extra.update)
            }
        } else {
            IconButton(onClick = onClick) { Icon(Icons.Outlined.DoneAll, "全部已读") }
        }
    }
}

/** 主题化的轻量 Snackbar：低饱和容器色 + 圆角，深色模式下不刺眼。 */
@Composable
private fun SoftSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun GuideCard(notifGranted: Boolean, onAction: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(AppTheme.extra.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("每天早上自动提醒新一期", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (!notifGranted) "开启通知后，新一期发布时会推送今日要点。"
                    else if (BackgroundGuide.isXiaomi) "小米手机请开启「自启动」并把省电策略设为「无限制」，否则后台检查可能被拦截。"
                    else "把 AI 日报加入电池优化白名单，后台检查会更准时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("以后再说") }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onClick = onAction) { Text(if (!notifGranted) "开启通知" else "去设置") }
        }
    }
}

/** 发现新版本：低调的细横幅，点按打开更新面板，可关掉（本次启动不再显示）。 */
@Composable
internal fun UpdateBanner(version: String, onClick: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (AppTheme.extra.isDark) 0.16f else 0.07f))
            .clickable(onClick = onClick)
            .testTag("update_banner")
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AppTheme.extra.heroStart, AppTheme.extra.heroEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append("发现新版本 v$version") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("  ·  查看更新内容") }
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "关闭", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OfflineNotice() {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(
            "离线模式：显示的是本地缓存，下拉可重试",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun MessageState(title: String, body: String, action: String, onAction: () -> Unit, icon: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                icon()
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(action)
                }
            }
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a")
    val c = MaterialTheme.colorScheme.surfaceContainerHighest
    LazyColumn(Modifier.fillMaxSize().alpha(a), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp)).background(c)) }
        item { Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(c)) }
        items(5) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(AppTheme.extra.card).padding(16.dp)) {
                Box(Modifier.width(80.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(c))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(c))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(c))
            }
        }
    }
}

