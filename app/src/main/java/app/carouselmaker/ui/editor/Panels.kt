package app.carouselmaker.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.ui.common.Segmented
import app.carouselmaker.ui.common.SheetHandle
import app.carouselmaker.ui.theme.Palette

enum class Panel { NONE, LAYOUTS, SPACING, STICKERS, TEXT, EDGES, SLIDES, SEAMS }

/** Rounded bottom sheet frame used by every editor panel. */
@Composable
fun SheetFrame(
    title: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Palette.Surface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            trailing()
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onDone).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Done", color = Palette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        content()
    }
}

// ---------------------------------------------------------------- layouts

@Composable
fun LayoutsPanel(vm: EditorViewModel, onDone: () -> Unit) {
    val project = vm.project ?: return
    var span by remember { mutableIntStateOf(1) }
    var all by remember { mutableStateOf(false) }
    val cur = vm.currentSlide()
    val currentBlock = project.blocks.firstOrNull { cur >= it.start && cur < it.start + it.span }

    fun apply(templateId: String) {
        vm.commit { p0 ->
            var p = p0
            // make sure there are enough slides
            while (p.slideCount < span) p = p.copy(slides = p.slides + Slide(p.slides.last().background))
            if (all) {
                var s = 0
                while (s + span <= p.slideCount) {
                    p = applyTemplate(p, s, span, templateId)
                    s += span
                }
            } else {
                val start = if (span == 1) cur else cur.coerceAtMost(p.slideCount - span)
                p = applyTemplate(p, start, span, templateId)
            }
            p
        }
    }

    SheetFrame("Layouts", onDone, Modifier.height(340.dp)) {
        Segmented(listOf("1 slide", "2-slide span", "3-slide span"), span - 1, { span = it + 1 }, Modifier.fillMaxWidth())
        val templates = Templates.forSpan(span)
        val cols = when (span) { 1 -> 4; 2 -> 3; else -> 2 }
        LazyVerticalGrid(
            GridCells.Fixed(cols),
            Modifier.weight(1f).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(templates, key = { it.id }) { t ->
                val selected = currentBlock?.templateId == t.id && currentBlock.span == span
                TemplateThumb(t, project.ratio, selected, Modifier.testTag("template_${t.id}").clickable { apply(t.id) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Apply to", Modifier.weight(1f), color = Palette.TextDim, fontSize = 13.sp)
            Segmented(
                listOf(if (span == 1) "This slide" else "Here", if (span == 1) "Every slide" else "Repeat"),
                if (all) 1 else 0, { all = it == 1 }, Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun TemplateThumb(t: Template, ratio: Ratio, selected: Boolean, modifier: Modifier = Modifier) {
    val aspect = (t.span * ratio.aw).toFloat() / ratio.ah
    Canvas(
        modifier.fillMaxWidth().aspectRatio(aspect)
            .clip(RoundedCornerShape(6.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Accent else Palette.Surface2, RoundedCornerShape(6.dp))
    ) {
        drawRect(Palette.Paper)
        val pad = 4.dp.toPx()
        val g = 1.5.dp.toPx()
        val aw = size.width - 2 * pad
        val ah = size.height - 2 * pad
        for (c in t.cells) {
            drawRect(
                if (selected) Color(0xFF8E9AA6) else Color(0xFFBDB8AE),
                Offset(pad + c.x * aw + g, pad + c.y * ah + g),
                Size(c.w * aw - 2 * g, c.h * ah - 2 * g),
            )
        }
        for (i in 1 until t.span) {
            val x = size.width * i / t.span
            drawLine(Color(0x59000000), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
        }
    }
}

// ---------------------------------------------------------------- spacing & background

private val BG_COLORS = listOf(
    0xFFFFFFFF, 0xFFF5F3EE, 0xFFE8DCCB, 0xFFCBD5C0, 0xFFD9E4EC, 0xFFF2D6D6,
    0xFFF5E6A8, 0xFF9CC3E6, 0xFFA8C686, 0xFFF28B9B, 0xFF26262D, 0xFF111111,
).map { it.toInt() }

@Composable
fun SpacingPanel(vm: EditorViewModel, onDone: () -> Unit) {
    val project = vm.project ?: return
    var tab by remember { mutableIntStateOf(0) }
    var applyAll by remember { mutableStateOf(true) }
    val ref = project.blocks.firstOrNull()
    var dragging by remember { mutableStateOf(false) }

    fun setSpacing(f: (LayoutBlock) -> LayoutBlock) {
        if (!dragging) { vm.beginGesture(); dragging = true }
        vm.live { p -> p.copy(blocks = p.blocks.map(f)) }
    }
    fun finish() { dragging = false; vm.endGesture() }

    SheetFrame(if (tab == 0) "Spacing" else "Background", onDone, Modifier.height(380.dp)) {
        Segmented(listOf("Spacing", "Background"), tab, { tab = it }, Modifier.fillMaxWidth())
        if (tab == 0) {
            if (ref == null) {
                Text("Add a layout first. Spacing applies to layout cells.", Modifier.padding(top = 24.dp), color = Palette.TextDim, fontSize = 14.sp)
            } else {
                Column(Modifier.padding(top = 8.dp)) {
                    LabeledSlider("Outer margin", ref.margin, 0f, 160f, { v -> setSpacing { it.copy(margin = v) } }, ::finish)
                    LabeledSlider("Gap between photos", ref.gap, 0f, 100f, { v -> setSpacing { it.copy(gap = v) } }, ::finish)
                    LabeledSlider("Corner radius", ref.radius, 0f, 120f, { v -> setSpacing { it.copy(radius = v) } }, ::finish)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Margins at slide seams", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (ref.seamGap) "On: a wide photo leaves a gap at each cut" else "Off: wide photos run straight across the cut",
                                color = Palette.TextDim, fontSize = 12.sp,
                            )
                        }
                        Switch(
                            ref.seamGap, { on -> vm.commit { p -> p.copy(blocks = p.blocks.map { it.copy(seamGap = on) }) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.OnAccent),
                        )
                    }
                }
            }
        } else {
            val cur = vm.currentSlide()
            val active = project.slides.getOrNull(cur)?.background
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    GridCells.Fixed(6), Modifier.height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(BG_COLORS) { c ->
                        Box(
                            Modifier.testTag("bg_$c").aspectRatio(1f).clip(CircleShape).background(Color(c))
                                .border(if (c == active) 3.dp else 1.dp, if (c == active) Palette.Accent else Color(0x33FFFFFF), CircleShape)
                                .clickable {
                                    vm.commit { p ->
                                        p.copy(slides = p.slides.mapIndexed { i, s -> if (applyAll || i == cur) s.copy(background = c) else s })
                                    }
                                },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply to", Modifier.weight(1f), color = Palette.TextDim, fontSize = 13.sp)
                    Segmented(listOf("This slide", "All slides"), if (applyAll) 1 else 0, { applyAll = it == 1 }, Modifier.width(220.dp))
                }
            }
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${value.toInt()}", color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value, onChange, valueRange = min..max, onValueChangeFinished = onFinished,
            colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Surface3),
        )
    }
}
