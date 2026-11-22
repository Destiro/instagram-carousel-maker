package app.carouselmaker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.Project
import app.carouselmaker.data.SLIDE_W
import app.carouselmaker.render.RenderCtx
import app.carouselmaker.render.drawScene
import app.carouselmaker.ui.theme.Palette

/** A small non-interactive rendering of a whole project. */
@Composable
fun ProjectThumb(project: Project, modifier: Modifier = Modifier, maxDim: Int = 480) {
    val ctx = remember(project, maxDim) {
        RenderCtx(project, AppGraph.images, maxDim = maxDim, editing = false, extras = AppGraph.extras)
    }
    Canvas(modifier) {
        val sc = minOf(size.width / project.canvasW, size.height / project.canvasH)
        val ox = (size.width - project.canvasW * sc) / 2
        val oy = (size.height - project.canvasH * sc) / 2
        translate(ox, oy) {
            scale(sc, sc, pivot = Offset.Zero) {
                drawScene(ctx)
                // dashed slide cuts
                for (i in 1 until project.slideCount) {
                    drawLine(
                        Color(0x55000000), Offset(i * SLIDE_W, 0f), Offset(i * SLIDE_W, project.canvasH),
                        strokeWidth = 4f / sc,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f / sc, 10f / sc)),
                    )
                }
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Palette.Text, enabled: Boolean = true) {
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) tint else Palette.Surface3)
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (selected) Palette.Accent else Palette.Surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Palette.OnAccent else Color(0xFFD4D4D8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (i == selected) Palette.Surface3 else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 13.sp,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (i == selected) Palette.Text else Palette.TextDim,
                )
            }
        }
    }
}

@Composable
fun SheetHandle() {
    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3F3F46)))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier, color = Palette.TextDim, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp,
    )
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun Column16(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(modifier.padding(horizontal = 16.dp), content = content)
