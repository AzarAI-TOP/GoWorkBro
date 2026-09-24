package com.azarai.goworkbro.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Rounded card with a soft border and springy press feedback.
 *  Long-press (when [onLongClick] is given) is the edit gesture. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CuteCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f),
        label = "cardPress",
    )
    val extras = LocalForestExtras.current
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(extras.card)
            .border(1.5.dp, extras.cardBorder, RoundedCornerShape(22.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(contentPadding),
    ) { content() }
}

/** Round pastel circle hosting a cartoon icon; optionally bobs cutely. */
@Composable
fun IconBubble(
    icon: String,
    bg: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    bob: Boolean = false,
    iconScale: Float = 0.72f,
) {
    val transition = rememberInfiniteTransition(label = "bob")
    val animated by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "bobY",
    )
    val dy = if (bob) animated else 0f
    Box(
        modifier = modifier.size(size).graphicsLayer { translationY = dy },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(bg))
        CartoonIcon(icon, Modifier.size(size * iconScale))
    }
}

/** Animated rounded progress bar. */
@Composable
fun CuteProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = LocalForestExtras.current.divider,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 220f),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** Cute screen top bar: round back button, title, optional action. */
@Composable
fun CuteTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** Round "+" button used on list screens. */
@Composable
fun AddActionButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Empty-state hint with a cartoon icon. */
@Composable
fun EmptyHint(icon: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBubble(icon, LocalForestExtras.current.inputFill, 72.dp, bob = true)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalForestExtras.current.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Grid picker for cartoon icons, used inside create/edit dialogs. */
@Composable
fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    val extras = LocalForestExtras.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.height(202.dp),
        userScrollEnabled = false,
    ) {
        items(CartoonIcons.all.size) { index ->
            val spec = CartoonIcons.all[index]
            val isSel = spec.key == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else extras.inputFill)
                    .border(
                        width = if (isSel) 2.5.dp else 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else extras.cardBorder,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(spec.key) }
                    .semantics { contentDescription = spec.label },
                contentAlignment = Alignment.Center,
            ) {
                CartoonIcon(spec.key, Modifier.size(30.dp))
            }
        }
    }
}

/** Pastel chips picker for the icon-circle background. */
@Composable
fun ColorDots(selected: Int, onSelect: (Int) -> Unit) {
    val extras = LocalForestExtras.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        extras.pastels.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (i == selected) 3.dp else 1.dp,
                        color = if (i == selected) MaterialTheme.colorScheme.primary else extras.cardBorder,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(i) },
            )
        }
    }
}

/** Small grey section header used by the log / stats pages. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = LocalForestExtras.current.textSecondary,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** One row of a day's log: dot, "HH:mm  <what>", delete cross. */
@Composable
fun LogEntryRow(text: String, onDelete: () -> Unit) {
    val extras = LocalForestExtras.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = extras.card,
        border = BorderStroke(1.dp, extras.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除",
                    tint = extras.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Big round icon + "total / goal unit" + progress bar + encouragement line,
 * shared by the water and fitness pages.
 */
@Composable
fun GoalHero(
    total: Int,
    goal: Int,
    unit: String,
    icon: String,
    pastelIndex: Int,
    doneText: String,
    onEditGoal: () -> Unit,
) {
    val extras = LocalForestExtras.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(icon, extras.pastels[pastelIndex % extras.pastels.size], 92.dp, bob = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$total",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                " / $goal $unit",
                style = MaterialTheme.typography.titleMedium,
                color = extras.textSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            IconButton(
                onClick = onEditGoal,
                modifier = Modifier
                    .size(28.dp)
                    .padding(bottom = 2.dp),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "改目标",
                    tint = extras.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        CuteProgressBar(
            progress = if (goal == 0) 0f else total.toFloat() / goal,
            height = 12.dp,
        )
        Spacer(Modifier.height(4.dp))
        Text(doneText, style = MaterialTheme.typography.bodySmall, color = extras.textSecondary)
    }
}

/** Simple number input dialog. */
@Composable
fun NumberInputDialog(
    title: String,
    initialValue: String,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val extras = LocalForestExtras.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val n = value.trim().toIntOrNull()
                if (n != null && n > 0) onConfirm(n)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> value = v.filter { it.isDigit() }.take(5) },
                singleLine = true,
                suffix = { Text(unit) },
            )
        },
        containerColor = extras.card,
    )
}
