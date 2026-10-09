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
    /** v1.2：主要 harness 产品 id（watchlist.products[].id），releases 栏目必带。 */
    val product: String? = null,
    /** v1.2：其他相关产品 id。 */
    val products: List<String> = emptyList(),
    /** v1.2：主要人物 / 账号 id（小写 X handle），people 栏目必带。 */
    val person: String? = null,
    /** v1.2：其他相关人物 id。 */
    val people: List<String> = emptyList(),
    /** v1.2：版本号原文，如 0.162.0；releases 栏目必带。 */
    val version: String? = null,
) {
    val regionKind: Region get() = Region.of(region)

    /** 产品集合 = {product} ∪ products（去空白、去重，保持顺序）。 */
    val productIds: Set<String> get() = idSet(product, products)

    /** 人物集合 = {person} ∪ people（id 为小写 handle，比较时统一小写）。 */
    val personIds: Set<String> get() = idSet(person, people)

    val versionText: String? get() = version?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun idSet(single: String?, many: List<String>?): Set<String> {
    val out = LinkedHashSet<String>()
    single?.let { normId(it) }?.let { out += it }
    many.orEmpty().forEach { s -> normId(s)?.let { out += it } }
    return out
}

/** id 规范化：去空白、去 @、小写；空串返回 null。 */
fun normId(raw: String?): String? = raw?.trim()?.removePrefix("@")?.lowercase()?.takeIf { it.isNotEmpty() }

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
