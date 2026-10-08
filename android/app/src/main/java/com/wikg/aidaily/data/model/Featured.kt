package com.wikg.aidaily.data.model

/**
 * 「关注」专栏：用户自定义的厂商列表（默认只有「小米」）。
 *
 * 纯客户端逻辑，不需要改数据格式：每个关注的厂商从整期（schema 的 `xiaomi.items` + 全部 `sections`）里
 * 深度优先收集 vendor 匹配的条目。匹配不区分大小写，并通过一张小别名表把同一家公司的常见叫法 / 主要产品
 * 归到一起（小米 ↔ Xiaomi ↔ Mi ↔ 澎湃OS，Anthropic ↔ Claude Code，微软 ↔ Microsoft ↔ GitHub Copilot ……）。
 */
object VendorMatcher {
    /** 每组第一个是展示用的规范名。全部小写比较。 */
    val aliasGroups: List<List<String>> = listOf(
        listOf("小米", "xiaomi", "mi", "redmi", "poco", "小爱", "小爱同学", "xiaoai", "澎湃", "澎湃os", "hyperos", "mimo", "小米汽车", "xiaomi mimo"),
        listOf("Anthropic", "claude", "claude code", "claude.ai"),
        listOf("OpenAI", "chatgpt", "codex", "openai codex", "sora", "gpt"),
        listOf("Google", "谷歌", "gemini", "gemini cli", "deepmind", "google deepmind", "jules", "android studio"),
        listOf("DeepSeek", "深度求索"),
        listOf("通义/阿里", "通义", "通义千问", "千问", "qwen", "阿里", "阿里巴巴", "阿里云", "alibaba", "aliyun", "百炼"),
        listOf("豆包", "doubao", "字节", "字节跳动", "bytedance", "火山引擎", "volcengine", "trae", "seed"),
        listOf("Kimi", "月之暗面", "moonshot", "moonshot ai"),
        listOf("智谱", "zhipu", "智谱ai", "glm", "chatglm", "z.ai", "zai"),
        listOf("Meta", "llama", "meta ai"),
        listOf("Microsoft", "微软", "github", "github copilot", "copilot", "vs code", "vscode", "azure"),
        listOf("Cursor", "anysphere"),
        listOf("xAI", "grok"),
        listOf("腾讯", "tencent", "混元", "hunyuan", "元宝", "codebuddy"),
        listOf("百度", "baidu", "文心", "文心一言", "ernie"),
        listOf("MiniMax", "稀宇", "海螺"),
        listOf("阶跃星辰", "stepfun", "阶跃"),
        listOf("NVIDIA", "英伟达"),
        listOf("Apple", "苹果"),
        listOf("Mistral", "mistral ai"),
    )

    /** 设置页的推荐厂商。 */
    val presets: List<String> = listOf(
        "小米", "Anthropic", "OpenAI", "Google", "DeepSeek", "通义/阿里", "豆包", "Kimi", "智谱",
        "Meta", "Microsoft", "Cursor", "xAI", "腾讯", "MiniMax", "阶跃星辰",
    )

    private val groupOf: Map<String, Int> = buildMap {
        aliasGroups.forEachIndexed { i, g -> g.forEach { put(norm(it), i) } }
    }

    fun norm(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun hasCjk(s: String) = s.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    /** 关注名展开后的全部别名（含自身）。 */
    fun aliasesOf(name: String): Set<String> {
        val n = norm(name)
        val keys = n.split('/').map { it.trim() }.filter { it.isNotEmpty() } + n
        val out = mutableSetOf<String>()
        keys.forEach { k ->
            out += k
            groupOf[k]?.let { gi -> aliasGroups[gi].forEach { out += norm(it) } }
        }
        return out
    }

    fun isXiaomi(name: String): Boolean = "xiaomi" in aliasesOf(name)

    /** 别名表里的规范名（没有则原样返回）。 */
    fun canonical(name: String): String = groupOf[norm(name)]?.let { aliasGroups[it].first() } ?: name.trim()

    /**
     * item.vendor 是否属于关注名 [aliases]：整串相等；或按空格 / 斜杠 / 中点切开后任一词相等
     * （"GitHub Copilot" → github）；或中文别名（≥2 字）作前缀（"小米汽车" → 小米）。
     */
    fun matches(vendor: String, aliases: Set<String>): Boolean {
        val v = norm(vendor)
        if (v.isEmpty()) return false
        if (v in aliases) return true
        val tokens = v.split(' ', '/', '·', '・', '|', '(', ')', '（', '）').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.any { it in aliases }) return true
        return aliases.any { a -> a.length >= 2 && hasCjk(a) && v.startsWith(a) }
    }
}

data class FeaturedColumn(
    val vendor: String,
    val items: List<NewsItem>,
    val emptyText: String,
    val isXiaomi: Boolean,
) {
    val key: String get() = "f-" + VendorMatcher.norm(vendor)
    /** 新闻条数（不含 group 容器，含子条目）。 */
    val count: Int get() = items.sumOf { it.newsCount() }
}

internal fun NewsItem.newsCount(): Int = (if (group) 0 else 1) + children.sumOf { it.newsCount() }

/**
 * 收集某个关注厂商在本期的全部条目：
 * - 关注小米时，schema 的 `xiaomi.items` 整体优先放在最前；
 * - 再深度优先遍历所有栏目：父条目匹配则整条（连同子条目）收录，不再下钻；否则继续看子条目；
 * - 按 id 去重（含已收录条目内部的子条目 id）。
 */
fun Issue.featuredColumn(vendor: String): FeaturedColumn {
    val aliases = VendorMatcher.aliasesOf(vendor)
    val mi = VendorMatcher.isXiaomi(vendor)
    val seen = HashSet<String>()
    val out = mutableListOf<NewsItem>()
    fun markAll(item: NewsItem) { seen += item.id; item.children.forEach(::markAll) }
    fun take(item: NewsItem) { if (item.id !in seen) { out += item; markAll(item) } }

    if (mi) xiaomi?.items.orEmpty().forEach(::take)

    fun walk(items: List<NewsItem>) {
        items.forEach { item ->
            if (item.id in seen) return@forEach
            if (VendorMatcher.matches(item.vendor, aliases)) take(item) else walk(item.children)
        }
    }
    if (!mi) walk(xiaomi?.items.orEmpty())
    sections.forEach { walk(it.items) }

    val label = vendor.trim()
    val empty = (if (mi) xiaomi?.emptyText?.takeIf { it.isNotBlank() } else null) ?: "本期未收录${label}相关动态"
    return FeaturedColumn(label, out, empty, mi)
}

/** 本期出现过的厂商（按条目数降序），设置页做推荐用。 */
fun Issue.vendorCounts(): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Pair<String, Int>>()
    allItems().filter { !it.group && it.vendor.isNotBlank() }.forEach {
        val k = VendorMatcher.norm(it.vendor)
        val cur = counts[k]
        counts[k] = (cur?.first ?: it.vendor.trim()) to ((cur?.second ?: 0) + 1)
    }
    return counts.values.sortedByDescending { it.second }
}

/** 清洗用户输入的关注列表：去空白、去重（不区分大小写）、限长。 */
fun normalizeFeatured(list: List<String>): List<String> {
    val seen = HashSet<String>()
    return list.map { it.trim().take(24) }.filter { it.isNotEmpty() && seen.add(VendorMatcher.norm(it)) }
}
