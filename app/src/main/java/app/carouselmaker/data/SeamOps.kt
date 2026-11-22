package app.carouselmaker.data

enum class SeamKind(val label: String) {
    CLEAN("Clean cut"),
    TORN("Torn"),
    TAPED("Taped"),
    STRIP("Paper strip"),
    SHADOW("Fold shadow"),
}

/** Replace the decoration on the seam between slide [seam] and [seam]+1 with [kind]. */
fun applySeam(p: Project, seam: Int, kind: SeamKind): Project {
    if (seam < 0 || seam >= p.slideCount - 1) return p
    val kept = p.items.filter { it.seam != seam }
    val x = (seam + 1) * SLIDE_W
    val h = p.canvasH
    fun paper(style: String, w: Float) = Item(
        id = newId(), type = ItemType.PAPER, style = style, seam = seam,
        x = x - w / 2, y = -6f, w = w, h = h + 12f,
    )
    fun tape(style: String, y: Float, rot: Float) = Item(
        id = newId(), type = ItemType.TAPE, style = style, seam = seam,
        x = x - 190f, y = y, w = 380f, h = 90f, rotation = rot,
    )
    val added = when (kind) {
        SeamKind.CLEAN -> emptyList()
        SeamKind.TORN -> listOf(paper("strip", 36f))
        SeamKind.STRIP -> listOf(paper("strip", 120f))
        SeamKind.SHADOW -> listOf(paper("shadow", 150f))
        SeamKind.TAPED -> listOf(tape("masking", h * 0.07f, -8f), tape("pink", h * 0.84f, 6f))
    }
    return p.copy(items = kept + added)
}

fun applySeamAll(p: Project, kind: SeamKind): Project {
    var q = p
    for (i in 0 until p.slideCount - 1) q = applySeam(q, i, kind)
    return q
}

/** Which kind is currently on the seam, or CLEAN. */
fun Project.seamKind(seam: Int): SeamKind {
    val on = items.filter { it.seam == seam }
    return when {
        on.isEmpty() -> SeamKind.CLEAN
        on.any { it.type == ItemType.TAPE } -> SeamKind.TAPED
        on.any { it.style == "shadow" } -> SeamKind.SHADOW
        on.any { it.w > 80f } -> SeamKind.STRIP
        else -> SeamKind.TORN
    }
}
