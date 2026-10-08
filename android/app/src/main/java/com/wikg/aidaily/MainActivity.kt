package com.wikg.aidaily

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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
