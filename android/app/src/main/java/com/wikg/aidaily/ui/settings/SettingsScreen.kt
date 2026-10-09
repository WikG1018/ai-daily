package com.wikg.aidaily.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import com.wikg.aidaily.data.model.Watchlist
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.AiDailyApp
import com.wikg.aidaily.BuildConfig
import com.wikg.aidaily.data.local.Settings
import com.wikg.aidaily.data.local.ThemeMode
import com.wikg.aidaily.ui.components.BrandMark
import com.wikg.aidaily.ui.components.CardRadius
import com.wikg.aidaily.ui.theme.AppFonts
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.util.BackgroundGuide
import com.wikg.aidaily.util.epochToBeijingLabel
import com.wikg.aidaily.util.openUrl
import com.wikg.aidaily.work.WorkScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    page: SettingsPage = SettingsPage.HUB,
    onBack: () -> Unit,
    onOpenFeatured: () -> Unit = {},
    onNavigate: (SettingsPage) -> Unit = {},
    onOpenFollow: (FollowKind) -> Unit = {},
) {
    val context = LocalContext.current
    val c = remember { (context.applicationContext as AiDailyApp).container }
    val settings by c.prefs.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val watchlist by c.repository.watchlist.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { c.repository.loadCachedWatchlist() } }
    val scope = rememberCoroutineScope()

    var notifGranted by remember { mutableStateOf(c.notifier.canNotify()) }
    var batteryOk by remember { mutableStateOf(BackgroundGuide.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        notifGranted = c.notifier.canNotify()
        batteryOk = BackgroundGuide.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = c.notifier.canNotify()
        if (!notifGranted) BackgroundGuide.openNotificationSettings(context)
    }
    val requestNotif = {
        if (Build.VERSION.SDK_INT >= 33 && !settings.notifAsked) {
            scope.launch { c.prefs.setNotifAsked() }
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else BackgroundGuide.openNotificationSettings(context)
    }

    val updates = remember { runCatching { c.updates }.getOrNull() }
    val upd = updates?.state?.collectAsStateWithLifecycle()?.value
    val updateRow = UpdateRowState(
        checking = upd?.phase is com.wikg.aidaily.update.UpdatePhase.Checking,
        available = upd?.info?.versionName,
        status = when (val ph = upd?.phase) {
            is com.wikg.aidaily.update.UpdatePhase.UpToDate -> "已是最新版本"
            is com.wikg.aidaily.update.UpdatePhase.Failed -> if (ph.duringCheck) "检查失败：${ph.message}" else null
            is com.wikg.aidaily.update.UpdatePhase.Downloading -> "正在下载…"
            else -> null
        },
        autoCheck = settings.autoUpdateCheck,
        lastCheckAt = maxOf(settings.lastUpdateCheckAt, upd?.lastCheckAt ?: 0),
    )
    SettingsContent(
        update = updateRow,
        page = page,
        settings = settings,
        notifGranted = notifGranted,
        batteryOk = batteryOk,
        isXiaomi = BackgroundGuide.isXiaomi,
        miSans = AppFonts.isMiSans,
        watchlist = watchlist,
        actions = SettingsActions(
            onBack = onBack,
            openFeatured = onOpenFeatured,
            openFollow = onOpenFollow,
            navigate = onNavigate,
            setNotify = { on -> scope.launch { c.prefs.setNotify(on) } },
            notifAction = { if (notifGranted) BackgroundGuide.openNotificationSettings(context) else requestNotif() },
            checkNow = {
                WorkScheduler.runNow(context)
                android.widget.Toast.makeText(context, "已开始检查，有新一期会发通知", android.widget.Toast.LENGTH_SHORT).show()
            },
            autostart = {
                BackgroundGuide.openAutostart(context)
                scope.launch { c.prefs.setAutostartConfirmed(true) }
            },
            batterySaver = { BackgroundGuide.openBatterySaver(context) },
            ignoreBattery = { BackgroundGuide.requestIgnoreBatteryOptimizations(context) },
            notificationSettings = { BackgroundGuide.openNotificationSettings(context) },
            setTheme = { m -> scope.launch { c.prefs.setTheme(m) } },
            openUrl = { url -> openUrl(context, url) },
            openCrashLog = { context.startActivity(android.content.Intent(context, com.wikg.aidaily.crash.CrashLogActivity::class.java)) },
            checkUpdate = { if (upd?.info != null) updates?.openSheet() else updates?.checkNow() },
            setAutoUpdate = { on -> scope.launch { c.prefs.setAutoUpdateCheck(on) } },
        ),
    )
}

class SettingsActions(
    val onBack: () -> Unit = {},
    val openFeatured: () -> Unit = {},
    val openFollow: (FollowKind) -> Unit = {},
    val navigate: (SettingsPage) -> Unit = {},
    val setNotify: (Boolean) -> Unit = {},
    val notifAction: () -> Unit = {},
    val checkNow: () -> Unit = {},
    val autostart: () -> Unit = {},
    val batterySaver: () -> Unit = {},
    val ignoreBattery: () -> Unit = {},
    val notificationSettings: () -> Unit = {},
    val setTheme: (ThemeMode) -> Unit = {},
    val openUrl: (String) -> Unit = {},
    val openCrashLog: () -> Unit = {},
    val checkUpdate: () -> Unit = {},
    val setAutoUpdate: (Boolean) -> Unit = {},
)

/** 「关于 → 检查更新」行的展示状态。 */
data class UpdateRowState(
    val checking: Boolean = false,
    /** 有可用新版本时的版本号。 */
    val available: String? = null,
    val status: String? = null,
    val autoCheck: Boolean = true,
    val lastCheckAt: Long = 0,
)

enum class SettingsPage(val route: String, val title: String) {
    HUB("settings", "设置"),
    NOTIFY("settings/notify", "通知与后台"),
    APPEARANCE("settings/appearance", "外观"),
    ABOUT("settings/about", "关于"),
}

/** 无状态设置页（便于截图测试）：一级「设置」是目录，各分组是独立的二级页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    page: SettingsPage,
    settings: Settings,
    notifGranted: Boolean,
    batteryOk: Boolean,
    isXiaomi: Boolean,
    miSans: Boolean,
    actions: SettingsActions,
    watchlist: Watchlist? = null,
    update: UpdateRowState = UpdateRowState(),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (page) {
                SettingsPage.HUB -> SettingsHub(settings, notifGranted, batteryOk, isXiaomi, actions, watchlist, update)
                SettingsPage.NOTIFY -> NotifySettings(settings, notifGranted, batteryOk, isXiaomi, actions)
                SettingsPage.APPEARANCE -> AppearanceSettings(settings, actions)
                SettingsPage.ABOUT -> AboutSettings(miSans, actions, update)
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SettingsHub(settings: Settings, notifGranted: Boolean, batteryOk: Boolean, isXiaomi: Boolean, actions: SettingsActions, watchlist: Watchlist?, update: UpdateRowState = UpdateRowState()) {
    // 顶部品牌卡
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(Brush.linearGradient(listOf(AppTheme.extra.heroStart, AppTheme.extra.heroEnd)))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("AI 日报", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "版本 ${BuildConfig.VERSION_NAME} · 每天北京时间 8:30 更新",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }

    val bgNeeds = !batteryOk || (isXiaomi && !settings.autostartConfirmed)
    val notifySummary = when {
        !notifGranted -> "系统通知未开启，收不到新一期提醒"
        !settings.notifyEnabled -> "新一期提醒已关闭"
        bgNeeds -> "提醒已开启 · 后台设置未完成"
        else -> "提醒已开启 · 后台已放行"
    }
    GroupTitle("首页与提醒")
    Card {
        HubRow(
            icon = Icons.Rounded.Star, tint = AppTheme.extra.featured, title = "关注厂商",
            subtitle = if (settings.featuredVendors.isEmpty()) "未关注任何厂商" else settings.featuredVendors.joinToString("、"),
            tag = "hub-featured", onClick = actions.openFeatured,
        )
        Divider()
        HubRow(
            icon = Icons.Rounded.Inventory2, tint = MaterialTheme.colorScheme.primary, title = "关注产品",
            subtitle = followSummary(settings.followProducts, "未关注任何产品 · 版本更新里只看关注的") { watchlist?.product(it)?.displayName },
            tag = "hub-follow-products", onClick = { actions.openFollow(FollowKind.PRODUCTS) },
        )
        Divider()
        HubRow(
            icon = Icons.Rounded.Groups, tint = Color(0xFFE0457B), title = "关注人物",
            subtitle = followSummary(settings.followPeople, "未关注任何人物 · 人物动态里只看关注的") { watchlist?.person(it)?.displayName },
            tag = "hub-follow-people", onClick = { actions.openFollow(FollowKind.PEOPLE) },
        )
        Divider()
        HubRow(
            icon = Icons.Rounded.NotificationsActive, tint = MaterialTheme.colorScheme.primary, title = "通知与后台",
            subtitle = notifySummary, warn = !notifGranted || bgNeeds,
            tag = "hub-notify", onClick = { actions.navigate(SettingsPage.NOTIFY) },
        )
    }
    GroupTitle("通用")
    Card {
        HubRow(
            icon = Icons.Rounded.Palette, tint = Color(0xFF0E9F6E), title = "外观",
            subtitle = when (settings.themeMode) { ThemeMode.SYSTEM -> "深色模式：跟随系统"; ThemeMode.LIGHT -> "深色模式：始终浅色"; ThemeMode.DARK -> "深色模式：始终深色" },
            tag = "hub-appearance", onClick = { actions.navigate(SettingsPage.APPEARANCE) },
        )
        Divider()
        HubRow(
            icon = Icons.Rounded.Info, tint = Color(0xFF7C8594), title = "关于",
            subtitle = if (update.available != null) "发现新版本 v${update.available} · 点按查看"
            else "版本 ${BuildConfig.VERSION_NAME} · 检查更新 · MIT 开源",
            warn = update.available != null,
            tag = "hub-about", onClick = { actions.navigate(SettingsPage.ABOUT) },
        )
    }
}

private fun followSummary(ids: List<String>, empty: String, nameOf: (String) -> String?): String =
    if (ids.isEmpty()) empty else ids.joinToString("、") { nameOf(it) ?: it }

@Composable
private fun HubRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    tag: String,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = if (AppTheme.extra.isDark) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(19.dp), tint = tint) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (warn) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun NotifySettings(settings: Settings, notifGranted: Boolean, batteryOk: Boolean, isXiaomi: Boolean, actions: SettingsActions) {
    // —— 每日提醒 ——
    GroupTitle("每日提醒")
    Card {
        SwitchRow(
            "新一期推送",
            "每天北京时间 8:35 起检查；没发布就每 20 分钟重试到中午",
            settings.notifyEnabled,
        ) { on -> actions.setNotify(on) }
        Divider()
        StatusRow(
            "系统通知权限",
            if (notifGranted) "已开启" else "未开启，收不到推送",
            ok = notifGranted,
            action = if (notifGranted) "通知设置" else "去开启",
            onAction = actions.notifAction,
        )
        Divider()
        StatusRow(
            "上次检查",
            epochToBeijingLabel(settings.lastCheckAt) + (settings.lastCheckResult?.let { " · $it" } ?: "") +
                (settings.lastMirror?.let { " · 数据源 $it" } ?: ""),
            ok = null,
            action = "立即检查",
            onAction = actions.checkNow,
        )
    }

    // —— 后台保活 ——
    GroupTitle(if (isXiaomi) "小米 / 澎湃OS 后台设置" else "后台运行设置")
    Card {
        Text(
            "AI 日报不使用任何推送服务，靠系统定时任务在后台检查新一期。小米等国产系统默认会限制后台，" +
                "请按下面几步放行，否则可能要打开 app 才能看到新一期。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Divider()
        StepRow(
            1, "允许自启动",
            "手机管家 → 应用管理 → 权限 → 自启动管理 → 打开「AI 日报」",
            done = if (isXiaomi) settings.autostartConfirmed else null,
            action = "去设置",
            onAction = actions.autostart,
        )
        Divider()
        StepRow(
            2, "省电策略设为「无限制」",
            "设置 → 应用设置 → 应用管理 → AI 日报 → 省电策略 → 无限制",
            done = null,
            action = "去设置",
            onAction = actions.batterySaver,
        )
        Divider()
        StepRow(
            3, "忽略电池优化",
            if (batteryOk) "已加入系统电池优化白名单" else "在系统弹窗中选择「允许」",
            done = batteryOk,
            action = if (batteryOk) null else "允许",
            onAction = actions.ignoreBattery,
        )
        Divider()
        StepRow(
            4, "锁定最近任务（可选）",
            "打开多任务界面，长按 AI 日报卡片（或下拉）点「锁定」，清理后台时不会被杀",
            done = null, action = null, onAction = {},
        )
        Divider()
        StepRow(
            5, "允许通知横幅与锁屏显示（可选）",
            "通知管理 → AI 日报 → 每日简报，打开悬浮通知和锁屏通知",
            done = null, action = "去设置",
            onAction = actions.notificationSettings,
        )
    }

}

@Composable
private fun AppearanceSettings(settings: Settings, actions: SettingsActions) {
    // —— 外观 ——
    GroupTitle("外观")
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("深色模式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            val modes = listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (m, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == m,
                        onClick = { actions.setTheme(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
        }
    }

}

@Composable
private fun AboutSettings(miSans: Boolean, actions: SettingsActions, update: UpdateRowState = UpdateRowState()) {
    // —— 关于 ——
    GroupTitle("关于")
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("AI 日报", style = MaterialTheme.typography.titleMedium)
                Text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）· MIT 开源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Divider()
        UpdateCheckRow(update, actions.checkUpdate)
        Divider()
        SwitchRow("自动检查更新", "打开 app 时每天最多检查一次，只提示、不会自动安装", update.autoCheck) { actions.setAutoUpdate(it) }
        Divider()
        LinkRow("源代码与数据", "github.com/WikG1018/ai-daily") {
            actions.openUrl("https://github.com/WikG1018/ai-daily")
        }
        Divider()
        LinkRow("数据源", "GitHub Raw（主）· jsDelivr（镜像，8 秒超时切换）") {}
        Divider()
        LinkRow(
            "界面字体",
            if (miSans) "本应用使用 MiSans 字体（© 小米科技），查看许可协议" else "系统默认字体（本次构建未打包 MiSans）",
        ) { actions.openUrl("https://hyperos.mi.com/font/zh/download") }
        Divider()
        LinkRow("崩溃日志", "查看并复制最近一次崩溃信息，发给我们帮助排查") { actions.openCrashLog() }
    }
}

@Composable
private fun UpdateCheckRow(update: UpdateRowState, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !update.checking, onClick = onClick).testTag("check_update").padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("检查更新", style = MaterialTheme.typography.titleSmall)
            val sub = buildString {
                append("当前版本 v${BuildConfig.VERSION_NAME}")
                when {
                    update.checking -> append(" · 正在检查…")
                    update.status != null -> append(" · ").append(update.status)
                    update.available == null && update.lastCheckAt > 0 -> append(" · 上次检查 ").append(epochToBeijingLabel(update.lastCheckAt))
                }
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (update.status?.startsWith("检查失败") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        when {
            update.checking -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            update.available != null -> com.wikg.aidaily.ui.components.Pill(
                "v${update.available} 可更新", bg = AppTheme.extra.updateContainer, fg = AppTheme.extra.update,
            )
            else -> Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
internal fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(AppTheme.extra.card),
        content = content,
    )
}

@Composable
internal fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusRow(title: String, subtitle: String, ok: Boolean?, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = when (ok) {
                    true -> AppTheme.extra.update
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, subtitle: String, done: Boolean?, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (done == true) AppTheme.extra.update else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (done == true) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
            else Text("$n", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
