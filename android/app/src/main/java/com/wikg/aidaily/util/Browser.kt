package com.wikg.aidaily.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** 优先 Custom Tabs；没有支持的浏览器时退回系统浏览器。只放行 http/https。 */
fun openUrl(context: Context, url: String, toolbar: Color? = null, dark: Boolean = false) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null || (uri.scheme != "https" && uri.scheme != "http")) {
        Toast.makeText(context, "链接无效", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val builder = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setColorScheme(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        if (toolbar != null) {
            builder.setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(toolbar.toArgb()).build()
            )
        }
        val intent = builder.build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}

fun shareText(context: Context, title: String, url: String?) {
    val text = listOfNotNull(title, url).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun hostOf(url: String): String = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
