package app.carouselmaker.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.Ratio
import app.carouselmaker.data.SLIDE_W
import app.carouselmaker.data.createProject
import app.carouselmaker.data.newId
import app.carouselmaker.ui.common.IconBtn
import app.carouselmaker.ui.common.SectionLabel
import app.carouselmaker.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun NewCarouselScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    var ratio by remember { mutableStateOf(Ratio.R4_5) }
    var count by remember { mutableIntStateOf(5) }
    var fromMedia by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun create(uris: List<Uri>) {
        busy = true
        scope.launch {
            val id = newId()
            val media = if (uris.isEmpty()) emptyList() else AppGraph.importer.import(id, uris)
            val name = "Carousel " + java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date())
            val project = createProject(name, ratio, count, media, blank = media.isEmpty(), id = id)
            AppGraph.repo.save(project)
            // navigation must happen on the main thread, whichever thread the import finished on
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                busy = false
                onCreated(project.id)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) create(uris)
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text("New carousel", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // live preview of the strip
                Box(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(140.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Palette.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val h = 96.dp
                        val w = h * (ratio.aw.toFloat() / ratio.ah)
                        val shown = minOf(count, 5)
                        Row(Modifier.clip(RoundedCornerShape(4.dp))) {
                            repeat(shown) { i ->
                                Box(
                                    Modifier.size(w * (if (count > 5) 0.8f else 1f).coerceAtLeast(0.1f), h).background(Palette.Paper)
                                        .border(0.5.dp, Color(0x22000000)),
                                    contentAlignment = Alignment.BottomCenter,
                                ) { Text("${i + 1}", color = Color(0xFF55555E), fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                            }
                        }
                        Text(
                            "$count slides · ${ratio.px} each" + if (count > 5) " (first 5 shown)" else "",
                            color = Palette.TextDim, fontSize = 12.sp,
                        )
                    }
                }

                SectionLabel("Format", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ratio.entries.forEach { r ->
                        val on = r == ratio
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
                                .border(2.dp, if (on) Palette.Accent else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { ratio = r }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(40.dp * (r.aw.toFloat() / r.ah), 40.dp).border(2.dp, if (on) Palette.Accent else Color(0xFF55555E), RoundedCornerShape(4.dp)))
                            }
                            Text(r.label, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(r.title, color = Palette.TextDim, fontSize = 12.sp)
                            Text(r.px, color = Palette.TextDim, fontSize = 11.sp)
                        }
                    }
                }

                SectionLabel("Slides", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Number of slides", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Up to 20, change it later", color = Palette.TextDim, fontSize = 12.sp)
                    }
                    Stepper(Icons.Filled.Remove, "Fewer slides") { count = (count - 1).coerceAtLeast(1) }
                    Text("$count", Modifier.padding(horizontal = 10.dp), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Stepper(Icons.Filled.Add, "More slides") { count = (count + 1).coerceAtMost(20) }
                }

                SectionLabel("Start with", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StartOption(fromMedia, "Pick photos & videos", "Auto-placed into a starter layout") { fromMedia = true }
                    StartOption(!fromMedia, "Blank canvas", "Choose layouts first, fill them later") { fromMedia = false }
                }
                Box(Modifier.height(24.dp))
            }
            Box(Modifier.padding(16.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Palette.Accent).clickable(enabled = !busy) {
                        if (fromMedia) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        } else create(emptyList())
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (fromMedia) "Choose photos & create" else "Create carousel", color = Palette.OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Palette.Accent)
                    Text("Importing your media…", color = Palette.Text)
                }
            }
        }
    }
}

@Composable
private fun Stepper(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Surface2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Palette.Text)
    }
}

@Composable
private fun StartOption(selected: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
            .border(2.dp, if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).border(if (selected) 6.dp else 2.dp, if (selected) Palette.Accent else Color(0xFF55555E), CircleShape))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}
