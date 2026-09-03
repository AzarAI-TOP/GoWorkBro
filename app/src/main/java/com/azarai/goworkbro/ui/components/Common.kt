@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.ui.theme.AppTheme

/** Rounded card matching v1: surface color, 16dp radius, 1dp border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val decorated = modifier
        .background(colors.card, shape)
        .border(1.dp, colors.cardBorder, shape)
        .then(
            when {
                onClick != null && onLongClick != null ->
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                onClick != null -> Modifier.clickable(onClick = onClick)
                onLongClick != null ->
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                else -> Modifier
            },
        )
        .padding(horizontal = 12.dp, vertical = 12.dp)
    Box(modifier = decorated) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/** Small colored tag chip (v1 `_infoChip`). */
@Composable
fun InfoChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = color,
        )
    }
}

/** 30dp filled circle action button (v1 habit card `_circleButton`). */
@Composable
fun CircleButton(
    icon: ImageVector,
    color: Color,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                if (enabled) color else color.copy(alpha = 0.25f),
                CircleShape,
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Simple segmented selector (v1 language/theme pickers). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.inputFill, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) colors.primary else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else colors.textMuted,
                )
            }
        }
    }
}
