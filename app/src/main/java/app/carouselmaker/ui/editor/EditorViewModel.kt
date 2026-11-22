package app.carouselmaker.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.carouselmaker.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Guide(val vertical: Boolean, val pos: Float)

class EditorViewModel(val projectId: String) : ViewModel() {
    var project by mutableStateOf<Project?>(null)
        private set
    var selectedId by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var guides by mutableStateOf<List<Guide>>(emptyList())

    // viewport: screen = offset + canvas * scale
    var scale by mutableStateOf(0.3f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(Size.Zero)
    private var viewportInitialised = false

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var gestureStart: Project? = null
    private var saveJob: Job? = null

    private var density = 3f

    init {
        viewModelScope.launch {
            project = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppGraph.repo.load(projectId) }
            if (viewport.width > 0) onViewport(viewport, density)
        }
    }

    // ---------- viewport ----------
    fun onViewport(size: Size, density: Float) {
        viewport = size
        this.density = density
        val p = project ?: return
        if (!viewportInitialised && size.width > 0) {
            viewportInitialised = true
            scale = minOf(size.width * 0.66f / SLIDE_W, size.height * 0.8f / p.canvasH)
            offset = Offset(16f * density, (size.height - p.canvasH * scale) / 2)
        }
    }

    fun minScale(): Float {
        val p = project ?: return 0.05f
        return minOf(viewport.width / p.canvasW, viewport.height / p.canvasH).coerceAtLeast(0.02f) * 0.8f
    }

    fun maxScale(): Float = 3f

    fun zoomAround(centroid: Offset, zoom: Float, pan: Offset) {
        val ns = (scale * zoom).coerceIn(minScale(), maxScale())
        val k = ns / scale
        offset = centroid - (centroid - offset) * k + pan
        scale = ns
    }

    fun toCanvas(screen: Offset): Offset = (screen - offset) / scale
    fun toScreen(canvas: Offset): Offset = canvas * scale + offset

    fun currentSlide(): Int {
        val p = project ?: return 0
        val cx = toCanvas(Offset(viewport.width / 2, 0f)).x
        return (cx / SLIDE_W).toInt().coerceIn(0, p.slideCount - 1)
    }

    fun scrollToSlide(i: Int) {
        val p = project ?: return
        val target = Offset(i * SLIDE_W + SLIDE_W / 2, p.canvasH / 2)
        offset = Offset(viewport.width / 2, viewport.height / 2) - target * scale
    }

    // ---------- project mutation ----------
    /** Apply [f] as one undoable step and autosave. */
    fun commit(f: (Project) -> Project) {
        val cur = project ?: return
        val next = f(cur)
        if (next == cur) return
        pushUndo(cur)
        project = next
        scheduleSave()
    }

    /** Replace the project without recording undo (used mid-gesture). */
    fun live(f: (Project) -> Project) {
        val cur = project ?: return
        project = f(cur)
    }

    fun beginGesture() {
        gestureStart = project
    }

    fun endGesture() {
        val start = gestureStart
        gestureStart = null
        guides = emptyList()
        val cur = project
        if (start != null && cur != null && start != cur) {
            pushUndo(start)
            scheduleSave()
        }
    }

    private fun pushUndo(p: Project) {
        undoStack.addLast(p)
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        project?.let { redoStack.addLast(it) }
        project = prev
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        if (selectedId != null && prev.items.none { it.id == selectedId }) selectedId = null
        scheduleSave()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        project?.let { undoStack.addLast(it) }
        project = next
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        if (selectedId != null && next.items.none { it.id == selectedId }) selectedId = null
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            project?.let { AppGraph.repo.save(it.touch()) }
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        project?.let { AppGraph.repo.saveNow(it.touch()) }
    }

    override fun onCleared() {
        saveNow()
    }

    // ---------- item helpers ----------
    fun item(id: String?): Item? = project?.items?.firstOrNull { it.id == id }

    fun mutate(id: String, f: (Item) -> Item) = commit { p ->
        p.copy(items = p.items.map { if (it.id == id) f(it) else it })
    }

    fun liveItem(id: String, f: (Item) -> Item) = live { p ->
        p.copy(items = p.items.map { if (it.id == id) f(it) else it })
    }

    fun detachLive(id: String) = live { it.detach(id) }

    fun addItem(item: Item, select: Boolean = true) {
        commit { it.copy(items = it.items + item) }
        if (select) selectedId = item.id
    }

    /** Add [proto] centred in the current view and select it. */
    fun addAtCenter(proto: Item) {
        val c = toCanvas(Offset(viewport.width / 2, viewport.height / 2))
        addItem(proto.copy(x = c.x - proto.w / 2, y = c.y - proto.h / 2))
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        val it = item(id) ?: return
        if (it.isBound() && it.isMedia()) {
            // keep the layout cell, just empty it
            mutate(id) { x -> x.copy(media = null, type = ItemType.PHOTO, mw = 0, mh = 0) }
        } else {
            commit { p -> p.copy(items = p.items.filter { x -> x.id != id }) }
            selectedId = null
        }
    }

    fun duplicateSelected() {
        val p = project ?: return
        val src = item(selectedId) ?: return
        val r = p.rectOf(src)
        val copy = src.copy(
            id = newId(), blockId = null, cell = -1,
            x = r.left + 40f, y = r.top + 40f, w = r.width, h = r.height, radius = p.radiusOf(src),
            seam = -1,
        )
        addItem(copy)
    }

    fun bringToFront() {
        val id = selectedId ?: return
        commit { p ->
            val it = p.items.firstOrNull { x -> x.id == id } ?: return@commit p
            p.copy(items = p.items.filter { x -> x.id != id } + it)
        }
    }

    fun sendToBack() {
        val id = selectedId ?: return
        commit { p ->
            val it = p.items.firstOrNull { x -> x.id == id } ?: return@commit p
            p.copy(items = listOf(it) + p.items.filter { x -> x.id != id })
        }
    }

    fun flipSelected() {
        val id = selectedId ?: return
        mutate(id) { it.copy(flipH = !it.flipH) }
    }

    fun addSlide(after: Int) = commit { p ->
        val s = (after + 1).coerceIn(0, p.slideCount)
        shiftSlides(p, s, +1).let { q ->
            q.copy(slides = q.slides.toMutableList().apply { add(s, Slide(p.slides.getOrNull(after)?.background ?: Slide().background)) })
        }
    }

    /** Move everything at or after slide [from] by [delta] slides (used when inserting). */
    private fun shiftSlides(p: Project, from: Int, delta: Int): Project {
        val dx = delta * SLIDE_W
        val thresh = from * SLIDE_W
        return p.copy(
            blocks = p.blocks.map { if (it.start >= from) it.copy(start = it.start + delta) else it },
            items = p.items.map {
                if (it.blockId == null && it.x + it.w / 2 >= thresh) it.copy(x = it.x + dx, seam = if (it.seam >= from) it.seam + delta else it.seam) else it
            },
        )
    }
}
