package app.carouselmaker.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import app.carouselmaker.data.Item
import app.carouselmaker.data.SLIDE_W
import kotlin.math.max
import kotlin.math.min

data class DecorStyle(val id: String, val name: String)

object TapeStyles {
    val all = listOf(
        DecorStyle("masking", "Masking tape"), DecorStyle("pink", "Pink washi"),
        DecorStyle("mint", "Mint washi"), DecorStyle("clear", "Clear tape"),
        DecorStyle("candy", "Candy stripe"), DecorStyle("polka", "Polka dot"),
        DecorStyle("grid", "Grid washi"), DecorStyle("kraft", "Kraft tape"),
    )
}

object PaperKinds {
    val all = listOf(
        DecorStyle("scrap", "Torn scrap"), DecorStyle("notebook", "Notebook page"),
        DecorStyle("polaroid", "Instant photo"), DecorStyle("ticket", "Ticket stub"),
        DecorStyle("sticky", "Sticky note"), DecorStyle("tag", "Kraft tag"),
    )
    /** Not shown in the gallery; used by slide transitions. */
    const val STRIP = "strip"
    const val SHADOW = "shadow"
}

/** Small deterministic random source so shapes stay identical between frames and exports. */
class Rng(seed: Int) {
    private var s = (seed.toLong() * 7919L + 12345L) and 0x7fffffffL
    fun next(): Float {
        s = (s * 16807L) % 2147483647L
        return s.toFloat() / 2147483647f
    }
}

private fun tapePath(r: Rect): Path {
    val tooth = min(r.height * 0.16f, 14f)
    val steps = max(3, (r.height / (tooth * 1.6f)).toInt())
    val stepH = r.height / steps
    return Path().apply {
        moveTo(r.left + tooth, r.top)
        lineTo(r.right - tooth, r.top)
        for (i in 0 until steps) {
            val y = r.top + i * stepH
            lineTo(r.right, y + stepH / 2)
            lineTo(r.right - tooth, y + stepH)
        }
        lineTo(r.left + tooth, r.bottom)
        for (i in steps downTo 1) {
            val y = r.top + i * stepH
            lineTo(r.left, y - stepH / 2)
            lineTo(r.left + tooth, y - stepH)
        }
        close()
    }
}

fun DrawScope.drawTape(item: Item, rect: Rect) {
    val path = tapePath(rect)
    translate(0f, 3f) { drawPath(path, Color(0x26000000)) }
    clipPath(path) {
        val a = item.opacity
        when (item.style) {
            "pink" -> drawRect(Color(0xE0F4B6C2), rect.topLeft, rect.size, alpha = a)
            "mint" -> drawRect(Color(0xE6BFE3D0), rect.topLeft, rect.size, alpha = a)
            "clear" -> {
                drawRect(Color(0x52FFFFFF), rect.topLeft, rect.size, alpha = a)
                drawRect(Color(0x40FFFFFF), rect.topLeft, Size(rect.width, 3f), alpha = a)
            }
            "candy" -> {
                drawRect(Color(0xFFFFF8E7), rect.topLeft, rect.size, alpha = a * 0.92f)
                var x = rect.left - rect.height
                while (x < rect.right) {
                    val p = Path().apply {
                        moveTo(x, rect.bottom); lineTo(x + 16f, rect.bottom)
                        lineTo(x + 16f + rect.height, rect.top); lineTo(x + rect.height, rect.top); close()
                    }
                    drawPath(p, Color(0xE6FF7A59), alpha = a)
                    x += 34f
                }
            }
            "polka" -> {
                drawRect(Color(0xE62E3B33), rect.topLeft, rect.size, alpha = a)
                var y = rect.top + 10f
                var row = 0
                while (y < rect.bottom) {
                    var x = rect.left + if (row % 2 == 0) 10f else 24f
                    while (x < rect.right) { drawCircle(Color(0xE6FFFFFF), 4.5f, Offset(x, y), alpha = a); x += 28f }
                    y += 22f; row++
                }
            }
            "grid" -> {
                drawRect(Color(0xE6CFE6F2), rect.topLeft, rect.size, alpha = a)
                var x = rect.left
                while (x < rect.right) { drawLine(Color(0x593F4E5E), Offset(x, rect.top), Offset(x, rect.bottom), 1.5f, alpha = a); x += 18f }
                var y = rect.top
                while (y < rect.bottom) { drawLine(Color(0x593F4E5E), Offset(rect.left, y), Offset(rect.right, y), 1.5f, alpha = a); y += 18f }
            }
            "kraft" -> drawRect(Color(0xF2C9A27A), rect.topLeft, rect.size, alpha = a)
            else -> drawRect(Color(0xEBE9DDB5), rect.topLeft, rect.size, alpha = a)
        }
    }
}

/** Jagged/torn rectangle outline; [sides] = left, top, right, bottom flags. */
fun jaggedRect(r: Rect, amp: Float, seed: Int, left: Boolean, top: Boolean, right: Boolean, bottom: Boolean): Path {
    val rng = Rng(seed)
    fun off(on: Boolean) = if (on) amp * (0.25f + 0.75f * rng.next()) else 0f
    val step = max(10f, amp * 1.2f)
    return Path().apply {
        var x = r.left
        moveTo(r.left + off(left), r.top + off(top))
        while (x < r.right) { x = min(r.right, x + step); lineTo(x, r.top + off(top)) }
        var y = r.top
        while (y < r.bottom) { y = min(r.bottom, y + step); lineTo(r.right - off(right), y) }
        x = r.right
        while (x > r.left) { x = max(r.left, x - step); lineTo(x, r.bottom - off(bottom)) }
        y = r.bottom
        while (y > r.top) { y = max(r.top, y - step); lineTo(r.left + off(left), y) }
        close()
    }
}

