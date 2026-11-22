package app.carouselmaker.data

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

/** Items bound to a layout cell. */
fun Item.isBound() = blockId != null && cell >= 0

fun Item.isMedia() = type == ItemType.PHOTO || type == ItemType.VIDEO

private fun mediaItem(m: ImportedMedia, base: Item? = null): Item {
    val type = if (m.isVideo) ItemType.VIDEO else ItemType.PHOTO
    return (base ?: Item(id = newId(), type = type)).copy(
        type = type,
        media = m.name,
        mw = m.width,
        mh = m.height,
        cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f,
        durationMs = m.durationMs,
        trimStartMs = 0,
        trimEndMs = m.durationMs,
    )
}

private fun placeholder(blockId: String, cell: Int) =
    Item(id = newId(), type = ItemType.PHOTO, blockId = blockId, cell = cell)

fun createProject(
    name: String,
    ratio: Ratio,
    slideCount: Int,
    media: List<ImportedMedia>,
    blank: Boolean,
    id: String = newId(),
): Project {
    val n = media.size
    val slides = if (n == 0) slideCount else maxOf(slideCount, ceil(n / 4f).toInt())
    var project = Project(
        id = id,
        name = name,
        ratio = ratio,
        slides = List(slides) { Slide() },
        updatedAt = System.currentTimeMillis(),
    )
    if (n == 0) {
        if (blank) for (s in 0 until slides) project = applyTemplate(project, s, 1, "full")
        return project
    }
    val base = n / slides
    val rem = n % slides
    var cursor = 0
    for (s in 0 until slides) {
        val count = base + if (s < rem) 1 else 0
        if (count == 0) continue
        val tpl = Templates.forCount(count)
        val block = LayoutBlock(newId(), s, 1, tpl.id)
        val items = (0 until count).map { i -> mediaItem(media[cursor + i], placeholder(block.id, i)) }
        cursor += count
        project = project.copy(blocks = project.blocks + block, items = project.items + items)
    }
    return project
}

/**
 * Put template [templateId] on slides [start, start+span). Media already sitting in the replaced
 * cells is re-flowed into the new cells in order; leftovers become free items; empty cells become
 * placeholders.
 */
fun applyTemplate(project: Project, start: Int, span: Int, templateId: String): Project {
    val endEx = start + span
    val old = project.blocks.filter { it.start < endEx && it.start + it.span > start }
    val oldIds = old.map { it.id }.toSet()
    val bound = project.items.filter { it.blockId in oldIds && it.cell >= 0 }
    val pool = bound
        .filter { it.media != null }
        .sortedWith(compareBy({ project.block(it.blockId)?.start ?: 0 }, { it.cell }))
    val tpl = Templates.byId(templateId)
    val keep = old.firstOrNull { it.start == start && it.span == span }
    val ref = keep ?: project.blocks.firstOrNull()
    val block = LayoutBlock(
        id = newId(), start = start, span = span, templateId = templateId,
        margin = ref?.margin ?: 60f,
        gap = ref?.gap ?: 24f,
        radius = ref?.radius ?: 0f,
        seamGap = ref?.seamGap ?: true,
    )
    val remap = HashMap<String, Item>()
    for ((i, it) in pool.withIndex()) {
        if (i < tpl.cells.size) {
            remap[it.id] = it.copy(blockId = block.id, cell = i)
        } else {
            val r = project.rectOf(it)
            remap[it.id] = it.copy(
                blockId = null, cell = -1,
                x = r.left, y = r.top, w = r.width, h = r.height,
                radius = project.radiusOf(it),
            )
        }
    }
    val dropIds = bound.filter { it.media == null }.map { it.id }.toSet()
    val kept = project.items.filter { it.id !in dropIds }.map { remap[it.id] ?: it }
    val placeholders = (pool.size until tpl.cells.size).map { placeholder(block.id, it) }
    return project.copy(
        blocks = project.blocks.filter { it.id !in oldIds } + block,
        items = placeholders + kept,
    )
}

/** Fill the placeholder cell [itemId] with [m]. */
fun fillPlaceholder(project: Project, itemId: String, m: ImportedMedia): Project =
    project.copy(items = project.items.map { if (it.id == itemId) mediaItem(m, it) else it })

/** Add media: fill empty cells first (in slide order), otherwise drop free items onto [slide]. */
fun addMedia(project: Project, media: List<ImportedMedia>, slide: Int): Project {
    var p = project
    var n = 0
    for (m in media) {
        val empty = p.items
            .filter { it.isBound() && it.media == null }
            .sortedWith(compareBy({ p.block(it.blockId)?.start ?: 0 }, { it.cell }))
            .firstOrNull()
        if (empty != null) {
            p = fillPlaceholder(p, empty.id, m)
        } else {
            val aspect = if (m.height > 0) m.width.toFloat() / m.height else 1f
            val maxW = SLIDE_W * 0.7f
            val maxH = p.ratio.slideH * 0.6f
            val w = min(maxW, maxH * aspect)
            val h = w / aspect
            val cx = slide * SLIDE_W + SLIDE_W / 2 + n * 30f
            val cy = p.ratio.slideH / 2 + n * 30f
            val item = mediaItem(m).copy(x = cx - w / 2, y = cy - h / 2, w = w, h = h)
            p = p.copy(items = p.items + item)
            n++
        }
    }
    return p
}

fun Project.touch(): Project = copy(updatedAt = System.currentTimeMillis())

/** Free a bound item at its current frame so it can be moved or resized by hand. */
fun Project.detach(itemId: String): Project {
    val it = items.firstOrNull { x -> x.id == itemId } ?: return this
    if (!it.isBound()) return this
    val r = rectOf(it)
    val rad = radiusOf(it)
    return copy(items = items.map { x ->
        if (x.id == itemId) x.copy(blockId = null, cell = -1, x = r.left, y = r.top, w = r.width, h = r.height, radius = rad) else x
    })
}
