package app.carouselmaker.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.*
import app.carouselmaker.render.PaperKinds
import app.carouselmaker.render.TapeStyles
import app.carouselmaker.render.drawPaper
import app.carouselmaker.render.drawTape
import app.carouselmaker.ui.common.Pill
import app.carouselmaker.ui.common.Segmented
import app.carouselmaker.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun StickerPanel(vm: EditorViewModel, onDone: () -> Unit) {
    val project = vm.project ?: return
    val extras = AppGraph.extras ?: return
    val lib = extras.stickers.library
    extras.stickers.version
    var tab by remember { mutableIntStateOf(0) }
    var pack by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val m = AppGraph.importer.importSticker(project.id, uri) ?: return@launch
            val s = 320f
            vm.addAtCenter(Item(id = newId(), type = ItemType.STICKER, media = m.name, mw = m.width, mh = m.height, w = s, h = s * m.height / m.width))
            onDone()
        }
    }

    SheetFrame("Add to canvas", onDone, Modifier.height(430.dp)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Bg).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            Box(Modifier.padding(start = 8.dp).weight(1f)) {
                if (query.isEmpty()) Text("Search – try “york”, “coffee”, “medal”", color = Palette.TextDim, fontSize = 14.sp, maxLines = 1)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp), cursorBrush = SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Segmented(listOf("Stickers", "Tape", "Paper"), tab, { tab = it }, Modifier.fillMaxWidth().padding(top = 8.dp))

        when (tab) {
            0 -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("All", pack == null, { pack = null })
                    lib.packs.forEach { p -> Pill(p.name.substringBefore(" ·"), pack == p.id, { pack = p.id }) }
                }
                val stickers = lib.packs.filter { pack == null || it.id == pack }.flatMap { p -> p.stickers.map { p to it } }
                    .filter { (_, s) -> query.isBlank() || s.name.contains(query.trim(), ignoreCase = true) }
                LazyVerticalGrid(
                    GridCells.Fixed(4), Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(4) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Surface2)
                                    .clickable { pngPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, null, tint = Palette.Text, modifier = Modifier.size(18.dp))
                                Text("Import PNG", Modifier.padding(start = 6.dp), color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Look", Modifier.weight(1f), color = Palette.TextDim, fontSize = 13.sp)
                            Segmented(
                                listOf("Wash", "Ink", "Pencil"),
                                project.look.ordinal, { vm.commit { p -> p.copy(look = StickerLook.entries[it]) } },
                                Modifier.width(190.dp),
                            )
                        }
                    }
                    items(stickers, key = { it.second.id }) { (_, s) ->
                        val img = extras.stickers.get(s.id, project.look, 200)
                        Box(
                            Modifier.testTag("sticker_${s.id}").aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Palette.Paper)
                                .clickable {
                                    vm.addAtCenter(Item(id = newId(), type = ItemType.STICKER, stickerId = s.id, w = 320f, h = 320f))
                                    onDone()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (img != null) Image(img, s.name, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
            1 -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TapeStyles.all, key = { it.id }) { t ->
                    Box(
                        Modifier.testTag("tape_${t.id}").height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A3A42)).clickable {
                            vm.addAtCenter(Item(id = newId(), type = ItemType.TAPE, style = t.id, w = 380f, h = 90f, rotation = -4f))
                            onDone()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(120.dp, 30.dp)) { drawTape(Item(id = "t", type = ItemType.TAPE, style = t.id), Rect(Offset.Zero, size)) }
                    }
                }
            }
            else -> LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PaperKinds.all, key = { it.id }) { k ->
                    Box(
                        Modifier.testTag("paper_${k.id}").height(96.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A3A42)).clickable {
                            val (w, h) = paperSize(k.id)
                            vm.addAtCenter(Item(id = newId(), type = ItemType.PAPER, style = k.id, w = w, h = h, rotation = 2f))
                            onDone()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        val (pw, ph) = paperSize(k.id)
                        val s = 64f / maxOf(pw, ph)
                        Canvas(Modifier.size((pw * s).dp, (ph * s).dp)) {
                            drawPaper(Item(id = "p", type = ItemType.PAPER, style = k.id), Rect(Offset.Zero, size))
                        }
                    }
                }
            }
        }
    }
}

fun paperSize(kind: String): Pair<Float, Float> = when (kind) {
    "notebook" -> 480f to 620f
    "polaroid" -> 440f to 540f
    "ticket" -> 520f to 220f
    "sticky" -> 360f to 360f
    "tag" -> 420f to 220f
    else -> 460f to 340f
}
