package com.wikg.aidaily.ui.update

/** 发布说明的「轻 Markdown」：标题 / 列表 / 段落 + **粗体**；链接只留文字，代码反引号去掉。纯 Kotlin，便于测试。 */
sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Bullet(val text: String) : NoteBlock
    data class Para(val text: String) : NoteBlock
}

private val LINK = Regex("""\[([^\]]+)]\((?:https?://)[^)]*\)""")
private val ORDERED = Regex("""^\d{1,2}[.)]\s+""")

fun cleanInline(s: String): String = LINK.replace(s) { it.groupValues[1] }.replace("`", "").trim()

fun parseNotes(raw: String, maxBlocks: Int = 60): List<NoteBlock> {
    val out = mutableListOf<NoteBlock>()
    for (line0 in raw.replace("\r\n", "\n").split('\n')) {
        val line = line0.trim()
        if (line.isEmpty() || line.matches(Regex("^[-*_]{3,}$"))) continue
        out += when {
            line.startsWith("#") -> NoteBlock.Heading(cleanInline(line.trimStart('#')).removeSurrounding("**"))
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") || line.startsWith("+ ") ->
                NoteBlock.Bullet(cleanInline(line.substring(2)))
            ORDERED.containsMatchIn(line) -> NoteBlock.Bullet(cleanInline(line.replaceFirst(ORDERED, "")))
            else -> NoteBlock.Para(cleanInline(line))
        }
        if (out.size >= maxBlocks) break
    }
    return out.filter {
        when (it) { is NoteBlock.Heading -> it.text.isNotBlank(); is NoteBlock.Bullet -> it.text.isNotBlank(); is NoteBlock.Para -> it.text.isNotBlank() }
    }
}
