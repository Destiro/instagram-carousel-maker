package app.carouselmaker.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette as PaletteIcon
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.carouselmaker.data.*
import app.carouselmaker.render.RenderCtx
import app.carouselmaker.render.drawScene
import app.carouselmaker.ui.common.IconBtn
import app.carouselmaker.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit, onExport: (String) -> Unit = {}) {
    val vm: EditorViewModel = viewModel(key = projectId, factory = viewModelFactory { initializer { EditorViewModel(projectId) } })
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingCell by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var cropping by remember { mutableStateOf<String?>(null) }

    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(40)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            val media = AppGraph.importer.import(projectId, uris)
            val slide = vm.currentSlide()
            vm.commit { addMedia(it, media, slide) }
            busy = false
        }
    }
    val onePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cell = pendingCell
        val rep = replacing
        pendingCell = null
        replacing = null
        if (uri != null) scope.launch {
            busy = true
            val m = AppGraph.importer.import(projectId, listOf(uri)).firstOrNull()
            if (m != null) {
                if (cell != null) vm.commit { fillPlaceholder(it, cell, m) }
                else if (rep != null) vm.commit { p ->
                    val old = p.items.firstOrNull { it.id == rep }
                    if (old == null) p else fillPlaceholder(p, rep, m)
                }
            }
            busy = false
        }
    }

    val project = vm.project
    val cropId = cropping
    if (cropId != null) {
        CropOverlay(vm, cropId) { cropping = null }
        return
    }
    Column(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding()) {
        TopBar(vm, onBack) { vm.saveNow(); onExport(projectId) }
        Box(Modifier.weight(1f).fillMaxWidth().background(Palette.Bg2)) {
            if (project == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
            } else {
                EditorCanvas(vm, onPlaceholderTap = { id ->
                    pendingCell = id
                    onePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                })
            }
            if (busy) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
        }
        if (project != null) {
            SlideStrip(vm, project)
            val sel = vm.item(vm.selectedId)
            if (panel != Panel.NONE) {
                PanelHost(vm, panel) { panel = Panel.NONE }
            } else if (sel != null) {
                ItemBar(vm, sel,
                    onEditText = { panel = Panel.TEXT },
                    onCrop = { cropping = sel.id },
                    onReplace = {
                        replacing = sel.id
                        onePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    })
            } else {
                MainBar(
                    onMedia = { addPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onPanel = { panel = it },
                    onText = { vm.addAtCenter(newTextItem()); panel = Panel.TEXT },
                )
            }
        }
    }
}

