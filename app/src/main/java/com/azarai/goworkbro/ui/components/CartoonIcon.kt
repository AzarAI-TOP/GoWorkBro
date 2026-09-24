package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

/** Catalog of hand-drawn cartoon icons (design space: 100 x 100). */
object CartoonIcons {

    data class Spec(val key: String, val label: String)

    val all: List<Spec> = listOf(
        Spec("scroll", "卷轴"),
        Spec("book", "书本"),
        Spec("cup", "水杯"),
        Spec("dumbbell", "哑铃"),
        Spec("dog", "小狗"),
        Spec("cat", "小猫"),
        Spec("star", "星星"),
        Spec("apple", "苹果"),
        Spec("music", "音符"),
        Spec("flame", "火焰"),
        Spec("moon", "月亮"),
        Spec("sun", "太阳"),
        Spec("pencil", "铅笔"),
        Spec("flower", "小花"),
        Spec("sprout", "嫩芽"),
        Spec("file", "文件"),
    )

}

// ---------------------------------------------------------------------------
// Palette used by the drawings themselves (theme-independent cartoon colors).
// ---------------------------------------------------------------------------
private val Ink = Color(0xFF5B4A3F)             // warm brown outline
private val Paper = Color(0xFFFFF8E7)
private val Roll = Color(0xFFF1DFA8)
private val RollEnd = Color(0xFFE0C880)
private val LineBrown = Color(0xFFB9A77E)
private val PageWhite = Color(0xFFFFFDF4)
private val CoverGreen = Color(0xFF8CB86A)
private val Glass = Color(0xFFE3F2FB)
private val Water = Color(0xFF7EC8E3)
private val WaterDeep = Color(0xFF5AB2D5)
private val StrawPink = Color(0xFFFF9F9F)
private val Metal = Color(0xFF8A9099)
private val MetalLight = Color(0xFFAAB2BC)
private val DogFur = Color(0xFFE8B87E)
private val DogEar = Color(0xFFB07B4F)
private val Snout = Color(0xFFFFF3DC)
private val CatFur = Color(0xFFDCDCE3)
private val CatEar = Color(0xFFC2C2CC)
private val StarYellow = Color(0xFFFFD75E)
private val SunRay = Color(0xFFF5B942)
private val AppleRed = Color(0xFFFF8B8B)
private val LeafGreen = Color(0xFF7DBB6A)
private val NoteGreen = Color(0xFF8CB86A)
private val FlameOrange = Color(0xFFFF9F45)
private val FlameYellow = Color(0xFFFFE08A)
private val MoonYellow = Color(0xFFFFE08A)
private val PencilYellow = Color(0xFFFFC94D)
private val PencilWood = Color(0xFFF5E7CF)
private val EraserPink = Color(0xFFFF9F9F)
private val Ferrule = Color(0xFFC9CDD4)
private val PetalPink = Color(0xFFFFB3C7)
private val FlowerCore = Color(0xFFFFE08A)
private val Soil = Color(0xFFA97C50)
private val Blush = Color(0xFFF5A3A3)

/** Composable entry: draws the cartoon icon scaled to fill [modifier]. */
@Composable
fun CartoonIcon(key: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCartoonIcon(key)
    }
}

