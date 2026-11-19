package com.selfgit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfgit.app.ui.theme.LocalPalette
import com.selfgit.app.ui.theme.SelfGitPalette

/**
 * 轻量 Markdown 渲染（Release 更新说明用），不引入第三方依赖。
 * 支持：# 标题、- / * / + 无序列表（含 [ ] / [x] 任务项）、1. 有序列表、
 * ``` 代码块、> 引用、--- 分割线，以及 **粗体** *斜体* ~~删除线~~ `行内代码` [链接](url)。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val palette = LocalPalette.current
    val baseColor = color ?: palette.textSecondary
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 17.sp
                        2 -> 15.5.sp
                        3 -> 14.5.sp
                        else -> 13.5.sp
                    }
                    Text(
                        text = inline(block.text, palette),
                        color = palette.textPrimary,
                        fontSize = size,
                        lineHeight = size * 1.45f,
                        fontWeight = FontWeight.Bold
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = inline(block.text, palette),
                    color = baseColor,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )

                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width((block.indent * 14).dp))
                    if (block.checkbox != null) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (block.checkbox) palette.primary.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (block.checkbox) palette.primary.copy(alpha = 0.55f)
                                    else palette.textTertiary.copy(alpha = 0.6f),
                                    RoundedCornerShape(3.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (block.checkbox) {
                                Text("✓", fontSize = 7.sp, color = palette.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                    } else {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.primary.copy(alpha = 0.75f))
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(
                        text = inline(block.text, palette),
                        color = baseColor,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }

                is MdBlock.Ordered -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = "${block.number}.",
                        color = palette.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = inline(block.text, palette),
                        color = baseColor,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(palette.primary.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = inline(block.text, palette),
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 18.sp
                    )
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.fieldFill)
                        .border(1.dp, palette.fieldBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = block.code,
                        color = palette.textSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }

                is MdBlock.Table -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, palette.fieldBorder, RoundedCornerShape(9.dp))
                ) {
                    val cols = block.header.size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.fieldFill)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        block.header.forEach { cell ->
                            Text(
                                text = inline(cell, palette),
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    block.rows.forEachIndexed { rIdx, row ->
                        HairLine()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (rIdx % 2 == 1) palette.fieldFill.copy(alpha = 0.45f) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            repeat(cols) { c ->
                                Text(
                                    text = inline(row.getOrElse(c) { "" }, palette),
                                    color = baseColor,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                MdBlock.Rule -> HairLine()
            }
        }
    }
}

// ---- 解析 ----

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val indent: Int, val text: String, val checkbox: Boolean?) : MdBlock
    data class Ordered(val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^[-*+]\\s+(.*)$")
private val ORDERED = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
private val RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val TABLE_SEP = Regex("^\\|?[\\s:|-]+\\|?$")

private fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split('|').map { it.trim() }
}

private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)\\s]+\\)")
private val HTML_IMG = Regex("<img[^>]*?alt\\s*=\\s*\"([^\"]*)\"[^>]*/?>", RegexOption.IGNORE_CASE)
private val HTML_A = Regex("<a[^>]*?href\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("</?[a-zA-Z][^>]*>")
private val HTML_ENTITY = Regex("&(amp|lt|gt|quot|nbsp);")

private fun cleanHtml(src: String): String {
    var s = src
    // <img alt="x"> → alt 文本（无 alt 则移除）
    s = HTML_IMG.replace(s) { m -> m.groupValues[1].trim() }
    // <a href="url">text</a> → [text](url)，交给链接渲染
    s = HTML_A.replace(s) { m -> "[${m.groupValues[2]}](${m.groupValues[1]})" }
    // 其余标签（<p align=...>、<br>、<div>、徽章 span 等）直接移除
    s = HTML_TAG.replace(s, " ")
    // 常见 HTML 实体
    s = HTML_ENTITY.replace(s) { m ->
        when (m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; else -> " "
        }
    }
    return s.replace(IMAGE, "$1 ")
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    // README 常含内联 HTML（居中段落、徽章 img、<a> 链接）：降级为纯 Markdown 再解析。
    val cleaned = cleanHtml(src.replace("\r\n", "\n").replace('\r', '\n'))
    val lines = cleaned.split('\n')
    val para = StringBuilder()

    fun flush() {
        if (para.isNotBlank()) {
            out += MdBlock.Paragraph(para.toString().trim())
            para.setLength(0)
        }
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()
        when {
            // 代码块
            trimmed.startsWith("```") -> {
                flush()
                val lang = trimmed.removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body.appendLine(lines[i])
                    i++
                }
                out += MdBlock.Code(lang, body.toString().trimEnd())
            }

            trimmed.isEmpty() -> flush()

            RULE.matches(trimmed) -> {
                flush()
                out += MdBlock.Rule
            }

            HEADING.matches(trimmed) -> {
                flush()
                val m = HEADING.find(trimmed)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }

            trimmed.startsWith(">") -> {
                flush()
                out += MdBlock.Quote(trimmed.trimStart('>').trim())
            }

            BULLET.matches(trimmed) -> {
                flush()
                val indent = (raw.length - raw.trimStart().length) / 2
                var content = BULLET.find(trimmed)!!.groupValues[1].trim()
                val checkbox: Boolean? = when {
                    content.startsWith("[ ]") -> {
                        content = content.removePrefix("[ ]").trim(); false
                    }

                    content.startsWith("[x]") || content.startsWith("[X]") -> {
                        content = content.removePrefix("[x]").removePrefix("[X]").trim(); true
                    }

                    else -> null
                }
                out += MdBlock.Bullet(indent, content, checkbox)
            }

            ORDERED.matches(trimmed) -> {
                flush()
                val m = ORDERED.find(trimmed)!!
                out += MdBlock.Ordered(m.groupValues[1], m.groupValues[2].trim())
            }

            // 表格：本行以 | 开头，且下一行是 |---|---| 分隔行
            trimmed.startsWith("|") && i + 1 < lines.size &&
                lines[i + 1].trim().let { it.contains('-') && TABLE_SEP.matches(it) } -> {
                flush()
                val header = splitTableRow(trimmed)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += splitTableRow(lines[i])
                    i++
                }
                out += MdBlock.Table(header, rows)
                continue
            }

            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(trimmed)
            }
        }
        i++
    }
    flush()
    return out
}

