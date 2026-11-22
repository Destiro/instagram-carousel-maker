package app.carouselmaker.render

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.carouselmaker.data.Item
import app.carouselmaker.data.ItemType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws stickers, tape, paper and text. Installed into [app.carouselmaker.data.AppGraph.extras]. */
class AppExtras(val stickers: StickerBitmaps) : RenderExtras {
    var texts: TextRenderer? = null

    override fun DrawScope.drawSticker(ctx: RenderCtx, item: Item, rect: Rect) {
        stickers.version // subscribe to async sticker rendering
        val img: ImageBitmap? = if (item.stickerId != null) {
            val px = if (ctx.blocking) 640 else 400
            if (ctx.blocking) stickers.getBlocking(item.stickerId, ctx.project.look, px) else stickers.get(item.stickerId, ctx.project.look, px)
        } else item.media?.let { ctx.image(it) }
        if (img == null) return
        val dstO = IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
        val dstS = IntSize(max(1, rect.width.roundToInt()), max(1, rect.height.roundToInt()))
        val src = IntSize(img.width, img.height)
        if (item.shadow) {
            drawImage(
                img, IntOffset.Zero, src, dstO + IntOffset(6, 12), dstS, alpha = 0.32f * item.opacity,
                colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn), filterQuality = FilterQuality.Medium,
            )
        }
        if (item.outline) {
            val r = max(4f, rect.width * 0.028f)
            for (k in 0 until 10) {
                val ang = k * 2 * PI / 10
                drawImage(
                    img, IntOffset.Zero, src,
                    dstO + IntOffset((cos(ang) * r).roundToInt(), (sin(ang) * r).roundToInt()), dstS,
                    alpha = item.opacity, colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        drawImage(
            img, IntOffset.Zero, src, dstO, dstS, alpha = item.opacity,
            colorFilter = item.tint?.let { ColorFilter.tint(Color(it), BlendMode.SrcIn) },
            filterQuality = FilterQuality.Medium,
        )
    }

    override fun DrawScope.drawText(ctx: RenderCtx, item: Item, rect: Rect) {
        texts?.run { drawText(item, rect) }
    }

    override fun textHeight(item: Item): Float = texts?.height(item) ?: item.h

    override fun DrawScope.drawDecor(ctx: RenderCtx, item: Item, rect: Rect) {
        when (item.type) {
            ItemType.TAPE -> drawTape(item, rect)
            ItemType.PAPER -> drawPaper(item, rect)
            else -> Unit
        }
    }
}

/** Implemented by the text feature. */
interface TextRenderer {
    fun DrawScope.drawText(item: Item, rect: Rect)
    fun height(item: Item): Float
}
