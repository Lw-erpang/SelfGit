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
    data object Rule : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^[-*+]\\s+(.*)$")
private val ORDERED = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
private val RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")

private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)\\s]+\\)")

private fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    // README 常含徽章/截图图片语法（![alt](url)）：无图渲染时降级为 alt 文本，
    // 避免 "![" 残留；链接 [label](url) 仍走行内样式。
    val cleaned = src.replace(IMAGE, "$1 ")
    val lines = cleaned.replace("\r\n", "\n").replace('\r', '\n').split('\n')
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
    remember(text, palette) { buildInline(text, palette) }

private fun buildInline(text: String, palette: SelfGitPalette): AnnotatedString = buildAnnotatedString {
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        color = palette.primary,
        background = palette.primary.copy(alpha = 0.10f)
    )
    var cursor = 0

    INLINE_TOKEN.findAll(text).forEach { match ->
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            // [label](url)
            token.startsWith("[") -> {
                val split = token.indexOf("](")
                val label = token.substring(1, split)
                val url = token.substring(split + 2, token.length - 1)
                if (url.startsWith("http://") || url.startsWith("https://")) {
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
                } else {
                    append(label)
                }
            }

            token.startsWith("`") -> appendStyled(token.trim('`'), codeStyle)
            token.startsWith("**") || token.startsWith("__") ->
                appendStyled(token.drop(2).dropLast(2), SpanStyle(fontWeight = FontWeight.Bold))

            token.startsWith("~~") ->
                appendStyled(token.drop(2).dropLast(2), SpanStyle(textDecoration = TextDecoration.LineThrough))

            else -> appendStyled(token.drop(1).dropLast(1), SpanStyle(fontStyle = FontStyle.Italic))
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun AnnotatedString.Builder.appendStyled(text: String, style: SpanStyle) {
    val start = length
    append(text)
    addStyle(style, start, length)
}
