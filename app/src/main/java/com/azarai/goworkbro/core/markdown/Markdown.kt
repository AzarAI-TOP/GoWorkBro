package com.azarai.goworkbro.core.markdown

/** Minimal Markdown model — covers the subset the news generator emits. */
sealed class MdBlock {
    data class Heading(val level: Int, val inline: List<MdInline>) : MdBlock()
    data class Paragraph(val inline: List<MdInline>) : MdBlock()
    data class Bullet(val indent: Int, val inline: List<MdInline>) : MdBlock()
    data class Ordered(val indent: Int, val marker: String, val inline: List<MdInline>) : MdBlock()
    data class Quote(val inline: List<MdInline>) : MdBlock()
    data class CodeBlock(val lines: List<String>) : MdBlock()
    data object Rule : MdBlock()
}

sealed class MdInline {
    data class Text(val text: String) : MdInline()
    data class Bold(val text: String) : MdInline()
    data class Italic(val text: String) : MdInline()
    data class Strike(val text: String) : MdInline()
    data class Code(val text: String) : MdInline()
    data class Link(val label: String, val url: String) : MdInline()
    data object Break : MdInline()
}

object MarkdownParser {

    /** Parses markdown (frontmatter already stripped by the news layer). */
    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()
        var quote: MdBlock.Quote? = null
        var inCode = false
        val codeLines = mutableListOf<String>()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            paragraph.setLength(0)
            if (text.isNotEmpty()) blocks += MdBlock.Paragraph(parseInline(text))
        }

        fun flushQuote() {
            quote?.let { blocks += it }
            quote = null
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            if (inCode) {
                if (trimmed.startsWith("```")) {
                    inCode = false
                    blocks += MdBlock.CodeBlock(codeLines.toList())
                    codeLines.clear()
                } else {
                    codeLines += line
                }
                return@forEach
            }

            when {
                trimmed.isEmpty() -> {
                    flushParagraph()
                    flushQuote()
                }
                trimmed.startsWith("```") -> {
                    flushParagraph()
                    flushQuote()
                    inCode = true
                }
                trimmed.startsWith("#") -> {
                    flushParagraph()
                    flushQuote()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val text = trimmed.dropWhile { it == '#' }.trim()
                    if (text.isNotEmpty()) {
                        blocks += MdBlock.Heading(level, parseInline(text))
                    }
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    flushParagraph()
                    flushQuote()
                    blocks += MdBlock.Rule
                }
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    flushParagraph()
                    val content = parseInline(trimmed.removePrefix(">").trim())
                    quote = quote?.let { it.copy(inline = it.inline + MdInline.Break + content) }
                        ?: MdBlock.Quote(content)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    flushQuote()
                    blocks += MdBlock.Bullet(
                        indent / 2,
                        parseInline(trimmed.substring(2).trim()),
                    )
                }
                ORDERED.matches(trimmed) -> {
                    flushParagraph()
                    flushQuote()
                    val match = ORDERED.find(trimmed)!!
                    blocks += MdBlock.Ordered(
                        indent / 2,
                        match.groupValues[1],
                        parseInline(trimmed.substring(match.value.length).trim()),
                    )
                }
                else -> {
                    flushQuote()
                    val last = blocks.lastOrNull()
                    val isListContinuation = paragraph.isEmpty() && indent >= listItemIndent(last)
                    if (isListContinuation && last is MdBlock.Bullet) {
                        blocks[blocks.lastIndex] =
                            last.copy(inline = last.inline + MdInline.Break + parseInline(trimmed))
                    } else if (isListContinuation && last is MdBlock.Ordered) {
                        blocks[blocks.lastIndex] =
                            last.copy(inline = last.inline + MdInline.Break + parseInline(trimmed))
                    } else {
                        if (paragraph.isNotEmpty()) paragraph.append(' ')
                        paragraph.append(trimmed)
                    }
                }
            }
        }
        if (inCode) blocks += MdBlock.CodeBlock(codeLines.toList())
        flushParagraph()
        flushQuote()
        return blocks
    }

    /** Column where an item's content starts: nesting indent plus the list marker. */
    private fun listItemIndent(block: MdBlock?): Int = when (block) {
        is MdBlock.Bullet -> block.indent * 2 + 2
        is MdBlock.Ordered -> block.indent * 2 + 2
        else -> Int.MAX_VALUE
    }

    private val ORDERED = Regex("""^(\d{1,2})[.)]\s+""")

    /** Characters that terminate a bare URL run (trailing ASCII punctuation is trimmed back). */
    private const val URL_END = "），。、；！？》】〉」』>) \t"

    /** Parses **bold**, *italic*, ~~strike~~, `code`, [label](url) and bare URLs. */
    fun parseInline(text: String): List<MdInline> {
        val out = mutableListOf<MdInline>()
        var plain = StringBuilder()
        var i = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out += MdInline.Text(plain.toString())
                plain = StringBuilder()
            }
        }

        fun emit(piece: MdInline) {
            flushPlain()
            out += piece
        }

        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        emit(MdInline.Bold(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        plain.append(c); i++
                    }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > 0) {
                        emit(MdInline.Strike(text.substring(i + 2, end)))
                        i = end + 2
                    } else {
                        plain.append(c); i++
                    }
                }
                c == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1 && !text[i + 1].isWhitespace() && !text[end - 1].isWhitespace()) {
                        emit(MdInline.Italic(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        plain.append(c); i++
                    }
                }
                c == '_' -> {
                    val prev = if (i == 0) ' ' else text[i - 1]
                    val end = text.indexOf('_', i + 1)
                    val next = text.getOrNull(end + 1)
                    val intraword = prev.isLetterOrDigit() || (next?.isLetterOrDigit() == true)
                    if (end > i + 1 && !intraword && !text[i + 1].isWhitespace()) {
                        emit(MdInline.Italic(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        plain.append(c); i++
                    }
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > 0) {
                        emit(MdInline.Code(text.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        plain.append(c); i++
                    }
                }
                c == '[' -> {
                    val labelEnd = text.indexOf(']', i)
                    if (labelEnd > 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                        val urlEnd = text.indexOf(')', labelEnd + 2)
                        if (urlEnd > 0) {
                            emit(
                                MdInline.Link(
                                    text.substring(i + 1, labelEnd),
                                    text.substring(labelEnd + 2, urlEnd),
                                ),
                            )
                            i = urlEnd + 1
                        } else {
                            plain.append(c); i++
                        }
                    } else {
                        plain.append(c); i++
                    }
                }
                text.startsWith("http://", i) || text.startsWith("https://", i) -> {
                    var end = i
                    while (end < text.length && text[end] !in URL_END) end++
                    while (end > i && text[end - 1] in ".,;:!?") end--
                    val url = text.substring(i, end)
                    emit(MdInline.Link(url, url))
                    i = end
                }
                else -> {
                    plain.append(c); i++
                }
            }
        }
        flushPlain()
        return out
    }
}
