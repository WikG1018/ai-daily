package com.wikg.aidaily

import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import com.wikg.aidaily.ui.theme.DarkColors
import com.wikg.aidaily.ui.theme.LightColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wikg.aidaily.data.local.Settings
import com.wikg.aidaily.data.local.ThemeMode
import com.wikg.aidaily.ui.AppNav
import com.wikg.aidaily.ui.theme.AiDailyTheme
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface DeepLink {
    data class Issue(val date: String) : DeepLink
    data class Item(val id: String) : DeepLink

    companion object {
        private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
        private val ID = Regex("""^\d{4}-\d{2}-\d{2}-\d{1,4}$""")

        /** aidaily://issue/2026-10-08 、aidaily://item/2026-10-08-001 */
        fun parse(intent: Intent?): DeepLink? {
            val uri = intent?.data ?: return null
            if (uri.scheme != "aidaily") return null
            val arg = uri.pathSegments.firstOrNull() ?: return null
            return when (uri.host) {
                "issue" -> arg.takeIf { DATE.matches(it) }?.let { Issue(it) }
                "item" -> arg.takeIf { ID.matches(it) }?.let { Item(it) }
                else -> null
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val deepLinks = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLinks.value = DeepLink.parse(intent)
        val prefs = (application as AiDailyApp).container.prefs
        // 首帧就用用户选的主题（DataStore 很小，读一次只要几毫秒），避免「浅色一闪再变深色」
        val initial = runBlocking { runCatching { withTimeoutOrNull(300) { prefs.settings.first() } }.getOrNull() } ?: Settings()
        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = initial)
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                // 窗口底色跟随 app 内主题（「深色」不跟随系统时，资源里的窗口色仍是浅色）
                window.setBackgroundDrawable(ColorDrawable(if (dark) DarkColors.background.toArgb() else LightColors.background.toArgb()))
                onDispose { }
            }
            AiDailyTheme(settings.themeMode) {
                AppNav(deepLinks) { deepLinks.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLink.parse(intent)?.let { deepLinks.value = it }
    }
}