@Composable
private fun TopBar(vm: EditorViewModel, onBack: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", { vm.saveNow(); onBack() })
        Column(Modifier.weight(1f)) {
            Text(vm.project?.name ?: "", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            vm.project?.let { Text("${it.slideCount} slides · ${it.ratio.label}", color = Palette.TextDim, fontSize = 11.sp) }
        }
        IconBtn(Icons.AutoMirrored.Filled.Undo, "Undo", { vm.undo() }, enabled = vm.canUndo)
        IconBtn(Icons.AutoMirrored.Filled.Redo, "Redo", { vm.redo() }, enabled = vm.canRedo)
        Row(
            Modifier.padding(start = 4.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Accent)
                .clickable(onClick = onExport).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FileDownload, null, tint = Palette.OnAccent, modifier = Modifier.size(16.dp))
            Text("Export", Modifier.padding(start = 6.dp), color = Palette.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SlideStrip(vm: EditorViewModel, project: Project) {
    val current = vm.currentSlide()
    val ctx = remember(project) { RenderCtx(project, AppGraph.images, maxDim = 260, editing = false, extras = AppGraph.extras) }
    Row(
        Modifier.fillMaxWidth().height(62.dp).background(Palette.Bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            itemsIndexed(List(project.slideCount) { it }) { _, i ->
                val w = 38.dp
                val h = w * (project.ratio.ah.toFloat() / project.ratio.aw)
                Canvas(
                    Modifier.size(w, h).clickable { vm.scrollToSlide(i) }
                        .border(if (i == current) 2.dp else 0.dp, Palette.Accent, RoundedCornerShape(2.dp)),
                ) {
                    val s = size.width / SLIDE_W
                    clipRect {
                        scale(s, s, Offset.Zero) { translate(-i * SLIDE_W, 0f) { drawScene(ctx) } }
                    }
                }
            }
        }
        IconBtn(Icons.Filled.Add, "Add slide", { vm.addSlide(vm.currentSlide()) }, tint = Palette.TextDim)
    }
}

@Composable
private fun MainBar(onMedia: () -> Unit, onPanel: (Panel) -> Unit, onText: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(Palette.Bar).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(Icons.Filled.AddPhotoAlternate, "Media", onClick = onMedia)
        ToolButton(Icons.Filled.TextFields, "Text", onClick = onText)
        ToolButton(Icons.Filled.GridView, "Layout") { onPanel(Panel.LAYOUTS) }
        ToolButton(Icons.Filled.Tune, "Spacing") { onPanel(Panel.SPACING) }
        ToolButton(Icons.Filled.EmojiEmotions, "Stickers") { onPanel(Panel.STICKERS) }
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, tint: Color = Color(0xFFD4D4D8), onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).height(62.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, Modifier.padding(top = 5.dp), color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ItemBar(vm: EditorViewModel, item: Item, onEditText: () -> Unit, onCrop: () -> Unit, onReplace: () -> Unit) {
    val title = when (item.type) {
        ItemType.PHOTO -> if (item.media == null) "Empty cell" else "Photo"
        ItemType.VIDEO -> "Video"
        ItemType.TEXT -> "Text"
        ItemType.STICKER -> "Sticker"
        ItemType.TAPE -> "Tape"
        ItemType.PAPER -> "Paper"
    }
    Column(Modifier.fillMaxWidth().background(Palette.Bar).padding(top = 8.dp, bottom = 10.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.selectedId = null }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
                Text("Done", Modifier.padding(start = 6.dp), color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (item.type == ItemType.PHOTO && item.media != null) ToolButton(Icons.Filled.Crop, "Crop", onClick = onCrop)
            if (item.type == ItemType.PHOTO || item.type == ItemType.VIDEO) ToolButton(Icons.Filled.SwapHoriz, "Replace", onClick = onReplace)
            if (item.type == ItemType.TEXT) ToolButton(Icons.Filled.Edit, "Edit", onClick = onEditText)
            if (item.type == ItemType.STICKER) {
                ToolButton(Icons.Filled.CropSquare, "Outline", tint = if (item.outline) Palette.Accent else Color(0xFFD4D4D8)) { vm.mutate(item.id) { it.copy(outline = !it.outline) } }
                ToolButton(Icons.Filled.FilterNone, "Shadow", tint = if (item.shadow) Palette.Accent else Color(0xFFD4D4D8)) { vm.mutate(item.id) { it.copy(shadow = !it.shadow) } }
                ToolButton(Icons.Filled.PaletteIcon, "Tint", tint = item.tint?.let { Color(it) } ?: Color(0xFFD4D4D8)) {
                    val i = TINTS.indexOf(item.tint)
                    vm.mutate(item.id) { it.copy(tint = TINTS[(i + 1) % TINTS.size]) }
                }
            }
            if (item.type == ItemType.TAPE) {
                ToolButton(Icons.Filled.Style, "Style") {
                    val ids = app.carouselmaker.render.TapeStyles.all.map { it.id }
                    val i = ids.indexOf(item.style)
                    vm.mutate(item.id) { it.copy(style = ids[(i + 1) % ids.size]) }
                }
            }
            if (item.type == ItemType.STICKER || item.type == ItemType.TAPE || item.type == ItemType.PAPER) {
                ToolButton(Icons.Filled.Opacity, "Opacity " + (item.opacity * 100).toInt() + "%") {
                    val next = when { item.opacity > 0.9f -> 0.75f; item.opacity > 0.7f -> 0.5f; item.opacity > 0.45f -> 0.25f; else -> 1f }
                    vm.mutate(item.id) { it.copy(opacity = next) }
                }
            }
            ToolButton(Icons.Filled.Flip, "Flip") { vm.flipSelected() }
            ToolButton(Icons.Filled.Layers, "Front") { vm.bringToFront() }
            ToolButton(Icons.Filled.Layers, "Back") { vm.sendToBack() }
            ToolButton(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateSelected() }
            ToolButton(Icons.Filled.Delete, "Delete", tint = Palette.Danger) { vm.deleteSelected() }
        }
    }
}

@Composable
private fun PanelHost(vm: EditorViewModel, panel: Panel, onClose: () -> Unit) {
    when (panel) {
        Panel.LAYOUTS -> LayoutsPanel(vm, onClose)
        Panel.SPACING -> SpacingPanel(vm, onClose)
        Panel.STICKERS -> StickerPanel(vm, onClose)
        Panel.TEXT -> TextPanel(vm, onClose)
        else -> onClose()
    }
}

private val TINTS: List<Int?> = listOf(null, 0xFF141414.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9483B.toInt(), 0xFF3F6FA8.toInt(), 0xFF4F7A57.toInt(), 0xFFF5C451.toInt())