fun DrawScope.drawPaper(item: Item, rect: Rect) {
    val a = item.opacity
    val sh = Color(0x33000000)
    when (item.style) {
        "notebook" -> {
            translate(0f, 5f) { drawRect(sh, rect.topLeft, rect.size) }
            drawRect(Color.White, rect.topLeft, rect.size, alpha = a)
            val n = 9
            for (i in 1..n) {
                val y = rect.top + rect.height * i / (n + 1)
                drawLine(Color(0xFF9CC3E6), Offset(rect.left, y), Offset(rect.right, y), 2f, alpha = a)
            }
            drawLine(Color(0xFFE57373), Offset(rect.left + rect.width * 0.14f, rect.top), Offset(rect.left + rect.width * 0.14f, rect.bottom), 2.5f, alpha = a)
            // spiral holes
            for (i in 0 until 6) drawCircle(Color(0xFFD8D4CC), 7f, Offset(rect.left + 14f, rect.top + rect.height * (i + 0.7f) / 6.4f), alpha = a)
        }
        "polaroid" -> {
            translate(0f, 6f) { drawRect(sh, rect.topLeft, rect.size) }
            drawRect(Color(0xFFFFFDF7), rect.topLeft, rect.size, alpha = a)
            val pad = rect.width * 0.07f
            drawRect(Color(0xFF55606E), Offset(rect.left + pad, rect.top + pad), Size(rect.width - 2 * pad, rect.height - pad - rect.height * 0.2f), alpha = a)
        }
        "ticket" -> {
            val r = rect.height * 0.12f
            val body = Path().apply {
                addRect(rect)
                // semicircle notches in the left and right edges
                val notch = Path().apply {
                    addOval(Rect(rect.left - r, rect.center.y - r, rect.left + r, rect.center.y + r))
                    addOval(Rect(rect.right - r, rect.center.y - r, rect.right + r, rect.center.y + r))
                }
                val out = Path()
                out.op(this, notch, androidx.compose.ui.graphics.PathOperation.Difference)
                reset()
                addPath(out)
            }
            translate(0f, 5f) { drawPath(body, sh) }
            drawPath(body, Color(0xFFF5C451), alpha = a)
            val x = rect.left + rect.width * 0.72f
            drawLine(Color(0xFF141414), Offset(x, rect.top + 8f), Offset(x, rect.bottom - 8f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)), alpha = a)
            for (i in 0..1) drawLine(Color(0xFF8A6A1E), Offset(rect.left + 24f, rect.top + rect.height * (0.38f + i * 0.2f)), Offset(rect.left + rect.width * (0.62f - i * 0.18f), rect.top + rect.height * (0.38f + i * 0.2f)), 6f, alpha = a)
        }
        "sticky" -> {
            val f = rect.width * 0.2f
            val path = Path().apply {
                moveTo(rect.left, rect.top); lineTo(rect.right, rect.top); lineTo(rect.right, rect.bottom - f)
                lineTo(rect.right - f, rect.bottom); lineTo(rect.left, rect.bottom); close()
            }
            translate(0f, 6f) { drawPath(path, sh) }
            drawPath(path, Color(0xFFFFE98A), alpha = a)
            val fold = Path().apply { moveTo(rect.right, rect.bottom - f); lineTo(rect.right - f, rect.bottom - f); lineTo(rect.right - f, rect.bottom); close() }
            drawPath(fold, Color(0xFFE6CC5C), alpha = a)
        }
        "tag" -> {
            val c = rect.width * 0.25f
            val path = Path().apply {
                moveTo(rect.left + c, rect.top); lineTo(rect.right, rect.top); lineTo(rect.right, rect.bottom)
                lineTo(rect.left + c, rect.bottom); lineTo(rect.left, rect.center.y); close()
            }
            translate(0f, 5f) { drawPath(path, sh) }
            drawPath(path, Color(0xFFC9A27A), alpha = a)
            drawPath(path, Color(0xFF8A6A4A), alpha = a, style = Stroke(3f))
            drawCircle(Color(0xFF3A3A42), rect.height * 0.07f, Offset(rect.left + c * 0.7f, rect.center.y), alpha = a)
            drawLine(Color(0xFFFFF8E7), Offset(rect.left + c * 0.7f, rect.center.y), Offset(rect.left - c * 0.5f, rect.top - c * 0.4f), 3f, alpha = a)
        }
        PaperKinds.STRIP -> {
            val p = jaggedRect(rect, 7f, item.id.hashCode(), true, false, true, false)
            translate(2f, 3f) { drawPath(p, Color(0x40000000)) }
            drawPath(p, Color(0xFFFFFDF7), alpha = a)
        }
        PaperKinds.SHADOW -> {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0x66000000), Color.Transparent), rect.left, rect.right),
                rect.topLeft, rect.size, alpha = a,
            )
        }
        else -> { // scrap
            val p = jaggedRect(rect, min(rect.width, rect.height) * 0.05f, item.id.hashCode(), true, true, true, true)
            translate(0f, 5f) { drawPath(p, sh) }
            drawPath(p, Color(0xFFFFFDF7), alpha = a)
        }
    }
}

/** Where a slide-to-slide decoration for seam [i] belongs. */
fun seamX(i: Int) = (i + 1) * SLIDE_W

