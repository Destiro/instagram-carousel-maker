package app.carouselmaker.data

import androidx.compose.ui.geometry.Rect

data class Cell(val x: Float, val y: Float, val w: Float, val h: Float)

data class Template(val id: String, val name: String, val span: Int, val cells: List<Cell>)

private fun c(x: Float, y: Float, w: Float, h: Float) = Cell(x, y, w, h)

object Templates {
    val all: List<Template> = listOf(
        // 1 slide
        Template("full", "Full bleed", 1, listOf(c(0f, 0f, 1f, 1f))),
        Template("stack2", "Two stacked", 1, listOf(c(0f, 0f, 1f, .5f), c(0f, .5f, 1f, .5f))),
        Template("side2", "Side by side", 1, listOf(c(0f, 0f, .5f, 1f), c(.5f, 0f, .5f, 1f))),
        Template("hero2", "Hero plus two", 1, listOf(c(0f, 0f, 1f, .6f), c(0f, .6f, .5f, .4f), c(.5f, .6f, .5f, .4f))),
        Template("grid4", "Grid of four", 1, listOf(c(0f, 0f, .5f, .5f), c(.5f, 0f, .5f, .5f), c(0f, .5f, .5f, .5f), c(.5f, .5f, .5f, .5f))),
        Template("cols3", "Three columns", 1, listOf(c(0f, 0f, 1 / 3f, 1f), c(1 / 3f, 0f, 1 / 3f, 1f), c(2 / 3f, 0f, 1 / 3f, 1f))),
        Template("polaroid", "Polaroid", 1, listOf(c(.08f, .06f, .84f, .7f))),
        Template("tall2", "Tall plus two", 1, listOf(c(0f, 0f, .58f, 1f), c(.58f, 0f, .42f, .5f), c(.58f, .5f, .42f, .5f))),
        // 2 slides
        Template("pano2", "Panorama", 2, listOf(c(0f, 0f, 1f, 1f))),
        Template("pano2two", "Panorama plus two", 2, listOf(c(0f, 0f, 1f, .6f), c(0f, .6f, .5f, .4f), c(.5f, .6f, .5f, .4f))),
        Template("bridge2", "Bridge across seam", 2, listOf(c(0f, 0f, .3f, 1f), c(.3f, 0f, .4f, 1f), c(.7f, 0f, .3f, 1f))),
        Template("mosaic2", "Offset mosaic", 2, listOf(c(0f, 0f, .62f, .6f), c(.62f, 0f, .38f, 1f), c(0f, .6f, .62f, .4f))),
        Template("herogrid2", "Hero plus grid", 2, listOf(c(0f, 0f, .5f, 1f), c(.5f, 0f, .25f, .5f), c(.75f, 0f, .25f, .5f), c(.5f, .5f, .25f, .5f), c(.75f, .5f, .25f, .5f))),
        Template("strips2", "Four strips", 2, listOf(c(0f, 0f, .25f, 1f), c(.25f, 0f, .25f, 1f), c(.5f, 0f, .25f, 1f), c(.75f, 0f, .25f, 1f))),
        // 3 slides
        Template("pano3", "Panorama", 3, listOf(c(0f, 0f, 1f, 1f))),
        Template("band3", "Letterbox band", 3, listOf(c(0f, .2f, 1f, .6f))),
        Template("wall3", "Gallery wall", 3, listOf(c(0f, 0f, .45f, 1f), c(.45f, 0f, .55f, .55f), c(.45f, .55f, .3f, .45f), c(.75f, .55f, .25f, .45f))),
        Template("stagger3", "Staggered grid", 3, listOf(c(0f, 0f, .5f, .5f), c(.5f, 0f, .5f, .5f), c(0f, .5f, .25f, .5f), c(.25f, .5f, .5f, .5f), c(.75f, .5f, .25f, .5f))),
    )

    fun byId(id: String): Template = all.firstOrNull { it.id == id } ?: all.first()
    fun forSpan(span: Int): List<Template> = all.filter { it.span == span }

    /** Single-slide template that holds exactly [count] photos (1..4). */
    fun forCount(count: Int): Template = when (count) {
        1 -> byId("full")
        2 -> byId("stack2")
        3 -> byId("hero2")
        else -> byId("grid4")
    }
}

fun Project.block(id: String?): LayoutBlock? = if (id == null) null else blocks.firstOrNull { it.id == id }

/** Rectangle of cell [idx] of [b] in canvas units, honouring margin and gap. */
fun Project.cellRect(b: LayoutBlock, idx: Int): Rect {
    val t = Templates.byId(b.templateId)
    val c = t.cells[idx.coerceIn(0, t.cells.lastIndex)]
    val bx = b.start * SLIDE_W
    val spanW = b.span * SLIDE_W
    val ax = bx + b.margin - b.gap / 2
    val ay = b.margin - b.gap / 2
    val aw = spanW - 2 * b.margin + b.gap
    val ah = ratio.slideH - 2 * b.margin + b.gap
    return Rect(
        ax + c.x * aw + b.gap / 2,
        ay + c.y * ah + b.gap / 2,
        ax + (c.x + c.w) * aw - b.gap / 2,
        ay + (c.y + c.h) * ah - b.gap / 2,
    )
}

/** Frame of an item in canvas units (before rotation). */
fun Project.rectOf(item: Item): Rect {
    val b = block(item.blockId)
    return if (b != null && item.cell >= 0) cellRect(b, item.cell)
    else Rect(item.x, item.y, item.x + item.w, item.y + item.h)
}

fun Project.radiusOf(item: Item): Float = block(item.blockId)?.radius ?: item.radius
