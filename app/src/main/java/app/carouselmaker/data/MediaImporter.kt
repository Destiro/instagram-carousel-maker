package app.carouselmaker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Copies picked media into the project folder, normalising photos (EXIF rotation, max size). */
class MediaImporter(private val context: Context, private val repo: ProjectRepository) {

    companion object {
        const val MAX_EDGE = 2560

        /** Thumbnail file name for a video file name. */
        fun thumbName(video: String) = video.substringBeforeLast('.') + ".thumb.jpg"
    }

    suspend fun import(projectId: String, uris: List<Uri>): List<ImportedMedia> = withContext(Dispatchers.IO) {
        uris.mapNotNull { runCatching { importOne(projectId, it) }.getOrNull() }
    }

    private fun importOne(projectId: String, uri: Uri): ImportedMedia? {
        val type = context.contentResolver.getType(uri) ?: ""
        return if (type.startsWith("video")) importVideo(projectId, uri) else importPhoto(projectId, uri)
    }

    private fun importPhoto(projectId: String, uri: Uri, keepAlpha: Boolean = false): ImportedMedia? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val scale = MAX_EDGE.toFloat() / max(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        val ext = if (keepAlpha) "png" else "jpg"
        val name = newId() + "." + ext
        FileOutputStream(repo.mediaFile(projectId, name)).use {
            if (keepAlpha) bmp.compress(Bitmap.CompressFormat.PNG, 100, it) else bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        return ImportedMedia(name, bmp.width, bmp.height, false)
    }

    /** Import a PNG (keeps transparency) for use as a custom sticker. */
    suspend fun importSticker(projectId: String, uri: Uri): ImportedMedia? = withContext(Dispatchers.IO) {
        runCatching { importPhoto(projectId, uri, keepAlpha = true) }.getOrNull()
    }

    /** Rotate a photo 90 degrees clockwise into a new file. */
    suspend fun rotate90(projectId: String, name: String): ImportedMedia? = withContext(Dispatchers.IO) {
        runCatching {
            val src = BitmapFactory.decodeFile(repo.mediaFile(projectId, name).absolutePath) ?: return@runCatching null
            val m = Matrix().apply { postRotate(90f) }
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            val png = name.endsWith(".png")
            val newName = newId() + if (png) ".png" else ".jpg"
            FileOutputStream(repo.mediaFile(projectId, newName)).use {
                if (png) out.compress(Bitmap.CompressFormat.PNG, 100, it) else out.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            ImportedMedia(newName, out.width, out.height, false)
        }.getOrNull()
    }

    private fun importVideo(projectId: String, uri: Uri): ImportedMedia? {
        val name = newId() + ".mp4"
        val dst = repo.mediaFile(projectId, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dst).use { out -> input.copyTo(out) }
        } ?: return null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(dst.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val frame = r.getFrameAtTime(minOf(500_000L, dur * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                val s = 1280f / max(frame.width, frame.height)
                val thumb = if (s < 1f) Bitmap.createScaledBitmap(frame, (frame.width * s).toInt(), (frame.height * s).toInt(), true) else frame
                FileOutputStream(File(dst.parentFile, thumbName(name))).use { thumb.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
            return ImportedMedia(name, w, h, true, dur)
        } finally {
            r.release()
        }
    }
}
