package com.azarai.goworkbro

import com.azarai.goworkbro.core.markdown.MarkdownParser
import com.azarai.goworkbro.core.markdown.MdBlock
import com.azarai.goworkbro.core.markdown.MdInline
import com.azarai.goworkbro.core.news.GistNews
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.todo.buildCombinedList
import com.azarai.goworkbro.ui.todo.mapReorder
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatesTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `logical date carries over before 4am in late-night mode`() {
        // 2026-09-01 01:00 with last rollover at 08-31 -> still 08-31
        val key = Dates.logicalDateKey(at(2026, 9, 1, 1), lateNightModeEnabled = true, lastRolloverDate = "2026-08-31")
        assertEquals("2026-08-31", key)
    }

    @Test
    fun `logical date does not carry over when already rolled`() {
        // Calendar already flipped to 09-01; 01:00 stays on 09-01 (never moves back)
        val key = Dates.logicalDateKey(at(2026, 9, 1, 1), lateNightModeEnabled = true, lastRolloverDate = "2026-09-01")
        assertEquals("2026-09-01", key)
    }

    @Test
    fun `late-night mode disabled keeps calendar date`() {
        val key = Dates.logicalDateKey(at(2026, 9, 1, 2), lateNightModeEnabled = false, lastRolloverDate = "2026-08-31")
        assertEquals("2026-09-01", key)
    }

    @Test
    fun `boundary at 4am sharp stops carrying over`() {
        val key = Dates.logicalDateKey(at(2026, 9, 1, 4), lateNightModeEnabled = true, lastRolloverDate = "2026-08-31")
        assertEquals("2026-09-01", key)
    }

    @Test
    fun `sleep before noon belongs to current row`() {
        assertEquals("2026-09-01", Dates.sleepRecordDateKey(at(2026, 9, 1, 1)))
        assertEquals("2026-09-01", Dates.sleepRecordDateKey(at(2026, 9, 1, 11, 59)))
    }

    @Test
    fun `sleep from noon onward belongs to next row`() {
        assertEquals("2026-09-02", Dates.sleepRecordDateKey(at(2026, 9, 1, 12)))
        assertEquals("2026-09-02", Dates.sleepRecordDateKey(at(2026, 9, 1, 23)))
    }

    @Test
    fun `future clock time resolves to yesterday`() {
        val resolved = Dates.resolveCheckInDateTime(at(2026, 9, 1, 9), 23, 30)
        assertEquals("2026-08-31", Dates.dateKeyOf(resolved))
    }

    @Test
    fun `formatSeconds formats mmss and hhmmss`() {
        assertEquals("05:09", Dates.formatSeconds(309))
        assertEquals("1:01:05", Dates.formatSeconds(3665))
    }

    @Test
    fun `formatHours wraps past midnight`() {
        assertEquals("01:30", Dates.formatHours(25.5))
    }
}

class GistNewsTest {

    @Test
    fun `parseGist extracts dated markdown files sorted desc`() {
        val payload = JSONObject()
            .put(
                "files",
                JSONObject()
                    .put(
                        "2026-08-28.md",
                        JSONObject().put("content", "# USTC 每日要闻 — 8月28日\n\n- item"),
                    )
                    .put(
                        "2026-08-29.md",
                        JSONObject().put("content", "# USTC 每日要闻 — 8月29日"),
                    )
                    .put("README.md", JSONObject().put("content", "ignore me")),
            )
        val editions = GistNews.parseGist(payload.toString())
        assertEquals(listOf("2026-08-29", "2026-08-28"), editions.map { it.date })
        assertEquals("USTC 每日要闻 — 8月29日", editions[0].title)
    }

    @Test
    fun `fromMarkdown strips frontmatter and extracts title`() {
        val raw = "---\ndate: 2026-08-28\n---\n# 每日要闻标题\n\n正文"
        val edition = GistNews.fromMarkdown("2026-08-28", raw)
        assertEquals("每日要闻标题", edition.title)
        assertTrue(edition.markdown.startsWith("# 每日要闻标题"))
    }

