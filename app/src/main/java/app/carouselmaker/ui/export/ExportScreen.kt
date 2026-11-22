package app.carouselmaker.ui.export

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.ItemType
import app.carouselmaker.data.Project
import app.carouselmaker.export.Exporter
import app.carouselmaker.export.ImageFormat
import app.carouselmaker.export.SavedFile
import app.carouselmaker.ui.common.IconBtn
import app.carouselmaker.ui.common.Pill
import app.carouselmaker.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportScreen(projectId: String, onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    val previews = remember { mutableStateListOf<ImageBitmap?>() }
    var format by remember { mutableStateOf(ImageFormat.JPG) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var saved by remember { mutableStateOf<List<SavedFile>?>(null) }

    LaunchedEffect(projectId) {
        val p = withContext(Dispatchers.IO) { AppGraph.repo.load(projectId) } ?: return@LaunchedEffect
        project = p
        previews.clear()
        repeat(p.slideCount) { previews.add(null) }
        for (i in 0 until p.slideCount) {
            val bmp = withContext(Dispatchers.Default) { Exporter.renderSlide(p, i, 540, blocking = false) }
            previews[i] = bmp.asImageBitmap()
        }
    }

    val p = project
    Column(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", if (saved != null) onHome else onBack)
            Text(if (saved != null) "Saved" else "Export", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (p == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        } else if (saved != null) {
            SavedView(p, saved!!, previews, onHome, onBack, Modifier.weight(1f))
        } else {
            val pager = rememberPagerState { p.slideCount }
            Text(
                "Preview – swipe it like it will look in the feed", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = Palette.TextDim, fontSize = 12.sp,
            )
            HorizontalPager(
                pager, Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
                pageSpacing = 8.dp,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val img = previews.getOrNull(page)
                    if (img != null) {
                        Image(img, "Slide ${page + 1}", Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Fit)
                    } else CircularProgressIndicator(color = Palette.Accent)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(p.slideCount) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp)
                            .clip(CircleShape).background(if (i == pager.currentPage) Palette.Accent else Color(0xFF3F3F46)),
                    )
                }
            }
            val videos = p.items.count { it.type == ItemType.VIDEO && it.media != null }
            Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Bar)) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo format", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImageFormat.entries.forEach { f -> Pill(f.label, f == format, { format = f }) }
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp)
                    Text(p.ratio.px + " px", color = Palette.TextDim, fontSize = 14.sp)
                }
                if (videos > 0) {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Video slides", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp)
                        Text("MP4 · 30 fps", color = Palette.TextDim, fontSize = 14.sp)
                    }
                }
            }
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saving) {
                    LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = Palette.Accent, trackColor = Palette.Surface2)
                    Text("Saving slides…", color = Palette.TextDim, fontSize = 13.sp)
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Palette.Accent).clickable {
                            saving = true
                            scope.launch {
                                val files = Exporter.exportAll(context, p, format) { d, t -> progress = d / t.toFloat() }
                                saving = false
                                if (files.isEmpty()) Toast.makeText(context, "Could not save. Check storage space.", Toast.LENGTH_LONG).show()
                                else saved = files
                            }
                        },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.FileDownload, null, tint = Palette.OnAccent)
                        Text("Save ${p.slideCount} slides to Gallery", Modifier.padding(start = 8.dp), color = Palette.OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text("Pictures › Carousel Maker › ${p.name}", color = Palette.TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SavedView(p: Project, files: List<SavedFile>, previews: List<ImageBitmap?>, onHome: () -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val photos = files.count { !it.isVideo }
    val videos = files.count { it.isVideo }
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 32.dp).size(80.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Palette.OnAccent, modifier = Modifier.size(40.dp))
        }
        Text("Saved to Gallery", Modifier.padding(top = 20.dp), color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append("$photos photo" + if (photos == 1) "" else "s")
                if (videos > 0) append(" + $videos video" + if (videos == 1) "" else "s")
                append(" · Pictures › Carousel Maker")
            },
            Modifier.padding(top = 6.dp), color = Palette.TextDim, fontSize = 14.sp,
        )
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            previews.forEachIndexed { i, img ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Paper)) {
                        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Text("%02d".format(i + 1), Modifier.padding(top = 4.dp), color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Bar).padding(16.dp)) {
            Text(
                "Saved so slide 1 shows first in your gallery. In Instagram, tap them left to right and the order stays right.",
                color = Color(0xFFD4D4D8), fontSize = 13.sp, lineHeight = 19.sp,
            )
        }
        Box(Modifier.weight(1f))
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Palette.Accent).clickable {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                    if (intent != null) context.startActivity(intent)
                    else Toast.makeText(context, "Instagram isn't installed. Your slides are in the Gallery.", Toast.LENGTH_LONG).show()
                },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Text("Open Instagram", color = Palette.OnAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Share…", Modifier.weight(1f)) { share(context, files) }
                SecondaryButton("Back to editor", Modifier.weight(1f), onEdit)
            }
        }
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(52.dp).clip(CircleShape).background(Palette.Surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

private fun share(context: android.content.Context, files: List<SavedFile>) {
    val uris = ArrayList<Uri>(files.map { it.uri })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (files.any { it.isVideo }) "*/*" else "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share carousel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
