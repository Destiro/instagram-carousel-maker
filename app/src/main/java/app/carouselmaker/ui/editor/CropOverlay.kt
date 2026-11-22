package app.carouselmaker.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.ui.common.Pill
import app.carouselmaker.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CropMode(val label: String) { CELL("Fit frame"), FREE("Free"), ORIGINAL("Original"), SQ("1:1"), P45("4:5"), P34("3:4"), W169("16:9") }

@Composable
fun CropOverlay(vm: EditorViewModel, itemId: String, onClose: () -> Unit) {
    val project = vm.project ?: return onClose()
    val item = vm.item(itemId) ?: return onClose()
    val name = item.media ?: return onClose()
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val frame = project.rectOf(item)
    val frameAspect = frame.width / frame.height

    var box by remember { mutableStateOf(Size.Zero) }
    var imgRect by remember { mutableStateOf(Rect.Zero) }
    var crop by remember { mutableStateOf(Rect.Zero) }
    var mode by remember { mutableStateOf(CropMode.CELL) }
    var flipped by remember { mutableStateOf(item.flipH) }
    val img = AppGraph.images.get(project.id, name, 1800)
    AppGraph.images.version

    fun aspectOf(m: CropMode): Float? = when (m) {
        CropMode.CELL -> frameAspect
        CropMode.FREE -> null
        CropMode.ORIGINAL -> if (item.mh > 0) item.mw.toFloat() / item.mh else null
        CropMode.SQ -> 1f
        CropMode.P45 -> 4f / 5f
        CropMode.P34 -> 3f / 4f
        CropMode.W169 -> 16f / 9f
    }

    /** Largest rect of aspect [a] centred on [around], inside [bounds]. */
    fun fitRect(a: Float, around: Offset, bounds: Rect): Rect {
        var w = bounds.width
        var h = w / a
        if (h > bounds.height) { h = bounds.height; w = h * a }
        w *= 0.92f; h *= 0.92f
        val cx = around.x.coerceIn(bounds.left + w / 2, bounds.right - w / 2)
        val cy = around.y.coerceIn(bounds.top + h / 2, bounds.bottom - h / 2)
        return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    // lay out the image and place the initial crop once we know sizes
    LaunchedEffect(box, item.mw, item.mh, name) {
        if (box.width <= 0f || item.mw <= 0) return@LaunchedEffect
        val ia = item.mw.toFloat() / item.mh
        var w = box.width
        var h = w / ia
        if (h > box.height) { h = box.height; w = h * ia }
        val r = Rect((box.width - w) / 2, (box.height - h) / 2, (box.width + w) / 2, (box.height + h) / 2)
        imgRect = r
        crop = Rect(
            r.left + item.cropL * r.width, r.top + item.cropT * r.height,
            r.left + item.cropR * r.width, r.top + item.cropB * r.height,
        )
        // the stored crop is "centre-cropped to the frame" at draw time, so show that exact region
        val a = aspectOf(CropMode.CELL)!!
        val cw = crop.width; val ch = crop.height
        crop = if (cw / ch > a) {
            val nw = ch * a; Rect(crop.center.x - nw / 2, crop.top, crop.center.x + nw / 2, crop.bottom)
        } else {
            val nh = cw / a; Rect(crop.left, crop.center.y - nh / 2, crop.right, crop.center.y + nh / 2)
        }
    }

    fun pickMode(m: CropMode) {
        mode = m
        val a = aspectOf(m) ?: return
        if (imgRect != Rect.Zero) crop = fitRect(a, crop.center, imgRect)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF08080A)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Cancel", color = Color(0xFFD4D4D8), fontSize = 15.sp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Crop", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Accent).clickable {
                    if (imgRect == Rect.Zero) return@clickable
                    val l = ((crop.left - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                    val t = ((crop.top - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                    val r = ((crop.right - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                    val b = ((crop.bottom - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                    val a = crop.width / crop.height
                    vm.commit { p ->
                        var q = p.detachIfNeeded(itemId, mode != CropMode.CELL)
                        q.copy(items = q.items.map {
                            if (it.id != itemId) it else {
                                var n = it.copy(cropL = l, cropT = t, cropR = r, cropB = b, flipH = flipped)
                                if (mode != CropMode.CELL && !n.isBound()) {
                                    // frame follows the new crop ratio, same width and centre
                                    val c = Offset(n.x + n.w / 2, n.y + n.h / 2)
                                    val nh = n.w / a
                                    n = n.copy(y = c.y - nh / 2, h = nh)
                                }
                                n
                            }
                        })
                    }
                    onClose()
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Done", color = Palette.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp).onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(imgRect, mode) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val c0 = crop
                        val hit = 44f * density
                        val corners = listOf(c0.topLeft, c0.topRight, c0.bottomLeft, c0.bottomRight)
                        val ci = corners.indexOfFirst { (it - down.position).getDistance() < hit }
                        val moving = ci < 0 && c0.contains(down.position)
                        if (ci < 0 && !moving) return@awaitEachGesture
                        val anchor = if (ci >= 0) corners[3 - ci] else Offset.Zero
                        var startRect = c0
                        var accum = Offset.Zero
                        drag(down.id) { ch ->
                            accum += ch.positionChange()
                            val a = aspectOf(mode)
                            if (moving) {
                                var dx = accum.x; var dy = accum.y
                                dx = dx.coerceIn(imgRect.left - startRect.left, imgRect.right - startRect.right)
                                dy = dy.coerceIn(imgRect.top - startRect.top, imgRect.bottom - startRect.bottom)
                                crop = startRect.translate(dx, dy)
                            } else {
                                val p = corners[ci] + accum
                                crop = resizeCrop(anchor, p, a, imgRect)
                            }
                            ch.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (imgRect == Rect.Zero) return@Canvas
                if (img != null) {
                    drawImage(
                        img, IntOffset.Zero, IntSize(img.width, img.height),
                        IntOffset(imgRect.left.roundToInt(), imgRect.top.roundToInt()),
                        IntSize(imgRect.width.roundToInt(), imgRect.height.roundToInt()),
                    )
                } else drawRect(Color(0xFF3A3A42), imgRect.topLeft, imgRect.size)
                val dim = Color(0xA008080A)
                drawRect(dim, imgRect.topLeft, Size(imgRect.width, crop.top - imgRect.top))
                drawRect(dim, Offset(imgRect.left, crop.bottom), Size(imgRect.width, imgRect.bottom - crop.bottom))
                drawRect(dim, Offset(imgRect.left, crop.top), Size(crop.left - imgRect.left, crop.height))
                drawRect(dim, Offset(crop.right, crop.top), Size(imgRect.right - crop.right, crop.height))
                drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(1.5f * density))
                val grid = Color(0x73FFFFFF)
                for (i in 1..2) {
                    drawLine(grid, Offset(crop.left + crop.width * i / 3, crop.top), Offset(crop.left + crop.width * i / 3, crop.bottom), 1f * density)
                    drawLine(grid, Offset(crop.left, crop.top + crop.height * i / 3), Offset(crop.right, crop.top + crop.height * i / 3), 1f * density)
                }
                val L = 22f * density
                val sw = 4f * density
                fun corner(o: Offset, dx: Float, dy: Float) {
                    drawLine(Color.White, o, Offset(o.x + dx * L, o.y), sw)
                    drawLine(Color.White, o, Offset(o.x, o.y + dy * L), sw)
                }
                corner(crop.topLeft, 1f, 1f); corner(crop.topRight, -1f, 1f)
                corner(crop.bottomLeft, 1f, -1f); corner(crop.bottomRight, -1f, -1f)
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropMode.entries.forEach { m -> Pill(m.label, m == mode, { pickMode(m) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            ToolButton(Icons.Filled.RotateRight, "Rotate 90°") {
                scope.launch {
                    val m = AppGraph.importer.rotate90(project.id, name) ?: return@launch
                    vm.commit { p ->
                        p.copy(items = p.items.map {
                            if (it.id == itemId) it.copy(media = m.name, mw = m.width, mh = m.height, cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f) else it
                        })
                    }
                }
            }
            ToolButton(Icons.Filled.Flip, "Flip") { flipped = !flipped }
            ToolButton(Icons.Filled.Refresh, "Reset") {
                if (imgRect != Rect.Zero) crop = fitRect(aspectOf(mode) ?: (imgRect.width / imgRect.height), imgRect.center, imgRect).let {
                    if (mode == CropMode.FREE) imgRect else it
                }
            }
        }
        Box(Modifier.height(8.dp))
    }
}

private fun Project.detachIfNeeded(id: String, needed: Boolean): Project = if (needed) detach(id) else this

private fun resizeCrop(anchor: Offset, p: Offset, aspect: Float?, bounds: Rect): Rect {
    val minS = 64f
    val sx = if (p.x >= anchor.x) 1f else -1f
    val sy = if (p.y >= anchor.y) 1f else -1f
    val maxW = if (sx > 0) bounds.right - anchor.x else anchor.x - bounds.left
    val maxH = if (sy > 0) bounds.bottom - anchor.y else anchor.y - bounds.top
    var w = abs(p.x - anchor.x)
    var h = abs(p.y - anchor.y)
    if (aspect != null) {
        w = max(w, h * aspect)
        h = w / aspect
        if (w > maxW) { w = maxW; h = w / aspect }
        if (h > maxH) { h = maxH; w = h * aspect }
        w = max(w, minS); h = w / aspect
    } else {
        w = w.coerceIn(minS, max(minS, maxW))
        h = h.coerceIn(minS, max(minS, maxH))
    }
    val x2 = anchor.x + sx * w
    val y2 = anchor.y + sy * h
    return Rect(min(anchor.x, x2), min(anchor.y, y2), max(anchor.x, x2), max(anchor.y, y2))
}
