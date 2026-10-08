package com.wikg.aidaily.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * MiSans 在构建时下载并打进 assets/fonts/（仓库里不放字体文件，见 README）。
 * 没打进去时自动退回系统字体。
 */
object AppFonts {
    @Volatile private var cached: FontFamily? = null
    @Volatile var isMiSans: Boolean = false
        private set

    fun family(context: Context): FontFamily {
        cached?.let { return it }
        val assets = context.applicationContext.assets
        val names = runCatching { assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
        val need = listOf("MiSans-Regular.otf", "MiSans-Medium.otf", "MiSans-Semibold.otf")
        val fam = if (names.containsAll(need)) {
            runCatching {
                FontFamily(
                    Font("fonts/MiSans-Regular.otf", assets, FontWeight.Normal),
                    Font("fonts/MiSans-Medium.otf", assets, FontWeight.Medium),
                    Font("fonts/MiSans-Semibold.otf", assets, FontWeight.SemiBold),
                    Font("fonts/MiSans-Semibold.otf", assets, FontWeight.Bold),
                ).also { isMiSans = true }
            }.getOrElse { FontFamily.Default }
        } else FontFamily.Default
        cached = fam
        return fam
    }
}

fun appTypography(f: FontFamily): Typography {
    val tight = (-0.01).em
    return Typography(
        displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = tight),
        headlineLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = tight),
        headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = tight),
        headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 31.sp, letterSpacing = tight),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 16.5.sp, lineHeight = 28.sp, letterSpacing = 0.01.em),
        bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    )
}
