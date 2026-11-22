package app.carouselmaker.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.carouselmaker.data.AppGraph
import app.carouselmaker.data.ItemType
import app.carouselmaker.data.Project
import app.carouselmaker.data.SLIDE_W
import app.carouselmaker.data.slideOf
import app.carouselmaker.render.RenderCtx
import app.carouselmaker.render.drawScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ImageFormat(val label: String, val ext: String, val mime: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
}

data class SavedFile(val uri: Uri, val isVideo: Boolean, val slide: Int)

object Exporter {

    /** Render slide [index] at [pxWidth] pixels wide (1080 for the real export). */
    fun renderSlide(project: Project, index: Int, pxWidth: Int, blocking: Boolean, holeItemId: String? = null, skip: Set<String> = emptySet()): Bitmap {
        val s = pxWidth / SLIDE_W
        val w = pxWidth
        val h = (project.canvasH * s).toInt().coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        val canvas = Canvas(image)
        val ctx = RenderCtx(
            project, AppGraph.images,
            maxDim = if (blocking) 2600 else 900,
            editing = false, blocking = true,
            holeItemId = holeItemId, skipIds = skip, extras = AppGraph.extras,
        )
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            scale(s, s, Offset.Zero) {
                translate(-index * SLIDE_W, 0f) { drawScene(ctx) }
            }
        }
        return image.asAndroidBitmap()
    }

    /** Does slide [index] contain a video that should become an MP4? */
    fun videoOn(project: Project, index: Int) = project.items.firstOrNull {
        it.type == ItemType.VIDEO && it.media != null && project.slideOf(it) == index
    }

    private fun folder(project: Project): String {
        val safe = project.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Carousel" }
        return "Carousel Maker/$safe"
    }

    /** Save [bmp] into Pictures/Carousel Maker/<name>. Newer [takenAt] sorts first in the gallery. */
    fun saveBitmap(context: Context, project: Project, bmp: Bitmap, slide: Int, format: ImageFormat, takenAt: Long): Uri? {
        val resolver = context.contentResolver
        val name = "%02d.%s".format(slide + 1, format.ext)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + folder(project))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        resolver.openOutputStream(uri)?.use {
            if (format == ImageFormat.PNG) bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            else bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    fun saveVideo(context: Context, project: Project, file: File, slide: Int, takenAt: Long): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "%02d.mp4".format(slide + 1))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + folder(project))
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    /**
     * Export every slide. Slides with a video become an MP4 (via [VideoExporter]); the rest are images.
     * Files get descending timestamps so slide 1 is the newest item in the gallery.
     */
    suspend fun exportAll(
        context: Context,
        project: Project,
        format: ImageFormat,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<SavedFile> = withContext(Dispatchers.Default) {
        val out = ArrayList<SavedFile>()
        val now = System.currentTimeMillis()
        val n = project.slideCount
        for (i in 0 until n) {
            onProgress(i, n)
            val taken = now - i * 1500L
            val video = videoOn(project, i)
            var done = false
            if (video != null) {
                val file = runCatching { VideoExporter.export(context, project, i, video) }.getOrNull()
                if (file != null) {
                    saveVideo(context, project, file, i, taken)?.let { out += SavedFile(it, true, i); done = true }
                    file.delete()
                }
            }
            if (!done) {
                val bmp = renderSlide(project, i, SLIDE_W.toInt(), blocking = true)
                saveBitmap(context, project, bmp, i, format, taken)?.let { out += SavedFile(it, false, i) }
                bmp.recycle()
            }
        }
        onProgress(n, n)
        out
    }

}
