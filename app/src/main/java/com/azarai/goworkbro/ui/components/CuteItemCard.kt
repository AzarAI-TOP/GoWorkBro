package com.azarai.goworkbro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/**
 * The cute two-column item card shared by the Todo and Habit pages:
 * a rounded rectangle with one big circular cartoon icon (diameter ~80% of
 * the card width) and a title underneath. Tap = primary action
 * (start / check in), long-press = edit.
 */
@Composable
fun CuteItemCard(
    title: String,
    icon: String,
    colorIndex: Int,
    done: Boolean,
    onAction: () -> Unit,
    onEdit: () -> Unit,
    status: String? = null,
    strikeTitle: Boolean = false,
) {
    val extras = LocalForestExtras.current
    val tick = rememberTick()
    CuteCard(
        onClick = { tick(); onAction() },
        onLongClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.84f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // big decorative circle: diameter = 80% of card width
                Box(
                    Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f)
                        .alpha(if (done) 0.55f else 1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(extras.pastels[colorIndex % extras.pastels.size]),
                    )
                    CartoonIcon(icon, Modifier.fillMaxSize(0.7f))
                }
                // check badge
                CheckBadge(visible = done)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                textDecoration = if (strikeTitle && done) TextDecoration.LineThrough else null,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedContent(
                targetState = status,
                transitionSpec = { (scaleIn(spring()) + fadeIn()) togetherWith androidx.compose.animation.fadeOut() },
                label = "status",
            ) { s ->
                Text(
                    s ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) MaterialTheme.colorScheme.primary else extras.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/** Round green check badge that pops in when an item is done. */
@Composable
fun CheckBadge(visible: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
        label = "badge",
    )
    if (scale > 0.01f) {
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(26.dp)
                .offset(x = 26.dp, y = (-10).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(bottom = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "完成",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
