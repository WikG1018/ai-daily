package com.wikg.aidaily.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** data/index.json —— 字段只增不改，未知字段忽略。 */
@Serializable
data class DailyIndex(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val latest: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val issues: List<IssueSummary> = emptyList(),
)

@Serializable
data class IssueSummary(
    val date: String,
    val title: String = "AI 日报",
    val highlights: String = "",
    @SerialName("item_count") val itemCount: Int = 0,
    val path: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
) {
    val resolvedPath: String get() = path.ifBlank { "data/$date.json" }
}

/** data/YYYY-MM-DD.json */
@Serializable
data class Issue(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val date: String,
    val title: String = "AI 日报",
    val window: CoverWindow? = null,
    val highlights: String = "",
    val xiaomi: XiaomiColumn? = null,
    val sections: List<Section> = emptyList(),
    val notes: List<String> = emptyList(),
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
) {
    /** 深度优先展开所有条目（含子条目、group 容器）。 */
    fun allItems(): List<NewsItem> = buildList {
        fun walk(items: List<NewsItem>) { items.forEach { add(it); walk(it.children) } }
        walk(xiaomi?.items.orEmpty())
        sections.forEach { walk(it.items) }
    }

    /** 新闻条数（不含 group 容器），与报头一致。 */
    val newsCount: Int get() = allItems().count { !it.group }
}

@Serializable
data class CoverWindow(
    val start: String? = null,
    val end: String? = null,
    val tz: String? = null,
)

@Serializable
data class XiaomiColumn(
    val items: List<NewsItem> = emptyList(),
    @SerialName("empty_text") val emptyText: String? = null,
)

@Serializable
data class Section(
    val id: String = "",
    val title: String = "",
    val icon: String? = null,
    val items: List<NewsItem> = emptyList(),
    @SerialName("empty_text") val emptyText: String? = null,
)

@Serializable
data class NewsItem(
    val id: String,
    val vendor: String = "",
    val region: String = "intl",
    val title: String = "",
    val summary: String? = null,
    val time: String? = null,
    val links: List<NewsLink> = emptyList(),
    val children: List<NewsItem> = emptyList(),
    val update: Boolean = false,
    @SerialName("update_note") val updateNote: String? = null,
    val group: Boolean = false,
) {
    val regionKind: Region get() = Region.of(region)
}

@Serializable
data class NewsLink(
    val label: String? = null,
    val url: String = "",
) {
    val displayLabel: String get() = label?.takeIf { it.isNotBlank() } ?: "原帖"
    val isSafe: Boolean get() = url.startsWith("https://") || url.startsWith("http://")
}

enum class Region(val label: String) {
    CN("中国"), US("美国"), INTL("国际");

    companion object {
        fun of(raw: String?): Region = when (raw?.lowercase()) {
            "cn" -> CN
            "us" -> US
            else -> INTL
        }
    }
}

/** 某条新闻在期内的位置信息（详情页用）。 */
data class ItemContext(
    val issue: Issue,
    val item: NewsItem,
    val parent: NewsItem?,
    val sectionTitle: String,
    val next: NewsItem?,
)

fun Issue.locate(id: String): ItemContext? {
    val flat = mutableListOf<Triple<NewsItem, NewsItem?, String>>()
    fun walk(items: List<NewsItem>, parent: NewsItem?, section: String) {
        items.forEach { flat += Triple(it, parent, section); walk(it.children, it, section) }
    }
    walk(xiaomi?.items.orEmpty(), null, "小米专栏")
    sections.forEach { walk(it.items, null, it.title) }
    val idx = flat.indexOfFirst { it.first.id == id }
    if (idx < 0) return null
    val (item, parent, section) = flat[idx]
    val next = flat.drop(idx + 1).firstOrNull { !it.first.group && it.second == null }?.first
    return ItemContext(this, item, parent, section, next)
}
