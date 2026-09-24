package com.azarai.goworkbro.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Tag on the overlay root so tests can assert it dismisses itself. */
const val STARTUP_REVEAL_TAG = "启动动画"

/**
 * Playful cold-start reveal: a big sprout sits in the middle of the screen,
 * then sweeps along an arc into the home header's top-right sprout slot while
 * shrinking and fading, with the home page emerging underneath.
 *
 * @param targetCenter sprout centre measured on the home header, in window px.
 * @param onDone called once the flight finished (remove the overlay).
 */
@Composable
fun StartupReveal(targetCenter: Offset?, onDone: () -> Unit) {
    val extras = LocalForestExtras.current
    val density = LocalDensity.current
    var flying by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (flying) 1f else 0f,
        animationSpec = tween(durationMillis = 950, easing = CubicBezierEasing(0.25f, 0.05f, 0.12f, 1f)),
        label = "revealFlight",
    )
    val pop by animateFloatAsState(
        targetValue = if (flying) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 240f),
        label = "revealPop",
    )

    LaunchedEffect(targetCenter) {
        if (targetCenter != null) {
            kotlinx.coroutines.delay(420) // let the big sprout be noticed
            flying = true
        }
    }
    LaunchedEffect(progress) {
        if (flying && progress >= 1f) {
            kotlinx.coroutines.delay(80)
            onDone()
        }
    }

    val startSize = 152.dp
    val endSize = 56.dp

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = STARTUP_REVEAL_TAG },
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val start = Offset(w / 2f, h * 0.40f)
        val end = targetCenter ?: Offset(w - with(density) { 46.dp.toPx() }, h * 0.10f)
        // control point pulls the path up so the sprout sweeps an arc, not a line
        val control = Offset(
            start.x + (end.x - start.x) * 0.02f,
            start.y - (start.y - end.y) * 0.88f,
        )
        val t = progress
        val pos = quadBezier(start, control, end, t)
        val size = startSize + (endSize - startSize) * t
        val sizePx = with(density) { size.toPx() }

        // opaque at first (hides home), then the home page emerges
        val bgAlpha = 1f - ((t - 0.48f) / 0.42f).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(extras.scaffold.copy(alpha = bgAlpha)))

        // the flying sprout: fades out over the last stretch as it lands
        val iconAlpha = 1f - ((t - 0.84f) / 0.16f).coerceIn(0f, 1f)
        Box(
            Modifier
                .graphicsLayer {
                    translationX = pos.x - sizePx / 2f
                    translationY = pos.y - sizePx / 2f
                    scaleX = pop
                    scaleY = pop
                    alpha = iconAlpha
                }
                .size(size),
        ) {
            IconBubble(
                icon = "sprout",
                bg = extras.pastels[1],
                size = size,
                iconScale = 0.74f,
            )
        }
    }
}

private fun quadBezier(p0: Offset, c: Offset, p1: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        (u * u * p0.x + 2f * u * t * c.x + t * t * p1.x),
        (u * u * p0.y + 2f * u * t * c.y + t * t * p1.y),
    )
}
