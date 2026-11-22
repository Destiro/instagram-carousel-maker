package app.carouselmaker.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.carouselmaker.data.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything a draw pass needs. All coordinates are canvas units (slide width = 1080). */
class RenderCtx(
    val project: Project,
    val images: ImageStore,
    val maxDim: Int = 1400,
    /** Show editing aids: placeholders, play badges. Off for export. */
    val editing: Boolean = true,
    /** Decode synchronously (export). */
    val blocking: Boolean = false,
    /** Item drawn as a transparent hole (used when compositing video). */
    val holeItemId: String? = null,
    /** Skip these items entirely. */
    val skipIds: Set<String> = emptySet(),
    val extras: RenderExtras? = null,
) {
    fun image(name: String): ImageBitmap? =
        if (blocking) images.getBlocking(project.id, name, maxDim) else images.get(project.id, name, maxDim)
}

/** Hooks for item types that need extra resources (stickers, text). */
interface RenderExtras {
    fun DrawScope.drawSticker(ctx: RenderCtx, item: Item, rect: Rect)
    fun DrawScope.drawText(ctx: RenderCtx, item: Item, rect: Rect)
    fun textHeight(item: Item): Float
}

fun DrawScope.drawScene(ctx: RenderCtx) {
    val p = ctx.project
    ctx.images.version // subscribe to async image loads
    clipRect(0f, 0f, p.canvasW, p.canvasH) {
        p.slides.forEachIndexed { i, s ->
            drawRect(Color(s.background), Offset(i * SLIDE_W, 0f), Size(SLIDE_W, p.canvasH))
        }
        for (item in p.items) {
            if (item.id in ctx.skipIds) continue
            drawItem(ctx, item)
        }
    }
}

fun DrawScope.drawItem(ctx: RenderCtx, item: Item) {
    val p = ctx.project
    val r = p.rectOf(item)
    if (r.width <= 0f || r.height <= 0f) return
    val rect = if (item.type == ItemType.TEXT) r.copy(bottom = r.top + (ctx.extras?.textHeight(item) ?: r.height)) else r
    withTransform({
        if (item.rotation != 0f) rotate(item.rotation, rect.center)
        if (item.flipH && item.type != ItemType.TEXT) scale(-1f, 1f, rect.center)
    }) {
        when (item.type) {
            ItemType.PHOTO, ItemType.VIDEO -> drawMedia(ctx, item, rect)
            ItemType.STICKER -> ctx.extras?.run { drawSticker(ctx, item, rect) }
            ItemType.TEXT -> ctx.extras?.run { drawText(ctx, item, rect) }
            ItemType.TAPE, ItemType.PAPER -> Unit
        }
    }
}

/** Clip shape of a photo/video frame. */
fun itemClipPath(ctx: RenderCtx, item: Item, rect: Rect): Path {
    val radius = ctx.project.radiusOf(item)
    return Path().apply {
        if (radius > 0f) addRoundRect(RoundRect(rect, CornerRadius(radius))) else addRect(rect)
    }
}

private fun DrawScope.drawMedia(ctx: RenderCtx, item: Item, rect: Rect) {
    val p = ctx.project
    if (item.id == ctx.holeItemId) {
        clipPath(itemClipPath(ctx, item, rect)) {
            drawRect(Color.Transparent, rect.topLeft, rect.size, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        }
        return
    }
    val name = item.media
    if (name == null) {
        if (ctx.editing) drawPlaceholder(rect, p.radiusOf(item))
        return
    }
    val img = ctx.image(name)
    val block = p.block(item.blockId)
    val seamCuts = if (block != null && block.span > 1 && block.seamGap) {
        (1 until block.span).map { (block.start + it) * SLIDE_W }.filter { it > rect.left && it < rect.right }
            .map { Rect(it - block.margin, rect.top - 1f, it + block.margin, rect.bottom + 1f) }
    } else emptyList()

    val clip = itemClipPath(ctx, item, rect)
    clipPath(clip) {
        fun body() {
            if (img != null) {
                drawCoverImage(img, rect, item.cropL, item.cropT, item.cropR, item.cropB, item.opacity)
            } else {
                drawRect(Color(0xFF3A3A42), rect.topLeft, rect.size)
            }
        }
        if (seamCuts.isEmpty()) body()
        else {
            // punch the seam margin out of a photo that spans slides
            fun nest(i: Int) {
                if (i == seamCuts.size) { body(); return }
                val c = seamCuts[i]
                clipRect(c.left, c.top, c.right, c.bottom, ClipOp.Difference) { nest(i + 1) }
            }
            nest(0)
        }
    }
    if (ctx.editing && item.type == ItemType.VIDEO) drawPlayBadge(rect)
}

/** Draw [img] cropped to the fractions and centre-cropped to fill [dst] without distortion. */
fun DrawScope.drawCoverImage(
    img: ImageBitmap, dst: Rect,
    cl: Float, ct: Float, cr: Float, cb: Float,
    alpha: Float = 1f,
) {
    var sx = cl * img.width
    var sy = ct * img.height
    var sw = (cr - cl) * img.width
    var sh = (cb - ct) * img.height
    if (sw < 1f || sh < 1f) { sx = 0f; sy = 0f; sw = img.width.toFloat(); sh = img.height.toFloat() }
    val target = dst.width / dst.height
    if (sw / sh > target) {
        val nw = sh * target
        sx += (sw - nw) / 2
        sw = nw
    } else {
        val nh = sw / target
        sy += (sh - nh) / 2
        sh = nh
    }
    drawImage(
        img,
        srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
        srcSize = IntSize(max(1, sw.roundToInt()), max(1, sh.roundToInt())),
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(max(1, dst.width.roundToInt()), max(1, dst.height.roundToInt())),
        alpha = alpha,
    )
}

private fun DrawScope.drawPlaceholder(rect: Rect, radius: Float) {
    val rr = RoundRect(rect, CornerRadius(radius))
    val path = Path().apply { addRoundRect(rr) }
    drawPath(path, Color(0x1F000000))
    drawPath(path, Color(0x80808088), style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
    val c = rect.center
    val s = minOf(rect.width, rect.height).coerceAtMost(260f) * 0.16f
    val col = Color(0xB0808088)
    drawLine(col, Offset(c.x - s, c.y), Offset(c.x + s, c.y), strokeWidth = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(col, Offset(c.x, c.y - s), Offset(c.x, c.y + s), strokeWidth = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

private fun DrawScope.drawPlayBadge(rect: Rect) {
    val c = rect.center
    drawCircle(Color(0x99000000), 54f, c)
    val tri = Path().apply {
        moveTo(c.x - 16f, c.y - 26f); lineTo(c.x + 28f, c.y); lineTo(c.x - 16f, c.y + 26f); close()
    }
    drawPath(tri, Color.White)
}
