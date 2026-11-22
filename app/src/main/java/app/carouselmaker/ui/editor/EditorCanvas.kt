package app.carouselmaker.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.render.RenderCtx
import app.carouselmaker.render.drawScene
import app.carouselmaker.render.frameOf
import app.carouselmaker.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class HandleKind(val sx: Int, val sy: Int) {
    TL(-1, -1), T(0, -1), TR(1, -1), L(-1, 0), R(1, 0), BL(-1, 1), B(0, 1), BR(1, 1), ROT(0, 0)
}

fun handlesFor(item: Item): List<HandleKind> {
    val h = HandleKind.entries
    fun pick(vararg k: HandleKind) = k.toList()
    return when (item.type) {
        ItemType.PHOTO, ItemType.VIDEO -> h
        ItemType.TEXT -> pick(HandleKind.TL, HandleKind.TR, HandleKind.BL, HandleKind.BR, HandleKind.L, HandleKind.R, HandleKind.ROT)
        ItemType.TAPE -> pick(HandleKind.L, HandleKind.R, HandleKind.ROT)
        else -> pick(HandleKind.TL, HandleKind.TR, HandleKind.BL, HandleKind.BR, HandleKind.ROT)
    }
}

fun rotVec(v: Offset, deg: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    val c = cos(r)
    val s = sin(r)
    return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
}

/** Canvas-space position of a local offset from the item's centre. */
private fun worldPoint(frame: Rect, rotation: Float, local: Offset) = frame.center + rotVec(local, rotation)

fun hitItem(p: Project, pt: Offset): Item? {
    val extras = app.carouselmaker.data.AppGraph.extras
    for (item in p.items.asReversed()) {
        val fr = p.frameOf(item, extras)
        val local = rotVec(pt - fr.center, -item.rotation)
        if (abs(local.x) <= fr.width / 2 && abs(local.y) <= fr.height / 2) return item
    }
    return null
}

/** New item geometry after dragging [h] to [pointer] (canvas units), starting from [item] with [frame]. */
fun resizeItem(item: Item, frame: Rect, h: HandleKind, pointer: Offset): Item {
    val hw = frame.width / 2
    val hh = frame.height / 2
    val pl = rotVec(pointer - frame.center, -item.rotation)
    val minS = 40f
    var x0 = -hw; var x1 = hw; var y0 = -hh; var y1 = hh
    if (h.sx != 0 && h.sy != 0) {
        val ax = -h.sx * hw
        val ay = -h.sy * hh
        val dx = 2 * h.sx * hw
        val dy = 2 * h.sy * hh
        var s = ((pl.x - ax) * dx + (pl.y - ay) * dy) / (dx * dx + dy * dy)
        s = max(s, minS / min(2 * hw, 2 * hh))
        val cx = ax + s * dx
        val cy = ay + s * dy
        x0 = min(ax, cx); x1 = max(ax, cx); y0 = min(ay, cy); y1 = max(ay, cy)
    } else {
        if (h.sx != 0) {
            val ax = -h.sx * hw
            val ex = if (h.sx > 0) max(pl.x, ax + minS) else min(pl.x, ax - minS)
            x0 = min(ax, ex); x1 = max(ax, ex)
        }
        if (h.sy != 0) {
            val ay = -h.sy * hh
            val ey = if (h.sy > 0) max(pl.y, ay + minS) else min(pl.y, ay - minS)
            y0 = min(ay, ey); y1 = max(ay, ey)
        }
    }
    val nw = x1 - x0
    val nh = y1 - y0
    val c = frame.center + rotVec(Offset((x0 + x1) / 2, (y0 + y1) / 2), item.rotation)
    var out = item.copy(x = c.x - nw / 2, y = c.y - nh / 2, w = nw, h = nh)
    if (item.type == ItemType.TEXT && h.sx != 0 && h.sy != 0) out = out.copy(fontSize = (item.fontSize * nw / frame.width).coerceIn(16f, 600f))
    return out
}