// ---- 行内样式 ----

private val INLINE_TOKEN = Regex(
    "(\\[[^\\]]+\\]\\([^)\\s]+\\))|(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(__[^_]+__)|(~~[^~]+~~)|(\\*[^*\\s][^*]*\\*)|(_[^_\\s][^_]*_)"
)

@Composable
private fun inline(text: String, palette: SelfGitPalette): AnnotatedString =
    remember(text, palette) { buildAnnotatedString { appendTokens(text, palette, emptyList(), 0) } }

/**
 * 递归解析行内 token，outerStyles 为外层累积的样式（如「粗体里套链接」「粗体套斜体」）。
 * 链接保持可点击，同时叠加外层样式；depth 限制防病态输入。
 */
private fun AnnotatedString.Builder.appendTokens(
    text: String,
    palette: SelfGitPalette,
    outerStyles: List<SpanStyle>,
    depth: Int
) {
    if (depth > 4) {
        appendWithStyles(text, outerStyles)
        return
    }
    var cursor = 0

    INLINE_TOKEN.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            appendWithStyles(text.substring(cursor, match.range.first), outerStyles)
        }
        val token = match.value
        when {
            // [label](url)
            token.startsWith("[") -> {
                val split = token.indexOf("](")
                val label = token.substring(1, split)
                val url = token.substring(split + 2, token.length - 1)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val start = length
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(
                                style = SpanStyle(
                                    color = palette.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) { append(label) }
                    // 外层样式（粗体等）叠加到链接文字上
                    outerStyles.forEach { addStyle(it, start, length) }
                } else {
                    appendWithStyles(label, outerStyles)
                }
            }

            token.startsWith("`") ->
                appendWithStyles(
                    token.trim('`'),
                    outerStyles + SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = palette.primary,
                        background = palette.primary.copy(alpha = 0.10f)
                    )
                )

            token.startsWith("**") || token.startsWith("__") ->
                appendTokens(
                    token.drop(2).dropLast(2), palette,
                    outerStyles + SpanStyle(fontWeight = FontWeight.Bold), depth + 1
                )

            token.startsWith("~~") ->
                appendTokens(
                    token.drop(2).dropLast(2), palette,
                    outerStyles + SpanStyle(textDecoration = TextDecoration.LineThrough), depth + 1
                )

            else ->
                appendTokens(
                    token.drop(1).dropLast(1), palette,
                    outerStyles + SpanStyle(fontStyle = FontStyle.Italic), depth + 1
                )
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) appendWithStyles(text.substring(cursor), outerStyles)
}

private fun AnnotatedString.Builder.appendWithStyles(text: String, styles: List<SpanStyle>) {
    val start = length
    append(text)
    styles.forEach { addStyle(it, start, length) }
}
