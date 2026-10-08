package com.wikg.aidaily.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wikg.aidaily.MainActivity
import com.wikg.aidaily.R
import com.wikg.aidaily.data.model.IssueSummary
import com.wikg.aidaily.util.issueDateLabel
import com.wikg.aidaily.util.stripBold

class Notifier(private val context: Context) {

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_daily_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyNewIssue(issue: IssueSummary) {
        if (!canNotify()) return
        ensureChannel()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aidaily://issue/${issue.date}"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, issue.date.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = stripBold(issue.highlights)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle("${issue.title} · ${issueDateLabel(issue.date)}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(if (issue.itemCount > 0) "${issue.itemCount} 条" else null)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL = "daily_issue"
        const val NOTIFICATION_ID = 1001
    }
}