/** Snap a moving rectangle to margins, centres and slide edges. Returns the correction and guides. */
fun snapRect(p: Project, r: Rect, threshold: Float): Triple<Float, Float, List<Guide>> {
    val m = p.blocks.firstOrNull()?.margin ?: 60f
    val xs = buildList {
        for (s in 0 until p.slideCount) {
            add(s * SLIDE_W); add(s * SLIDE_W + m); add(s * SLIDE_W + SLIDE_W / 2)
            add((s + 1) * SLIDE_W - m); add((s + 1) * SLIDE_W)
        }
    }
    val ys = listOf(0f, m, p.canvasH / 2, p.canvasH - m, p.canvasH)
    fun best(cands: List<Float>, pts: List<Float>): Pair<Float, Float>? {
        var bd = Float.MAX_VALUE
        var line = 0f
        for (pt in pts) for (c in cands) {
            val d = c - pt
            if (abs(d) < abs(bd)) { bd = d; line = c }
        }
        return if (abs(bd) <= threshold) bd to line else null
    }
    val bx = best(xs, listOf(r.left, r.center.x, r.right))
    val by = best(ys, listOf(r.top, r.center.y, r.bottom))
    val guides = buildList {
        bx?.let { add(Guide(true, it.second)) }
        by?.let { add(Guide(false, it.second)) }
    }
    return Triple(bx?.first ?: 0f, by?.first ?: 0f, guides)
}

