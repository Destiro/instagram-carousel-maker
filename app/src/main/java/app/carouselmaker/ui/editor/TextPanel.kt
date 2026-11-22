package app.carouselmaker.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.render.FontCatalog
import app.carouselmaker.ui.common.Pill
import app.carouselmaker.ui.common.Segmented
import app.carouselmaker.ui.theme.Palette

private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF141414, 0xFFF5F3EE, 0xFFF5C451, 0xFFE8735A, 0xFFD9483B,
    0xFFF28B9B, 0xFFA78BFA, 0xFF5AC8FA, 0xFF3F6FA8, 0xFF8DB57A, 0xFFD4FF3A,
).map { it.toInt() }

/** Edits the selected text item. All edits are live and form a single undo step. */
@Composable
fun TextPanel(vm: EditorViewModel, onDone: () -> Unit) {
    val id = vm.selectedId
    val item = vm.item(id)
    if (id == null || item == null || item.type != ItemType.TEXT) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    LaunchedEffect(id) { vm.beginGesture() }
    DisposableEffect(id) { onDispose { vm.endGesture() } }

    var tab by remember { mutableIntStateOf(0) }
    var cat by remember { mutableStateOf("All") }
    fun edit(f: (Item) -> Item) = vm.liveItem(id, f)

    SheetFrame("Text", onDone, Modifier.height(470.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Bg)
                .border(1.dp, Palette.Surface3, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                item.text, { t -> edit { it.copy(text = t) } },
                textStyle = TextStyle(color = Palette.Text, fontSize = 16.sp), cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp, max = 80.dp),
            )
        }
        Segmented(listOf("Font", "Style", "Colour"), tab, { tab = it }, Modifier.fillMaxWidth().padding(top = 10.dp))
        Box(Modifier.weight(1f).padding(top = 10.dp)) {
            when (tab) {
                0 -> Column {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FontCatalog.categories.forEach { c -> Pill(c, cat == c, { cat = c }) }
                    }
                    LazyColumn(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FontCatalog.all.filter { cat == "All" || it.category == cat }, key = { it.name }) { f ->
                            val on = f.name == item.font
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) Color(0xFF2C2C34) else Color(0xFF202026))
                                    .border(if (on) 2.dp else 0.dp, if (on) Palette.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { edit { it.copy(font = f.name) } }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.text.ifBlank { f.name }, Modifier.weight(1f),
                                    color = Palette.Text, fontFamily = f.family, fontSize = (20 * f.sizeScale).sp, maxLines = 1,
                                )
                                Text(f.name, color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabeledSlider("Size", item.fontSize, 24f, 400f, { v -> edit { it.copy(fontSize = v) } }) {}
                    LabeledSlider("Letter spacing", item.spacing, -5f, 30f, { v -> edit { it.copy(spacing = v) } }) {}
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Align", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Segmented(listOf("Left", "Centre", "Right"), listOf("left", "center", "right").indexOf(item.align), { i -> edit { it.copy(align = listOf("left", "center", "right")[i]) } }, Modifier.width(220.dp))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Effect", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                        listOf("none" to "None", "shadow" to "Shadow", "label" to "Label", "outline" to "Outline").forEach { (k, l) ->
                            Pill(l, item.effect == k, { edit { it.copy(effect = k) } })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ALL CAPS", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Switch(item.caps, { on -> edit { it.copy(caps = on) } }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.OnAccent))
                    }
                }
                else -> LazyVerticalGrid(
                    GridCells.Fixed(6), Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(TEXT_COLORS) { c ->
                        Box(
                            Modifier.testTag("tcolor_$c").aspectRatio(1f).clip(CircleShape).background(Color(c))
                                .border(if (c == item.color) 3.dp else 1.dp, if (c == item.color) Palette.Accent else Color(0x33FFFFFF), CircleShape)
                                .clickable { edit { it.copy(color = c) } },
                        )
                    }
                }
            }
        }
    }
}

fun newTextItem() = Item(
    id = newId(), type = ItemType.TEXT, text = "Your text", font = "Permanent Marker",
    fontSize = 130f, w = 760f, h = 160f, color = 0xFFFFFFFF.toInt(), effect = "shadow", align = "center",
)
