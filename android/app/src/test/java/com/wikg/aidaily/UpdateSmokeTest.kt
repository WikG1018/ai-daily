package com.wikg.aidaily

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wikg.aidaily.data.local.Settings
import com.wikg.aidaily.data.local.ThemeMode
import com.wikg.aidaily.data.model.UpdateInfo
import com.wikg.aidaily.data.remote.DailyApi
import com.wikg.aidaily.ui.home.HomeContent
import com.wikg.aidaily.ui.home.HomeUiState
import com.wikg.aidaily.ui.settings.SettingsActions
import com.wikg.aidaily.ui.settings.SettingsContent
import com.wikg.aidaily.ui.settings.SettingsPage
import com.wikg.aidaily.ui.settings.UpdateRowState
import com.wikg.aidaily.ui.theme.AiDailyTheme
import com.wikg.aidaily.ui.update.UpdateSheetActions
import com.wikg.aidaily.ui.update.UpdateSheetContent
import com.wikg.aidaily.update.ApkInstaller
import com.wikg.aidaily.update.UpdateManager
import com.wikg.aidaily.update.UpdatePhase
import com.wikg.aidaily.update.UpdateUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class UpdateSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val info = UpdateInfo(
        versionName = "1.3.0", versionCode = 5, notes = "- 应用内更新", size = 22_000_000,
        apkUrls = listOf("https://github.com/WikG1018/ai-daily/releases/download/v1.3.0/ai-daily-1.3.0.apk"),
    )

    @Test fun aboutPageShowsCheckUpdateAndToggle() {
        var clicked = 0
        var auto: Boolean? = null
        compose.setContent {
            AiDailyTheme(ThemeMode.LIGHT) {
                SettingsContent(
                    page = SettingsPage.ABOUT, settings = Settings(), notifGranted = true, batteryOk = true, isXiaomi = true, miSans = false,
                    actions = SettingsActions(checkUpdate = { clicked++ }, setAutoUpdate = { auto = it }),
                    update = UpdateRowState(status = "已是最新版本"),
                )
            }
        }
        compose.onNodeWithTag("check_update").assertIsDisplayed().performClick()
        compose.onNodeWithText("当前版本 v${BuildConfig.VERSION_NAME} · 已是最新版本").assertExists()
        compose.onNodeWithText("自动检查更新").performClick()
        assertEquals(1, clicked)
        assertEquals(false, auto)
    }

    @Test fun sheetButtonsWork() {
        var update = 0; var ignore = 0
        compose.setContent {
            AiDailyTheme(ThemeMode.DARK) {
                UpdateSheetContent(UpdateUiState(phase = UpdatePhase.Available, info = info), info, "1.2.0",
                    UpdateSheetActions(update = { update++ }, ignore = { ignore++ }))
            }
        }
        compose.onNodeWithText("v1.3.0").assertExists()
        compose.onNodeWithText("应用内更新").assertExists()
        compose.onNodeWithTag("update_now").performClick()
        compose.onNodeWithText("忽略此版本").performClick()
        assertEquals(1, update); assertEquals(1, ignore)
    }

    @Test fun homeBannerAppearsAndOpensSheet() {
        var opened = 0
        compose.setContent {
            AiDailyTheme(ThemeMode.LIGHT) {
                HomeContent(
                    state = HomeUiState(initialLoading = false, settings = Settings(guideDismissed = true)),
                    snackbar = remember { SnackbarHostState() }, showGuide = false, notifGranted = true,
                    onRefresh = {}, onSelectDate = {}, onBackToLatest = {}, onMarkAllRead = {}, onShowPicker = {},
                    onOpenItem = {}, onOpenSettings = {}, onGuideAction = {}, onGuideDismiss = {},
                    updateBanner = "1.3.0", onOpenUpdate = { opened++ },
                )
            }
        }
        compose.onNodeWithTag("update_banner").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test fun deepLinkParsesUpdate() {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("aidaily://update"))
        assertEquals(DeepLink.Update, DeepLink.parse(i))
    }

    @Test fun fileProviderExposesUpdatesDir() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = File(ApkInstaller.updatesDir(ctx).apply { mkdirs() }, "ai-daily-9.9.9.apk").apply { writeText("x") }
        val uri = FileProvider.getUriForFile(ctx, ApkInstaller.authority(ctx), f)
        assertEquals("content", uri.scheme)
        assertEquals(ApkInstaller.authority(ctx), uri.authority)
        val intent = ApkInstaller.installIntent(ctx, f)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun managerOfflineNeverThrowsAndCleansOldApks() = runBlocking {
        DailyApi.offlineForTests = true
        try {
            val app = ApplicationProvider.getApplicationContext<AiDailyApp>()
            val dir = ApkInstaller.updatesDir(app).apply { mkdirs() }
            val old = File(dir, "ai-daily-1.0.0.apk").apply { writeText("old") }
            val part = File(dir, "ai-daily-9.0.0.apk.part").apply { writeText("p") }
            val future = File(dir, "ai-daily-99.0.0.apk").apply { writeText("f") }
            val m = UpdateManager(app, app.container.prefs)
            m.autoCheckIfDue()
            assertFalse(old.exists()); assertFalse(part.exists()); assertTrue(future.exists())
            m.checkNow() // 手动检查离线：进入失败态，不抛异常
            kotlinx.coroutines.delay(500)
            m.cancelDownload(); m.closeSheet(); m.dismissBanner()
            assertTrue(m.state.value.phase !is UpdatePhase.Downloading)
        } finally {
            DailyApi.offlineForTests = false
        }
    }

    @Test fun apkInspectorRejectsGarbage() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = File(ctx.cacheDir, "garbage.apk").apply { writeBytes(ByteArray(100) { 1 }) }
        val v = ApkInstaller.verify(ctx, f)
        assertFalse(com.wikg.aidaily.data.model.SignatureRules.allowInstall(v, shaVerified = true))
    }
}