@Composable
fun EditorCanvas(
    vm: EditorViewModel,
    onPlaceholderTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .fillMaxSize()
            .testTag("editor_canvas")
            .onSizeChanged { vm.onViewport(Size(it.width.toFloat(), it.height.toFloat()), density) }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val handleR = 30f * density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val p0 = vm.project ?: return@awaitEachGesture
                    val extras = AppGraph.extras

                    // what did we touch?
                    var handle: HandleKind? = null
                    val sel0 = vm.item(vm.selectedId)
                    if (sel0 != null) {
                        val fr = p0.frameOf(sel0, extras)
                        for (hk in handlesFor(sel0)) {
                            val sp = handleScreenPos(vm, sel0, fr, hk, density)
                            if ((sp - down.position).getDistance() <= handleR) { handle = hk; break }
                        }
                    }
                    val hit = if (handle == null) hitItem(p0, vm.toCanvas(down.position)) else null

                    vm.beginGesture()
                    var moved = false
                    var pinching = false
                    var total = Offset.Zero
                    var dragId: String? = null
                    var startItem: Item? = null
                    var startFrame: Rect = Rect.Zero
                    var detached = false

                    fun prepareDrag(id: String) {
                        if (!detached) {
                            vm.detachLive(id)
                            detached = true
                        }
                        val pp = vm.project ?: return
                        startItem = vm.item(id)
                        startFrame = startItem?.let { pp.frameOf(it, extras) } ?: Rect.Zero
                    }

                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            pinching = true
                            moved = true
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val rotD = ev.calculateRotation()
                            val cen = ev.calculateCentroid()
                            val selId = vm.selectedId
                            val sel = vm.item(selId)
                            val pp = vm.project
                            if (sel != null && pp != null &&
                                run {
                                    val fr = pp.frameOf(sel, extras)
                                    val l = rotVec(vm.toCanvas(cen) - fr.center, -sel.rotation)
                                    abs(l.x) <= fr.width / 2 + 40f && abs(l.y) <= fr.height / 2 + 40f
                                }
                            ) {
                                if (!detached) { vm.detachLive(sel.id); detached = true }
                                val cur = vm.item(sel.id)!!
                                val fr = vm.project!!.frameOf(cur, extras)
                                val nw = (cur.w * zoom).coerceIn(40f, 6000f)
                                val k = nw / cur.w
                                val nh = cur.h * k
                                val c = fr.center + pan / vm.scale
                                vm.liveItem(sel.id) {
                                    it.copy(
                                        x = c.x - nw / 2, y = c.y - nh / 2, w = nw, h = nh,
                                        rotation = it.rotation + rotD,
                                        fontSize = if (it.type == ItemType.TEXT) (it.fontSize * k).coerceIn(16f, 600f) else it.fontSize,
                                    )
                                }
                            } else {
                                vm.zoomAround(cen, zoom, pan)
                            }
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressed == 1 && !pinching) {
                            val ch = ev.changes.firstOrNull { it.pressed }
                            if (ch != null) {
                                val d = ch.positionChange()
                                total += d
                                if (!moved && total.getDistance() > slop) {
                                    moved = true
                                    if (handle != null) {
                                        dragId = vm.selectedId
                                        dragId?.let { prepareDrag(it) }
                                    } else if (hit != null) {
                                        vm.selectedId = hit.id
                                        dragId = hit.id
                                        prepareDrag(hit.id)
                                    }
                                }
                                if (moved) {
                                    val id = dragId
                                    val si = startItem
                                    if (id != null && si != null && handle != null) {
                                        val pc = vm.toCanvas(ch.position)
                                        if (handle == HandleKind.ROT) {
                                            val ang = atan2(pc.y - startFrame.center.y, pc.x - startFrame.center.x) * 180f / PI.toFloat() + 90f
                                            var a = ((ang % 360f) + 360f) % 360f
                                            if (a > 180f) a -= 360f
                                            for (t in listOf(-180f, -135f, -90f, -45f, 0f, 45f, 90f, 135f, 180f)) if (abs(a - t) < 3.5f) a = t
                                            vm.liveItem(id) { it.copy(rotation = a) }
                                        } else {
                                            val ni = resizeItem(si, startFrame, handle, pc)
                                            vm.liveItem(id) { ni }
                                        }
                                    } else if (id != null && si != null) {
                                        val dc = total / vm.scale
                                        var r = startFrame.translate(dc)
                                        val (sx, sy, g) = snapRect(vm.project!!, r, 10f * density / vm.scale)
                                        r = r.translate(Offset(sx, sy))
                                        vm.guides = g
                                        vm.liveItem(id) { it.copy(x = r.left, y = r.top) }
                                    } else {
                                        vm.offset += d
                                    }
                                }
                                if (ch.positionChanged()) ch.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })

                    if (!moved) {
                        if (hit != null) {
                            if (hit.media == null && hit.type == ItemType.PHOTO && hit.isBound()) onPlaceholderTap(hit.id)
                            else vm.selectedId = hit.id
                        } else if (handle == null) {
                            vm.selectedId = null
                        }
                    }
                    vm.endGesture()
                }
            },
    ) {
        val p = vm.project ?: return@Canvas
        val sc = vm.scale
        val ctx = RenderCtx(
            p, AppGraph.images,
            maxDim = if (sc * density > 0.9f) 2200 else 1400,
            editing = true,
            extras = AppGraph.extras,
        )
        withTransform({
            translate(vm.offset.x, vm.offset.y)
            scale(sc, sc, Offset.Zero)
        }) {
            // soft backdrop shadow so the canvas reads against the dark bg
            drawRect(Color(0x66000000), Offset(0f, 8f / sc), Size(p.canvasW, p.canvasH))
            drawScene(ctx)
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f / sc * density, 10f / sc * density))
            for (i in 1 until p.slideCount) {
                drawLine(
                    Palette.Accent.copy(alpha = 0.9f), Offset(i * SLIDE_W, -6f / sc), Offset(i * SLIDE_W, p.canvasH + 6f / sc),
                    strokeWidth = 1.5f * density / sc, pathEffect = dash,
                )
            }
            for (g in vm.guides) {
                if (g.vertical) drawLine(Palette.Snap, Offset(g.pos, -20f / sc), Offset(g.pos, p.canvasH + 20f / sc), strokeWidth = 1.5f * density / sc)
                else drawLine(Palette.Snap, Offset(-20f / sc, g.pos), Offset(p.canvasW + 20f / sc, g.pos), strokeWidth = 1.5f * density / sc)
            }
        }
        // slide numbers above the canvas (measured explicitly so off-screen labels cannot break constraints)
        val numStyle = TextStyle(color = Palette.TextDim, fontSize = 12.sp)
        for (i in 0 until p.slideCount) {
            val pos = vm.toScreen(Offset(i * SLIDE_W, 0f))
            if (pos.x < -SLIDE_W * sc || pos.x > size.width) continue
            val layout = measurer.measure("${i + 1}", numStyle)
            drawText(layout, topLeft = Offset(pos.x + 2f * density, pos.y - 20f * density))
        }
        drawSelection(vm, p, measurer, density)
    }
}

