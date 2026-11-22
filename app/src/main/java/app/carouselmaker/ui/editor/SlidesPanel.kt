package app.carouselmaker.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.render.RenderCtx
import app.carouselmaker.render.drawScene
import app.carouselmaker.ui.theme.Palette
import androidx.compose.ui.graphics.Color

@Composable
fun SlidesPanel(vm: EditorViewModel, onDone: () -> Unit) {
    val project = vm.project ?: return
    var sel by remember { mutableIntStateOf(vm.currentSlide()) }
    sel = sel.coerceIn(0, project.slideCount - 1)
    val ctx = remember(project) { RenderCtx(project, AppGraph.images, maxDim = 400, editing = false, extras = AppGraph.extras) }

    fun go(i: Int) { sel = i; vm.scrollToSlide(i) }

    SheetFrame("Slides", onDone, Modifier.height(330.dp)) {
        Text(
            "${project.slideCount} of 20 · tap a slide, then use the buttons", Modifier.fillMaxWidth(),
            color = Palette.TextDim, fontSize = 12.sp,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (i in 0 until project.slideCount) {
                val w = 86.dp
                val h = w * (project.ratio.ah.toFloat() / project.ratio.aw)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        Modifier.size(w, h).clip(RoundedCornerShape(3.dp)).clickable { go(i) }
                            .border(if (i == sel) 3.dp else 0.dp, Palette.Accent, RoundedCornerShape(3.dp)),
                    ) {
                        val s = size.width / SLIDE_W
                        clipRect { scale(s, s, Offset.Zero) { translate(-i * SLIDE_W, 0f) { drawScene(ctx) } } }
                    }
                    Text("${i + 1}", Modifier.padding(top = 4.dp), color = if (i == sel) Palette.Accent else Palette.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            ToolButton(Icons.AutoMirrored.Filled.ArrowBack, "Move left", tint = if (sel > 0) Color(0xFFD4D4D8) else Palette.Surface3) {
                if (sel > 0) { vm.commit { moveSlide(it, sel, sel - 1) }; go(sel - 1) }
            }
            ToolButton(Icons.AutoMirrored.Filled.ArrowForward, "Move right", tint = if (sel < project.slideCount - 1) Color(0xFFD4D4D8) else Palette.Surface3) {
                if (sel < project.slideCount - 1) { vm.commit { moveSlide(it, sel, sel + 1) }; go(sel + 1) }
            }
            ToolButton(Icons.Filled.ContentCopy, "Duplicate") {
                if (project.slideCount < 20) { vm.commit { duplicateSlide(it, sel) }; go(sel + 1) }
            }
            ToolButton(Icons.Filled.NoteAdd, "Blank after") {
                if (project.slideCount < 20) { vm.commit { insertSlide(it, sel + 1, it.slides[sel].background) }; go(sel + 1) }
            }
            ToolButton(Icons.Filled.Delete, "Delete", tint = Palette.Danger) {
                if (project.slideCount > 1) { vm.commit { deleteSlide(it, sel) }; go((sel - 1).coerceAtLeast(0)) }
            }
        }
    }
}