    @Test
    fun `missing title falls back to default`() {
        val edition = GistNews.fromMarkdown("2026-08-28", "just body text")
        assertEquals("USTC 每日要闻 — 2026-08-28", edition.title)
    }
}

class MarkdownParserTest {

    @Test
    fun `parses headings bullets and rule`() {
        val blocks = MarkdownParser.parse(
            """
            # Title
            ## Section
            - item one
            - item two
            ---
            Plain paragraph.
            """.trimIndent(),
        )
        assertEquals(MdBlock.Heading(1, listOf(MdInline.Text("Title"))), blocks[0])
        assertEquals(MdBlock.Heading(2, listOf(MdInline.Text("Section"))), blocks[1])
        assertTrue(blocks[2] is MdBlock.Bullet)
        assertTrue(blocks[3] is MdBlock.Bullet)
        assertEquals(MdBlock.Rule, blocks[4])
        assertTrue(blocks[5] is MdBlock.Paragraph)
    }

    @Test
    fun `parses bold and links inline`() {
        val inline = MarkdownParser.parseInline("hello **world** and [USTC](https://ustc.edu.cn)!")
        assertEquals(
            listOf(
                MdInline.Text("hello "),
                MdInline.Bold("world"),
                MdInline.Text(" and "),
                MdInline.Link("USTC", "https://ustc.edu.cn"),
                MdInline.Text("!"),
            ),
            inline,
        )
    }

    @Test
    fun `multi-line paragraph is joined`() {
        val blocks = MarkdownParser.parse("line one\nline two")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals(listOf(MdInline.Text("line one line two")), paragraph.inline)
    }

    @Test
    fun `indented continuation line joins its list item`() {
        val blocks = MarkdownParser.parse(
            "- **报名截止** 7月15日\n" +
                "  → https://www.teach.ustc.edu.cn/notice/notice-teaching/20291.html\n" +
                "- second",
        )
        assertEquals(2, blocks.size)
        val item = blocks[0] as MdBlock.Bullet
        assertEquals(0, item.indent)
        assertEquals(
            listOf(
                MdInline.Bold("报名截止"),
                MdInline.Text(" 7月15日"),
                MdInline.Break,
                MdInline.Text("→ "),
                MdInline.Link(
                    "https://www.teach.ustc.edu.cn/notice/notice-teaching/20291.html",
                    "https://www.teach.ustc.edu.cn/notice/notice-teaching/20291.html",
                ),
            ),
            item.inline,
        )
    }

    @Test
    fun `bare urls autolink with trailing punctuation trimmed`() {
        val inline = MarkdownParser.parseInline("详见 https://ustc.edu.cn/a.html。下句")
        assertEquals(
            listOf(
                MdInline.Text("详见 "),
                MdInline.Link("https://ustc.edu.cn/a.html", "https://ustc.edu.cn/a.html"),
                MdInline.Text("。下句"),
            ),
            inline,
        )
    }

    @Test
    fun `parses italic strike and inline code`() {
        val inline = MarkdownParser.parseInline("*重点* ~~过期~~ `code`")
        assertEquals(
            listOf(
                MdInline.Italic("重点"),
                MdInline.Text(" "),
                MdInline.Strike("过期"),
                MdInline.Text(" "),
                MdInline.Code("code"),
            ),
            inline,
        )
    }

    @Test
    fun `parses fenced code block`() {
        val blocks = MarkdownParser.parse("before\n```\nval x = 1\n```\nafter")
        assertEquals(MdBlock.Paragraph(listOf(MdInline.Text("before"))), blocks[0])
        assertEquals(MdBlock.CodeBlock(listOf("val x = 1")), blocks[1])
        assertEquals(MdBlock.Paragraph(listOf(MdInline.Text("after"))), blocks[2])
    }

    @Test
    fun `consecutive quote lines merge into one block`() {
        val blocks = MarkdownParser.parse("> 一\n> 二\n\n正文")
        val quote = blocks[0] as MdBlock.Quote
        assertEquals(listOf(MdInline.Text("一"), MdInline.Break, MdInline.Text("二")), quote.inline)
        assertEquals(MdBlock.Paragraph(listOf(MdInline.Text("正文"))), blocks[1])
    }
}
