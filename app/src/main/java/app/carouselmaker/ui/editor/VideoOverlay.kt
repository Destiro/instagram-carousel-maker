package app.carouselmaker.ui.editor

import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.carouselmaker.data.AppGraph
import app.carouselmaker.ui.theme.Palette

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoOverlay(vm: EditorViewModel, itemId: String, onClose: () -> Unit) {
    val project = vm.project ?: return onClose()
    val item = vm.item(itemId) ?: return onClose()
    val name = item.media ?: return onClose()
    val context = LocalContext.current
    val duration = item.durationMs.coerceAtLeast(1000L)

    var range by remember { mutableStateOf(item.trimStartMs.toFloat()..(if (item.trimEndMs > item.trimStartMs) item.trimEndMs else duration).toFloat()) }
    var mute by remember { mutableStateOf(item.mute) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL; volume = if (item.mute) 0f else 1f }
    }
    fun load(r: ClosedFloatingPointRange<Float>) {
        val mi = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(AppGraph.repo.mediaFile(project.id, name)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(r.start.toLong()).setEndPositionMs(r.endInclusive.toLong()).build(),
            ).build()
        player.setMediaItem(mi)
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(Unit) {
        load(range)
        onDispose { player.release() }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF08080A)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Cancel", color = Color(0xFFD4D4D8), fontSize = 15.sp)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Trim video", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Accent).clickable {
                    vm.mutate(itemId) { it.copy(trimStartMs = range.start.toLong(), trimEndMs = range.endInclusive.toLong(), mute = mute) }
                    onClose()
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Done", color = Palette.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }

        Box(Modifier.padding(16.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${fmt(range.start.toLong())} – ${fmt(range.endInclusive.toLong())}", Modifier.weight(1f), color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Palette.Surface).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text("%.1f s".format((range.endInclusive - range.start) / 1000f), color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        RangeSlider(
            value = range,
            onValueChange = { r ->
                // keep at least one second and at most 60 s (Instagram's limit)
                var s = r.start; var e = r.endInclusive
                if (e - s < 1000f) { if (s != range.start) s = e - 1000f else e = s + 1000f }
                if (e - s > 60_000f) { if (s != range.start) s = e - 60_000f else e = s + 60_000f }
                range = s.coerceAtLeast(0f)..e.coerceAtMost(duration.toFloat())
            },
            valueRange = 0f..duration.toFloat(),
            onValueChangeFinished = { load(range) },
            modifier = Modifier.padding(horizontal = 16.dp),
            colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Surface3),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", color = Palette.TextDim, fontSize = 11.sp)
            Text(fmt(duration), color = Palette.TextDim, fontSize = 11.sp)
        }

        Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Bar).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sound", Modifier.weight(1f), color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(
                !mute, { on -> mute = !on; player.volume = if (mute) 0f else 1f },
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.OnAccent),
            )
        }
        Text(
            "This slide exports as an MP4 (${project.ratio.px.replace('×', 'x')}). Everything else on the slide stays still around the clip.",
            Modifier.padding(horizontal = 20.dp), color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }
}