/** All drawing happens in a 0..100 design space, scaled to fit the canvas. */
fun DrawScope.drawCartoonIcon(key: String) {
    val s = size.minDimension / 100f
    scale(s, pivot = Offset.Zero) {
        when (key) {
            "scroll" -> drawScroll()
            "book" -> drawBook()
            "cup" -> drawCup()
            "dumbbell" -> drawDumbbell()
            "dog" -> drawDog()
            "cat" -> drawCat()
            "star" -> drawStar()
            "apple" -> drawApple()
            "music" -> drawMusic()
            "flame" -> drawFlame()
            "moon" -> drawMoon()
            "sun" -> drawSun()
            "pencil" -> drawPencil()
            "flower" -> drawFlower()
            "sprout" -> drawSprout()
            "file" -> drawFile()
            else -> drawScroll()
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

private fun DrawScope.stroke(width: Float = 4.5f) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.outlinedCircle(color: Color, center: Offset, radius: Float) {
    drawCircle(color, radius, center, style = Fill)
    drawCircle(Ink, radius, center, style = stroke())
}

private fun DrawScope.eyes(left: Offset, right: Offset, radius: Float = 3.1f) {
    drawCircle(Ink, radius, left)
    drawCircle(Ink, radius, right)
}

private fun DrawScope.smile(center: Offset, radius: Float, color: Color = Ink, width: Float = 3.4f) {
    drawArc(
        color = color,
        startAngle = 35f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
}

private fun DrawScope.blushAt(vararg centers: Pair<Float, Float>) {
    centers.forEach { drawCircle(Blush.copy(alpha = 0.55f), 4.6f, Offset(it.first, it.second)) }
}

private fun DrawScope.line(from: Offset, to: Offset, color: Color, width: Float, cap: StrokeCap = StrokeCap.Round) {
    drawLine(color, from, to, strokeWidth = width, cap = cap)
}

private fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        cubicTo(center.x + r * 0.18f, center.y - r * 0.18f, center.x + r * 0.18f, center.y - r * 0.18f, center.x + r, center.y)
        cubicTo(center.x + r * 0.18f, center.y + r * 0.18f, center.x + r * 0.18f, center.y + r * 0.18f, center.x, center.y + r)
        cubicTo(center.x - r * 0.18f, center.y + r * 0.18f, center.x - r * 0.18f, center.y + r * 0.18f, center.x - r, center.y)
        cubicTo(center.x - r * 0.18f, center.y - r * 0.18f, center.x - r * 0.18f, center.y - r * 0.18f, center.x, center.y - r)
        close()
    }
    drawPath(p, color, style = Fill)
}

// ---------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------

private fun DrawScope.drawScroll() {
    // paper body
    val paper = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(24f, 22f, 76f, 78f, 5f, 5f))
    }
    drawPath(paper, Paper, style = Fill)
    drawPath(paper, Ink, style = stroke())
    // text lines
    line(Offset(34f, 36f), Offset(66f, 36f), LineBrown, 3.6f)
    line(Offset(34f, 50f), Offset(66f, 50f), LineBrown, 3.6f)
    line(Offset(34f, 64f), Offset(56f, 64f), LineBrown, 3.6f)
    // top and bottom rolls
    listOf(10f to 24f, 76f to 90f).forEach { (top, bottom) ->
        val roll = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(16f, top, 84f, bottom, 7f, 7f))
        }
        drawPath(roll, Roll, style = Fill)
        drawPath(roll, Ink, style = stroke(4f))
        drawCircle(RollEnd, 5.2f, Offset(20f, (top + bottom) / 2f))
        drawCircle(RollEnd, 5.2f, Offset(80f, (top + bottom) / 2f))
    }
}

private fun DrawScope.drawBook() {
    // cover underneath
    val cover = Path().apply {
        moveTo(50f, 24f)
        cubicTo(36f, 14f, 16f, 16f, 10f, 22f)
        lineTo(10f, 76f)
        cubicTo(16f, 70f, 36f, 68f, 50f, 78f)
        cubicTo(64f, 68f, 84f, 70f, 90f, 76f)
        lineTo(90f, 22f)
        cubicTo(84f, 16f, 64f, 14f, 50f, 24f)
        close()
    }
    drawPath(cover, CoverGreen, style = Fill)
    drawPath(cover, Ink, style = stroke())
    // left page
    val left = Path().apply {
        moveTo(50f, 26f)
        cubicTo(38f, 17f, 20f, 18f, 15f, 23f)
        lineTo(15f, 71f)
        cubicTo(20f, 66f, 38f, 66f, 50f, 75f)
        close()
    }
    drawPath(left, PageWhite, style = Fill)
    drawPath(left, Ink, style = stroke(4f))
    // right page
    val right = Path().apply {
        moveTo(50f, 26f)
        cubicTo(62f, 17f, 80f, 18f, 85f, 23f)
        lineTo(85f, 71f)
        cubicTo(80f, 66f, 62f, 66f, 50f, 75f)
        close()
    }
    drawPath(right, PageWhite, style = Fill)
    drawPath(right, Ink, style = stroke(4f))
    // spine
    line(Offset(50f, 26f), Offset(50f, 75f), Ink, 4f)
    // text lines
    line(Offset(24f, 34f), Offset(42f, 33f), LineBrown, 3.2f)
    line(Offset(24f, 44f), Offset(42f, 43f), LineBrown, 3.2f)
    line(Offset(58f, 33f), Offset(76f, 34f), LineBrown, 3.2f)
    line(Offset(58f, 43f), Offset(76f, 44f), LineBrown, 3.2f)
}

