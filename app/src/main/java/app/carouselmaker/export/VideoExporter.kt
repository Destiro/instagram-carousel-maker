package app.carouselmaker.export

import android.content.Context
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.Item
import app.carouselmaker.data.Project
import app.carouselmaker.data.SLIDE_W
import app.carouselmaker.data.rectOf
import androidx.media3.effect.TextureOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Exports a slide that contains a video as an MP4.
 *
 * The slide is drawn once as a bitmap with a transparent hole where the video goes; the video is
 * stretched to the slide size, then scaled and moved so it covers that hole; finally the bitmap is
 * overlaid so everything around (and on top of) the video appears on top of it.
 */
@OptIn(UnstableApi::class)
object VideoExporter {
    const val MAX_MS = 60_000L

    suspend fun export(context: Context, project: Project, slide: Int, video: Item): File? {
        val name = video.media ?: return null
        val src = AppGraph.repo.mediaFile(project.id, name)
        if (!src.exists()) return null

        val w = SLIDE_W.toInt()
        val h = project.ratio.slideH.toInt()
        val overlay = withContext(Dispatchers.Default) {
            Exporter.renderSlide(project, slide, w, blocking = true, holeItemId = video.id)
        }

        val rect = project.rectOf(video).translate(-slide * SLIDE_W, 0f)
        val aspect = if (video.mh > 0 && video.mw > 0) video.mw.toFloat() / video.mh else rect.width / rect.height
        val dw = max(rect.width, rect.height * aspect)
        val dh = dw / aspect
        val matrix = Matrix().apply {
            postScale(dw / w, dh / h)
            postTranslate(2f * (rect.center.x - w / 2f) / w, -2f * (rect.center.y - h / 2f) / h)
        }

        val start = video.trimStartMs.coerceAtLeast(0)
        var end = if (video.trimEndMs > start) video.trimEndMs else video.durationMs
        if (end <= start) end = start + MAX_MS
        end = minOf(end, start + MAX_MS)

        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(src))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build(),
            )
            .build()
        val effects = listOf<Effect>(
            Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT),
            MatrixTransformation { _ -> matrix },
            OverlayEffect(listOf<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(overlay))),
        )
        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(video.mute)
            .setEffects(Effects(emptyList(), effects))
            .build()
        val out = File(context.cacheDir, "slide_${slide}_${System.currentTimeMillis()}.mp4")

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            overlay.recycle()
                            if (cont.isActive) cont.resume(out)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            overlay.recycle()
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(edited, out.absolutePath)
            }
        }
    }
}
