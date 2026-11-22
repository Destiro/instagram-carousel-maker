package app.carouselmaker.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import app.carouselmaker.data.Edge
import app.carouselmaker.data.EdgeType
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** True when the edge style cuts the photo outline (fade uses a mask instead). */
fun Edge.isShape(): Boolean =
    type != EdgeType.NONE && type != EdgeType.FADE && (left || top || right || bottom)

fun Edge.isFade(): Boolean = type == EdgeType.FADE && (left || top || right || bottom)

/** Roughness 0..100 -> amplitude in canvas units for a frame of the given size. */
private fun amplitude(edge: Edge, rect: Rect, scale: Float): Float {
    val base = 6f + edge.roughness * 0.55f
    val cap = min(rect.width, rect.height) * 0.12f
    return min(base, cap) * scale
}

private fun offsets(len: Float, type: EdgeType, amp: Float, rng: Rng, scale: Float): List<Pair<Float, Float>> {
    val step = when (type) {
        EdgeType.DECKLE -> 12f
        EdgeType.ZIGZAG -> 34f
        EdgeType.WAVE -> 14f
        else -> 24f
    } * scale
    val out = ArrayList<Pair<Float, Float>>()
    var t = 0f
    var k = 0
    while (t <= len + 0.01f) {
        val o = when (type) {
            EdgeType.TORN -> amp * (0.25f + 0.75f * rng.next())
            EdgeType.DECKLE -> amp * 0.4f * rng.next()
            EdgeType.WAVE -> amp * 0.5f * (1f + sin(t / (45f * scale) * 1f))
            else -> if (k % 2 == 1) amp * 0.7f else 0f
        }
        out += t to o
        t += step
        k++
    }
    if (out.last().first < len) out += len to out.last().second
    return out
}

/**
 * Outline of [rect] with the chosen sides roughened. [grow] pushes the whole outline outward
 * (used for the paper rim) and [ampK] scales the roughness.
 */
fun edgePath(rect: Rect, edge: Edge, scale: Float = 1f, grow: Float = 0f, ampK: Float = 1f, seedOffset: Int = 0): Path {
    val rng = Rng(edge.seed + seedOffset)
    val amp = amplitude(edge, rect, scale) * ampK
    val r = Rect(
        rect.left - (if (edge.left) grow else 0f), rect.top - (if (edge.top) grow else 0f),
        rect.right + (if (edge.right) grow else 0f), rect.bottom + (if (edge.bottom) grow else 0f),
    )
    val w = r.width
    val h = r.height
    fun side(on: Boolean, len: Float) = if (on) offsets(len, edge.type, amp, rng, scale) else listOf(0f to 0f, len to 0f)
    val top = side(edge.top, w)
    val right = side(edge.right, h)
    val bottom = side(edge.bottom, w)
    val left = side(edge.left, h)
    val pts = ArrayList<Offset>()
    top.forEach { (t, o) -> pts += Offset(r.left + t, r.top + o) }
    right.forEach { (t, o) -> pts += Offset(r.right - o, r.top + t) }
    bottom.forEach { (t, o) -> pts += Offset(r.right - t, r.bottom - o) }
    left.forEach { (t, o) -> pts += Offset(r.left + o, r.bottom - t) }
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
}

/** White ripped-paper rim that peeks out behind a torn photo. */
fun rimPath(rect: Rect, edge: Edge): Path {
    val amp = amplitude(edge, rect, 1f)
    return edgePath(rect, edge, grow = max(6f, amp * 0.55f), ampK = 0.55f, seedOffset = 101)
}