private fun DrawScope.drawCup() {
    // straw (behind the rim)
    line(Offset(68f, 8f), Offset(58f, 30f), StrawPink, 6.5f)
    // cup body
    val body = Path().apply {
        moveTo(30f, 26f)
        lineTo(70f, 26f)
        lineTo(65.5f, 78f)
        cubicTo(65f, 84f, 60f, 86f, 50f, 86f)
        cubicTo(40f, 86f, 35f, 84f, 34.5f, 78f)
        close()
    }
    drawPath(body, Glass, style = Fill)
    // water inside (clipped)
    clipPath(body) {
        val wave = Path().apply {
            moveTo(24f, 50f)
            cubicTo(33f, 46f, 41f, 54f, 50f, 50f)
            cubicTo(59f, 46f, 67f, 54f, 76f, 50f)
            lineTo(76f, 90f)
            lineTo(24f, 90f)
            close()
        }
        drawPath(wave, Water, style = Fill)
        drawCircle(WaterDeep, 3.4f, Offset(41f, 64f))
        drawCircle(WaterDeep, 3.0f, Offset(58f, 70f))
    }
    drawPath(body, Ink, style = stroke())
    // rim band
    line(Offset(30f, 26f), Offset(70f, 26f), Ink, 5f)
    // shine
    line(Offset(37f, 36f), Offset(35f, 50f), Color.White.copy(alpha = 0.8f), 3.4f)
}

private fun DrawScope.drawDumbbell() {
    // bar
    line(Offset(30f, 50f), Offset(70f, 50f), Metal, 7f)
    // inner plates
    drawRoundRectAndroid(MetalLight, 28f, 38f, 36f, 62f, 4f)
    drawRoundRectAndroid(MetalLight, 64f, 38f, 72f, 62f, 4f)
    // outer plates
    outlinedRoundRect(Metal, 14f, 30f, 28f, 70f, 7f)
    outlinedRoundRect(Metal, 72f, 30f, 86f, 70f, 7f)
}

private fun DrawScope.drawRoundRectAndroid(color: Color, l: Float, t: Float, r: Float, b: Float, rad: Float) {
    val p = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, rad, rad)) }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.outlinedRoundRect(color: Color, l: Float, t: Float, r: Float, b: Float, rad: Float) {
    val p = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, rad, rad)) }
    drawPath(p, color, style = Fill)
    drawPath(p, Ink, style = stroke())
}

private fun DrawScope.drawDog() {
    // floppy ears
    rotate(-24f, pivot = Offset(28f, 30f)) {
        drawEllipseFill(DogEar, Offset(26f, 30f), 9.5f, 18f)
    }
    rotate(24f, pivot = Offset(74f, 30f)) {
        drawEllipseFill(DogEar, Offset(74f, 30f), 9.5f, 18f)
    }
    // face
    outlinedCircle(DogFur, Offset(50f, 56f), 31f)
    // snout + nose + mouth
    drawEllipseFill(Snout, Offset(50f, 66f), 13f, 9.5f)
    drawEllipseFill(Ink, Offset(50f, 61f), 5f, 3.8f)
    line(Offset(50f, 65f), Offset(50f, 69f), Ink, 3f)
    smile(Offset(44f, 66f), 6f)
    smile(Offset(56f, 66f), 6f)
    // eyes + brow spots
    eyes(Offset(39f, 50f), Offset(61f, 50f))
    sparkle(Offset(41.5f, 46.5f), 2.4f, Color.White)
    sparkle(Offset(63.5f, 46.5f), 2.4f, Color.White)
    // head stripe
    line(Offset(50f, 25f), Offset(50f, 34f), DogEar, 5f)
    blushAt(30f to 62f, 70f to 62f)
}

