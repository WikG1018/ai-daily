package com.wikg.aidaily.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikg.aidaily.data.model.Region
import com.wikg.aidaily.ui.theme.AppTheme

/** 版本号徽标：等宽数字、主色描边淡底，紧凑。原文显示，不加前缀。 */
@Composable
fun VersionBadge(version: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = MaterialTheme.colorScheme.primary
    Text(
        version,
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 8.dp else 6.dp))
            .background(c.copy(alpha = if (AppTheme.extra.isDark) 0.20f else 0.09f))
            .border(0.8.dp, c.copy(alpha = 0.28f), RoundedCornerShape(if (large) 8.dp else 6.dp))
            .padding(horizontal = if (large) 9.dp else 6.dp, vertical = if (large) 3.dp else 1.dp)
            .testTag("version-badge"),
        color = c,
        style = (if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium).copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum",
            letterSpacing = 0.2.sp,
        ),
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

private fun initialOf(name: String): String {
    val t = name.trim().removePrefix("@")
    val ch = t.firstOrNull { it.isLetterOrDigit() } ?: return "•"
    return ch.uppercaseChar().toString()
}

/** 产品标：区域色渐变的圆角方块 + 首字。 */
@Composable
fun ProductMark(name: String, region: Region, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = regionColor(region)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.92f), c.copy(alpha = 0.62f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialOf(name), color = Color.White, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 人物头像：个人圆形、官方号圆角方形；区域色柔和底 + 首字。 */
@Composable
fun PersonAvatar(name: String, region: Region, official: Boolean = false, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = regionColor(region)
    val dark = AppTheme.extra.isDark
    Box(
        modifier
            .size(size)
            .clip(if (official) RoundedCornerShape(size * 0.3f) else CircleShape)
            .background(c.copy(alpha = if (dark) 0.28f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialOf(name), color = c, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 已关注的小星标。 */
@Composable
fun FollowStar(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Icon(Icons.Rounded.Star, "已关注", modifier.size(size), tint = AppTheme.extra.featured)
}

/** 「全部 / 只看关注」分段切换：胶囊底 + 选中段主色。 */
@Composable
fun FollowFilterToggle(followedOnly: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "filter") {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to "全部", true to "只看关注").forEach { (value, label) ->
            val sel = value == followedOnly
            val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, tween(200), label = "seg")
            val fg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(200), label = "segFg",
            )
            Text(
                label,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { if (!sel) onChange(value) }
                    .semantics { role = Role.Tab; selected = sel }
                    .testTag("$tagPrefix-" + if (value) "followed" else "all")
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                color = fg,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** 关注 / 已关注 按钮（详情页、设置页共用）。 */
@Composable
fun FollowButton(followed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, tag: String = "follow-button") {
    val e = AppTheme.extra
    val bg by animateColorAsState(if (followed) e.featuredContainer else MaterialTheme.colorScheme.primary, tween(200), label = "fb")
    val fg by animateColorAsState(if (followed) e.featured else MaterialTheme.colorScheme.onPrimary, tween(200), label = "fbFg")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onToggle)
            .testTag(tag)
            .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (followed) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(15.dp), tint = fg)
        Text(
            if (followed) "已关注" else "关注",
            modifier = Modifier.padding(start = 3.dp),
            color = fg,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
