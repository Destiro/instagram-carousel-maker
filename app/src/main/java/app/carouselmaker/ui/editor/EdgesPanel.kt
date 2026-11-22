package app.carouselmaker.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.render.drawTape
import app.carouselmaker.render.edgePath
import app.carouselmaker.ui.common.IconBtn
import app.carouselmaker.ui.common.Segmented
import app.carouselmaker.ui.theme.Palette

private val EDGE_TYPES = listOf(
    EdgeType.NONE to "None", EdgeType.TORN to "Torn", EdgeType.DECKLE to "Deckle",
    EdgeType.WAVE to "Wavy", EdgeType.ZIGZAG to "Pinking", EdgeType.FADE to "Fade",
)

@Composable
fun EdgesPanel(vm: EditorViewModel, startTab: Int, onDone: () -> Unit) {
    var tab by remember { mutableIntStateOf(startTab) }
    val id = vm.selectedId
    val item = vm.item(id)
    val photo = item != null && (item.type == ItemType.PHOTO || item.type == ItemType.VIDEO) && item.media != null

    // edits to a photo edge are live and form one undo step
    LaunchedEffect(id, tab) { if (tab == 0) vm.beginGesture() }
    DisposableEffect(id, tab) { onDispose { if (tab == 0) vm.endGesture() } }

    SheetFrame(
        if (tab == 0) "Edges" else "Transitions", onDone, Modifier.height(430.dp),
        trailing = {
            if (tab == 0 && photo && id != null) {
                IconBtn(Icons.Filled.Shuffle, "Shuffle the tear", { vm.liveItem(id) { it.copy(edge = it.edge.copy(seed = it.edge.seed + 1)) } }, tint = Palette.Text)
            }
        },
    ) {
        Segmented(listOf("Photo edge", "Between slides"), tab, { tab = it }, Modifier.fillMaxWidth())
        if (tab == 0) {
            if (!photo || id == null || item == null) {
                Text("Tap a photo on the canvas first, then choose how its edge looks.", Modifier.padding(top = 24.dp), color = Palette.TextDim, fontSize = 14.sp)
            } else EdgeControls(vm, id, item.edge, Modifier.weight(1f))
        } else SeamControls(vm, Modifier.weight(1f))
    }
}

@Composable
private fun EdgeControls(vm: EditorViewModel, id: String, edge: Edge, modifier: Modifier = Modifier) {
    fun edit(f: (Edge) -> Edge) = vm.liveItem(id) { it.copy(edge = f(it.edge)) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in EDGE_TYPES.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((type, label) in row) {
                    val on = edge.type == type
                    Column(
                        Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Surface2)
                            .border(if (on) 2.dp else 0.dp, if (on) Palette.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable {
                                // choosing a style for the first time picks a sensible side
                                edit { e -> e.copy(type = type, right = if (!e.left && !e.top && !e.right && !e.bottom) true else e.right) }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        EdgeSample(type)
                        Text(label, Modifier.padding(top = 6.dp), color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sides", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SideChip("L", edge.left) { edit { e -> e.copy(left = !e.left) } }
                SideChip("T", edge.top) { edit { e -> e.copy(top = !e.top) } }
                SideChip("R", edge.right) { edit { e -> e.copy(right = !e.right) } }
                SideChip("B", edge.bottom) { edit { e -> e.copy(bottom = !e.bottom) } }
            }
        }
        LabeledSlider("Roughness", edge.roughness.toFloat(), 0f, 100f, { v -> edit { it.copy(roughness = v.toInt()) } }) {}
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Paper fibres", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("White ripped-paper rim on torn edges", color = Palette.TextDim, fontSize = 12.sp)
            }
            Switch(edge.rim, { on -> edit { it.copy(rim = on) } }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.OnAccent))
        }
    }
}

@Composable
private fun SideChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.Accent else Palette.Surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) Palette.OnAccent else Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun EdgeSample(type: EdgeType) {
    Canvas(Modifier.size(52.dp, 28.dp)) {
        val r = Rect(Offset.Zero, size)
        val col = Color(0xFF7A8B99)
        when (type) {
            EdgeType.NONE -> drawRect(col, r.topLeft, r.size)
            EdgeType.FADE -> {
                drawRect(col, r.topLeft, r.size)
                drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), r.right - r.width * 0.5f, r.right), Offset(r.right - r.width * 0.5f, 0f), Size(r.width * 0.5f, r.height), blendMode = BlendMode.DstIn)
            }
            else -> drawPath(edgePath(r, Edge(type = type, right = true, roughness = 60, seed = 3), scale = 0.22f), col)
        }
    }
}

@Composable
private fun SeamControls(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val p = vm.project ?: return
    if (p.slideCount < 2) {
        Text("Add a second slide to decorate the cut between slides.", Modifier.padding(top = 24.dp), color = Palette.TextDim, fontSize = 14.sp)
        return
    }
    var seam by remember { mutableIntStateOf(vm.currentSlide().coerceIn(0, p.slideCount - 2)) }
    var all by remember { mutableStateOf(false) }
    val active = p.seamKind(seam)
    LaunchedEffect(seam) { vm.scrollToSlide(seam) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Filled.ChevronLeft, "Previous seam", { seam = (seam - 1).coerceAtLeast(0) }, enabled = seam > 0)
            Text(
                "Cut between slide ${seam + 1} and ${seam + 2}", Modifier.weight(1f),
                color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            IconBtn(Icons.Filled.ChevronRight, "Next seam", { seam = (seam + 1).coerceAtMost(p.slideCount - 2) }, enabled = seam < p.slideCount - 2)
        }
        for (row in SeamKind.entries.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in row) {
                    val on = active == k
                    Column(
                        Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Surface2)
                            .border(if (on) 2.dp else 0.dp, if (on) Palette.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { vm.commit { if (all) applySeamAll(it, k) else applySeam(it, seam, k) } },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        SeamSample(k)
                        Text(k.label, Modifier.padding(top = 6.dp), color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Apply to every cut", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(all, { all = it }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.OnAccent))
        }
    }
}

@Composable
private fun SeamSample(kind: SeamKind) {
    Canvas(Modifier.size(52.dp, 28.dp)) {
        val a = Color(0xFFB08A6E)
        val b = Color(0xFF7A8B99)
        drawRect(a, Offset.Zero, Size(size.width / 2, size.height))
        drawRect(b, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
        val cx = size.width / 2
        when (kind) {
            SeamKind.CLEAN -> Unit
            SeamKind.TORN -> drawRect(Color(0xFFFFFDF7), Offset(cx - 2f, 0f), Size(4f, size.height))
            SeamKind.STRIP -> drawRect(Color(0xFFFFFDF7), Offset(cx - 7f, 0f), Size(14f, size.height))
            SeamKind.SHADOW -> drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x99000000), Color.Transparent), cx - 14f, cx + 14f), Offset(cx - 14f, 0f), Size(28f, size.height))
            SeamKind.TAPED -> {
                drawTape(Item(id = "a", type = ItemType.TAPE, style = "masking"), Rect(cx - 16f, 2f, cx + 16f, 12f))
                drawTape(Item(id = "b", type = ItemType.TAPE, style = "pink"), Rect(cx - 16f, size.height - 12f, cx + 16f, size.height - 2f))
            }
        }
    }
}

