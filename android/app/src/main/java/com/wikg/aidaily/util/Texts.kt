package com.wikg.aidaily.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** 唯一的标记是 **粗体**，非贪婪匹配；绝不按 HTML 解析。 */
val BoldRegex = Regex("""\*\*(.+?)\*\*""")

fun stripBold(text: String): String = BoldRegex.replace(text) { it.groupValues[1] }

/** 把文本拆成 (片段, 是否粗体)，纯 Kotlin，便于单元测试。 */
fun splitBold(text: String): List<Pair<String, Boolean>> {
    val out = mutableListOf<Pair<String, Boolean>>()
    var last = 0
    for (m in BoldRegex.findAll(text)) {
        if (m.range.first > last) out += text.substring(last, m.range.first) to false
        out += m.groupValues[1] to true
        last = m.range.last + 1
    }
    if (last < text.length) out += text.substring(last) to false
    return out
}

fun richText(text: String, boldStyle: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold)): AnnotatedString =
    buildAnnotatedString {
        for ((part, bold) in splitBold(text)) {
            if (bold) withStyle(boldStyle) { append(part) } else append(part)
        }
    }
