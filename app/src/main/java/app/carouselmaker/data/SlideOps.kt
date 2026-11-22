package app.carouselmaker.data

import kotlin.math.max
import kotlin.math.min

/** Index of the slide an item sits on, judged by its centre. */
fun Project.slideOf(item: Item): Int {
    val r = rectOf(item)
    return (r.center.x / SLIDE_W).toInt().coerceIn(0, slideCount - 1)
}

/** Turn a layout block's cells into free items and remove the block. */
fun freeBlock(p: Project, blockId: String): Project {
    var q = p
    q.items.filter { it.blockId == blockId }.forEach { q = q.detach(it.id) }
    return q.copy(blocks = q.blocks.filter { it.id != blockId })
}

/** Shift everything on slides >= [from] by [delta] slides (does not touch the slide list). */
private fun shiftSlides(p: Project, from: Int, delta: Int): Project {
    val dx = delta * SLIDE_W
    return p.copy(
        blocks = p.blocks.map { if (it.start >= from) it.copy(start = it.start + delta) else it },
        items = p.items.map {
            when {
                it.isBound() -> it
                it.seam >= 0 -> if (it.seam >= from) it.copy(x = it.x + dx, seam = it.seam + delta) else it
                p.slideOf(it) >= from -> it.copy(x = it.x + dx)
                else -> it
            }
        },
    )
}

fun insertSlide(p: Project, at: Int, background: Int): Project {
    val q = shiftSlides(p, at, +1)
    return q.copy(slides = q.slides.toMutableList().apply { add(at.coerceIn(0, size), Slide(background)) })
}

fun duplicateSlide(p: Project, i: Int): Project {
    val src = p.slides.getOrNull(i) ?: return p
    val blocks = p.blocks.filter { it.span == 1 && it.start == i }
    val free = p.items.filter { !it.isBound() && it.seam < 0 && p.slideOf(it) == i }
    var q = insertSlide(p, i + 1, src.background)
    val newBlocks = ArrayList<LayoutBlock>()
    val newItems = ArrayList<Item>()
    for (b in blocks) {
        val nb = b.copy(id = newId(), start = i + 1)
        newBlocks += nb
        p.items.filter { it.blockId == b.id }.forEach { newItems += it.copy(id = newId(), blockId = nb.id) }
    }
    free.forEach { newItems += it.copy(id = newId(), x = it.x + SLIDE_W) }
    q = q.copy(blocks = q.blocks + newBlocks, items = q.items + newItems)
    return q
}

fun deleteSlide(p: Project, i: Int): Project {
    if (p.slideCount <= 1 || i !in 0 until p.slideCount) return p
    var q = p
    q.blocks.filter { it.span > 1 && i >= it.start && i < it.start + it.span }.forEach { q = freeBlock(q, it.id) }
    val deadBlocks = q.blocks.filter { it.span == 1 && it.start == i }.map { it.id }.toSet()
    q = q.copy(
        blocks = q.blocks.filter { it.id !in deadBlocks },
        items = q.items.filter { item ->
            when {
                item.blockId in deadBlocks -> false
                item.seam in (i - 1)..i -> false
                !item.isBound() && item.seam < 0 && q.slideOf(item) == i -> false
                else -> true
            }
        },
    )
    q = shiftSlides(q, i + 1, -1)
    return q.copy(slides = q.slides.filterIndexed { idx, _ -> idx != i })
}

fun moveSlide(p: Project, from: Int, to: Int): Project {
    if (from == to || from !in 0 until p.slideCount || to !in 0 until p.slideCount) return p
    val lo = min(from, to)
    val hi = max(from, to)
    var q = p
    q.blocks.filter { it.span > 1 && it.start <= hi && it.start + it.span - 1 >= lo }.forEach { q = freeBlock(q, it.id) }
    fun map(i: Int): Int = when {
        i == from -> to
        from < to && i in (from + 1)..to -> i - 1
        from > to && i in to until from -> i + 1
        else -> i
    }
    val slides = q.slides.toMutableList().apply { add(to, removeAt(from)) }
    val items = q.items.filter { !(it.seam >= 0 && it.seam in (lo - 1)..hi) }.map { item ->
        if (item.isBound() || item.seam >= 0) item else {
            val si = q.slideOf(item)
            item.copy(x = item.x + (map(si) - si) * SLIDE_W)
        }
    }
    return q.copy(slides = slides, blocks = q.blocks.map { it.copy(start = map(it.start)) }, items = items)
}
