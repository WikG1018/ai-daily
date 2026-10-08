package com.wikg.aidaily

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
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

    private fun homeState() = HomeUiState(
        index = index, issue = issue, initialLoading = false, readIds = readIds,
        settings = Settings(guideDismissed = true),
    )

    private fun home(mode: ThemeMode, name: String, scrollTo: Int = 0) {
        compose.setContent {
            AiDailyTheme(mode) {
                val ls = rememberLazyListState(initialFirstVisibleItemIndex = scrollTo)
                HomeContent(
                    state = homeState(), listState = ls, snackbar = remember { SnackbarHostState() },
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
    @Test fun homeSectionsLight() = home(ThemeMode.LIGHT, "home_sections_light", scrollTo = 4)

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

    @Test fun settingsLight() {
        compose.setContent {
            AiDailyTheme(ThemeMode.LIGHT) {
                SettingsContent(
                    settings = Settings(lastCheckAt = 1_791_506_000_000, lastCheckResult = "已是最新（2026-10-08）", lastMirror = "GitHub Raw"),
                    notifGranted = true, batteryOk = false, isXiaomi = true, miSans = true, actions = SettingsActions(),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(outDir, "settings_light.png").path)
    }
}
