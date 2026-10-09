package com.wikg.aidaily.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * data/watchlist.json（schema §5）：可关注的 harness 产品与人物 / 官方账号。
 * 低频变化；字段只增不改，未知字段忽略；缺字段时给默认值，绝不因清单格式问题崩溃。
 */
@Serializable
data class Watchlist(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("updated_at") val updatedAt: String? = null,
    val products: List<WatchProduct> = emptyList(),
    val people: List<WatchPerson> = emptyList(),
) {
    private val productMap: Map<String, WatchProduct> by lazy {
        products.mapNotNull { p -> normId(p.id)?.let { it to p } }.toMap()
    }
    private val personMap: Map<String, WatchPerson> by lazy {
        people.mapNotNull { p -> normId(p.id)?.let { it to p } }.toMap()
    }

    fun product(id: String?): WatchProduct? = normId(id)?.let { productMap[it] }
    fun person(id: String?): WatchPerson? = normId(id)?.let { personMap[it] }

    /** 设置页：去掉 id 为空的脏数据，按清单顺序。 */
    val validProducts: List<WatchProduct> get() = products.filter { normId(it.id) != null }
    val individuals: List<WatchPerson> get() = people.filter { normId(it.id) != null && !it.isOfficial }
    val officials: List<WatchPerson> get() = people.filter { normId(it.id) != null && it.isOfficial }

    companion object {
        val EMPTY = Watchlist()
    }
}

@Serializable
data class WatchProduct(
    val id: String = "",
    val name: String = "",
    val vendor: String = "",
    val region: String = "intl",
    /** 目前固定 harness；不认识的值按 harness 处理。 */
    val kind: String = "harness",
    val url: String? = null,
) {
    val displayName: String get() = name.ifBlank { id }
    val regionKind: Region get() = Region.of(region)
    val safeUrl: String? get() = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

@Serializable
data class WatchPerson(
    val id: String = "",
    val name: String = "",
    val handle: String = "",
    val org: String = "",
    val role: String = "",
    val region: String = "intl",
    /** person 个人 / official 官方号；未知值按个人处理。 */
    val kind: String = "person",
    val url: String? = null,
) {
    val isOfficial: Boolean get() = kind.trim().equals("official", ignoreCase = true)
    val displayName: String get() = name.ifBlank { handle.ifBlank { id } }
    val displayHandle: String get() = handle.ifBlank { id }.removePrefix("@")
    val regionKind: Region get() = Region.of(region)
    val orgRole: String get() = listOf(org, role).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
    val safeUrl: String? get() = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: displayHandle.takeIf { it.isNotBlank() }?.let { "https://x.com/$it" }
}

// ---------------------------------------------------------------------------
// 关注匹配（schema §3 / §5）
// 条目产品集 = {product} ∪ products，人物集 = {person} ∪ people；
// 命中任一已关注 id 即算，跨栏目、子条目同规则；清单里没有的 id 不影响匹配，也绝不崩溃。
// 与按 vendor 的厂商专栏互相独立。
// ---------------------------------------------------------------------------

/** 用户的关注（只存 id）。 */
data class Follows(
    val products: Set<String> = emptySet(),
    val people: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = products.isEmpty() && people.isEmpty()

    companion object {
        fun of(products: Collection<String>, people: Collection<String>) = Follows(
            products.mapNotNull { normId(it) }.toSet(),
            people.mapNotNull { normId(it) }.toSet(),
        )
    }
}

fun NewsItem.matches(f: Follows): Boolean =
    (f.products.isNotEmpty() && productIds.any { it in f.products }) ||
        (f.people.isNotEmpty() && personIds.any { it in f.people })

/** 条目本身或任一子孙命中。 */
fun NewsItem.matchesDeep(f: Follows): Boolean = matches(f) || children.any { it.matchesDeep(f) }

/** 一条命中结果及其来源栏目 id（用于选择渲染样式）。 */
data class FollowHit(val item: NewsItem, val sectionId: String)

/**
 * 从一组条目里挑出命中关注的条目：父条目命中则整条（连同子条目）收录，不再下钻；
 * group 容器本身不带字段时继续看子条目；按 id 去重。
 */
fun collectFollowed(items: List<NewsItem>, f: Follows, seen: MutableSet<String> = HashSet()): List<NewsItem> {
    if (f.isEmpty) return emptyList()
    val out = mutableListOf<NewsItem>()
    fun markAll(item: NewsItem) { seen += item.id; item.children.forEach(::markAll) }
    fun walk(list: List<NewsItem>) {
        list.forEach { item ->
            if (item.id in seen) return@forEach
            if (item.matches(f)) { out += item; markAll(item) } else walk(item.children)
        }
    }
    walk(items)
    return out
}

/** 整期（xiaomi.items + 全部栏目）里命中关注的条目，带来源栏目，跨栏目去重。 */
fun Issue.followedHits(f: Follows): List<FollowHit> {
    if (f.isEmpty) return emptyList()
    val seen = HashSet<String>()
    val out = mutableListOf<FollowHit>()
    collectFollowed(xiaomi?.items.orEmpty(), f, seen).forEach { out += FollowHit(it, "xiaomi") }
    sections.forEach { s -> collectFollowed(s.items, f, seen).forEach { out += FollowHit(it, s.id) } }
    return out
}

object SectionIds {
    const val RELEASES = "releases"
    const val PEOPLE = "people"
}

/** 清洗关注 id 列表：规范化、去重、保持顺序（清单里没有的 id 保留，不自动删除）。 */
fun normalizeFollowIds(list: List<String>): List<String> {
    val seen = LinkedHashSet<String>()
    list.forEach { s -> normId(s)?.take(64)?.let { seen += it } }
    return seen.toList()
}
