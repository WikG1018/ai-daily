package com.wikg.aidaily.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikg.aidaily.data.model.Region
import com.wikg.aidaily.ui.theme.AppTheme

@Composable
fun regionColor(region: Region): Color = when (region) {
    Region.CN -> AppTheme.extra.regionCn
    Region.US -> AppTheme.extra.regionUs
    Region.INTL -> AppTheme.extra.regionIntl
}

/** 厂商标签：区域色圆点 + 区域色文字 + 淡底。 */
@Composable
fun VendorTag(vendor: String, region: Region, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = regionColor(region)
    Row(
        modifier
            .clip(RoundedCornerShape(if (large) 8.dp else 6.dp))
            .background(c.copy(alpha = if (AppTheme.extra.isDark) 0.16f else 0.09f))
            .padding(horizontal = if (large) 10.dp else 7.dp, vertical = if (large) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (large) 7.dp else 5.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(if (large) 6.dp else 4.dp))
        Text(
            vendor.ifBlank { "未知" },
            color = c,
            style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun RegionLabel(region: Region, modifier: Modifier = Modifier) {
    Text(
        region.label,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = regionColor(region).copy(alpha = 0.85f),
    )
}

@Composable
fun UpdateBadge(modifier: Modifier = Modifier) {
    Text(
        "更新",
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppTheme.extra.update)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/** 品牌标：与启动图标一致的「日」字标——白色圆角字框 + 晨光琥珀的一横。 */
@Composable
fun BrandMark(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF4636E8), Color(0xFF1A1260)))),
    ) {
        // 以 108 视口的自适应图标为基准，放大 108/72 让字形填满（相当于去掉安全区外圈）
        val k = this.size.width / 72f
        fun x(v: Float) = (v - 18f) * k
        val stroke = 6.5f * k
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(x(39.5f), x(34.5f)),
            size = Size(29f * k, 39f * k),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * k, 4f * k),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        drawLine(Color(0xFFFFB547), Offset(x(42.75f), x(54f)), Offset(x(65.25f), x(54f)), stroke)
    }
}

@Composable
fun MiMark(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(AppTheme.extra.featured),
        contentAlignment = Alignment.Center,
    ) {
        Text("mi", color = Color.White, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}

/** 关注厂商的标：小米用 mi 标，其他厂商取首字（拉丁字母大写）。 */
@Composable
fun VendorMark(vendor: String, isXiaomi: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    if (isXiaomi) { MiMark(modifier, size); return }
    val initial = vendor.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "★"
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(AppTheme.extra.featured),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun DotSeparator() {
    Box(
        Modifier.padding(horizontal = 6.dp).size(3.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
    )
}

val CardRadius = 18.dp

fun groupShape(first: Boolean, last: Boolean, radius: Dp = CardRadius) = RoundedCornerShape(
    topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
    bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
)

val ContentHorizontalPadding = 16.dp
val ArrangementTight = Arrangement.spacedBy(6.dp)