private fun DrawScope.drawCat() {
    // ears
    val leftEar = Path().apply {
        moveTo(28f, 42f); lineTo(24f, 12f); lineTo(50f, 28f); close()
    }
    val rightEar = Path().apply {
        moveTo(72f, 42f); lineTo(76f, 12f); lineTo(50f, 28f); close()
    }
    listOf(leftEar, rightEar).forEach {
        drawPath(it, CatFur, style = Fill)
        drawPath(it, Ink, style = stroke(4f))
    }
    // inner ears
    val li = Path().apply { moveTo(31f, 36f); lineTo(29f, 20f); lineTo(43f, 28f); close() }
    val ri = Path().apply { moveTo(69f, 36f); lineTo(71f, 20f); lineTo(57f, 28f); close() }
    drawPath(li, PetalPink, style = Fill)
    drawPath(ri, PetalPink, style = Fill)
    // face
    outlinedCircle(CatFur, Offset(50f, 58f), 30f)
    // face features
    eyes(Offset(40f, 54f), Offset(60f, 54f))
    // nose
    val nose = Path().apply {
        moveTo(46.5f, 62f); lineTo(53.5f, 62f); lineTo(50f, 66.5f); close()
    }
    drawPath(nose, PetalPink, style = Fill)
    drawPath(nose, Ink, style = stroke(2.6f))
    smile(Offset(50f, 68f), 5f)
    // whiskers
    line(Offset(22f, 58f), Offset(36f, 60f), Ink, 2.8f)
    line(Offset(22f, 67f), Offset(36f, 66f), Ink, 2.8f)
    line(Offset(78f, 58f), Offset(64f, 60f), Ink, 2.8f)
    line(Offset(78f, 67f), Offset(64f, 66f), Ink, 2.8f)
    blushAt(32f to 66f, 68f to 66f)
}

