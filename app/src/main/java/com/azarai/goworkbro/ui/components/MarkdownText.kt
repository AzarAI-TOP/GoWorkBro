package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = inlineAnnotated(block.inline, colors.primary, bold = true),
                        style = style.copy(fontFamily = NewsFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (index == 0) 0.dp else 14.dp,
                                bottom = 6.dp,
                            ),
                        color = if (block.level <= 2) colors.textPrimary else colors.textSecondary,
                    )
                }
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
                is MdBlock.Bullet -> RowBullet(
                    indent = block.indent,
                    text = inlineAnnotated(block.inline, colors.primary),
                )
                is MdBlock.Ordered -> RowBullet(
                    indent = block.indent,
                    text = inlineAnnotated(
                        listOf(MdInline.Bold("${block.marker}. ")) + block.inline,
                        colors.primary,
                    ),
                )
                is MdBlock.Quote -> Text(
                    text = inlineAnnotated(block.inline, colors.primary),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NewsFontFamily),
                    color = colors.textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
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
private fun RowBullet(indent: Int, text: AnnotatedString) {
    val colors = AppTheme.colors
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 14).dp, top = 3.dp, bottom = 3.dp),
    ) {
        Text(
            text = "•  ",
            color = colors.primary,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = NewsFontFamily),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = NewsFontFamily,
                lineHeight = 24.sp,
            ),
            color = colors.textBody,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun inlineAnnotated(
    inline: List<MdInline>,
    linkColor: androidx.compose.ui.graphics.Color,
    bold: Boolean = false,
): AnnotatedString {
    val bodyColor = AppTheme.colors.textBody
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
            }
        }
    }
}

