package com.wikg.aidaily.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wikg.aidaily.data.model.Follows
import com.wikg.aidaily.data.model.NewsItem
import com.wikg.aidaily.data.model.Region
import com.wikg.aidaily.data.model.Watchlist
import com.wikg.aidaily.ui.components.CardRadius
import com.wikg.aidaily.ui.components.FollowButton
import com.wikg.aidaily.ui.components.PersonAvatar
import com.wikg.aidaily.ui.components.ProductMark
import com.wikg.aidaily.ui.components.VersionBadge
import com.wikg.aidaily.ui.components.regionColor
import com.wikg.aidaily.ui.home.OfficialTag
import com.wikg.aidaily.ui.theme.AppTheme

data class RefsUi(
    val watchlist: Watchlist? = null,
    val follows: Follows = Follows(),
    val onToggleProduct: (String) -> Unit = {},
    val onTogglePerson: (String) -> Unit = {},
)

private data class RefRow(
    val kind: Char, // 'p' 产品 / 'u' 人物
    val id: String,
    val title: String,
    val handle: String?,
    val subtitle: String,
    val region: Region,
    val official: Boolean,
    val url: String?,
    val linkLabel: String,
    val primary: Boolean,
)

/**
 * 详情页「关联」卡：条目带的产品 / 人物（主要的排前面），可一键关注，可打开更新日志 / X 主页。
 * 清单已加载但不认识的 id 按 schema 忽略；清单还没加载时用原始 id 兜底显示。
 */
@Composable
internal fun RefCards(item: NewsItem, refs: RefsUi, openLink: (String) -> Unit) {
    val w = refs.watchlist
    val rows = buildList {
        val primaryProduct = com.wikg.aidaily.data.model.normId(item.product)
        item.productIds.forEach { id ->
            val p = w?.product(id)
            if (w != null && p == null) return@forEach
            add(
                RefRow(
                    'p', id, p?.displayName ?: id, null,
                    p?.let { listOf(it.vendor, it.regionKind.label).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "产品",
                    p?.regionKind ?: item.regionKind, false, p?.safeUrl, "更新日志", id == primaryProduct,
                ),
            )
        }
        val primaryPerson = com.wikg.aidaily.data.model.normId(item.person)
        item.personIds.forEach { id ->
            val p = w?.person(id)
            if (w != null && p == null) return@forEach
            add(
                RefRow(
                    'u', id, p?.displayName ?: id, p?.displayHandle ?: id,
                    p?.orgRole ?: "", p?.regionKind ?: item.regionKind, p?.isOfficial == true,
                    p?.safeUrl ?: "https://x.com/$id", "X 主页", id == primaryPerson,
                ),
            )
        }
    }.sortedByDescending { it.primary }
    if (rows.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(AppTheme.extra.card).testTag("detail-refs"),
    ) {
        rows.forEachIndexed { i, r ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(
                Modifier.padding(start = 62.dp, end = 14.dp), thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant,
            )
            val followed = if (r.kind == 'p') r.id in refs.follows.products else r.id in refs.follows.people
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.kind == 'p') ProductMark(r.title, r.region, 36.dp) else PersonAvatar(r.title, r.region, r.official, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (r.official) { Spacer(Modifier.width(5.dp)); OfficialTag() }
                        if (r.kind == 'p' && r.primary) item.versionText?.let { Spacer(Modifier.width(6.dp)); VersionBadge(it) }
                    }
                    val sub = listOfNotNull(r.handle?.let { "@$it" }, r.subtitle.takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (sub.isNotBlank()) Text(
                        sub, style = MaterialTheme.typography.bodySmall,
                        color = if (r.kind == 'p') regionColor(r.region).copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    r.url?.let { url ->
                        Text(
                            r.linkLabel + " ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { openLink(url) }.padding(vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                FollowButton(
                    followed = followed,
                    onToggle = { if (r.kind == 'p') refs.onToggleProduct(r.id) else refs.onTogglePerson(r.id) },
                    tag = "follow-${r.kind}-${r.id}",
                )
            }
        }
    }
}
