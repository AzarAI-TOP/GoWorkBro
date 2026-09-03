package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.core.markdown.MarkdownParser
import com.azarai.goworkbro.core.markdown.MdBlock
import com.azarai.goworkbro.core.markdown.MdInline
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.NewsFontFamily

/** Renders the news markdown subset with the LXGW WenKai reading font. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = AppTheme.colors
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier = modifier.padding(contentPadding)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> Heading(block, index, blocks)
                is MdBlock.Paragraph -> Text(
                    text = inlineAnnotated(block.inline, colors.primary),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = NewsFontFamily,
                        lineHeight = 24.sp,
                    ),
                    color = colors.textBody,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
                is MdBlock.Bullet -> {
                    val (top, bottom) = listRunPadding(blocks, index)
                    RowListItem(
                        indent = block.indent,
                        marker = null,
                        text = inlineAnnotated(block.inline, colors.primary),
                        top = top,
                        bottom = bottom,
                    )
                }
                is MdBlock.Ordered -> {
                    val (top, bottom) = listRunPadding(blocks, index)
                    RowListItem(
                        indent = block.indent,
                        marker = block.marker,
                        text = inlineAnnotated(block.inline, colors.primary),
                        top = top,
                        bottom = bottom,
                    )
                }
                is MdBlock.Quote -> QuoteBlock(inlineAnnotated(block.inline, colors.primary))
                is MdBlock.CodeBlock -> CodeBlockView(block.lines)
                MdBlock.Rule -> HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    color = colors.divider,
                )
            }
        }
    }
}

@Composable
private fun Heading(block: MdBlock.Heading, index: Int, blocks: List<MdBlock>) {
    val colors = AppTheme.colors
    val top = if (index == 0) 0.dp else 16.dp
    when (block.level) {
        1 -> Text(
            text = inlineAnnotated(block.inline, colors.primary, bold = true),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = NewsFontFamily),
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = top, bottom = 8.dp),
        )
        2 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = top, bottom = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(colors.primary, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = inlineAnnotated(block.inline, colors.primary, bold = true),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = NewsFontFamily),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        else -> Text(
            text = inlineAnnotated(block.inline, colors.primary, bold = true),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = NewsFontFamily),
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = top, bottom = 6.dp),
        )
    }
}

/** Breathes at the edges of a list run, tighter between adjacent items. */
private fun listRunPadding(blocks: List<MdBlock>, index: Int): Pair<Dp, Dp> {
    val prev = blocks.getOrNull(index - 1)
    val next = blocks.getOrNull(index + 1)
    val inList = { b: MdBlock? -> b is MdBlock.Bullet || b is MdBlock.Ordered }
    return Pair(
        if (inList(prev)) 4.dp else 6.dp,
        if (inList(next)) 4.dp else 6.dp,
    )
}

@Composable
private fun RowListItem(
    indent: Int,
    marker: String?,
    text: AnnotatedString,
    top: Dp,
    bottom: Dp,
) {
    val colors = AppTheme.colors
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = NewsFontFamily,
        lineHeight = 24.sp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 14).dp, top = top, bottom = bottom),
    ) {
        if (marker == null) {
            Text(
                text = when {
                    indent <= 0 -> "•"
                    indent == 1 -> "◦"
                    else -> "▪"
                },
                color = colors.primary,
                style = style,
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Text(
                text = "$marker.",
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                style = style,
                modifier = Modifier.width(22.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = style,
            color = colors.textBody,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuoteBlock(text: AnnotatedString) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.inputFill, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = NewsFontFamily,
                lineHeight = 20.sp,
            ),
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CodeBlockView(lines: List<String>) {
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        ),
        color = AppTheme.colors.textBody,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(AppTheme.colors.inputFill, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}

@Composable
private fun inlineAnnotated(
    inline: List<MdInline>,
    linkColor: androidx.compose.ui.graphics.Color,
    bold: Boolean = false,
): AnnotatedString {
    val colors = AppTheme.colors
    val bodyColor = colors.textBody
    return buildAnnotatedString {
        inline.forEach { piece ->
            when (piece) {
                is MdInline.Text -> {
                    withStyle(
                        SpanStyle(
                            color = bodyColor,
                            fontFamily = NewsFontFamily,
                            fontWeight = if (bold) FontWeight.SemiBold else null,
                        ),
                    ) { append(piece.text) }
                }
                is MdInline.Bold -> withStyle(
                    SpanStyle(
                        color = bodyColor,
                        fontFamily = NewsFontFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                ) { append(piece.text) }
                is MdInline.Italic -> withStyle(
                    SpanStyle(
                        color = bodyColor,
                        fontFamily = NewsFontFamily,
                        fontStyle = FontStyle.Italic,
                    ),
                ) { append(piece.text) }
                is MdInline.Strike -> withStyle(
                    SpanStyle(
                        color = bodyColor,
                        fontFamily = NewsFontFamily,
                        textDecoration = TextDecoration.LineThrough,
                    ),
                ) { append(piece.text) }
                is MdInline.Code -> withStyle(
                    SpanStyle(
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        background = colors.inputFill,
                    ),
                ) { append(" ${piece.text} ") }
                is MdInline.Link -> withLink(
                    LinkAnnotation.Url(
                        piece.url,
                        TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.None,
                                fontFamily = NewsFontFamily,
                            ),
                        ),
                    ),
                ) { append(piece.label) }
                MdInline.Break -> append("\n")
            }
        }
    }
}
