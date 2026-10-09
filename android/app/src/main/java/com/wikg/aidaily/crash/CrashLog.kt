package com.wikg.aidaily.crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.wikg.aidaily.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 极简崩溃记录器：把最后一次未捕获异常写进 filesDir/crash/last_crash.txt，
 * 下次启动时可在「设置 → 关于 → 崩溃日志」查看并复制；
 * 若上次是「启动后几秒内就崩溃」，下次启动直接进入崩溃日志页（纯 View，不依赖 Compose / 字体）。
 */
object CrashLog {
    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"
    private const val SEEN = "last_crash.seen"
    /** 进程启动后多少毫秒内崩溃算「启动崩溃」。 */
    const val STARTUP_WINDOW_MS = 15_000L

    @Volatile private var installed = false

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR)
    fun file(ctx: Context) = File(dir(ctx), FILE)
    private fun seenFile(ctx: Context) = File(dir(ctx), SEEN)

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext ?: ctx
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            if (previous != null) previous.uncaughtException(thread, error)
            else { Process.killProcess(Process.myPid()); System.exit(10) }
        }
    }

    fun record(ctx: Context, thread: Thread, error: Throwable) {
        val uptime = runCatching { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() }.getOrDefault(-1L)
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val text = buildString {
            appendLine("AI 日报 崩溃日志")
            appendLine("时间: " + runCatching {
                ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (北京时间)"
            }.getOrDefault(System.currentTimeMillis().toString()))
            appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("线程: ${thread.name}")
            appendLine("启动后: ${uptime} ms")
            appendLine("startup_crash=${uptime in 0..STARTUP_WINDOW_MS}")
            appendLine()
            append(sw.toString())
        }
        val d = dir(ctx).apply { mkdirs() }
        File(d, FILE).writeText(text)
        File(d, SEEN).delete()
    }

    fun read(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete(); seenFile(ctx).delete() }
    }

    fun markSeen(ctx: Context) {
        runCatching { dir(ctx).mkdirs(); seenFile(ctx).writeText("1") }
    }

    /** 上次崩溃发生在启动阶段且用户还没看过 → 本次启动先展示崩溃日志。 */
    fun shouldShowOnLaunch(ctx: Context): Boolean = runCatching {
        val t = read(ctx) ?: return false
        !seenFile(ctx).exists() && t.contains("startup_crash=true")
    }.getOrDefault(false)
}
