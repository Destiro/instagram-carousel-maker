package app.carouselmaker.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.Project
import app.carouselmaker.ui.common.IconBtn
import app.carouselmaker.ui.common.ProjectThumb
import app.carouselmaker.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onNew: () -> Unit, onOpen: (String) -> Unit) {
    val repo = AppGraph.repo
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(Unit) { projects = repo.list() }

    Column(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(1f, .65f, .35f).forEach {
                    Box(Modifier.size(7.dp, 20.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Accent.copy(alpha = it)))
                }
            }
            Text("Carousel Maker", Modifier.padding(start = 10.dp), color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Your carousels", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 16.dp),
            color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Palette.Accent).clickable(onClick = onNew).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Bg), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, tint = Palette.Accent, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text("New carousel", color = Palette.OnAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Pick a format, add photos & videos", color = Color(0xFF26262D), fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Saved on this phone", color = Palette.TextDim, fontSize = 13.sp)
        }
        val list = projects
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text("Nothing here yet. Tap New carousel to make your first one.", color = Palette.TextDim, fontSize = 14.sp)
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list ?: emptyList(), key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.clickable { onOpen(p.id) }) {
                    Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Surface)) {
                        ProjectThumb(p, Modifier.fillMaxSize().padding(8.dp))
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                            Text(p.name, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "${p.slideCount} slides · ${p.ratio.label} · " +
                                    DateUtils.getRelativeTimeSpanString(p.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                                color = Palette.TextDim, fontSize = 12.sp, maxLines = 1,
                            )
                        }
                        Box {
                            IconBtn(Icons.Filled.MoreVert, "More", { menu = true }, Modifier.size(36.dp), Palette.TextDim)
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = p })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = p })
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename carousel") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton({
                    scope.launch {
                        repo.save(p.copy(name = name.ifBlank { p.name }))
                        projects = repo.list()
                        renaming = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("This removes the carousel and its photos from the app. Photos in your gallery are not touched.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        repo.delete(p.id)
                        projects = repo.list()
                        deleting = null
                    }
                }) { Text("Delete", color = Palette.Danger) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}
