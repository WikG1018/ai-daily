package com.wikg.aidaily.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wikg.aidaily.MainActivity
import com.wikg.aidaily.R

/** 更新下载的进度 / 完成通知（低重要性，不响铃）。点按回到 app 的更新面板。 */
internal class UpdateNotifier(private val context: Context) {
    private var lastPost = 0L

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "应用更新", NotificationManager.IMPORTANCE_LOW).apply {
                description = "下载新版本的进度"
                setShowBadge(false)
            },
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context, 2001,
        Intent(Intent.ACTION_VIEW, Uri.parse("aidaily://update"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canPost() = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)

    fun progress(version: String, bytes: Long, total: Long) {
        val t = SystemClock.elapsedRealtime()
        if (t - lastPost < 700 && bytes < total) return
        lastPost = t
        post(
            base("正在下载 AI 日报 v$version")
                .setContentText(if (total > 0) "${formatBytes(bytes)} / ${formatBytes(total)}" else formatBytes(bytes))
                .setProgress(100, if (total > 0) (bytes * 100 / total).toInt() else 0, total <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true),
        )
    }

    fun ready(version: String) = post(
        base("AI 日报 v$version 已下载")
            .setContentText("已通过校验，点按安装")
            .setAutoCancel(true),
    )

    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    private fun base(title: String) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(context, R.color.brand))
        .setContentTitle(title)
        .setContentIntent(contentIntent())
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun post(b: NotificationCompat.Builder) {
        if (!canPost()) return
        runCatching {
            ensureChannel()
            NotificationManagerCompat.from(context).notify(ID, b.build())
        }
    }

    companion object {
        const val CHANNEL = "app_update"
        const val ID = 2001
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