private fun handleScreenPos(vm: EditorViewModel, item: Item, frame: Rect, h: HandleKind, density: Float): Offset {
    return if (h == HandleKind.ROT) {
        val top = worldPoint(frame, item.rotation, Offset(0f, -frame.height / 2))
        vm.toScreen(top) + rotVec(Offset(0f, -34f * density), item.rotation)
    } else {
        vm.toScreen(worldPoint(frame, item.rotation, Offset(h.sx * frame.width / 2, h.sy * frame.height / 2)))
    }
}

private fun DrawScope.drawSelection(vm: EditorViewModel, p: Project, measurer: androidx.compose.ui.text.TextMeasurer, density: Float) {
    val item = vm.item(vm.selectedId) ?: return
    val fr = p.frameOf(item, AppGraph.extras)
    val corners = listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f)).map {
        vm.toScreen(worldPoint(fr, item.rotation, Offset(it.x * fr.width / 2, it.y * fr.height / 2)))
    }
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(corners[0].x, corners[0].y)
        for (i in 1..3) lineTo(corners[i].x, corners[i].y)
        close()
    }
    drawPath(path, Palette.Accent, style = Stroke(width = 2f * density))
    val handles = handlesFor(item)
    if (HandleKind.ROT in handles) {
        val top = vm.toScreen(worldPoint(fr, item.rotation, Offset(0f, -fr.height / 2)))
        val rp = handleScreenPos(vm, item, fr, HandleKind.ROT, density)
        drawLine(Palette.Accent, top, rp, strokeWidth = 2f * density)
        drawCircle(Color.White, 11f * density, rp)
        drawCircle(Palette.Accent, 11f * density, rp, style = Stroke(2f * density))
    }
    for (h in handles) {
        if (h == HandleKind.ROT) continue
        val sp = handleScreenPos(vm, item, fr, h, density)
        val isCorner = h.sx != 0 && h.sy != 0
        if (isCorner) {
            drawCircle(Color.White, 8f * density, sp)
            drawCircle(Palette.Accent, 8f * density, sp, style = Stroke(2f * density))
        } else {
            val w = if (h.sx != 0) 8f * density else 20f * density
            val hh = if (h.sx != 0) 20f * density else 8f * density
            drawRoundRect(Color.White, Offset(sp.x - w / 2, sp.y - hh / 2), Size(w, hh), CornerRadius(4f * density))
            drawRoundRect(Palette.Accent, Offset(sp.x - w / 2, sp.y - hh / 2), Size(w, hh), CornerRadius(4f * density), style = Stroke(2f * density))
        }
    }
    // size readout
    if (item.type != ItemType.TEXT) {
        val label = "${fr.width.roundToInt()} × ${fr.height.roundToInt()} px"
        val tl = measurer.measure(label, TextStyle(color = Color.White, fontSize = 11.sp))
        val c = vm.toScreen(fr.center + Offset(0f, fr.height / 2)) + Offset(0f, -18f * density)
        val tp = Offset(c.x - tl.size.width / 2f, c.y - tl.size.height / 2f)
        drawRoundRect(Color(0xCC08080A), tp - Offset(8f * density, 4f * density), Size(tl.size.width + 16f * density, tl.size.height + 8f * density), CornerRadius(12f * density))
        drawText(tl, topLeft = tp)
    }
}
