package com.wikg.aidaily.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wikg.aidaily.data.model.NewsItem
import com.wikg.aidaily.data.model.Region
import com.wikg.aidaily.data.model.SectionIds
import com.wikg.aidaily.data.model.Watchlist
import com.wikg.aidaily.data.model.matches
import com.wikg.aidaily.ui.components.FollowStar
import com.wikg.aidaily.ui.components.PersonAvatar
import com.wikg.aidaily.ui.components.ProductMark
import com.wikg.aidaily.ui.components.UpdateBadge
import com.wikg.aidaily.ui.components.VersionBadge
import com.wikg.aidaily.ui.components.groupShape
import com.wikg.aidaily.ui.components.regionColor
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.util.richText
import com.wikg.aidaily.util.stripBold

internal enum class RowStyle { NEWS, RELEASE, PERSON }

/** 版本更新 / 人物动态栏目里的普通条目用定制样式；group 容器和其他栏目照旧。 */
internal fun rowStyleOf(item: NewsItem, sectionId: String?): RowStyle = when {
    item.group -> RowStyle.NEWS
    sectionId == SectionIds.RELEASES -> RowStyle.RELEASE
    sectionId == SectionIds.PEOPLE -> RowStyle.PERSON
    else -> RowStyle.NEWS
}

/** 产品显示名：清单里的 name → 标题去掉版本号 → 产品 id → 标题。 */
internal fun productDisplayName(item: NewsItem, watchlist: Watchlist?): String {
    watchlist?.product(item.product)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
    val title = stripBold(item.title).trim()
    val v = item.versionText
    if (v != null && title.contains(v)) title.replace(v, "").trim().trimEnd('-', '·', ':', '：').trim().takeIf { it.isNotEmpty() }?.let { return it }
    return item.product?.takeIf { it.isNotBlank() } ?: title
}

data class PersonDisplay(val name: String, val handle: String?, val orgRole: String, val official: Boolean, val region: Region)

/** 人物信息：清单里有就用清单；没有时退回 person id 和条目的 vendor。 */
internal fun personDisplay(item: NewsItem, watchlist: Watchlist?): PersonDisplay {
    val p = watchlist?.person(item.person) ?: item.personIds.firstNotNullOfOrNull { watchlist?.person(it) }
    if (p != null) return PersonDisplay(p.displayName, p.displayHandle, p.orgRole.ifBlank { item.vendor }, p.isOfficial, p.regionKind)
    val id = item.person?.trim()?.removePrefix("@")?.takeIf { it.isNotEmpty() }
    return PersonDisplay(id ?: item.vendor.ifBlank { "未知" }, id, item.vendor, false, item.regionKind)
}

@Composable
private fun RowContainer(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(groupShape(first, last))
            .background(AppTheme.extra.card),
    ) {
        if (!first) HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

/** 版本更新：紧凑一行——产品标 · 产品名 + 版本徽标 · 厂商 · 时间。 */
@Composable
internal fun ReleaseRow(item: NewsItem, first: Boolean, last: Boolean, readIds: Set<String>, follow: FollowUi, onOpen: (String) -> Unit) {
    val name = productDisplayName(item, follow.watchlist)
    val read = item.id in readIds
    val followed = item.matches(follow.follows)
    val titleColor by animateColorAsState(if (read) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface, tween(450), label = "relRead")
    RowContainer(first, last) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(item.id) }
                .testTag("release-${item.id}")
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val region = follow.watchlist?.product(item.product)?.regionKind ?: item.regionKind
            ProductMark(name, region, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (followed) { Spacer(Modifier.width(4.dp)); FollowStar() }
                    if (item.update) { Spacer(Modifier.width(6.dp)); UpdateBadge() }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.versionText?.let { VersionBadge(it, Modifier.weight(1f, fill = false)); Spacer(Modifier.width(7.dp)) }
                    Text(
                        item.vendor.ifBlank { "未知厂商" },
                        style = MaterialTheme.typography.labelMedium,
                        color = regionColor(item.regionKind),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                item.summary?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stripBold(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (read) 0.7f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                item.time?.let { Text(timeShort(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                if (item.children.isNotEmpty()) Text("+${item.children.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        }
    }
}

/** 人物动态：头像 · 人名 @handle · 组织 / 身份 · 时间，下方是帖子标题与摘要。 */
@Composable
internal fun PersonRow(item: NewsItem, first: Boolean, last: Boolean, readIds: Set<String>, follow: FollowUi, onOpen: (String) -> Unit) {
    val who = personDisplay(item, follow.watchlist)
    val read = item.id in readIds
    val followed = item.matches(follow.follows)
    val titleColor by animateColorAsState(if (read) AppTheme.extra.readTitle else MaterialTheme.colorScheme.onSurface, tween(450), label = "pplRead")
    RowContainer(first, last) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(item.id) }
                .testTag("person-${item.id}")
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(who.name, who.region, who.official, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            who.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (who.official) { Spacer(Modifier.width(5.dp)); OfficialTag() }
                        if (followed) { Spacer(Modifier.width(4.dp)); FollowStar() }
                    }
                    val sub = listOfNotNull(who.handle?.let { "@$it" }, who.orgRole.takeIf { it.isNotBlank() }).joinToString("  ·  ")
                    if (sub.isNotBlank()) Text(
                        sub,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.update) { UpdateBadge(); Spacer(Modifier.width(6.dp)) }
                    item.time?.let { Text(timeShort(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                richText(item.title),
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            item.summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    stripBold(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (read) 0.7f else 1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.children.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ChildList(item.children, readIds, onOpen)
            }
        }
    }
}

@Composable
internal fun OfficialTag() {
    Text(
        "官方",
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 5.dp, vertical = 0.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
