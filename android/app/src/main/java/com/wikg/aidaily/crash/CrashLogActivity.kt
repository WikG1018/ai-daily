package com.wikg.aidaily.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.wikg.aidaily.MainActivity

/** 崩溃日志页：纯 View 实现，即使 Compose / 字体 / 数据层出问题也能打开。 */
class CrashLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) 0xFF0E1015.toInt() else 0xFFF5F6FA.toInt()
        val fg = if (night) 0xFFE6E8EE.toInt() else 0xFF1B1D24.toInt()
        val log = CrashLog.read(this)
        val fromLaunch = intent.getBooleanExtra(EXTRA_FROM_LAUNCH, false)
        CrashLog.markSeen(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = if (fromLaunch) "上次打开时 AI 日报崩溃了" else "崩溃日志"
            textSize = 20f
            setTextColor(fg)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = if (log == null) "暂无崩溃记录。" else "请点「复制」后把内容发给我们，便于定位问题。"
            textSize = 14f
            setTextColor(fg)
            setPadding(0, pad / 2, 0, pad / 2)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        if (log != null) {
            buttons.addView(btn("复制") {
                val cm = getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("AI 日报崩溃日志", log))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            })
            buttons.addView(btn("清除") {
                CrashLog.clear(this)
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
                recreate()
            })
        }
        if (fromLaunch) {
            buttons.addView(btn("继续打开") {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            })
        }
        root.addView(buttons)
        root.addView(ScrollView(this).apply {
            addView(TextView(this@CrashLogActivity).apply {
                text = log ?: ""
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(fg)
                setTextIsSelectable(true)
                setBackgroundColor(Color.TRANSPARENT)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    companion object {
        const val EXTRA_FROM_LAUNCH = "from_launch"
    }
}
