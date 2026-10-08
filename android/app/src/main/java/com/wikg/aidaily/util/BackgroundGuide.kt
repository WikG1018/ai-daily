package com.wikg.aidaily.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 小米 / Redmi / POCO（MIUI、澎湃 OS）后台保活引导。
 * 厂商页面没有公开 API，组件名可能随系统版本变化：逐个尝试，全部失败就退回应用详情页。
 */
object BackgroundGuide {
    val isXiaomi: Boolean
        get() = listOf(Build.MANUFACTURER, Build.BRAND).any {
            it.equals("Xiaomi", true) || it.equals("Redmi", true) || it.equals("POCO", true)
        }

    private fun tryStart(context: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** 自启动管理 */
    fun openAutostart(context: Context): Boolean = tryStart(
        context,
        listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity"))
                .putExtra("package_name", context.packageName).putExtra("package_label", "AI 日报"),
            appDetails(context),
        ),
    )

    /** 省电策略（选「无限制」） */
    fun openBatterySaver(context: Context): Boolean = tryStart(
        context,
        listOf(
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                .putExtra("package_name", context.packageName).putExtra("package_label", "AI 日报"),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity"))
                .putExtra("package_name", context.packageName).putExtra("package_label", "AI 日报"),
            appDetails(context),
        ),
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** 系统级电池优化白名单 */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean = tryStart(
        context,
        listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetails(context),
        ),
    )

    fun openNotificationSettings(context: Context): Boolean = tryStart(
        context,
        listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            appDetails(context),
        ),
    )
}
