package com.azarai.goworkbro.core.markdown

/** Minimal Markdown model — covers the subset the news generator emits. */
sealed class MdBlock {
    data class Heading(val level: Int, val inline: List<MdInline>) : MdBlock()
    data class Paragraph(val inline: List<MdInline>) : MdBlock()
    data class Bullet(val indent: Int, val inline: List<MdInline>) : MdBlock()
    data class Ordered(val indent: Int, val marker: String, val inline: List<MdInline>) : MdBlock()
    data class Quote(val inline: List<MdInline>) : MdBlock()
    data object Rule : MdBlock()
}

sealed class MdInline {
    data class Text(val text: String) : MdInline()
    data class Bold(val text: String) : MdInline()
    data class Link(val label: String, val url: String) : MdInline()
}

object MarkdownParser {

    /** Parses markdown (frontmatter already stripped by the news layer). */
    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            paragraph.setLength(0)
            if (text.isNotEmpty()) blocks += MdBlock.Paragraph(parseInline(text))
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            when {
                trimmed.isEmpty() -> flushParagraph()
                trimmed.startsWith("#") -> {
                    flushParagraph()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val text = trimmed.dropWhile { it == '#' }.trim()
                    if (text.isNotEmpty()) {
                        blocks += MdBlock.Heading(level, parseInline(text))
                    }
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                }
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    flushParagraph()
                    blocks += MdBlock.Quote(parseInline(trimmed.removePrefix(">").trim()))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    blocks += MdBlock.Bullet(
                        indent / 2,
                        parseInline(trimmed.substring(2).trim()),
                    )
                }
                ORDERED.matches(trimmed) -> {
                    flushParagraph()
                    val match = ORDERED.find(trimmed)!!
                    blocks += MdBlock.Ordered(
                        indent / 2,
                        match.groupValues[1],
                        parseInline(trimmed.substring(match.value.length).trim()),
                    )
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        flushParagraph()
        return blocks
    }

    private val ORDERED = Regex("""^(\d{1,2})[.)]\s+""")

    /** Parses **bold** and [label](url) inline markup. */
    fun parseInline(text: String): List<MdInline> {
        val out = mutableListOf<MdInline>()
        var plain = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        if (plain.isNotEmpty()) {
                            out += MdInline.Text(plain.toString()); plain = StringBuilder()
                        }
                        out += MdInline.Bold(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        plain.append(text[i]); i++
                    }
                }
                text[i] == '[' -> {
                    val labelEnd = text.indexOf(']', i)
                    if (labelEnd > 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                        val urlEnd = text.indexOf(')', labelEnd + 2)
                        if (urlEnd > 0) {
                            if (plain.isNotEmpty()) {
                                out += MdInline.Text(plain.toString()); plain = StringBuilder()
                            }
                            out += MdInline.Link(
                                text.substring(i + 1, labelEnd),
                                text.substring(labelEnd + 2, urlEnd),
                            )
                            i = urlEnd + 1
                        } else {
                            plain.append(text[i]); i++
                        }
                    } else {
                        plain.append(text[i]); i++
                    }
                }
                else -> {
                    plain.append(text[i]); i++
                }
            }
        }
        if (plain.isNotEmpty()) out += MdInline.Text(plain.toString())
        return out
    }
}