private fun DrawScope.drawStar() {
    val c = Offset(50f, 54f)
    val p = starPath(c, 34f, 15f)
    drawPath(p, StarYellow, style = Fill)
    drawPath(p, Ink, style = stroke())
    eyes(Offset(41f, 50f), Offset(59f, 50f))
    smile(Offset(50f, 56f), 7f)
    blushAt(36f to 57f, 64f to 57f)
    sparkle(Offset(78f, 20f), 6f, StarYellow)
    sparkle(Offset(18f, 26f), 4f, StarYellow)
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val r = if (i % 2 == 0) outer else inner
        val x = center.x + (r * Math.cos(angle)).toFloat()
        val y = center.y + (r * Math.sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawApple() {
    // stem + leaf
    line(Offset(50f, 32f), Offset(55f, 16f), Soil, 5f)
    rotate(28f, pivot = Offset(63f, 20f)) {
        drawEllipseFill(LeafGreen, Offset(63f, 20f), 10f, 5.5f)
    }
    // body
    val body = Path().apply {
        moveTo(50f, 36f)
        cubicTo(60f, 24f, 82f, 32f, 80f, 58f)
        cubicTo(78f, 80f, 62f, 88f, 50f, 82f)
        cubicTo(38f, 88f, 22f, 80f, 20f, 58f)
        cubicTo(18f, 32f, 40f, 24f, 50f, 36f)
        close()
    }
    drawPath(body, AppleRed, style = Fill)
    drawPath(body, Ink, style = stroke())
    // shine
    drawCircle(Color.White.copy(alpha = 0.55f), 5f, Offset(34f, 46f))
    // face
    eyes(Offset(42f, 56f), Offset(58f, 56f))
    smile(Offset(50f, 62f), 6f)
    blushAt(34f to 64f, 66f to 64f)
}

private fun DrawScope.drawMusic() {
    // flag
    val flag = Path().apply {
        moveTo(48f, 26f)
        cubicTo(62f, 30f, 70f, 38f, 66f, 50f)
        cubicTo(64f, 40f, 56f, 34f, 48f, 36f)
        close()
    }
    drawPath(flag, NoteGreen, style = Fill)
    // stem
    line(Offset(48f, 28f), Offset(48f, 70f), NoteGreen, 5.5f)
    // head
    rotate(-18f, pivot = Offset(38f, 72f)) {
        drawEllipseFill(NoteGreen, Offset(38f, 72f), 12f, 8.5f)
        drawEllipseOutlineInk(Offset(38f, 72f), 12f, 8.5f)
    }
    sparkle(Offset(74f, 30f), 5f, StarYellow)
}

private fun DrawScope.drawFlame() {
    val outer = Path().apply {
        moveTo(50f, 12f)
        cubicTo(64f, 30f, 78f, 40f, 74f, 60f)
        cubicTo(71f, 80f, 61f, 87f, 50f, 87f)
        cubicTo(39f, 87f, 29f, 80f, 26f, 60f)
        cubicTo(22f, 40f, 36f, 30f, 50f, 12f)
        close()
    }
    drawPath(outer, FlameOrange, style = Fill)
    drawPath(outer, Ink, style = stroke())
    val inner = Path().apply {
        moveTo(50f, 40f)
        cubicTo(58f, 50f, 63f, 54f, 61f, 64f)
        cubicTo(59f, 74f, 55f, 77f, 50f, 77f)
        cubicTo(45f, 77f, 41f, 74f, 39f, 64f)
        cubicTo(37f, 54f, 42f, 50f, 50f, 40f)
        close()
    }
    drawPath(inner, FlameYellow, style = Fill)
    eyes(Offset(44f, 60f), Offset(56f, 60f), 2.8f)
    smile(Offset(50f, 64f), 4.5f, Ink, 3f)
}

private fun DrawScope.drawMoon() {
    val crescent = Path().apply {
        moveTo(66f, 12f)
        cubicTo(40f, 12f, 22f, 28f, 22f, 50f)
        cubicTo(22f, 72f, 40f, 88f, 66f, 88f)
        cubicTo(48f, 82f, 37f, 67f, 37f, 50f)
        cubicTo(37f, 33f, 48f, 18f, 66f, 12f)
        close()
    }
    drawPath(crescent, MoonYellow, style = Fill)
    drawPath(crescent, Ink, style = stroke())
    // face sits inside the crescent body
    eyes(Offset(27f, 45f), Offset(34f, 45f), 2.3f)
    smile(Offset(30.5f, 48f), 3.6f, Ink, 2.8f)
    sparkle(Offset(74f, 28f), 5.5f, StarYellow)
    sparkle(Offset(80f, 56f), 4f, StarYellow)
    sparkle(Offset(70f, 74f), 3f, StarYellow)
}

private fun DrawScope.drawSun() {
    // rays
    for (i in 0 until 8) {
        rotate(i * 45f, pivot = Offset(50f, 52f)) {
            line(Offset(50f, 24f), Offset(50f, 13f), SunRay, 6f)
        }
    }
    outlinedCircle(StarYellow, Offset(50f, 52f), 22f)
    eyes(Offset(43f, 49f), Offset(57f, 49f), 2.9f)
    smile(Offset(50f, 55f), 6.5f)
    blushAt(38f to 56f, 62f to 56f)
}

private fun DrawScope.drawPencil() {
    rotate(-38f, pivot = Offset(56f, 50f)) {
        // body
        outlinedRoundRect(PencilYellow, 32f, 42f, 78f, 58f, 3f)
        // ferrule + eraser
        outlinedRoundRect(Ferrule, 26f, 42f, 32f, 58f, 2f)
        outlinedRoundRect(EraserPink, 18f, 42f, 26f, 58f, 4f)
        // tip
        val tip = Path().apply {
            moveTo(78f, 42f); lineTo(94f, 50f); lineTo(78f, 58f); close()
        }
        drawPath(tip, PencilWood, style = Fill)
        drawPath(tip, Ink, style = stroke(4f))
        val lead = Path().apply {
            moveTo(89.5f, 47.4f); lineTo(94f, 50f); lineTo(89.5f, 52.6f); close()
        }
        drawPath(lead, Ink, style = Fill)
    }
    sparkle(Offset(24f, 22f), 5f, StarYellow)
}

private fun DrawScope.drawFlower() {
    val c = Offset(50f, 48f)
    // five petals
    for (i in 0 until 5) {
        val angle = Math.toRadians((-90 + i * 72).toDouble())
        val px = c.x + (17.5 * Math.cos(angle)).toFloat()
        val py = c.y + (17.5 * Math.sin(angle)).toFloat()
        drawCircle(PetalPink, 12.5f, Offset(px, py), style = Fill)
    }
    // center with outline drawn over the petals' inner edges
    outlinedCircle(FlowerCore, c, 11f)
    eyes(Offset(46f, 46f), Offset(54f, 46f), 2.4f)
    smile(c, 4f, Ink, 2.8f)
    blushAt(44f to 51f, 56f to 51f)
    sparkle(Offset(82f, 24f), 4.5f, StarYellow)
}

private fun DrawScope.drawFile() {
    // blank page with a folded corner
    val page = Path().apply {
        moveTo(28f, 16f)
        lineTo(62f, 16f)
        lineTo(78f, 32f)
        lineTo(78f, 84f)
        cubicTo(78f, 88f, 75f, 90f, 71f, 90f)
        lineTo(28f, 90f)
        cubicTo(24f, 90f, 22f, 88f, 22f, 84f)
        lineTo(22f, 22f)
        cubicTo(22f, 18f, 24f, 16f, 28f, 16f)
        close()
    }
    drawPath(page, PageWhite, style = Fill)
    drawPath(page, Ink, style = stroke())
    // folded corner
    val fold = Path().apply {
        moveTo(62f, 16f)
        lineTo(78f, 32f)
        lineTo(62f, 32f)
        close()
    }
    drawPath(fold, Roll, style = Fill)
    drawPath(fold, Ink, style = stroke(3.6f))
    // tiny face so the empty state stays friendly
    eyes(Offset(40f, 56f), Offset(60f, 56f), 2.8f)
    smile(Offset(50f, 62f), 6f)
    blushAt(38f to 64f, 62f to 64f)
}

private fun DrawScope.drawSprout() {
    // soil mound
    val mound = Path().apply {
        moveTo(22f, 84f)
        cubicTo(30f, 64f, 70f, 64f, 78f, 84f)
        close()
    }
    drawPath(mound, Soil, style = Fill)
    drawPath(mound, Ink, style = stroke(4f))
    // stem
    line(Offset(50f, 68f), Offset(50f, 38f), LeafGreen, 5.5f)
    // two leaves
    rotate(-38f, pivot = Offset(39f, 46f)) {
        drawEllipseFill(LeafGreen, Offset(39f, 46f), 11.5f, 6.5f)
        drawEllipseOutlineInk(Offset(39f, 46f), 11.5f, 6.5f)
    }
    rotate(38f, pivot = Offset(61f, 38f)) {
        drawEllipseFill(LeafGreen, Offset(61f, 38f), 11.5f, 6.5f)
        drawEllipseOutlineInk(Offset(61f, 38f), 11.5f, 6.5f)
    }
    sparkle(Offset(76f, 20f), 4.5f, StarYellow)
}

private fun DrawScope.drawEllipseFill(color: Color, center: Offset, rx: Float, ry: Float) {
    drawOval(color, topLeft = Offset(center.x - rx, center.y - ry), size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2), style = Fill)
}

private fun DrawScope.drawEllipseOutlineInk(center: Offset, rx: Float, ry: Float) {
    drawOval(Ink, topLeft = Offset(center.x - rx, center.y - ry), size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2), style = stroke(4f))
}
