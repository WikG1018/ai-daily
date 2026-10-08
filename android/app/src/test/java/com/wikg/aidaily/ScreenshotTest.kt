package com.wikg.aidaily

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.wikg.aidaily.data.local.Settings
import com.wikg.aidaily.data.local.ThemeMode
import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.model.locate
import com.wikg.aidaily.data.remote.AppJson
import com.wikg.aidaily.ui.detail.DetailContent
import com.wikg.aidaily.ui.home.HomeContent
import com.wikg.aidaily.ui.home.HomeUiState
import com.wikg.aidaily.ui.settings.SettingsActions
import com.wikg.aidaily.ui.settings.SettingsContent
import com.wikg.aidaily.ui.settings.SettingsPage
import com.wikg.aidaily.ui.settings.FeaturedContent
import com.wikg.aidaily.ui.theme.AiDailyTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 用 Robolectric 原生图形渲染真实的 Compose 界面（真实主题、MiSans 字体、仓库里的样例数据），
 * 生成 android/screenshots 下的 PNG。默认跳过，加 -Pscreenshots 才运行。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("aidaily.screenshotDir") ?: "build/screenshots")
    private val index = AppJson.decodeFromString(DailyIndex.serializer(), File("../../data/index.json").readText())
    private val issue = AppJson.decodeFromString(Issue.serializer(), File("../../data/2026-10-08.json").readText())
    private val readIds = setOf("2026-10-08-003", "2026-10-08-006")

    @Before fun only() = assumeTrue(System.getProperty("aidaily.screenshots") == "true")

    private val featured = listOf("小米", "Anthropic", "OpenAI")

    private fun homeState() = HomeUiState(
        index = index, issue = issue, initialLoading = false, readIds = readIds,
        settings = Settings(guideDismissed = true, featuredVendors = featured),
    )

    private fun home(mode: ThemeMode, name: String, page: Int = 0) {
        compose.setContent {
            AiDailyTheme(mode) {
                HomeContent(
                    state = homeState(), snackbar = remember { SnackbarHostState() }, initialPage = page,
                    showGuide = false, notifGranted = true,
                    onRefresh = {}, onSelectDate = {}, onBackToLatest = {}, onMarkAllRead = {}, onShowPicker = {},
                    onOpenItem = {}, onOpenSettings = {}, onGuideAction = {}, onGuideDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    @Test fun homeLight() = home(ThemeMode.LIGHT, "home_light")
    @Test fun homeDark() = home(ThemeMode.DARK, "home_dark")
    // 页序：今日 0 · 小米 1 · Anthropic 2 · OpenAI 3 · 模型 4 · 编码 Agent 5 · 其他 6
    @Test fun homeFeaturedLight() = home(ThemeMode.LIGHT, "home_featured_light", page = 2)
    @Test fun homeXiaomiEmptyLight() = home(ThemeMode.LIGHT, "home_featured_empty_light", page = 1)
    @Test fun homeSectionsLight() = home(ThemeMode.LIGHT, "home_sections_light", page = 5)
    @Test fun homeSectionsDark() = home(ThemeMode.DARK, "home_sections_dark", page = 4)

    private fun detail(mode: ThemeMode, id: String, name: String) {
        val ctx = issue.locate(id)!!
        compose.setContent {
            AiDailyTheme(mode) {
                DetailContent(ctx = ctx, loading = false, readIds = readIds, onBack = {}, onOpenItem = {}, onOpenIssue = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    @Test fun detailLight() = detail(ThemeMode.LIGHT, "2026-10-08-001", "detail_light")
    @Test fun detailDark() = detail(ThemeMode.DARK, "2026-10-08-001", "detail_dark")

    private fun settings(mode: ThemeMode, page: SettingsPage, name: String) {
        compose.setContent {
            AiDailyTheme(mode) {
                SettingsContent(
                    page = page,
                    settings = Settings(
                        lastCheckAt = 1_791_506_000_000, lastCheckResult = "已是最新（2026-10-08）", lastMirror = "GitHub Raw",
                        featuredVendors = featured,
                    ),
                    notifGranted = true, batteryOk = false, isXiaomi = true, miSans = true, actions = SettingsActions(),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
    }

    @Test fun settingsHubLight() = settings(ThemeMode.LIGHT, SettingsPage.HUB, "settings_light")
    @Test fun settingsHubDark() = settings(ThemeMode.DARK, SettingsPage.HUB, "settings_dark")
    @Test fun settingsNotifyLight() = settings(ThemeMode.LIGHT, SettingsPage.NOTIFY, "settings_notify_light")

    @Test fun featuredManagerLight() {
        compose.setContent {
            AiDailyTheme(ThemeMode.LIGHT) {
                FeaturedContent(
                    featured = featured, issue = issue,
                    recentVendors = listOf("DeepSeek" to 3, "Kimi" to 2), onChange = {}, onBack = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "featured_light.png").path)
    }

    /** 启动图标：自适应图标的前景 / 背景按圆形与圆角方形两种遮罩渲染。 */
    @Test fun launcherIcon() {
        compose.setContent {
            AiDailyTheme(ThemeMode.LIGHT) {
                Row(
                    Modifier.background(Color(0xFFF4F5F9)).padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    listOf(CircleShape, RoundedCornerShape(30), RoundedCornerShape(22)).forEach { shape ->
                        Box(Modifier.size(78.dp).clip(shape), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(117.dp))
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(117.dp))
                        }
                    }
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0xFFDDE3F0)), contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.requiredSize(117.dp),
                            colorFilter = ColorFilter.tint(Color(0xFF2E3A59)),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "icon.png").path)
    }
}
